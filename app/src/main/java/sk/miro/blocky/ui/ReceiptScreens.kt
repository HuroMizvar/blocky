package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.miro.blocky.data.ItemEntity
import sk.miro.blocky.data.ReceiptEntity
import sk.miro.blocky.data.ReceiptListRow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.Color

import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.MerchantType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Suma, ktorá sa započítava (bez prečiarknutých položiek). */
val ReceiptListRow.counted: Long get() = countedCents ?: receipt.totalCents

private val DT = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEE d. M. · HH:mm", SK)
fun fmtDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DT)
private val DM = DateTimeFormatter.ofPattern("d. M.")

/** Pondelok týždňa, do ktorého nákup patrí. */
fun weekStart(ms: Long): java.time.LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

/** „Týždeň 41 · 6. 10. – 12. 10.“ – dni orezané na daný mesiac. */
fun weekLabel(start: java.time.LocalDate, month: java.time.YearMonth): String {
    val from = maxOf(start, month.atDay(1)); val to = minOf(start.plusDays(6), month.atEndOfMonth())
    val wk = start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return "Týždeň $wk · ${from.format(DM)} – ${to.format(DM)}"
}

private fun fmtDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DAY)

private fun pad(p: PaddingValues) =
    PaddingValues(start = 20.dp, end = 20.dp, top = p.calculateTopPadding(), bottom = p.calculateBottomPadding() + 24.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptsScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit,
                   actions: @Composable RowScope.() -> Unit) {
    val all by vm.receiptList.collectAsState()
    val pending by vm.openPending.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val q = sk.miro.blocky.domain.Categorizer.normalize(query)
    val receipts = if (q.isBlank()) all else all.filter {
        sk.miro.blocky.domain.Categorizer.normalize(it.receipt.merchantName).contains(q)
    }
    val grouped = receipts.groupBy { Analytics.monthOf(it.receipt.issuedAt) }
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = pad(contentPadding)) {
        item { ScreenTitle("Bločky", actions = actions) }
        if (all.isEmpty() && pending.isEmpty()) {
            item {
                BCard(Modifier.padding(top = 12.dp)) {
                    Text("Zatiaľ žiadne bločky", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Naskenujte prvý bloček zeleným tlačidlom Skenuj dole.", fontSize = 14.sp, color = Pal.Muted)
                }
            }
        }
        if (all.size > 3) item {
            // vyhľadávanie podľa obchodu
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Pal.Card)
                .padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Pal.Muted, modifier = Modifier.size(20.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Pal.Ink, fontFamily = Jakarta),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Hľadať obchod…", fontSize = 15.sp, color = Pal.Faint)
                        inner()
                    })
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, "Vymazať", tint = Pal.Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (q.isNotBlank()) item {
            Text(if (receipts.isEmpty()) "Nič sa nenašlo." else "Nájdené: ${receipts.size} · spolu ${eur(receipts.sumOf { it.counted })}",
                Modifier.padding(top = 10.dp, start = 4.dp), fontSize = 13.sp, color = Pal.Muted, fontWeight = FontWeight.SemiBold)
        }
        if (pending.isNotEmpty()) {
            item { SectionHeader("Čaká na načítanie (${pending.size})") }
            item {
                BCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    pending.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(
                                if (p.status == "FAILED") Pal.RedSoft else Pal.AmberSoft), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Schedule, null, tint = if (p.status == "FAILED") Pal.Red else Pal.Amber,
                                    modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (p.status == "FAILED") "Nepodarilo sa načítať" else "Načíta sa automaticky",
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(fmtDate(p.createdAt) + (p.message?.let { " · $it" } ?: ""), fontSize = 12.sp,
                                    color = Pal.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.deletePending(p.raw) }) { Icon(Icons.Default.Delete, "Odstrániť", tint = Pal.Muted) }
                        }
                    }
                }
            }
        }
        grouped.forEach { (month, list) ->
            // hlavička mesiaca ostáva prilepená hore pri posúvaní
            stickyHeader(key = "h$month") {
                Row(Modifier.fillMaxWidth().background(Pal.Bg).padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(monthLabel(month), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${list.size} ${receiptWord(list.size)}", fontSize = 12.sp, color = Pal.Muted)
                    }
                    Text(eur(list.sumOf { it.counted }), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            // v rámci mesiaca po týždňoch (pondelok–nedeľa, orezané na daný mesiac)
            list.groupBy { weekStart(it.receipt.issuedAt) }.forEach { (wStart, wList) ->
                item(key = "w$month-$wStart") {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(weekLabel(wStart, month), Modifier.weight(1f), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = Pal.Muted)
                        Text(eur(wList.sumOf { it.counted }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Pal.Muted)
                    }
                }
                item(key = "c$month-$wStart") {
                    BCard(Modifier.padding(bottom = 10.dp), padding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
                        wList.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 62.dp, end = 10.dp), color = Pal.Track)
                            ReceiptRow(r) { onOpen(r.receipt.id) }
                        }
                    }
                }
            }
        }
    }
}

fun receiptWord(n: Int) = when { n == 1 -> "bloček"; n in 2..4 -> "bločky"; else -> "bločkov" }
private fun itemWord(n: Int) = when { n == 1 -> "položka"; n in 2..4 -> "položky"; else -> "položiek" }

@Composable
private fun ReceiptRow(row: ReceiptListRow, onClick: () -> Unit) {
    val r = row.receipt
    val edited = row.editedCount > 0
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp))
        .background(if (edited) Pal.AmberSoft.copy(alpha = 0.55f) else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (r.source == "MANUAL") CatBadge(Category.INE, 42)
        else MerchantBadge(MerchantType.fromName(r.merchantType), 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(shortMerchant(r.merchantName), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtDay(r.issuedAt) + (if (row.itemCount > 0) " · ${row.itemCount} ${itemWord(row.itemCount)}" else "") +
                    when (r.source) { "MANUAL" -> " · ručne"; "DEMO" -> " · demo"; else -> "" },
                fontSize = 12.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            r.addedBy?.let { who ->
                Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberDot(who, 14)
                    Spacer(Modifier.width(4.dp))
                    Text(who, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = memberColor(who))
                }
            }
            if (edited) EditedChip(Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(eur(row.counted), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (row.counted != r.totalCents) Text(eur(r.totalCents), fontSize = 11.sp, color = Pal.Muted,
                textDecoration = TextDecoration.LineThrough)
        }
    }
}

/** Jemné označenie ručne upraveného bločku / položky. */
@Composable
fun EditedChip(modifier: Modifier = Modifier, text: String = "upravený") {
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(Pal.AmberSoft).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Edit, null, tint = Pal.Amber, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Pal.Amber)
    }
}

@Composable
fun ReceiptDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val data by vm.repo.receipt(id).collectAsState(initial = null)
    var editingId by remember { mutableStateOf<Long?>(null) }
    var pickingFor by remember { mutableStateOf<ItemEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var changeType by remember { mutableStateOf(false) }

    Scaffold(containerColor = Pal.Bg) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, Modifier.offset(x = (-12).dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Zmazať bloček", tint = Pal.Muted) }
                }
            }
            val d = data ?: return@LazyColumn
            item {
                BCard {
                    val mType = MerchantType.fromName(d.receipt.merchantType)
                    if (d.receipt.source != "MANUAL") Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { changeType = true }
                        .padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MerchantBadge(mType, 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(mType.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = merchantStyle(mType).fg)
                            Text("Zmeniť typ prevádzky", fontSize = 12.sp, color = Pal.Muted)
                        }
                    }
                    Text(shortMerchant(d.receipt.merchantName), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(fmtDate(d.receipt.issuedAt), fontSize = 13.sp, color = Pal.Muted)
                    d.receipt.addedBy?.let { who ->
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            MemberDot(who, 18)
                            Spacer(Modifier.width(6.dp))
                            Text("Nahral(a): $who", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = memberColor(who))
                        }
                    }
                    d.receipt.storeAddress?.let { Text(it, fontSize = 13.sp, color = Pal.Muted) }
                    Spacer(Modifier.height(10.dp))
                    val counted = d.items.filter { !it.excluded }.sumOf { it.priceCents }
                    val excludedN = d.items.count { it.excluded }
                    val saved = d.items.sumOf { it.discountCents }
                    val editedN = d.items.count { it.edited }
                    Text(eur(if (editedN > 0) counted else d.receipt.totalCents), fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    if (editedN > 0) {
                        EditedChip(Modifier.padding(top = 2.dp, bottom = 4.dp), "upravený bloček")
                        Text("Započítané po úpravách · na bločku ${eur(d.receipt.totalCents)}" +
                                (if (excludedN > 0) " · prečiarknuté $excludedN" else ""),
                            fontSize = 12.sp, color = Pal.Amber)
                    }
                    if (saved > 0) Text("Na zľavách ste ušetrili ${eur(saved)}", fontSize = 12.sp,
                        color = Pal.Green, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("${d.items.size} ${itemWord(d.items.size)} · ťuknutím na položku zmeníte kategóriu alebo počet kusov, " +
                        "ikonou vpravo ju prečiarknete", fontSize = 12.sp, color = Pal.Muted, lineHeight = 16.sp)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                BCard(padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    d.items.forEach { item ->
                        val c = Category.fromName(item.category)
                        val ex = item.excluded
                        val strike = if (ex) TextDecoration.LineThrough else TextDecoration.None
                        val qtyChanged = item.origQuantity != null
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (item.edited) Pal.AmberSoft.copy(alpha = 0.55f) else Color.Transparent)
                            .clickable { editingId = item.id }
                            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.alpha(if (ex) 0.4f else 1f)) { CatBadge(c, 34) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = strike,
                                    color = if (ex) Pal.Muted else Pal.Ink)
                                val qty = when {
                                    qtyChanged -> "${fmtQty(item.quantity)} z ${fmtQty(item.origQuantity!!)} ks · "
                                    item.quantity != 1.0 -> "${fmtQty(item.quantity)} × · "
                                    else -> ""
                                }
                                Text(if (ex) "Nezapočítané – vrátené / reklamované"
                                     else qty + c.label + if (item.categoryManual) " · upravené" else "",
                                    fontSize = 12.sp, color = if (ex) Pal.Muted else catStyle(c).fg)
                                if (qtyChanged && !ex) Text("na bločku ${eur(item.origPriceCents ?: item.priceCents)} · počet upravený",
                                    fontSize = 12.sp, color = Pal.Amber, fontWeight = FontWeight.SemiBold)
                                if (item.discountCents > 0 && !ex) Text(
                                    "zľava −${eur(item.discountCents)} · pôvodne ${eur(item.priceCents + item.discountCents)}",
                                    fontSize = 12.sp, color = Pal.Green, fontWeight = FontWeight.SemiBold)
                            }
                            Text(eur(item.priceCents), fontSize = 14.sp, fontWeight = FontWeight.Bold, textDecoration = strike,
                                color = if (ex) Pal.Muted else Pal.Ink)
                            IconButton(onClick = { vm.setExcluded(item.id, !ex) }) {
                                Icon(if (ex) Icons.AutoMirrored.Filled.Undo else Icons.Default.RemoveCircleOutline,
                                    if (ex) "Znova započítať" else "Prečiarknuť – nezapočítať",
                                    tint = if (ex) Pal.Green else Pal.Faint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    val rec = data?.receipt
    if (changeType && rec?.merchantKey != null) {
        MerchantTypeDialog(
            sk.miro.blocky.data.MerchantEntity(rec.merchantKey, rec.merchantName, rec.merchantType ?: "UNKNOWN", false),
            onPick = { t -> vm.answerMerchant(sk.miro.blocky.data.MerchantEntity(rec.merchantKey, rec.merchantName, t.name, true), t); changeType = false },
            onLater = { changeType = false })
    }

    val editingItem = editingId?.let { eid -> data?.items?.firstOrNull { it.id == eid } }
    if (editingItem != null) ItemEditDialog(editingItem,
        onDismiss = { editingId = null },
        onCategory = { pickingFor = editingItem; editingId = null },
        onQuantity = { q -> vm.setQuantity(editingItem.id, q) },
        onExclude = { ex -> vm.setExcluded(editingItem.id, ex) },
        onRestore = { vm.restoreItem(editingItem.id) })
    pickingFor?.let { item ->
        CategoryPickerDialog(item.name, Category.fromName(item.category), onDismiss = { pickingFor = null }) { c, all ->
            vm.recategorize(item.id, item.normName, c, all); pickingFor = null
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Pal.Card,
        title = { Text("Zmazať bloček?") },
        confirmButton = { TextButton(onClick = { vm.deleteReceipt(id); confirmDelete = false; onBack() }) { Text("Zmazať", color = Pal.Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } }
    )
}

/** Úprava položky: kategória, počet kusov, prečiarknutie, obnovenie pôvodného stavu. */
@Composable
private fun ItemEditDialog(item: ItemEntity, onDismiss: () -> Unit, onCategory: () -> Unit,
                           onQuantity: (Double) -> Unit, onExclude: (Boolean) -> Unit, onRestore: () -> Unit) {
    val c = Category.fromName(item.category)
    val origQty = item.origQuantity ?: item.quantity
    // počet sa dá meniť pri kusových položkách (celé číslo, aspoň 2 ks na bločku)
    val canQty = origQty >= 2.0 && origQty % 1.0 == 0.0 && item.priceCents > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // kategória
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Bg).clickable(onClick = onCategory)
                    .padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(c, 36)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kategória", fontSize = 12.sp, color = Pal.Muted)
                        Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Zmeniť", color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                // počet kusov
                if (canQty) Column {
                    Text("Koľko kusov ste naozaj zaplatili?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pal.Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val q = item.quantity
                        FilledTonalIconButton(onClick = { onQuantity(q - 1) }, enabled = q > 1.0 && !item.excluded) {
                            Icon(Icons.Default.Remove, "Menej") }
                        Text("${fmtQty(q)} z ${fmtQty(origQty)} ks", Modifier.weight(1f), fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        FilledTonalIconButton(onClick = { onQuantity(q + 1) }, enabled = q < origQty && !item.excluded) {
                            Icon(Icons.Default.Add, "Viac") }
                    }
                    Text("Započíta sa ${eur(item.priceCents)}" +
                            (item.origPriceCents?.let { " (na bločku ${eur(it)})" } ?: ""),
                        Modifier.fillMaxWidth().padding(top = 4.dp), fontSize = 13.sp,
                        color = if (item.origQuantity != null) Pal.Amber else Pal.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                // prečiarknutie
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onExclude(!item.excluded) }
                    .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Nezapočítať do výdavkov", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("vrátené, reklamované, platil niekto iný", fontSize = 12.sp, color = Pal.Muted)
                    }
                    Switch(checked = item.excluded, onCheckedChange = { onExclude(it) })
                }
                if (item.edited) TextButton(onClick = onRestore, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Pal.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Obnoviť podľa bločku", color = Pal.Amber, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hotovo", fontWeight = FontWeight.Bold) } }
    )
}

private fun fmtQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else "%.3f".format(SK, q).trimEnd('0').trimEnd(',')


@Composable
fun BudgetsScreen(vm: MainViewModel, contentPadding: PaddingValues, actions: @Composable RowScope.() -> Unit) {
    val st by vm.state.collectAsState()
    val spent = st.report?.categories?.associate { it.category to it.cents }.orEmpty()
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = pad(contentPadding)) {
        item { ScreenTitle("Rozpočty", actions = actions) }
        item {
            Text("Mesačný limit na kategóriu. Prázdne pole = bez limitu. Prekročenie sa ukáže v tipoch.",
                Modifier.padding(bottom = 12.dp), fontSize = 13.sp, color = Pal.Muted)
        }
        item {
            BCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Category.entries.filter { it != Category.ZLAVY && it != Category.ZALOHY }.forEach { c ->
                    BudgetRow(c, st.budgets[c], spent[c] ?: 0L) { vm.setBudget(c, it) }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(c: Category, budget: Long?, spent: Long, onSave: (Long?) -> Unit) {
    var text by remember(budget) { mutableStateOf(budget?.let { (it / 100).toString() } ?: "") }
    val over = budget != null && spent > budget
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CatBadge(c, 34)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("tento mesiac ${eur(spent)}", fontSize = 12.sp, color = if (over) Pal.Red else Pal.Muted)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(6)
                onSave(text.toLongOrNull()?.times(100))
            },
            modifier = Modifier.width(104.dp), singleLine = true, suffix = { Text("€") },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

/** Ručné zadanie výdavku bez QR (napr. trh, bloček bez eKasa). */
@Composable
fun ManualEntryDialog(onDismiss: () -> Unit, onSave: (String, Long, Category) -> Unit) {
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Category.INE) }
    var expanded by remember { mutableStateOf(false) }
    val cents = amount.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text("Ručný záznam", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Obchod / popis") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp))
                OutlinedTextField(amount, { amount = it }, label = { Text("Suma €") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Box {
                    OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(14.dp)) {
                        CatBadge(cat, 24); Spacer(Modifier.width(8.dp)); Text(cat.label)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        Category.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, leadingIcon = { CatBadge(c, 24) },
                                onClick = { cat = c; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = { onSave(merchant, cents!!, cat) }) { Text("Uložiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}
