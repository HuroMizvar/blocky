package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.miro.blocky.domain.Recommendation
import java.time.LocalDate
import java.time.YearMonth

private fun listPadding(p: PaddingValues) =
    PaddingValues(start = 20.dp, end = 20.dp, top = p.calculateTopPadding(), bottom = p.calculateBottomPadding() + 24.dp)

@Composable
fun ReportScreen(
    vm: MainViewModel, contentPadding: PaddingValues,
    onOpenTips: () -> Unit, onOpenBudgets: () -> Unit, onScan: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val st by vm.state.collectAsState()
    val r = st.report
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = listPadding(contentPadding)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth, actions) }
        if (r == null || r.receiptCount == 0) {
            item {
                BCard(Modifier.padding(top = 12.dp)) {
                    Text(if (st.loading) "Načítavam…" else "Za tento mesiac zatiaľ nemáte žiadne bločky.",
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (!st.loading) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onScan, shape = RoundedCornerShape(16.dp)) { Text("Skenovať bloček") }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            val budgetSum = st.budgets.values.sum()
            val isCurrent = r.month == YearMonth.now()
            BCard(Modifier.padding(top = 12.dp)) {
                Text(if (isCurrent) "Minuté tento mesiac" else "Minuté v mesiaci", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = Pal.Muted)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(eur(r.totalCents), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Spacer(Modifier.width(10.dp))
                    deltaPct(r.totalCents, r.prevTotalCents.takeIf { it > 0 })?.let { d ->
                        val good = d <= 0
                        Tag((if (d > 0) "+" else if (d < 0) "−" else "") + "${kotlin.math.abs(d)} % vs. min. mes.",
                            if (good) Pal.Green else Pal.Amber, if (good) Pal.GreenSoft else Pal.AmberSoft)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (budgetSum > 0) {
                    ProgressTrack(r.totalCents.toFloat() / budgetSum, if (r.totalCents > budgetSum) Pal.Red else Pal.Green, height = 10, track = Pal.Line)
                    Spacer(Modifier.height(8.dp))
                    val left = budgetSum - r.totalCents
                    val days = if (isCurrent) r.month.lengthOfMonth() - LocalDate.now().dayOfMonth else 0
                    Row {
                        Text("Rozpočet ${eur(budgetSum)}", Modifier.weight(1f), fontSize = 13.sp, color = Pal.Muted)
                        Text(if (left >= 0) "zostáva ${eur(left)}" + (if (days > 0) " · $days dní" else "")
                             else "prekročené o ${eur(-left)}",
                            fontSize = 13.sp, color = if (left >= 0) Pal.Muted else Pal.Red, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Nastaviť mesačný rozpočet", Modifier.clickable(onClick = onOpenBudgets).padding(vertical = 6.dp),
                        color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(buildString {
                    append("${r.receiptCount} bločkov · priemerný nákup ${eur(r.totalCents / r.receiptCount)}")
                    r.projectedTotalCents?.let { append("\nOdhad do konca mesiaca (lineárne): ${eur(it)}") }
                }, fontSize = 12.sp, color = Pal.Muted, lineHeight = 18.sp)
            }
        }

        item {
            SectionHeader("Kategórie") {
                if (st.recommendations.isNotEmpty())
                    Text("${st.recommendations.size} ${tipWord(st.recommendations.size)}",
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenTips).padding(6.dp),
                        color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        item {
            val cats = r.categories.filter { it.cents != 0L || it.budgetCents != null }
            val max = cats.maxOfOrNull { maxOf(it.cents, it.budgetCents ?: 0L) } ?: 1L
            BCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                cats.forEach { c ->
                    val parts = listOfNotNull(
                        "${Math.round(c.share * 100)} % výdavkov",
                        deltaText(c.cents, c.avg3Cents, "priemer"),
                        c.budgetCents?.let { "rozpočet ${eur(it)}" }
                    )
                    CategoryRow(c.category, c.cents, max, parts.joinToString(" · "),
                        over = c.budgetCents != null && c.cents > c.budgetCents)
                }
            }
        }

        item { SectionHeader("Obchody") }
        item {
            BCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                r.merchants.take(6).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Pal.Track), contentAlignment = Alignment.Center) {
                            Text(m.name.trim().take(1).uppercase(), fontWeight = FontWeight.ExtraBold, color = Pal.Ink)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(shortMerchant(m.name), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${m.visits}× nákup", fontSize = 12.sp, color = Pal.Muted)
                        }
                        Text(eur(m.cents), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { SectionHeader("Najväčšie položky") }
        item {
            BCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                r.topItems.take(10).forEach { i ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(i.category, 32)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(i.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${i.purchases}× · ${i.category.label}", fontSize = 12.sp, color = Pal.Muted)
                        }
                        Text(eur(i.cents), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun tipWord(n: Int) = when (n) { 1 -> "tip na úsporu"; in 2..4 -> "tipy na úsporu"; else -> "tipov na úsporu" }

/** „Lidl Slovenská republika, v.o.s.“ → „Lidl Slovenská republika“ */
fun shortMerchant(name: String): String =
    name.replace(Regex(",?\\s*(s\\.\\s?r\\.\\s?o\\.|a\\.\\s?s\\.|v\\.\\s?o\\.\\s?s\\.|spol\\..*)$", RegexOption.IGNORE_CASE), "").trim()

@Composable
fun RecommendationsScreen(vm: MainViewModel, contentPadding: PaddingValues, actions: @Composable RowScope.() -> Unit) {
    val st by vm.state.collectAsState()
    val recs = st.recommendations
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = listPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth, actions) }
        if (recs.isEmpty()) {
            item {
                BCard {
                    Text("Zatiaľ žiadne tipy", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Porovnania potrebujú aspoň 2–3 mesiace bločkov.", fontSize = 14.sp, color = Pal.Muted)
                }
            }
            return@LazyColumn
        }
        val withSaving = recs.filter { (it.estSavingCents ?: 0L) > 0 }
        if (withSaving.isNotEmpty()) item {
            BCard(color = Pal.Ink) {
                Text("Odhad mesačnej úspory", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC9D3CD))
                Text("až ${eur(withSaving.sumOf { it.estSavingCents ?: 0L })}", fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("súčet ${withSaving.size} ${if (withSaving.size == 1) "tipu" else "tipov"} · môžu sa prekrývať",
                    fontSize = 13.sp, color = Color(0xFFC9D3CD))
            }
        }
        items(recs) { TipCard(it) }
    }
}

@Composable
private fun TipCard(r: Recommendation) {
    val (tag, fg, bg) = severityStyle(r.severity)
    BCard(padding = PaddingValues(16.dp)) {
        Tag(tag, fg, bg)
        Spacer(Modifier.height(10.dp))
        Text(r.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
        Spacer(Modifier.height(4.dp))
        Text(r.detail, fontSize = 14.sp, color = Pal.Muted, lineHeight = 20.sp)
        r.estSavingCents?.takeIf { it > 0 }?.let {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Pal.Track)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Odhad úspory", Modifier.weight(1f), fontSize = 13.sp, color = Pal.Muted)
                Text("${eur(it)} / mes.", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Pal.Green)
            }
        }
    }
}
