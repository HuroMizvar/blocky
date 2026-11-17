package sk.miro.blocky.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.miro.blocky.data.ReceiptCopies
import sk.miro.blocky.domain.ReceiptCopy

/**
 * Elektronická kópia bločku vo vzhľade papierového bločku – vždy biely papier s čiernym textom
 * (vo všetkých farebných témach), aby ho predavač spoznal. Dá sa stiahnuť ako PDF alebo zdieľať.
 */
@Composable
fun ReceiptCopyDialog(vm: MainViewModel, id: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copy by remember { mutableStateOf<ReceiptCopy?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(id) {
        copy = vm.repo.receiptCopy(id)
        loading = false
    }
    // Android 8–9: uloženie cez výber miesta
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val c = copy ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { ReceiptCopies.writePdf(c, it) } }.isSuccess
            }
            Toast.makeText(context, sk.miro.blocky.domain.I18n.tr(if (ok) "Bloček uložený" else "Bloček sa nepodarilo uložiť"),
                Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Pal.Bg)) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Zavrieť") }
                Text("Elektronický bloček", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val c = copy
                when {
                    loading -> Column(Modifier.padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Pal.Green)
                        Spacer(Modifier.height(12.dp))
                        Text("Načítavam bloček…", color = Pal.Muted, fontSize = 14.sp)
                    }
                    c == null -> Text("Bloček sa nenašiel.", Modifier.padding(top = 60.dp), color = Pal.Muted)
                    else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp)) {
                        PaperReceipt(c)
                        if (!c.complete && !c.manual) Text(
                            "Úplné údaje z eKasa sa nepodarilo načítať (bez pripojenia?). Kópia je zostavená z uložených položiek – " +
                                "skúste ju otvoriť znova s internetom.",
                            Modifier.padding(top = 10.dp).widthIn(max = 360.dp), fontSize = 12.sp, color = Pal.Amber
                        )
                    }
                }
            }
            val c = copy
            if (c != null) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { runCatching { ReceiptCopies.saveToDownloads(context, c) }.getOrNull() }
                        when {
                            uri != null -> Toast.makeText(context,
                                sk.miro.blocky.domain.I18n.tr("Uložené do Stiahnuté → Bločky"), Toast.LENGTH_LONG).show()
                            Build.VERSION.SDK_INT < 29 -> createDoc.launch(c.fileName())
                            else -> Toast.makeText(context, sk.miro.blocky.domain.I18n.tr("Bloček sa nepodarilo uložiť"),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Green, contentColor = Pal.OnGreen)) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Stiahnuť PDF")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { runCatching { ReceiptCopies.shareFile(context, c) }.getOrNull() }
                            ?: return@launch
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, f.nameWithoutExtension)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, sk.miro.blocky.domain.I18n.tr("Zdieľať bloček")))
                    }
                }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Zdieľať")
                }
            }
        }
    }
}

private val PaperInk = Color(0xFF111111)

/** Papierový bloček: biely pás, písmo s pevnou šírkou, QR kód na overenie. */
@Composable
private fun PaperReceipt(c: ReceiptCopy) {
    // texty bločku sa neprekladajú (je to kópia dokladu) – preto material3.Text, nie prekladajúci Text
    Column(Modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
        .padding(horizontal = 14.dp, vertical = 18.dp)) {
        @Composable
        fun rows(list: List<ReceiptCopy.Row>) = list.forEach { r ->
            androidx.compose.material3.Text(r.text, Modifier.fillMaxWidth(), color = PaperInk, fontFamily = FontFamily.Monospace,
                fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false,
                fontWeight = if (r.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (r.center) TextAlign.Center else TextAlign.Start)
        }
        rows(c.rows())
        c.qrContent?.let { content ->
            val m = remember(content) { runCatching { ReceiptCopies.qr(content) }.getOrNull() }
            if (m != null) Canvas(Modifier.padding(vertical = 10.dp).size(150.dp).align(Alignment.CenterHorizontally)) {
                val cell = size.width / m.width
                for (i in 0 until m.width) for (j in 0 until m.height) if (m[i, j])
                    drawRect(PaperInk, Offset(i * cell, j * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
        rows(c.footer())
    }
}
