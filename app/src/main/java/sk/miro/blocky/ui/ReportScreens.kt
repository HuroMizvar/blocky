package sk.miro.blocky.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.miro.blocky.domain.Recommendation

@Composable
fun ReportScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val st by vm.state.collectAsState()
    val r = st.report
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
        start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth) }
        if (r == null || r.receiptCount == 0) {
            item {
                Text(
                    if (st.loading) "Načítavam…" else
                        "Za tento mesiac zatiaľ nemáte žiadne bločky.\nNaskenujte QR kód z bločku tlačidlom dole.",
                    Modifier.padding(top = 32.dp), style = MaterialTheme.typography.bodyLarge
                )
            }
            return@LazyColumn
        }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Výdavky spolu", style = MaterialTheme.typography.labelLarge)
                    Text(eur(r.totalCents), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    deltaText(r.totalCents, r.prevTotalCents.takeIf { it > 0 }, "minulý mesiac")?.let { Text(it) }
                    deltaText(r.totalCents, r.avg3TotalCents, "priemer (max. 3 mes.)")?.let { Text(it) }
                    Spacer(Modifier.height(6.dp))
                    Text("${r.receiptCount} bločkov · priemerný nákup ${eur(r.totalCents / r.receiptCount)}",
                        style = MaterialTheme.typography.bodySmall)
                    r.projectedTotalCents?.let {
                        Text("Odhad do konca mesiaca (lineárna projekcia): ${eur(it)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionTitle("Podľa kategórií") }
        val maxCat = r.categories.maxOfOrNull { maxOf(it.cents, it.budgetCents ?: 0L) } ?: 1L
        items(r.categories.filter { it.cents != 0L || it.budgetCents != null }) { c ->
            val parts = listOfNotNull(
                "${"%.0f".format(c.share * 100)} %",
                deltaText(c.cents, c.avg3Cents, "priemer"),
                c.budgetCents?.let { "rozpočet ${eur(it)}" }
            )
            AmountBar(c.category.label, c.cents, maxCat, parts.joinToString(" · "), c.budgetCents)
        }
        item { SectionTitle("Obchody") }
        val maxM = r.merchants.firstOrNull()?.cents ?: 1L
        items(r.merchants.take(8)) { m -> AmountBar(m.name, m.cents, maxM, "${m.visits}× nákup") }
        item { SectionTitle("Najväčšie položky") }
        items(r.topItems) { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(i.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text("${i.category.label} · ${i.purchases}×", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(eur(i.cents), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RecommendationsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val st by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
        start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth) }
        val recs = st.recommendations
        if (recs.isEmpty()) {
            item {
                Text("Žiadne odporúčania. Pre zmysluplné porovnania treba aspoň 2–3 mesiace bločkov.",
                    Modifier.padding(top = 32.dp))
            }
            return@LazyColumn
        }
        val total = recs.sumOf { it.estSavingCents ?: 0L }
        if (total > 0) item {
            Text("Potenciál úspory (odhad, súčet jednotlivých odporúčaní, môžu sa prekrývať): ${eur(total)} / mesiac",
                Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        items(recs) { RecCard(it) }
    }
}

@Composable
private fun RecCard(r: Recommendation) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.padding(top = 6.dp).size(10.dp)) {
                Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraSmall, color = severityColor(r.severity)) {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(r.detail, style = MaterialTheme.typography.bodyMedium)
                r.estSavingCents?.takeIf { it > 0 }?.let {
                    Text("Odhad úspory: ${eur(it)} / mesiac", Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = severityColor(r.severity))
                }
            }
        }
    }
}
