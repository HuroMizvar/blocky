package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
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
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.HouseholdProfile
import sk.miro.blocky.domain.BudgetState
import sk.miro.blocky.domain.Period
import sk.miro.blocky.domain.MerchantType
import sk.miro.blocky.domain.Recommendation
import java.time.LocalDate
import java.time.YearMonth

private fun listPadding(p: PaddingValues) =
    PaddingValues(start = 20.dp, end = 20.dp, top = p.calculateTopPadding(), bottom = p.calculateBottomPadding() + 24.dp)

@Composable
fun ReportScreen(
    vm: MainViewModel, contentPadding: PaddingValues,
    onOpenTips: () -> Unit, onOpenBudgets: () -> Unit, onScan: () -> Unit,
    onOpenItems: (kind: String, key: String, month: YearMonth) -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val st by vm.state.collectAsState()
    val unknown by vm.unknownMerchants.collectAsState()
    var asking by remember { mutableStateOf<sk.miro.blocky.data.MerchantEntity?>(null) }
    asking?.let { m ->
        MerchantTypeDialog(m, onPick = { t -> vm.answerMerchant(m, t); asking = null }, onLater = { asking = null })
    }
    val r = st.report
    var editFixed by remember { mutableStateOf(false) }
    if (editFixed) FixedMonthDialog(r.month, r.fixedPayments, onDismiss = { editFixed = false }) { id, cents ->
        vm.setFixedAmount(id, r.month, cents)
    }
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = listPadding(contentPadding)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth, actions) }
        if (unknown.isNotEmpty()) item {
            BCard(Modifier.padding(top = 12.dp), color = Pal.AmberSoft, padding = PaddingValues(16.dp)) {
                Text("Pomôžte zaradiť ${unknown.size} ${if (unknown.size == 1) "prevádzku" else if (unknown.size < 5) "prevádzky" else "prevádzok"}",
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Pal.Amber)
                Text("Neviem, či ide o obchod, reštauráciu, pumpu… Ťuknite a vyberte.", fontSize = 13.sp, color = Pal.Muted)
                Spacer(Modifier.height(8.dp))
                unknown.take(5).forEach { m ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { asking = m }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MerchantBadge(MerchantType.UNKNOWN, 32)
                        Spacer(Modifier.width(10.dp))
                        Text(shortMerchant(m.name), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Zaradiť", color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
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
            val isCurrent = r.month == Period.current()
            BCard(Modifier.padding(top = 12.dp), color = Pal.Total) {
              CompositionLocalProvider(LocalContentColor provides Pal.OnTotal) {
                Text(if (isCurrent) "Minuté tento mesiac (bez záloh)" else "Minuté v mesiaci (bez záloh)", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = Pal.OnTotalMuted)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(eur(r.totalCents), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Spacer(Modifier.width(10.dp))
                    deltaPct(r.totalCents, r.prevTotalCents.takeIf { it > 0 })?.let { d ->
                        val good = d <= 0
                        if (AppTheme.id == ThemeId.FRESH) Tag((if (d > 0) "+" else if (d < 0) "−" else "") + "${kotlin.math.abs(d)} % vs. min. mes.",
                            Pal.OnHighlight, Pal.Highlight)
                        else Tag((if (d > 0) "+" else if (d < 0) "−" else "") + "${kotlin.math.abs(d)} % vs. min. mes.",
                            if (good) Pal.Green else Pal.Amber, if (good) Pal.GreenSoft else Pal.AmberSoft)
                    }
                }
                if (r.fixedCents > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("S fixnými výdavkami", Modifier.weight(1f), fontSize = 14.sp, color = Pal.OnTotalMuted, fontWeight = FontWeight.SemiBold)
                        Text(eur(r.totalWithFixedCents), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("fixné ${eur(r.fixedCents)}: " + r.fixedPayments.joinToString(", ") { it.expense.name },
                        fontSize = 12.sp, color = Pal.OnTotalMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                if (budgetSum > 0) {
                    ProgressTrack(r.totalCents.toFloat() / budgetSum,
                        if (r.totalCents > budgetSum) Pal.Red else if (AppTheme.id == ThemeId.FRESH) Pal.OnTotal else Pal.Green,
                        height = 10, track = Pal.TotalTrack)
                    Spacer(Modifier.height(8.dp))
                    val left = budgetSum - r.totalCents
                    val days = if (isCurrent) Period.length(r.month) - Period.dayIndex(r.month, LocalDate.now()) else 0
                    Row {
                        Text("Rozpočet ${eur(budgetSum)}", Modifier.weight(1f), fontSize = 13.sp, color = Pal.OnTotalMuted)
                        Text(if (left >= 0) "zostáva ${eur(left)}" + (if (days > 0) " · $days dní" else "")
                             else "prekročené o ${eur(-left)}",
                            fontSize = 13.sp, color = if (left >= 0) Pal.OnTotal else if (AppTheme.id == ThemeId.FRESH) Pal.Highlight else Pal.Red,
                            fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Nastaviť mesačný rozpočet", Modifier.clickable(onClick = onOpenBudgets).padding(vertical = 6.dp),
                        color = if (AppTheme.id == ThemeId.FRESH) Pal.Highlight else Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
              }
            }
        }

        // rýchle údaje
        item {
            val isCurrent = r.month == Period.current()
            val days = if (isCurrent) Period.dayIndex(r.month, LocalDate.now()) else Period.length(r.month)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Bločky", "${r.receiptCount}", Modifier.weight(1f))
                StatTile("Priemerný nákup", eur(r.totalCents / r.receiptCount), Modifier.weight(1f))
                if (isCurrent && r.projectedTotalCents != null) StatTile("Odhad mesiaca", eur(r.projectedTotalCents), Modifier.weight(1f))
                else StatTile("Na deň", eur(r.totalCents / maxOf(1, days)), Modifier.weight(1f))
            }
        }

        val cats = r.categories.filter { it.cents > 0L || it.budgetCents != null }
        item {
            SectionHeader("Kategórie") {
                if (st.recommendations.isNotEmpty())
                    Text("${st.recommendations.size} ${tipWord(st.recommendations.size)}",
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenTips).padding(6.dp),
                        color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        item {
            ExpandableCard(cats.size, initial = 6, what = "kategórie") { limit ->
                // farebné rozdelenie výdavkov
                SplitBar(cats.filter { it.cents > 0 }.map { themedFg(catStyle(it.category).fg) to it.share.toFloat() })
                Spacer(Modifier.height(6.dp))
                val max = cats.filter { it.category != Category.FIXNE }.maxOfOrNull { maxOf(it.cents, it.budgetCents ?: 0L) } ?: 1L
                cats.take(limit).forEach { c ->
                    if (c.category == Category.FIXNE) ListRow(
                        leading = { CatBadge(c.category, 40) },
                        title = c.category.label,
                        subtitle = "${r.fixedPayments.size}× · ťuknutím upravíte sumu",
                        amount = c.cents,
                        onClick = { editFixed = true }
                    ) else ListRow(
                        leading = { CatBadge(c.category, 40) },
                        title = c.category.label,
                        subtitle = "${Math.round(c.share * 100)} % výdavkov" + (c.budgetCents?.let { " · rozpočet ${eur(it)}" } ?: ""),
                        amount = c.cents,
                        trend = deltaPct(c.cents, c.avg3Cents),
                        bar = c.cents.toFloat() / max to themedFg(catStyle(c.category).fg),
                        onClick = { onOpenItems("cat", c.category.name, r.month) }
                    )
                }
            }
        }

        item { SectionHeader("Obchody") }
        item {
            ExpandableCard(r.merchants.size, initial = 5, what = "obchody") { limit ->
                r.merchants.take(limit).forEach { m ->
                    val generic = m.type == MerchantType.OTHER || m.type == MerchantType.UNKNOWN
                    val cat = m.mainCategory?.takeIf { generic }
                    ListRow(
                        leading = { if (cat != null) CatBadge(cat, 40) else MerchantBadge(m.type, 40) },
                        title = shortMerchant(m.name),
                        subtitle = "${m.visits}× nákup · ${cat?.label ?: m.type.label}",
                        amount = m.cents,
                        onClick = { onOpenItems("merchant", m.name, r.month) }
                    )
                }
            }
        }

        item { SectionHeader("Najviac ste minuli za") }
        item {
            ExpandableCard(r.topItems.size, initial = 5, what = "položky") { limit ->
                r.topItems.take(limit).forEach { i ->
                    ListRow(
                        leading = { CatBadge(i.category, 36) },
                        title = i.name,
                        subtitle = "${i.purchases}× · ${i.category.label}",
                        amount = i.cents
                    )
                }
            }
        }

        if (r.depositPaidCents > 0 || r.depositReturnedCents > 0) {
            item { SectionHeader("Zálohy za fľaše a plechovky") }
            item {
                val net = r.depositPaidCents - r.depositReturnedCents
                BCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(Category.ZALOHY, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (net > 0) "Na vrátenie čaká ${eur(net)}" else "Všetko vrátené",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Zaplatené ${eur(r.depositPaidCents)} · vrátené ${eur(r.depositReturnedCents)} · do výdavkov sa nepočítajú",
                                fontSize = 12.sp, color = Pal.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** Malá dlaždica s jedným údajom. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Pal.Card).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(label, fontSize = 12.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Vodorovný pruh rozdelený podľa podielov kategórií. */
@Composable
private fun SplitBar(parts: List<Pair<Color, Float>>) {
    if (parts.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Pal.Track),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.second > 0.005f }.forEach { (c, w) ->
            Box(Modifier.weight(w).fillMaxHeight().background(c))
        }
    }
}

/** Karta so zoznamom: zobrazí prvých [initial] riadkov, zvyšok po ťuknutí. */
@Composable
private fun ExpandableCard(total: Int, initial: Int, what: String, content: @Composable ColumnScope.(limit: Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    BCard(padding = PaddingValues(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
        content(if (expanded) total else initial)
        if (total > initial) {
            HorizontalDivider(Modifier.padding(top = 4.dp), color = Pal.Track)
            Text(if (expanded) "Zobraziť menej" else "Zobraziť všetky $what ($total)",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }
                    .padding(vertical = 12.dp),
                color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Jednotný riadok zoznamu: ikona, názov, popis, suma, voliteľne trend a pruh. */
@Composable
private fun ListRow(
    leading: @Composable () -> Unit, title: String, subtitle: String, amount: Long,
    amountColor: Color = Pal.Ink, trend: Int? = null, bar: Pair<Float, Color>? = null, onClick: (() -> Unit)? = null,
    highlight: Color? = null
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .then(if (highlight != null) Modifier.background(highlight) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 10.dp, horizontal = if (highlight != null) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (bar != null) {
                Spacer(Modifier.height(6.dp))
                ProgressTrack(bar.first, bar.second, height = 5)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(eur(amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = amountColor)
            if (trend != null && kotlin.math.abs(trend) >= 5) {
                val up = trend > 0
                Row(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (up) Pal.AmberSoft else Pal.GreenSoft).padding(horizontal = 5.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (up) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null,
                        tint = if (up) Pal.Amber else Pal.Green, modifier = Modifier.size(11.dp))
                    Text("${kotlin.math.abs(trend)} %", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (up) Pal.Amber else Pal.Green)
                }
            }
        }
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = Pal.Faint, modifier = Modifier.size(20.dp))
    }
}

/** Úprava skutočných súm fixných výdavkov v jednom mesiaci (napr. paušál 20 € → 25,43 €). */
@Composable
private fun FixedMonthDialog(month: YearMonth, payments: List<sk.miro.blocky.domain.FixedPayment>, onDismiss: () -> Unit,
                             onSave: (id: String, cents: Long?) -> Unit) {
    val fmt = { c: Long -> "%d,%02d".format(c / 100, c % 100) }
    val values = remember(payments) { mutableStateMapOf<String, String>().apply { payments.forEach { put(it.expense.id, fmt(it.cents)) } } }
    fun parse(s: String) = s.replace(',', '.').replace(" ", "").toDoubleOrNull()?.let { Math.round(it * 100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text("Fixné výdavky – ${monthName(month)}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Upravte skutočne zaplatenú sumu. Zmena platí len pre tento mesiac.", fontSize = 13.sp, color = Pal.Muted)
                payments.forEach { p ->
                    val e = p.expense
                    Column {
                        OutlinedTextField(values[e.id] ?: "", { values[e.id] = it.take(12) },
                            label = { Text(e.name) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                            supportingText = {
                                Text("bežne ${eur(e.cents)} · zaplatené ${p.date.dayOfMonth}. ${p.date.monthValue}.", fontSize = 12.sp)
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                        if (p.edited) TextButton(onClick = { values[e.id] = fmt(e.cents) }) {
                            Text("Vrátiť bežnú sumu ${eur(e.cents)}", color = Pal.Green)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = payments.all { parse(values[it.expense.id] ?: "")?.let { c -> c >= 0 } == true }, onClick = {
                payments.forEach { p ->
                    val c = parse(values[p.expense.id] ?: "") ?: return@forEach
                    if (c != p.cents) onSave(p.expense.id, if (c == p.expense.cents) null else c)
                }
                onDismiss()
            }) { Text("Uložiť", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

fun tipWord(n: Int) = when (n) { 1 -> "tip na úsporu"; in 2..4 -> "tipy na úsporu"; else -> "tipov na úsporu" }

/** „Lidl Slovenská republika, v.o.s.“ → „Lidl Slovenská republika“ */
fun shortMerchant(name: String): String =
    name.replace(Regex(",?\\s*(s\\.\\s?r\\.\\s?o\\.|a\\.\\s?s\\.|v\\.\\s?o\\.\\s?s\\.|spol\\..*)$", RegexOption.IGNORE_CASE), "").trim()

@Composable
fun RecommendationsScreen(vm: MainViewModel, contentPadding: PaddingValues, actions: @Composable RowScope.() -> Unit) {
    val st by vm.state.collectAsState()
    val recs = st.recommendations
    var editProfile by remember { mutableStateOf(false) }
    var aiOpen by remember { mutableStateOf(false) }
    if (editProfile) ProfileDialog(st.profile, onDismiss = { editProfile = false }) { vm.setProfile(it); editProfile = false }
    if (aiOpen) AiShareDialog(vm, onEditProfile = { aiOpen = false; editProfile = true }) { aiOpen = false }
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = listPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(st.month, st.months, vm::selectMonth, actions) }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.Hero)
                .clickable { aiOpen = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Pal.OnHero.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Pal.OnHero, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Analýza pomocou AI", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pal.OnHero)
                    Text("Pripravím súhrn, skontrolujete ho a pošlete do AI aplikácie, ktorú si vyberiete",
                        fontSize = 13.sp, color = Pal.OnHeroMuted)
                }
            }
        }
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
            BCard(color = Pal.Highlight) {
                Text("Odhad mesačnej úspory", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.OnHighlight)
                Text("až ${eur(withSaving.sumOf { it.estSavingCents ?: 0L })}", fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold, color = Pal.OnHighlight)
                Text("súčet ${withSaving.size} ${if (withSaving.size == 1) "tipu" else "tipov"} · môžu sa prekrývať",
                    fontSize = 13.sp, color = Pal.OnHighlight)
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

/** Krátky popis domácnosti – ukladá sa len v telefóne a slúži na prispôsobenie tipov. */
@Composable
fun ProfileDialog(initial: HouseholdProfile, onDismiss: () -> Unit, onSave: (HouseholdProfile) -> Unit) {
    var people by remember { mutableIntStateOf(if (initial.people > 0) initial.people else 1) }
    var kids by remember { mutableIntStateOf(initial.kids) }
    var carer by remember { mutableStateOf(initial.carer) }
    var pets by remember { mutableStateOf(initial.pets) }
    var car by remember { mutableStateOf(initial.car) }
    var note by remember { mutableStateOf(initial.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text("Moja domácnosť", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper("Počet ľudí v domácnosti", people, 1, 10) { people = it; if (kids > it - 1) kids = maxOf(0, it - 1) }
                Stepper("Z toho deti", kids, 0, maxOf(0, people - 1)) { kids = it }
                CheckRow("Starám sa o niekoho (rodič, chorý blízky)", carer) { carer = it }
                CheckRow("Máme domáce zviera", pets) { pets = it }
                CheckRow("Máme auto", car) { car = it }
                OutlinedTextField(note, { note = it.take(1000) }, Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    label = { Text("O mne (voliteľné)") },
                    placeholder = { Text("Napr. varím doma, šetrím na dovolenku, chcem menej míňať v reštauráciách…") },
                    shape = RoundedCornerShape(14.dp))
                Text("Text „O mne“ sa použije len v AI analýze – a len ak ju sami odošlete.",
                    fontSize = 12.sp, color = Pal.Muted)
                Text("Podľa toho upravím tipy, napr. potraviny na osobu, s deťmi vyšší limit na sladkosti, " +
                    "pri aute nehlásim tankovanie ako nárast.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(people = people, kids = kids, carer = carer, pets = pets, car = car, note = note.trim())) }) { Text("Uložiť", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
internal fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        OutlinedIconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min, modifier = Modifier.size(36.dp)) {
            Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text("$value", Modifier.width(36.dp), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedIconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max, modifier = Modifier.size(36.dp)) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, fontSize = 14.sp)
    }
}

/**
 * AI analýza bez toho, aby aplikácia čokoľvek sama odoslala:
 * ukáže presný text, používateľ ho skopíruje alebo zdieľa do AI aplikácie podľa vlastného výberu.
 */
@Composable
private fun AiShareDialog(vm: MainViewModel, onEditProfile: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val profile by vm.profile.collectAsState()
    var merchants by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    val text = remember(merchants, items, profile) {
        vm.aiSummary(sk.miro.blocky.domain.AiExportOptions(includeMerchants = merchants, includeItems = items))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text("Analýza pomocou AI", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aplikácia sama nič neodosiela. Nižšie je presne ten text, ktorý pošlete – súčty po kategóriách " +
                    "za posledné 3 mesiace, bez adries, IČO a ID bločkov. Po odoslaní platia podmienky súkromia zvolenej AI aplikácie.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp)
                CheckRow("Zahrnúť názvy obchodov", merchants) { merchants = it }
                CheckRow("Zahrnúť konkrétne položky", items) { items = it }
                if (!profile.filled || profile.note.isBlank()) Text(
                    "Doplniť „Moja domácnosť“ a „O mne“ pre presnejšiu analýzu",
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onEditProfile).padding(vertical = 4.dp),
                    color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)).background(Pal.Track)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(10.dp)) {
                    Text(text, fontSize = 11.sp, lineHeight = 15.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Pal.Ink)
                }
                if (copied) Text("Skopírované – vložte do AI aplikácie.", color = Pal.Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Poslať do AI aplikácie"))
            }) { Text("Poslať do AI…", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); copied = true }) { Text("Kopírovať") }
                TextButton(onClick = onDismiss) { Text("Zavrieť") }
            }
        }
    )
}
