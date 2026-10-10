package sk.miro.blocky.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sk.miro.blocky.data.ItemEntity
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Category
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DT = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm")
fun fmtDate(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DT)

@Composable
fun ReceiptsScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val receipts by vm.receipts.collectAsState()
    val pending by vm.openPending.collectAsState()
    if (receipts.isEmpty() && pending.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(24.dp)) {
            Text("Zatiaľ žiadne bločky.")
        }
        return
    }
    val grouped = receipts.groupBy { Analytics.monthOf(it.issuedAt) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
        start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        if (pending.isNotEmpty()) {
            item(key = "pending-h") {
                Text("Čaká na načítanie z eKasa (${pending.size})", Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(pending, key = { "p-" + it.raw }) { p ->
                ListItem(
                    headlineContent = { Text(if (p.status == "FAILED") "Nepodarilo sa načítať" else "Čaká – skúsi sa automaticky") },
                    supportingContent = { Text(fmtDate(p.createdAt) + (p.message?.let { "\n$it" } ?: "")) },
                    trailingContent = {
                        IconButton(onClick = { vm.deletePending(p.raw) }) { Icon(Icons.Default.Delete, "Odstrániť") }
                    }
                )
            }
        }
        grouped.forEach { (month, list) ->
            item(key = "h$month") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                    Text(monthLabel(month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(eur(list.sumOf { it.totalCents }), fontWeight = FontWeight.SemiBold)
                }
            }
            items(list, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.merchantName) },
                    supportingContent = {
                        Text(fmtDate(r.issuedAt) + when (r.source) {
                            "MANUAL" -> " · ručne"
                            "DEMO" -> " · demo"
                            else -> ""
                        })
                    },
                    trailingContent = { Text(eur(r.totalCents), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.clickable { onOpen(r.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val data by vm.repo.receipt(id).collectAsState(initial = null)
    var editing by remember { mutableStateOf<ItemEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(data?.receipt?.merchantName ?: "Bloček") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") } },
            actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Zmazať") } }
        )
    }) { pad ->
        val d = data ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(fmtDate(d.receipt.issuedAt), style = MaterialTheme.typography.bodyMedium)
                d.receipt.storeAddress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Spolu ${eur(d.receipt.totalCents)}", Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Ťuknutím na položku zmeníte kategóriu.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(d.items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().clickable { editing = item }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        val qty = if (item.quantity != 1.0) "${item.quantity} × · " else ""
                        Text(qty + Category.fromName(item.category).label + if (item.categoryManual) " ✎" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(eur(item.priceCents), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider()
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
        title = { Text("Zmazať bloček?") },
        confirmButton = { TextButton(onClick = { vm.deleteReceipt(id); confirmDelete = false; onBack() }) { Text("Zmazať") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } }
    )
}

@Composable
private fun CategoryDialog(item: ItemEntity, onDismiss: () -> Unit, onPick: (Category, Boolean) -> Unit) {
    var all by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it })
                    Text("Zapamätať pre všetky položky s týmto názvom", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Category.entries.forEach { c ->
                        Text(c.label, Modifier.fillMaxWidth().clickable { onPick(c, all) }.padding(vertical = 10.dp),
                            fontWeight = if (c.name == item.category) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zavrieť") } }
    )
}

@Composable
fun BudgetsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val st by vm.state.collectAsState()
    val spent = st.report?.categories?.associate { it.category to it.cents }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
        start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
        item {
            SectionTitle("Mesačné rozpočty")
            Text("Prázdne pole = bez rozpočtu. Prekročenie sa zobrazí v odporúčaniach.",
                style = MaterialTheme.typography.bodySmall)
        }
        items(Category.entries.filter { it != Category.ZLAVY && it != Category.ZALOHY }) { c ->
            BudgetRow(c, st.budgets[c], spent[c] ?: 0L) { vm.setBudget(c, it) }
        }
    }
}

@Composable
private fun BudgetRow(c: Category, budget: Long?, spent: Long, onSave: (Long?) -> Unit) {
    var text by remember(budget) { mutableStateOf(budget?.let { (it / 100).toString() } ?: "") }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(c.label)
            Text("tento mesiac ${eur(spent)}", style = MaterialTheme.typography.bodySmall,
                color = if (budget != null && spent > budget) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }.take(6)
                onSave(text.toLongOrNull()?.times(100))
            },
            modifier = Modifier.width(110.dp), singleLine = true, suffix = { Text("€") },
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
        title = { Text("Ručný záznam") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Obchod / popis") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Suma €") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(cat.label) }
                    DropdownMenu(expanded, { expanded = false }) {
                        Category.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.label) }, onClick = { cat = c; expanded = false })
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
