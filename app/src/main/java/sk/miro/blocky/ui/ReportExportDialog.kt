package sk.miro.blocky.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.miro.blocky.data.Downloads
import sk.miro.blocky.domain.FixedExpenses
import sk.miro.blocky.domain.I18n
import sk.miro.blocky.domain.Period
import sk.miro.blocky.domain.ReportExport
import sk.miro.blocky.domain.Xlsx
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Report výdavkov do Excelu: výber obdobia (predvolene minulý mesiac podľa dňa výplaty),
 * fixné výdavky áno/nie, stiahnuť do Stiahnutých súborov alebo zdieľať.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportExportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val prev = Period.current(today).minusMonths(1)
    val cur = Period.current(today)
    val first = remember { vm.firstReceiptDate() ?: Period.start(prev) }
    var from by remember { mutableStateOf(Period.start(prev)) }
    var to by remember { mutableStateOf(Period.end(prev)) }
    val hasFixed = FixedExpenses.list.collectAsState().value.isNotEmpty()
    var withFixed by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<Int?>(null) }   // 0 = od, 1 = do
    var busy by remember { mutableStateOf(false) }
    val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
    val df = DateTimeFormatter.ofPattern("d. M. yyyy")
    val name = ReportExport.fileName(from, to)

    fun toast(s: String) = Toast.makeText(context, I18n.tr(s), Toast.LENGTH_LONG).show()
    suspend fun bytes(): ByteArray {
        val sheets = vm.reportSheets(from, to, hasFixed && withFixed)
        return withContext(Dispatchers.IO) { java.io.ByteArrayOutputStream().also { Xlsx.write(sheets, it) }.toByteArray() }
    }
    // Android 8–9: uloženie cez výber miesta
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Downloads.XLSX)) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val b = bytes()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(b) } }
            }.isSuccess
            toast(if (ok) "Report uložený" else "Report sa nepodarilo uložiť")
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Pal.Card,
        title = { Text("Report do Excelu", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Súhrn podľa kategórií, všetky položky, zoznam bločkov a súhrn podľa obchodov – súbor .xlsx " +
                    "(Excel, Google Tabuľky).", fontSize = 13.sp, color = Pal.Muted)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = from == Period.start(prev) && to == Period.end(prev),
                        onClick = { from = Period.start(prev); to = Period.end(prev) }, label = { Text("Minulý mesiac") })
                    FilterChip(selected = from == Period.start(cur) && to == today,
                        onClick = { from = Period.start(cur); to = today }, label = { Text("Tento mesiac") })
                    FilterChip(selected = from == first && to == today,
                        onClick = { from = first; to = today }, label = { Text("Celá história") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = 0 }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("Od ${from.format(df)}", maxLines = 1)
                    }
                    OutlinedButton(onClick = { picking = 1 }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("Do ${to.format(df)}", maxLines = 1)
                    }
                }
                Text(if (days < 1) "Dátum „od“ musí byť pred dátumom „do“." else "$days ${dayWord(days)}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (days < 1) Pal.Red else Pal.Muted)
                if (hasFixed) Row(Modifier.fillMaxWidth().clickable { withFixed = !withFixed },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(withFixed, { withFixed = it }, colors = CheckboxDefaults.colors(checkedColor = Pal.Green))
                    Text("Zahrnúť fixné výdavky", fontSize = 15.sp)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pal.Green)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = days >= 1 && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val f = runCatching {
                            val b = bytes()
                            withContext(Dispatchers.IO) {
                                File(File(context.cacheDir, "exports").apply { mkdirs() }, name).apply { writeBytes(b) }
                            }
                        }.getOrNull()
                        busy = false
                        if (f == null) { toast("Report sa nepodarilo vytvoriť"); return@launch }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = Downloads.XLSX
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, I18n.tr("Zdieľať report")))
                        onDismiss()
                    }
                }) { Text("Zdieľať") }
                TextButton(enabled = days >= 1 && !busy, onClick = {
                    if (Build.VERSION.SDK_INT < 29) createDoc.launch(name) else {
                    busy = true
                    scope.launch {
                        val uri = runCatching {
                            val b = bytes()
                            withContext(Dispatchers.IO) { Downloads.save(context, name, Downloads.XLSX) { it.write(b) } }
                        }.getOrNull()
                        busy = false
                        if (uri != null) { toast("Uložené do Stiahnuté → Bločky"); onDismiss() }
                        else toast("Report sa nepodarilo uložiť")
                    }
                    }
                }) { Text("Stiahnuť", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Zrušiť") } }
    )

    picking?.let { which ->
        val init = if (which == 0) from else to
        val state = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == 0) from = d else to = d
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Zrušiť") } }
        ) { DatePicker(state) }
    }
}
