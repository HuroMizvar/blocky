package sk.miro.blocky.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.miro.blocky.data.EkasaClient
import sk.miro.blocky.data.ScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nastavenia") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)) {
            UpdateSection(vm)
            HorizontalDivider(Modifier.padding(top = 20.dp))
            TestSection(vm)
            HorizontalDivider(Modifier.padding(top = 20.dp))
            EndpointSection(vm)
        }
    }
}

@Composable
private fun UpdateSection(vm: MainViewModel) {
    val u by vm.update.collectAsState()
    val up = vm.updater
    SectionTitle("Aktualizácie")
    Text("Nainštalovaná verzia: ${up.currentVersionName} (build ${up.currentVersion})")
    if (!up.enabled) {
        Text("Lokálny build – aktualizácie z GitHubu sú vypnuté. Nainštalujte APK z GitHub Actions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("Zdroj: github.com/${up.repoName}", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    when (val s = u) {
        UpdateUi.Idle -> Button(onClick = { vm.checkUpdate() }) { Text("Skontrolovať aktualizácie") }
        UpdateUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Kontrolujem…")
        }
        UpdateUi.UpToDate -> {
            Text("Máte najnovšiu verziu.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { vm.checkUpdate() }) { Text("Skontrolovať znova") }
        }
        is UpdateUi.Error -> {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { vm.checkUpdate() }) { Text("Skúsiť znova") }
        }
        else -> UpdateActions(vm, s)
    }
}

/** Spoločné ovládanie pre „dostupná / sťahuje sa / pripravená“ – používa ho aj dialóg pri štarte. */
@Composable
fun UpdateActions(vm: MainViewModel, s: UpdateUi) {
    when (s) {
        is UpdateUi.Available -> {
            Text("Dostupná nová verzia: build ${s.info.versionCode}", fontWeight = FontWeight.SemiBold)
            if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.downloadUpdate(s.info) }, Modifier.padding(top = 8.dp)) {
                Text("Stiahnuť a nainštalovať (${s.info.sizeBytes / 1_048_576} MB)")
            }
        }
        is UpdateUi.Downloading -> {
            Text("Sťahujem build ${s.info.versionCode}… ${(s.progress * 100).toInt()} %")
            LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        is UpdateUi.ReadyToInstall -> {
            val up = vm.updater
            var allowed by remember { mutableStateOf(up.canInstall()) }
            // po návrate z nastavení Androidu znovu overiť povolenie
            LaunchedEffect(Unit) {
                while (!allowed) { kotlinx.coroutines.delay(1000); allowed = up.canInstall() }
            }
            Text("Build ${s.info.versionCode} je stiahnutý.", fontWeight = FontWeight.SemiBold)
            if (!allowed) {
                Text("Jednorazovo povoľte aplikácii Bločky inštalovať aplikácie (Nastavenia → Inštalovať neznáme aplikácie).",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { up.openInstallPermissionSettings() }, Modifier.padding(top = 8.dp)) { Text("Otvoriť povolenie") }
            } else {
                Button(onClick = { up.install(s.file) }, Modifier.padding(top = 8.dp)) { Text("Nainštalovať") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun TestSection(vm: MainViewModel) {
    val mock by vm.mockMode.collectAsState()
    val demo by vm.demoCount.collectAsState()
    val msg by vm.testMsg.collectAsState()
    val scan by vm.scan.collectAsState()
    var qr by remember { mutableStateOf("") }
    var json by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { vm.resetScan() } }

    SectionTitle("Testovací režim")
    Text("Demo bločky v databáze: $demo", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::seedDemo) { Text("Nahrať demo dáta") }
        OutlinedButton(onClick = vm::clearDemo, enabled = demo > 0) { Text("Zmazať demo") }
    }
    msg?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall) }

    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Simulovaný eKasa server")
            Text("Skener prijme akýkoľvek QR kód a vytvorí náhodný demo nákup. Nevolá Finančnú správu.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(mock, vm::setMockMode)
    }

    Text("Obsah QR kódu (bez kamery)", Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
    OutlinedTextField(qr, { qr = it }, Modifier.fillMaxWidth(), placeholder = { Text("O-XXXXXXXX… alebo OKP:…") },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace), singleLine = true)
    Button(onClick = { vm.resetScan(); vm.onQr(qr) }, enabled = qr.isNotBlank() && scan !is ScanUi.Loading,
        modifier = Modifier.padding(top = 4.dp)) { Text("Spracovať QR") }

    Text("JSON odpoveď eKasa (ladenie parsera)", Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
    OutlinedTextField(json, { json = it }, Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp),
        placeholder = { Text("{\"returnValue\":0,\"receipt\":{…}}") },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace))
    Button(onClick = { vm.resetScan(); vm.importJson(json) }, enabled = json.isNotBlank(),
        modifier = Modifier.padding(top = 4.dp)) { Text("Importovať JSON") }

    when (val s = scan) {
        ScanUi.Loading -> Text("Spracúvam…", Modifier.padding(top = 8.dp))
        is ScanUi.Done -> {
            val (text, isErr) = when (val r = s.result) {
                is ScanResult.Saved -> "Uložené: ${r.merchant}, ${r.items} položiek, ${eur(r.totalCents)}" to false
                is ScanResult.Duplicate -> "Už uložené: ${r.receiptId}" to false
                is ScanResult.Error -> r.message to true
            }
            Text(text, Modifier.padding(top = 8.dp),
                color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        ScanUi.Idle -> Unit
    }
}

@Composable
private fun EndpointSection(vm: MainViewModel) {
    var endpoint by remember { mutableStateOf(vm.repo.endpoint) }
    SectionTitle("eKasa API")
    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(),
        label = { Text("Endpoint") }, singleLine = true)
    Row {
        TextButton(onClick = { vm.repo.endpoint = endpoint }) { Text("Uložiť") }
        TextButton(onClick = { endpoint = EkasaClient.DEFAULT_ENDPOINT; vm.repo.endpoint = endpoint }) { Text("Predvolené") }
    }
}
