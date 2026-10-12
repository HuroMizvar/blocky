package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.miro.blocky.R
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.data.MerchantEntity
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.MerchantType
import sk.miro.blocky.domain.Severity
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

val SK = Locale.forLanguageTag("sk-SK")

/** Paleta smeru A – „Svetlý a pokojný“. */
object Pal {
    val Bg = Color(0xFFF6F5F1)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF17201B)
    val Muted = Color(0xFF55605A)
    val Faint = Color(0xFFA3A9A5)
    val Line = Color(0xFFECEAE4)
    val Track = Color(0xFFF0EEE8)
    val Neutral = Color(0xFFE6E4DD)
    val Green = Color(0xFF1F7A4D)
    val GreenSoft = Color(0xFFE3F1E8)
    val Red = Color(0xFFB3361B)
    val RedSoft = Color(0xFFFCE7E2)
    val Amber = Color(0xFF9A5B06)
    val AmberSoft = Color(0xFFFDF0DB)
    val Camera = Color(0xFF1B1D1C)
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold)
)

private val Colors = lightColorScheme(
    primary = Pal.Green, onPrimary = Color.White,
    primaryContainer = Pal.GreenSoft, onPrimaryContainer = Pal.Green,
    secondary = Pal.Ink, onSecondary = Color.White,
    tertiaryContainer = Pal.AmberSoft, onTertiaryContainer = Pal.Amber,
    background = Pal.Bg, onBackground = Pal.Ink,
    surface = Pal.Bg, onSurface = Pal.Ink,
    surfaceVariant = Pal.Track, onSurfaceVariant = Pal.Muted,
    surfaceContainer = Pal.Card, surfaceContainerHigh = Pal.Card, surfaceContainerLow = Pal.Card,
    surfaceContainerLowest = Pal.Card, surfaceContainerHighest = Pal.Neutral,
    outline = Pal.Faint, outlineVariant = Pal.Line,
    error = Pal.Red, onError = Color.White, errorContainer = Pal.RedSoft
)

private fun Typography.withFont(): Typography {
    fun TextStyle.j() = copy(fontFamily = Jakarta)
    return copy(
        displayLarge = displayLarge.j(), displayMedium = displayMedium.j(), displaySmall = displaySmall.j(),
        headlineLarge = headlineLarge.j(), headlineMedium = headlineMedium.j(), headlineSmall = headlineSmall.j(),
        titleLarge = titleLarge.j(), titleMedium = titleMedium.j(), titleSmall = titleSmall.j(),
        bodyLarge = bodyLarge.j(), bodyMedium = bodyMedium.j(), bodySmall = bodySmall.j(),
        labelLarge = labelLarge.j(), labelMedium = labelMedium.j(), labelSmall = labelSmall.j()
    )
}

@Composable
fun BlockyTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Colors, typography = Typography().withFont(), content = content)

fun eur(c: Long) = Analytics.eur(c)

fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(JTextStyle.FULL_STANDALONE, SK).replaceFirstChar { it.uppercase() } + " ${m.year}"

fun monthName(m: YearMonth): String =
    m.month.getDisplayName(JTextStyle.FULL_STANDALONE, SK).replaceFirstChar { it.uppercase() }

/** Biela zaoblená karta – základný stavebný prvok dizajnu. */
@Composable
fun BCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), color: Color = Pal.Card,
          content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(color).padding(padding), content = content)
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Pal.Ink)
        actions()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        trailing()
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, months: List<YearMonth>, onSelect: (YearMonth) -> Unit,
                  actions: @Composable RowScope.() -> Unit = {}) {
    val idx = months.indexOf(month)
    val canPrev = idx in 0 until months.size - 1
    val canNext = idx > 0
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { months.getOrNull(idx + 1)?.let(onSelect) }, enabled = canPrev) {
            Icon(Icons.Default.ChevronLeft, "Predchádzajúci mesiac", tint = if (canPrev) Pal.Ink else Pal.Faint)
        }
        Text(monthLabel(month), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        IconButton(onClick = { months.getOrNull(idx - 1)?.let(onSelect) }, enabled = canNext) {
            Icon(Icons.Default.ChevronRight, "Nasledujúci mesiac", tint = if (canNext) Pal.Ink else Pal.Faint)
        }
        actions()
    }
}

@Composable
fun ProgressTrack(fraction: Float, color: Color, height: Int = 6, track: Color = Pal.Track) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height.dp)).background(color))
    }
}

@Composable
fun Tag(text: String, fg: Color, bg: Color) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** Ikony: Phosphor Icons (duotone), licencia MIT – res/drawable/ph_*.xml */
data class CatStyle(val bg: Color, val fg: Color, @DrawableRes val icon: Int)

fun catStyle(c: Category): CatStyle = when (c) {
    Category.MASO -> CatStyle(Color(0xFFFCE7E2), Color(0xFFB3361B), R.drawable.ph_cow)
    Category.MLIECNE -> CatStyle(Color(0xFFE4ECF7), Color(0xFF2F5E9E), R.drawable.ph_cheese)
    Category.PECIVO -> CatStyle(Color(0xFFFDF0DB), Color(0xFF9A5B06), R.drawable.ph_bread)
    Category.OVOCIE_ZELENINA -> CatStyle(Color(0xFFE3F1E8), Color(0xFF1F7A4D), R.drawable.ph_carrot)
    Category.TRVANLIVE -> CatStyle(Color(0xFFF1EEE6), Color(0xFF6B5D3F), R.drawable.ph_grains)
    Category.MRAZENE_HOTOVE -> CatStyle(Color(0xFFE6F0F3), Color(0xFF2D6475), R.drawable.ph_snowflake)
    Category.NAPOJE -> CatStyle(Color(0xFFE2F2F7), Color(0xFF1F6A86), R.drawable.ph_drop)
    Category.ALKOHOL -> CatStyle(Color(0xFFF5E6EC), Color(0xFF9C3A5E), R.drawable.ph_wine)
    Category.TABAK -> CatStyle(Color(0xFFECE9E4), Color(0xFF5E564C), R.drawable.ph_cigarette)
    Category.SLADKOSTI -> CatStyle(Color(0xFFFBE8F2), Color(0xFFA23D78), R.drawable.ph_cookie)
    Category.DROGERIA -> CatStyle(Color(0xFFEEEAF7), Color(0xFF5B4A9E), R.drawable.ph_hand_soap)
    Category.DOMACNOST -> CatStyle(Color(0xFFEAF0E6), Color(0xFF4D6B37), R.drawable.ph_broom)
    Category.ZDRAVIE -> CatStyle(Color(0xFFE3F3F1), Color(0xFF1D7268), R.drawable.ph_pill)
    Category.ZVIERATA -> CatStyle(Color(0xFFF3ECE3), Color(0xFF7A5230), R.drawable.ph_paw_print)
    Category.RESTAURACIE -> CatStyle(Color(0xFFFBEADF), Color(0xFFB4531F), R.drawable.ph_fork_knife)
    Category.UBYTOVANIE -> CatStyle(Color(0xFFE8EAF6), Color(0xFF3F4A9A), R.drawable.ph_bed)
    Category.VOLNY_CAS -> CatStyle(Color(0xFFE0F2EE), Color(0xFF1F6F5F), R.drawable.ph_flower_lotus)
    Category.OBLECENIE -> CatStyle(Color(0xFFF6E8F0), Color(0xFF8A3F6B), R.drawable.ph_t_shirt)
    Category.ELEKTRONIKA -> CatStyle(Color(0xFFE7EBF0), Color(0xFF3E5468), R.drawable.ph_device_mobile)
    Category.SLUZBY -> CatStyle(Color(0xFFEFECE6), Color(0xFF5E5547), R.drawable.ph_wrench)
    Category.PHM -> CatStyle(Color(0xFFE7E9F8), Color(0xFF3C4BA0), R.drawable.ph_gas_pump)
    Category.ZALOHY -> CatStyle(Color(0xFFE3F1E8), Color(0xFF2E6B47), R.drawable.ph_recycle)
    Category.ZLAVY -> CatStyle(Color(0xFFE3F1E8), Color(0xFF1F7A4D), R.drawable.ph_tag)
    Category.INE -> CatStyle(Color(0xFFEFEFEA), Color(0xFF55605A), R.drawable.ph_dots_three)
}

@Composable
fun CatBadge(c: Category, size: Int = 38) {
    val s = catStyle(c)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(s.bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(s.icon), null, tint = s.fg, modifier = Modifier.size((size * 0.6f).dp))
    }
}

data class MerchantStyle(val bg: Color, val fg: Color, @DrawableRes val icon: Int)

/** Ikona prevádzky podľa jej typu (namiesto log značiek – tie sú ochranné známky). */
fun merchantStyle(t: MerchantType): MerchantStyle = when (t) {
    MerchantType.GROCERY -> MerchantStyle(Color(0xFFE3F1E8), Color(0xFF1F7A4D), R.drawable.ph_basket)
    MerchantType.FUEL -> MerchantStyle(Color(0xFFE7E9F8), Color(0xFF3C4BA0), R.drawable.ph_gas_pump)
    MerchantType.RESTAURANT -> MerchantStyle(Color(0xFFFBEADF), Color(0xFFB4531F), R.drawable.ph_fork_knife)
    MerchantType.HOTEL -> MerchantStyle(Color(0xFFE8EAF6), Color(0xFF3F4A9A), R.drawable.ph_bed)
    MerchantType.WELLNESS -> MerchantStyle(Color(0xFFE0F2EE), Color(0xFF1F6F5F), R.drawable.ph_flower_lotus)
    MerchantType.PHARMACY -> MerchantStyle(Color(0xFFE3F3F1), Color(0xFF1D7268), R.drawable.ph_first_aid_kit)
    MerchantType.DRUGSTORE -> MerchantStyle(Color(0xFFEEEAF7), Color(0xFF5B4A9E), R.drawable.ph_spray_bottle)
    MerchantType.HOME -> MerchantStyle(Color(0xFFEAF0E6), Color(0xFF4D6B37), R.drawable.ph_house)
    MerchantType.CLOTHING -> MerchantStyle(Color(0xFFF6E8F0), Color(0xFF8A3F6B), R.drawable.ph_t_shirt)
    MerchantType.ELECTRONICS -> MerchantStyle(Color(0xFFE7EBF0), Color(0xFF3E5468), R.drawable.ph_device_mobile)
    MerchantType.SERVICES -> MerchantStyle(Color(0xFFEFECE6), Color(0xFF5E5547), R.drawable.ph_wrench)
    MerchantType.OTHER -> MerchantStyle(Color(0xFFEFEFEA), Color(0xFF55605A), R.drawable.ph_storefront)
    MerchantType.UNKNOWN -> MerchantStyle(Color(0xFFFDF0DB), Color(0xFF9A5B06), R.drawable.ph_question)
}

@Composable
fun MerchantBadge(t: MerchantType, size: Int = 40) {
    val s = merchantStyle(t)
    Box(Modifier.size(size.dp).clip(CircleShape).background(s.bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(s.icon), t.label, tint = s.fg, modifier = Modifier.size((size * 0.56f).dp))
    }
}

/** Otázka „Čo je to za prevádzku?“ – zobrazí sa, keď ju aplikácia nevie zaradiť. */
@Composable
fun MerchantTypeDialog(m: MerchantEntity, onPick: (MerchantType) -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Pal.Card,
        title = {
            Column {
                Text("Čo je to za prevádzku?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(m.name, fontSize = 14.sp, color = Pal.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Podľa toho správne zaradím tento aj ďalšie bločky z nej. Opýtam sa len raz.",
                    fontSize = 13.sp, color = Pal.Muted)
                MerchantType.choices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            val st = merchantStyle(t)
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(st.bg)
                                .clickable { onPick(t) }.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(st.icon), null, tint = st.fg, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(t.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.Ink,
                                    maxLines = 2, lineHeight = 16.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onLater) { Text("Neskôr") } }
    )
}

/** Riadok kategórie: odznak, názov, suma a pruh. */
@Composable
fun CategoryRow(c: Category, cents: Long, maxCents: Long, sub: String? = null, over: Boolean = false) {
    val s = catStyle(c)
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        CatBadge(c)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(c.label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(eur(cents), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (over) Pal.Red else Pal.Ink)
            }
            ProgressTrack(if (maxCents > 0) cents.toFloat() / maxCents else 0f, if (over) Pal.Red else s.fg)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Pal.Muted)
        }
    }
}

fun severityStyle(s: Severity): Triple<String, Color, Color> = when (s) {
    Severity.HIGH -> Triple("Prekročené", Pal.Red, Pal.RedSoft)
    Severity.MEDIUM -> Triple("Pozor", Pal.Amber, Pal.AmberSoft)
    Severity.INFO -> Triple("Tip", Pal.Green, Pal.GreenSoft)
}

fun deltaPct(cur: Long, ref: Long?): Int? =
    if (ref == null || ref == 0L) null else Math.round((cur - ref).toDouble() / ref * 100).toInt()

fun deltaText(cur: Long, ref: Long?, refLabel: String): String? =
    deltaPct(cur, ref)?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + "${kotlin.math.abs(it)} % vs. $refLabel" }

/** Pozadie celej obrazovky + bočné okraje. */
@Composable
fun ScreenBox(contentPadding: PaddingValues, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Pal.Bg).padding(top = contentPadding.calculateTopPadding()), content = content)
}

@Composable
fun SectionTitle(text: String) = SectionHeader(text)
