package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Severity
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

val SK = Locale.forLanguageTag("sk-SK")

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF0B3D0E),
    secondary = Color(0xFF455A64), error = Color(0xFFC62828),
    surface = Color(0xFFFAFAF7), background = Color(0xFFFAFAF7)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81C784), onPrimary = Color(0xFF0B3D0E),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
    error = Color(0xFFEF9A9A)
)

@Composable
fun BlockyTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)

fun eur(c: Long) = Analytics.eur(c)

fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL_STANDALONE, SK).replaceFirstChar { it.uppercase() } + " ${m.year}"

@Composable
fun MonthSwitcher(month: YearMonth, months: List<YearMonth>, onSelect: (YearMonth) -> Unit) {
    val idx = months.indexOf(month)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { months.getOrNull(idx + 1)?.let(onSelect) }, enabled = idx in 0 until months.size - 1) {
            Icon(Icons.Default.ChevronLeft, "Predchádzajúci mesiac")
        }
        Text(monthLabel(month), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { months.getOrNull(idx - 1)?.let(onSelect) }, enabled = idx > 0) {
            Icon(Icons.Default.ChevronRight, "Nasledujúci mesiac")
        }
    }
}

/** Vodorovný pruh: hodnota vs. maximum, voliteľne s rozpočtovou značkou. */
@Composable
fun AmountBar(label: String, cents: Long, maxCents: Long, sub: String? = null, budgetCents: Long? = null) {
    val over = budgetCents != null && cents > budgetCents
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text(eur(cents), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        val frac = if (maxCents > 0) (cents.toFloat() / maxCents).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                .background(if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
        }
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) = Text(text, Modifier.padding(top = 20.dp, bottom = 6.dp),
    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
fun severityColor(s: Severity): Color = when (s) {
    Severity.HIGH -> MaterialTheme.colorScheme.error
    Severity.MEDIUM -> Color(0xFFEF6C00)
    Severity.INFO -> MaterialTheme.colorScheme.primary
}

fun deltaText(cur: Long, ref: Long?, refLabel: String): String? {
    if (ref == null || ref == 0L) return null
    val d = (cur - ref).toDouble() / ref * 100
    val sign = if (d >= 0) "+" else ""
    return "$sign${"%.0f".format(d)} % vs. $refLabel"
}
