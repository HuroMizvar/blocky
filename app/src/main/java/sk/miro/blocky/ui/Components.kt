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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
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
import sk.miro.blocky.domain.CustomCategories
import sk.miro.blocky.domain.MerchantType
import sk.miro.blocky.domain.Severity
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

val SK = Locale.forLanguageTag("sk-SK")
private val EN = Locale.forLanguageTag("en-GB")
/** jazyk pre názvy mesiacov a dní */
fun uiLocale(): Locale = if (sk.miro.blocky.domain.I18n.english) EN else SK

/**
 * Text s prekladom: v celom balíku ui sa namiesto material3.Text použije tento (rovnaké parametre),
 * takže sa preloží každý text v aplikácii. Názvy položiek z bločkov v slovníku nie sú, ostanú bez zmeny.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current
) = androidx.compose.material3.Text(
    sk.miro.blocky.domain.I18n.tr(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing,
    textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines, null, style
)

/** preklad textu mimo Text (napr. popis ikony) */
fun t(s: String): String = sk.miro.blocky.domain.I18n.tr(s)

/** Farebné témy – vyberá si ich každý používateľ zvlášť (Nastavenia), nesynchronizujú sa. */
enum class ThemeId(val label: String) { PAPER("Pokojný papier"), FRESH("Svieža zelená"), NIGHT("Nočný režim") }

class Palette(
    val bg: Color, val card: Color, val ink: Color, val muted: Color, val faint: Color, val line: Color,
    val track: Color, val neutral: Color, val green: Color, val greenSoft: Color, val onGreen: Color,
    val red: Color, val redSoft: Color, val amber: Color, val amberSoft: Color, val camera: Color,
    /** tmavé „hero“ karty (AI analýza, automatické rozpočty) */
    val hero: Color, val onHero: Color, val onHeroMuted: Color,
    /** hlavná karta „Minuté tento mesiac“ */
    val total: Color, val onTotal: Color, val onTotalMuted: Color, val totalTrack: Color,
    /** zvýraznenie (Svieža zelená: žltá) */
    val highlight: Color, val onHighlight: Color,
    val dark: Boolean
)

private val PAPER = Palette(
    bg = Color(0xFFF6F5F1), card = Color(0xFFFFFFFF), ink = Color(0xFF17201B), muted = Color(0xFF55605A),
    faint = Color(0xFFA3A9A5), line = Color(0xFFECEAE4), track = Color(0xFFF0EEE8), neutral = Color(0xFFE6E4DD),
    green = Color(0xFF1F7A4D), greenSoft = Color(0xFFE3F1E8), onGreen = Color.White,
    red = Color(0xFFB3361B), redSoft = Color(0xFFFCE7E2), amber = Color(0xFF9A5B06), amberSoft = Color(0xFFFDF0DB),
    camera = Color(0xFF1B1D1C), hero = Color(0xFF17201B), onHero = Color.White, onHeroMuted = Color(0xFFC9D2CD),
    total = Color(0xFFFFFFFF), onTotal = Color(0xFF17201B), onTotalMuted = Color(0xFF55605A), totalTrack = Color(0xFFF0EEE8),
    highlight = Color(0xFFE3F1E8), onHighlight = Color(0xFF1F7A4D), dark = false
)

private val FRESH = Palette(
    bg = Color(0xFFEEF5EF), card = Color(0xFFFFFFFF), ink = Color(0xFF10251A), muted = Color(0xFF44594C),
    faint = Color(0xFF9DB3A5), line = Color(0xFFDCE8DF), track = Color(0xFFE3EEE6), neutral = Color(0xFFD6EADC),
    green = Color(0xFF1F7A4D), greenSoft = Color(0xFFD6EADC), onGreen = Color.White,
    red = Color(0xFFB3361B), redSoft = Color(0xFFFCE7E2), amber = Color(0xFF8A5300), amberSoft = Color(0xFFFFF6E2),
    camera = Color(0xFF142019), hero = Color(0xFF1F7A4D), onHero = Color.White, onHeroMuted = Color(0xFFCFEBDB),
    total = Color(0xFF1F7A4D), onTotal = Color.White, onTotalMuted = Color(0xFFCFEBDB), totalTrack = Color(0x2EFFFFFF),
    highlight = Color(0xFFF2C14E), onHighlight = Color(0xFF10251A), dark = false
)

private val NIGHT = Palette(
    bg = Color(0xFF111614), card = Color(0xFF1B221F), ink = Color(0xFFEEF3EF), muted = Color(0xFF9AA8A0),
    faint = Color(0xFF5E6B64), line = Color(0xFF26302B), track = Color(0xFF2A3530), neutral = Color(0xFF232B27),
    green = Color(0xFF5FD3A0), greenSoft = Color(0xFF1E3A2E), onGreen = Color(0xFF0C100E),
    red = Color(0xFFF48A70), redSoft = Color(0xFF3A2420), amber = Color(0xFFF0B44C), amberSoft = Color(0xFF3A3020),
    camera = Color(0xFF0C100E), hero = Color(0xFF232B27), onHero = Color(0xFFEEF3EF), onHeroMuted = Color(0xFF9AA8A0),
    total = Color(0xFF1B221F), onTotal = Color(0xFFEEF3EF), onTotalMuted = Color(0xFF9AA8A0), totalTrack = Color(0xFF2A3530),
    highlight = Color(0xFF1E3A2E), onHighlight = Color(0xFF5FD3A0), dark = true
)

object AppTheme {
    /** Compose stav – zmena témy prekreslí celú aplikáciu */
    var id by mutableStateOf(ThemeId.PAPER)
    val p: Palette get() = when (id) { ThemeId.PAPER -> PAPER; ThemeId.FRESH -> FRESH; ThemeId.NIGHT -> NIGHT }
    val dark: Boolean get() = p.dark
}

/** Farby aktuálnej témy (názvy ostali z pôvodnej palety, aby sa nemuseli meniť všetky obrazovky). */
object Pal {
    val Bg get() = AppTheme.p.bg
    val Card get() = AppTheme.p.card
    val Ink get() = AppTheme.p.ink
    val Muted get() = AppTheme.p.muted
    val Faint get() = AppTheme.p.faint
    val Line get() = AppTheme.p.line
    val Track get() = AppTheme.p.track
    val Neutral get() = AppTheme.p.neutral
    val Green get() = AppTheme.p.green
    val GreenSoft get() = AppTheme.p.greenSoft
    val OnGreen get() = AppTheme.p.onGreen
    val Red get() = AppTheme.p.red
    val RedSoft get() = AppTheme.p.redSoft
    val Amber get() = AppTheme.p.amber
    val AmberSoft get() = AppTheme.p.amberSoft
    val Camera get() = AppTheme.p.camera
    val Hero get() = AppTheme.p.hero
    val OnHero get() = AppTheme.p.onHero
    val OnHeroMuted get() = AppTheme.p.onHeroMuted
    val Total get() = AppTheme.p.total
    val OnTotal get() = AppTheme.p.onTotal
    val OnTotalMuted get() = AppTheme.p.onTotalMuted
    val TotalTrack get() = AppTheme.p.totalTrack
    val Highlight get() = AppTheme.p.highlight
    val OnHighlight get() = AppTheme.p.onHighlight
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold)
)

private fun colors() = if (AppTheme.dark) darkColorScheme(
    primary = Pal.Green, onPrimary = Pal.OnGreen,
    primaryContainer = Pal.GreenSoft, onPrimaryContainer = Pal.Green,
    secondary = Pal.Ink, onSecondary = Pal.Bg,
    tertiaryContainer = Pal.AmberSoft, onTertiaryContainer = Pal.Amber,
    background = Pal.Bg, onBackground = Pal.Ink,
    surface = Pal.Bg, onSurface = Pal.Ink,
    surfaceVariant = Pal.Track, onSurfaceVariant = Pal.Muted,
    surfaceContainer = Pal.Card, surfaceContainerHigh = Pal.Card, surfaceContainerLow = Pal.Card,
    surfaceContainerLowest = Pal.Card, surfaceContainerHighest = Pal.Neutral,
    outline = Pal.Faint, outlineVariant = Pal.Line,
    error = Pal.Red, onError = Pal.Bg, errorContainer = Pal.RedSoft
) else lightColorScheme(
    primary = Pal.Green, onPrimary = Pal.OnGreen,
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
fun BlockyTheme(content: @Composable () -> Unit) {
    val dark = AppTheme.dark
    // ikony stavového a navigačného riadku podľa témy
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val ctl = androidx.core.view.WindowCompat.getInsetsController(window, view)
        ctl.isAppearanceLightStatusBars = !dark
        ctl.isAppearanceLightNavigationBars = !dark
        @Suppress("DEPRECATION")
        window.navigationBarColor = Pal.Card.toArgb()
    }
    MaterialTheme(colorScheme = colors(), typography = Typography().withFont(), content = content)
}

fun eur(c: Long) = Analytics.eur(c)

fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(JTextStyle.FULL_STANDALONE, uiLocale()).replaceFirstChar { it.uppercase() } + " ${m.year}"

fun monthName(m: YearMonth): String =
    m.month.getDisplayName(JTextStyle.FULL_STANDALONE, uiLocale()).replaceFirstChar { it.uppercase() }

/** Biela zaoblená karta – základný stavebný prvok dizajnu. */
@Composable
fun BCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), color: Color = Pal.Card,
          content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (AppTheme.id == ThemeId.FRESH) 26.dp else 24.dp)
    Column(modifier.fillMaxWidth()
        .then(if (AppTheme.id == ThemeId.PAPER) Modifier.shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1A17201B)) else Modifier)
        .then(if (AppTheme.id == ThemeId.NIGHT) Modifier.border(1.dp, Pal.Line, shape) else Modifier)
        .clip(shape).background(color).padding(padding), content = content)
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
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthLabel(month), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            sk.miro.blocky.domain.Period.rangeLabel(month)?.let { Text(it, fontSize = 12.sp, color = Pal.Muted) }
        }
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
    Category.LAHODKY -> CatStyle(Color(0xFFFBEDE3), Color(0xFFA0522D), R.drawable.ph_bowl_food)
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
    Category.AUTO_MOTO -> CatStyle(Color(0xFFE3EEF5), Color(0xFF2D6A8E), R.drawable.ph_car)
    Category.ZALOHY -> CatStyle(Color(0xFFE3F1E8), Color(0xFF2E6B47), R.drawable.ph_recycle)
    Category.ZLAVY -> CatStyle(Color(0xFFE3F1E8), Color(0xFF1F7A4D), R.drawable.ph_tag)
    Category.INE -> CatStyle(Color(0xFFEFEFEA), Color(0xFF55605A), R.drawable.ph_dots_three)
    Category.FIXNE -> CatStyle(Color(0xFFE6EAF2), Color(0xFF34466B), R.drawable.ph_house)
    Category.CUSTOM1, Category.CUSTOM2, Category.CUSTOM3, Category.CUSTOM4,
    Category.CUSTOM5, Category.CUSTOM6, Category.CUSTOM7, Category.CUSTOM8 -> {
        val d = CustomCategories.def(c)
        val (bg, fg) = CUSTOM_COLORS[(d?.color ?: 0).coerceIn(0, CUSTOM_COLORS.lastIndex)]
        CatStyle(bg, fg, d?.icon?.let { CUSTOM_ICONS[it] } ?: R.drawable.ph_star)
    }
}

/** Ikony na výber pre vlastné kategórie (Phosphor duotone, rovnaký štýl ako zvyšok aplikácie). */
val CUSTOM_ICONS: Map<String, Int> = linkedMapOf(
    "star" to R.drawable.ph_star, "house" to R.drawable.ph_house, "car" to R.drawable.ph_car,
    "gift" to R.drawable.ph_gift, "heart" to R.drawable.ph_heart, "baby" to R.drawable.ph_baby,
    "baby_carriage" to R.drawable.ph_baby_carriage, "dog" to R.drawable.ph_dog, "cat" to R.drawable.ph_cat,
    "book" to R.drawable.ph_book_open, "school" to R.drawable.ph_graduation_cap, "game" to R.drawable.ph_game_controller,
    "airplane" to R.drawable.ph_airplane, "train" to R.drawable.ph_train, "bus" to R.drawable.ph_bus,
    "bicycle" to R.drawable.ph_bicycle, "coffee" to R.drawable.ph_coffee, "pizza" to R.drawable.ph_pizza,
    "hamburger" to R.drawable.ph_hamburger, "bowl" to R.drawable.ph_bowl_food, "cake" to R.drawable.ph_cake,
    "ice_cream" to R.drawable.ph_ice_cream, "wine" to R.drawable.ph_wine, "beer" to R.drawable.ph_beer_bottle,
    "bag" to R.drawable.ph_shopping_bag, "cart" to R.drawable.ph_shopping_cart, "sneaker" to R.drawable.ph_sneaker,
    "tshirt" to R.drawable.ph_t_shirt, "scissors" to R.drawable.ph_scissors, "wrench" to R.drawable.ph_wrench,
    "lightbulb" to R.drawable.ph_lightbulb, "lightning" to R.drawable.ph_lightning, "drop" to R.drawable.ph_drop,
    "flower" to R.drawable.ph_flower, "tree" to R.drawable.ph_tree, "sun" to R.drawable.ph_sun,
    "umbrella" to R.drawable.ph_umbrella, "tent" to R.drawable.ph_tent, "football" to R.drawable.ph_football,
    "barbell" to R.drawable.ph_barbell, "guitar" to R.drawable.ph_guitar, "music" to R.drawable.ph_music_note,
    "film" to R.drawable.ph_film_strip, "ticket" to R.drawable.ph_ticket, "tooth" to R.drawable.ph_tooth,
    "first_aid" to R.drawable.ph_first_aid, "pill" to R.drawable.ph_pill, "brush" to R.drawable.ph_paint_brush,
    "laptop" to R.drawable.ph_laptop, "money" to R.drawable.ph_money, "piggy" to R.drawable.ph_piggy_bank,
    "smiley" to R.drawable.ph_smiley
)

/** Farby pre vlastné kategórie (pozadie, ikona) – z palety aplikácie. */
val CUSTOM_COLORS: List<Pair<Color, Color>> = listOf(
    Color(0xFFE3F1E8) to Color(0xFF1F7A4D), Color(0xFFE4ECF7) to Color(0xFF2F5E9E),
    Color(0xFFFDF0DB) to Color(0xFF9A5B06), Color(0xFFFCE7E2) to Color(0xFFB3361B),
    Color(0xFFF5E6EC) to Color(0xFF9C3A5E), Color(0xFFEEEAF7) to Color(0xFF5B4A9E),
    Color(0xFFE3F3F1) to Color(0xFF1D7268), Color(0xFFF3ECE3) to Color(0xFF7A5230)
)

@Composable
fun CatBadge(c: Category, size: Int = 38) {
    val s = catStyle(c)
    val (bg, tint) = badgeColors(s.bg, s.fg)
    val shape = if (AppTheme.id == ThemeId.FRESH) CircleShape else RoundedCornerShape((size / 3).dp)
    Box(Modifier.size(size.dp).clip(shape).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(s.icon), null, tint = tint, modifier = Modifier.size((size * 0.6f).dp))
    }
}

/** farba ikony kategórie / prevádzky podľa témy (pásy, grafy) */
fun themedFg(fg: Color): Color = if (AppTheme.dark) androidx.compose.ui.graphics.lerp(fg, Color.White, 0.45f) else fg

/** pozadie a farba ikony odznaku podľa témy: papier = pastel, svieža = plná farba, noc = svetlá ikona na tmavom */
fun badgeColors(bg: Color, fg: Color): Pair<Color, Color> = when (AppTheme.id) {
    ThemeId.PAPER -> bg to fg
    ThemeId.FRESH -> fg to Color.White
    ThemeId.NIGHT -> themedFg(fg).copy(alpha = 0.16f).compositeOver(Pal.Card) to themedFg(fg)
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
    MerchantType.AUTO -> MerchantStyle(Color(0xFFE3EEF5), Color(0xFF2D6A8E), R.drawable.ph_car)
    MerchantType.SERVICES -> MerchantStyle(Color(0xFFEFECE6), Color(0xFF5E5547), R.drawable.ph_wrench)
    MerchantType.OTHER -> MerchantStyle(Color(0xFFEFEFEA), Color(0xFF55605A), R.drawable.ph_storefront)
    MerchantType.UNKNOWN -> MerchantStyle(Color(0xFFFDF0DB), Color(0xFF9A5B06), R.drawable.ph_question)
}

@Composable
fun MerchantBadge(t: MerchantType, size: Int = 40) {
    val s = merchantStyle(t)
    val (bg, tint) = badgeColors(s.bg, s.fg)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(s.icon), t.label, tint = tint, modifier = Modifier.size((size * 0.56f).dp))
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
                            val tileBg = if (AppTheme.dark) themedFg(st.fg).copy(alpha = 0.16f).compositeOver(Pal.Card) else st.bg
                            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(tileBg)
                                .clickable { onPick(t) }.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(st.icon), null, tint = themedFg(st.fg), modifier = Modifier.size(22.dp))
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
fun CategoryRow(c: Category, cents: Long, maxCents: Long, sub: String? = null, over: Boolean = false,
                onClick: (() -> Unit)? = null) {
    val s = catStyle(c)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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

/** Skupiny kategórií vo výbere – každá kategória je práve v jednej skupine. */
val CATEGORY_GROUPS: List<Pair<String, List<Category>>> get() = listOf(
    "Potraviny a nápoje" to listOf(Category.MASO, Category.LAHODKY, Category.MLIECNE, Category.PECIVO, Category.OVOCIE_ZELENINA,
        Category.TRVANLIVE, Category.MRAZENE_HOTOVE, Category.NAPOJE, Category.SLADKOSTI, Category.ALKOHOL, Category.TABAK),
    "Domácnosť a zdravie" to listOf(Category.DROGERIA, Category.DOMACNOST, Category.ZDRAVIE, Category.ZVIERATA),
    "Mimo domu" to listOf(Category.RESTAURACIE, Category.UBYTOVANIE, Category.VOLNY_CAS),
    "Ostatné" to listOf(Category.OBLECENIE, Category.ELEKTRONIKA, Category.SLUZBY, Category.PHM, Category.AUTO_MOTO,
        Category.ZALOHY, Category.ZLAVY, Category.INE),
    "Vlastné" to CustomCategories.all.map { it.slot }
)

/** Výber kategórie pre položku – na celú obrazovku, všetky kategórie viditeľné a posúvateľné. */
@Composable
fun CategoryPickerDialog(itemName: String, current: Category, onDismiss: () -> Unit,
                         onCreate: ((String, String, Int) -> Category?)? = null, onPick: (Category, Boolean) -> Unit) {
    var all by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    if (creating && onCreate != null) CustomCategoryDialog(null, onDismiss = { creating = false }) { label, icon, color ->
        creating = false
        onCreate(label, icon, color)?.let { onPick(it, all) }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f).clip(RoundedCornerShape(28.dp)).background(Pal.Bg)) {
            Column(Modifier.fillMaxWidth().background(Pal.Card).padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zaradiť do kategórie", fontSize = 13.sp, color = Pal.Muted)
                        Text(itemName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Zavrieť") }
                }
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { all = !all }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it })
                    Text("Zapamätať pre všetky položky s týmto názvom", fontSize = 13.sp)
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CATEGORY_GROUPS.filter { (t, c) -> c.isNotEmpty() || (t == "Vlastné" && onCreate != null) }.forEach { (title, cats) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(title, Modifier.padding(top = 8.dp, bottom = 2.dp), fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold, color = Pal.Muted)
                    }
                    cats.forEach { c ->
                        item {
                            val sel = c == current
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (sel) catStyle(c).bg else Pal.Card)
                                .clickable { onPick(c, all) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                CatBadge(c, 34)
                                Spacer(Modifier.width(10.dp))
                                Text(c.label, fontSize = 13.sp, lineHeight = 16.sp,
                                    fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 2)
                            }
                        }
                    }
                    if (title == "Vlastné" && onCreate != null && CustomCategories.all.size < 8) item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.GreenSoft)
                            .clickable { creating = true }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Pal.Card), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = Pal.Green, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Nová kategória", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pal.Green)
                        }
                    }
                }
            }
        }
    }
}


/** Vytvorenie / úprava vlastnej kategórie: názov, ikona, farba. [existing] = null pre novú. */
@Composable
fun CustomCategoryDialog(existing: CustomCategories.Def?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null,
                         onSave: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(existing?.label ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "star") }
    var color by remember { mutableStateOf(existing?.color ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val (bg, fg) = CUSTOM_COLORS[color.coerceIn(0, CUSTOM_COLORS.lastIndex)]
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(RoundedCornerShape(28.dp)).background(Pal.Card).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Icon(painterResource(CUSTOM_ICONS[icon] ?: R.drawable.ph_star), null, tint = fg, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(if (existing == null) "Nová kategória" else "Upraviť kategóriu", Modifier.weight(1f),
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Zavrieť") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Názov (napr. Deti, Darčeky, Chalupa)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(12.dp))
            Text("Farba", fontSize = 13.sp, color = Pal.Muted, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUSTOM_COLORS.forEachIndexed { i, (b, f) ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(f).clickable { color = i }
                        .padding(if (i == color) 8.dp else 0.dp).clip(CircleShape).background(if (i == color) b else f))
                }
            }
            Text("Ikona", fontSize = 13.sp, color = Pal.Muted, fontWeight = FontWeight.SemiBold)
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(52.dp),
                modifier = Modifier.weight(1f).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CUSTOM_ICONS.forEach { (key, res) ->
                    item {
                        val sel = key == icon
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) bg else Pal.Bg)
                            .clickable { icon = key }, contentAlignment = Alignment.Center) {
                            Icon(painterResource(res), key, tint = if (sel) fg else Pal.Muted, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Zmazať", color = Pal.Red) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(name.trim(), icon, color) }, enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(14.dp)) { Text("Uložiť") }
            }
        }
    }
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Pal.Card,
        title = { Text("Zmazať kategóriu?") },
        text = { Text("Položky z tejto kategórie sa presunú do „Iné“.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Zmazať", color = Pal.Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } })
}


/** Jemné podfarbenie kategórie podľa rozpočtu: oranžová = hrozí prekročenie, červená = prekročené. */
fun budgetHighlight(s: sk.miro.blocky.domain.BudgetState): Color? = when (s) {
    sk.miro.blocky.domain.BudgetState.OVER -> Pal.RedSoft
    sk.miro.blocky.domain.BudgetState.RISK -> Pal.AmberSoft
    else -> null
}


/** Zdroj údajov a vyhlásenie, že aplikácia nie je štátna (požiadavka Google Play na vládne informácie). */
@Composable
fun GovSourceNote(modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column(modifier.fillMaxWidth()) {
        Text("Zdroj údajov: služba eKasa – Over doklad, Finančná správa SR", fontSize = 12.sp, color = Pal.Muted)
        Text(GOV_SOURCE_URL, Modifier.clickable { uri.openUri(GOV_SOURCE_URL) }.padding(vertical = 2.dp),
            fontSize = 12.sp, color = Pal.Green, fontWeight = FontWeight.SemiBold)
        Text("Bločky nie sú aplikáciou Finančnej správy SR ani iného štátneho orgánu a nekonajú v ich mene.",
            fontSize = 12.sp, color = Pal.Muted)
    }
}

const val GOV_SOURCE_URL = "https://opd.financnasprava.sk/"
