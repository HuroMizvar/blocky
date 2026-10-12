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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onExport: () -> Unit) {
    Scaffold(containerColor = Pal.Bg, topBar = {
        TopAppBar(
            title = { Text("Nastavenia") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)) {
            PrivacySection(vm, onExport, onBack)
            HorizontalDivider(Modifier.padding(top = 20.dp))
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
    val auto by vm.autoUpdate.collectAsState()
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Kontrolovať pri spustení")
            Text("Aplikácia sa len opýta GitHubu na číslo najnovšej verzie. Žiadne vaše dáta neposiela.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(auto, vm::setAutoUpdate)
    }
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

@Composable
private fun PrivacySection(vm: MainViewModel, onExport: () -> Unit, onDeleted: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SectionTitle("Súkromie a vaše dáta")
    BCard(padding = PaddingValues(16.dp)) {
        listOf(
            "Bločky, položky, rozpočty a profil sú uložené len v tomto telefóne – v súkromnom úložisku aplikácie.",
            "Bez účtu, bez cloudu, bez reklám, bez analytiky a bez reklamného ID.",
            "Dáta sa nezálohujú do Google Drive ani neprenášajú na iný telefón.",
            "QR kód sa číta priamo v telefóne. Obraz z kamery sa nikam neposiela.",
            "Kategórie a tipy počítajú pravidlá v aplikácii – žiadna umelá inteligencia a nič sa nepoužíva na jej trénovanie.",
            "Sieť sa používa len na: (1) načítanie položiek bločku z eKasa Finančnej správy SR podľa ID dokladu, " +
                "(2) kontrolu aktualizácií na GitHube (dá sa vypnúť nižšie)."
        ).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", Modifier.width(14.dp), color = Pal.Green, fontWeight = FontWeight.Bold)
                Text(line, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExport, shape = RoundedCornerShape(14.dp)) { Text("Exportovať dáta (CSV)") }
        Button(onClick = { confirm = true }, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Red)) { Text("Zmazať všetko") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = Pal.Card,
        title = { Text("Zmazať všetky dáta?") },
        text = { Text("Natrvalo sa zmažú všetky bločky, položky, rozpočty, pravidlá kategórií, prevádzky a profil domácnosti. Nedá sa to vrátiť.") },
        confirmButton = {
            TextButton(onClick = { confirm = false; vm.deleteEverything(onDeleted) }) { Text("Zmazať", color = Pal.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Zrušiť") } }
    )
}
