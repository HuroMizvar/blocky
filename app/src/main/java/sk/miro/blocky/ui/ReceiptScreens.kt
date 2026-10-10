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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Category
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DT = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
private val DAY = DateTimeFormatter.ofPattern("d. M. · HH:mm")
fun fmtDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DT)
private fun fmtDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DAY)

private fun pad(p: PaddingValues) =
    PaddingValues(start = 20.dp, end = 20.dp, top = p.calculateTopPadding(), bottom = p.calculateBottomPadding() + 24.dp)

@Composable
fun ReceiptsScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit,
                   actions: @Composable RowScope.() -> Unit) {
    val receipts by vm.receipts.collectAsState()
    val pending by vm.openPending.collectAsState()
    val grouped = receipts.groupBy { Analytics.monthOf(it.issuedAt) }
    LazyColumn(Modifier.fillMaxSize().background(Pal.Bg), contentPadding = pad(contentPadding)) {
        item { ScreenTitle("Bločky", actions = actions) }
        if (receipts.isEmpty() && pending.isEmpty()) {
            item {
                BCard(Modifier.padding(top = 12.dp)) {
                    Text("Zatiaľ žiadne bločky", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Naskenujte prvý bloček tlačidlom v strede dole.", fontSize = 14.sp, color = Pal.Muted)
                }
            }
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
            item(key = "h$month") {
                SectionHeader(monthLabel(month)) {
                    Text(eur(list.sumOf { it.totalCents }), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pal.Muted)
                }
            }
            item(key = "c$month") {
                BCard(padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    list.forEach { r -> ReceiptRow(r) { onOpen(r.id) } }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(r: ReceiptEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Pal.Track), contentAlignment = Alignment.Center) {
            Text(r.merchantName.trim().take(1).uppercase(), fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(shortMerchant(r.merchantName), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtDay(r.issuedAt) + when (r.source) { "MANUAL" -> " · ručne"; "DEMO" -> " · demo"; else -> "" },
                fontSize = 12.sp, color = Pal.Muted)
        }
        Text(eur(r.totalCents), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReceiptDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val data by vm.repo.receipt(id).collectAsState(initial = null)
    var editing by remember { mutableStateOf<ItemEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

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
                    Text(shortMerchant(d.receipt.merchantName), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(fmtDate(d.receipt.issuedAt), fontSize = 13.sp, color = Pal.Muted)
                    d.receipt.storeAddress?.let { Text(it, fontSize = 13.sp, color = Pal.Muted) }
                    Spacer(Modifier.height(10.dp))
                    Text(eur(d.receipt.totalCents), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Text("${d.items.size} položiek · ťuknutím zmeníte kategóriu", fontSize = 12.sp, color = Pal.Muted)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                BCard(padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    d.items.forEach { item ->
                        val c = Category.fromName(item.category)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { editing = item }
                            .padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatBadge(c, 34)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                val qty = if (item.quantity != 1.0) "${fmtQty(item.quantity)} × · " else ""
                                Text(qty + c.label + if (item.categoryManual) " · upravené" else "",
                                    fontSize = 12.sp, color = catStyle(c).fg)
                            }
                            Text(eur(item.priceCents), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        CategoryDialog(item, onDismiss = { editing = null }) { c, all ->
            vm.recategorize(item, c, all); editing = null
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

private fun fmtQty(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else "%.3f".format(SK, q).trimEnd('0').trimEnd(',')

@Composable
private fun CategoryDialog(item: ItemEntity, onDismiss: () -> Unit, onPick: (Category, Boolean) -> Unit) {
    var all by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it })
                    Text("Zapamätať pre všetky položky s týmto názvom", fontSize = 13.sp)
                }
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Category.entries.forEach { c ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c, all) }
                            .padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatBadge(c, 30)
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, fontWeight = if (c.name == item.category) FontWeight.ExtraBold else FontWeight.Normal)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zavrieť") } }
    )
}

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
