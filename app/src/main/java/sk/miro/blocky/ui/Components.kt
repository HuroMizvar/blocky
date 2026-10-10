package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import sk.miro.blocky.domain.Category
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

data class CatStyle(val abbr: String, val bg: Color, val fg: Color)

fun catStyle(c: Category): CatStyle = when (c) {
    Category.MASO -> CatStyle("MA", Color(0xFFFCE7E2), Color(0xFFB3361B))
    Category.MLIECNE -> CatStyle("ML", Color(0xFFE4ECF7), Color(0xFF2F5E9E))
    Category.PECIVO -> CatStyle("PE", Color(0xFFFDF0DB), Color(0xFF9A5B06))
    Category.OVOCIE_ZELENINA -> CatStyle("OZ", Color(0xFFE3F1E8), Color(0xFF1F7A4D))
    Category.TRVANLIVE -> CatStyle("TR", Color(0xFFF1EEE6), Color(0xFF6B5D3F))
    Category.MRAZENE_HOTOVE -> CatStyle("HJ", Color(0xFFE6F0F3), Color(0xFF2D6475))
    Category.NAPOJE -> CatStyle("NA", Color(0xFFE2F2F7), Color(0xFF1F6A86))
    Category.ALKOHOL -> CatStyle("AL", Color(0xFFF5E6EC), Color(0xFF9C3A5E))
    Category.TABAK -> CatStyle("TA", Color(0xFFECE9E4), Color(0xFF5E564C))
    Category.SLADKOSTI -> CatStyle("SL", Color(0xFFFBE8F2), Color(0xFFA23D78))
    Category.DROGERIA -> CatStyle("DR", Color(0xFFEEEAF7), Color(0xFF5B4A9E))
    Category.DOMACNOST -> CatStyle("DO", Color(0xFFEAF0E6), Color(0xFF4D6B37))
    Category.ZDRAVIE -> CatStyle("ZD", Color(0xFFE3F3F1), Color(0xFF1D7268))
    Category.RESTAURACIE -> CatStyle("RE", Color(0xFFFBEADF), Color(0xFFB4531F))
    Category.PHM -> CatStyle("PH", Color(0xFFE7E9F8), Color(0xFF3C4BA0))
    Category.ZALOHY -> CatStyle("ZÁ", Color(0xFFEFEFEA), Color(0xFF55605A))
    Category.ZLAVY -> CatStyle("ZĽ", Color(0xFFE3F1E8), Color(0xFF1F7A4D))
    Category.INE -> CatStyle("IN", Color(0xFFEFEFEA), Color(0xFF55605A))
}

@Composable
fun CatBadge(c: Category, size: Int = 38) {
    val s = catStyle(c)
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(s.bg), contentAlignment = Alignment.Center) {
        Text(s.abbr, color = s.fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
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
