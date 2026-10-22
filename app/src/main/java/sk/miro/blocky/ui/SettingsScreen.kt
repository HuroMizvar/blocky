package sk.miro.blocky.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        val tabs = listOf("Domácnosť", "AI", "Aktualizácie", "Súkromie a dáta")
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val update by vm.update.collectAsState()
        val hasUpdate = update is UpdateUi.Available || update is UpdateUi.ReadyToInstall
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Pal.Bg, contentColor = Pal.Ink, edgePadding = 12.dp,
                indicator = { pos ->
                    with(TabRowDefaults) { SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = Pal.Green) }
                }) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        selectedContentColor = Pal.Ink, unselectedContentColor = Pal.Muted,
                        text = {
                            BadgedBox(badge = { if (i == 2 && hasUpdate) Badge(containerColor = Pal.Green) }) {
                                Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Medium)
                            }
                        })
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)) {
                when (tab) {
                    0 -> {
                        HouseholdProfileSection(vm)
                        HorizontalDivider(Modifier.padding(top = 20.dp))
                        HouseholdSection(vm)
                    }
                    1 -> AiSection(vm)
                    2 -> {
                        UpdateSection(vm)
                        // nástroje na testovanie len vo verzii z GitHubu (nie v Obchode Play)
                        if (sk.miro.blocky.BuildConfig.DEBUG) {
                            HorizontalDivider(Modifier.padding(top = 20.dp))
                            TestSection(vm)
                            HorizontalDivider(Modifier.padding(top = 20.dp))
                            EndpointSection(vm)
                        }
                    }
                    else -> {
                        BackupSection(vm)
                        HorizontalDivider(Modifier.padding(top = 20.dp))
                        PrivacySection(vm, onExport, onBack)
                    }
                }
            }
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
        Text(if (sk.miro.blocky.BuildConfig.DEBUG) "Lokálny build – aktualizácie z GitHubu sú vypnuté. Nainštalujte APK z GitHub Actions."
             else "Aplikáciu aktualizuje Obchod Google Play.",
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
            "Bez spoločnej domácnosti sú bločky, položky, rozpočty a profil uložené len v tomto telefóne.",
            "Spoločná domácnosť je voliteľná. Údaje sa zašifrujú v telefóne a server (Google Firebase, EÚ) " +
                "dostane len zašifrovaný text – kľúč majú iba členovia domácnosti.",
            "Bez Google účtu, bez reklám, bez analytiky a bez reklamného ID. Pre domácnosť sa vytvorí anonymný prístup bez mena a e-mailu.",
            "QR kód sa číta priamo v telefóne. Obraz z kamery sa nikam neposiela.",
            "Kategórie a tipy počítajú pravidlá v aplikácii – žiadna umelá inteligencia a nič sa nepoužíva na jej trénovanie.",
            "Sieť sa používa len na: (1) načítanie položiek bločku z eKasa Finančnej správy SR podľa ID dokladu, " +
                "(2) kontrolu aktualizácií na GitHube (dá sa vypnúť nižšie), (3) synchronizáciu domácnosti, ak ju zapnete."
        ).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", Modifier.width(14.dp), color = Pal.Green, fontWeight = FontWeight.Bold)
                Text(line, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
    GovSourceNote(Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExport, shape = RoundedCornerShape(14.dp)) { Text("Exportovať dáta (CSV)") }
        Button(onClick = { confirm = true }, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Red)) { Text("Zmazať všetko") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = Pal.Card,
        title = { Text("Zmazať všetky dáta?") },
        text = { Text("Natrvalo sa zmažú všetky bločky, položky, rozpočty, pravidlá kategórií, prevádzky a profil v tomto telefóne. Ak ste v spoločnej domácnosti, odídete z nej (údaje ostatných členov ostanú). Nedá sa to vrátiť.") },
        confirmButton = {
            TextButton(onClick = { confirm = false; vm.deleteEverything(onDeleted) }) { Text("Zmazať", color = Pal.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Zrušiť") } }
    )
}

/** Zaraďovanie položiek pomocou AI: vstavaná AI (v aplikácii) a voliteľne AI telefónu (Gemini Nano). */
@Composable
private fun AiSection(vm: MainViewModel) {
    val ai by vm.aiEnabled.collectAsState()
    val phone by vm.phoneAiEnabled.collectAsState()
    val phoneState by vm.phoneAiState.collectAsState()
    val msg by vm.aiMsg.collectAsState()
    SectionTitle("Zaraďovanie položiek (AI)")
    BCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Vstavaná AI", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Rozumie aj skratkám z bločkov (napr. „MLK POLOT.“). Beží len v telefóne, nič neodosiela " +
                    "a učí sa z vašich opráv.", fontSize = 12.sp, color = Pal.Muted, lineHeight = 16.sp)
            }
            Switch(ai, vm::setAiEnabled)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Pal.Track)
        when (phoneState) {
            null -> Column {
                Text("AI telefónu (Gemini Nano)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Niektoré novšie telefóny majú vlastnú AI. Zistenie aj používanie prebieha cez Google ML Kit, " +
                    "ktorý posiela Googlu diagnostické údaje (model telefónu, chyby – nie vaše položky).",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 16.sp)
                TextButton(onClick = vm::checkPhoneAi, contentPadding = PaddingValues(0.dp)) { Text("Zistiť, či ju má tento telefón") }
            }
            sk.miro.blocky.data.PhoneAi.State.UNAVAILABLE -> Text(
                "Tento telefón nemá vstavanú AI (Gemini Nano). Používa sa AI aplikácie.", fontSize = 12.sp, color = Pal.Muted)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI telefónu (Gemini Nano)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Ak je zapnutá, má prednosť pred vstavanou AI. Názvy položiek spracuje v telefóne, " +
                        "ale Google pri nej zbiera diagnostické údaje (model telefónu, veľkosť vstupu, chyby – nie text položiek)." +
                        if (phoneState == sk.miro.blocky.data.PhoneAi.State.DOWNLOADABLE) " Pri zapnutí sa stiahne do telefónu." else "",
                        fontSize = 12.sp, color = Pal.Muted, lineHeight = 16.sp)
                }
                Switch(phone, { vm.setPhoneAiEnabled(it) }, enabled = ai || phone)
            }
        }
        msg?.let { Text(it, fontSize = 12.sp, color = Pal.Green, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
        TextButton(onClick = vm::recategorizeAll, contentPadding = PaddingValues(0.dp)) { Text("Preradiť všetky bločky znova") }
    }
}


/** Moja domácnosť (pre tipy) a deň výplaty (začiatok rozpočtového mesiaca). */
@Composable
private fun HouseholdProfileSection(vm: MainViewModel) {
    val profile by vm.profile.collectAsState()
    var edit by remember { mutableStateOf(false) }
    SectionTitle("Moja domácnosť")
    BCard(padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { edit = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Zloženie domácnosti", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (profile.filled) profile.summary() else "Vyplňte pre presnejšie tipy",
                    fontSize = 13.sp, color = if (profile.filled) Pal.Muted else Pal.Green)
            }
            Text(if (profile.filled) "Upraviť" else "Vyplniť", color = Pal.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Pal.Track)
        Text("Deň výplaty", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Od tohto dňa sa počíta mesiac pre prehľad aj rozpočty. 1 = kalendárny mesiac.",
            fontSize = 13.sp, color = Pal.Muted)
        Spacer(Modifier.height(4.dp))
        Stepper("Mesiac začína dňom", profile.payDay, 1, 31) { vm.setProfile(profile.copy(payDay = it)) }
        val m = sk.miro.blocky.domain.Period.current()
        Text(
            if (profile.payDay == 1) "Teraz: kalendárny mesiac (${monthLabel(m)})."
            else "Napr. ${monthName(m)}: ${sk.miro.blocky.domain.Period.rangeLabel(m)}. Ak mesiac daný deň nemá, začne posledným dňom mesiaca.",
            fontSize = 12.sp, color = Pal.Muted)
        Text("V spoločnej domácnosti platí pre všetkých členov.", fontSize = 12.sp, color = Pal.Muted)
    }
    if (edit) ProfileDialog(profile, onDismiss = { edit = false }) { vm.setProfile(it); edit = false }
}


/** Záloha do súboru, obnova, automatická záloha a kľúč domácnosti. */
@Composable
private fun BackupSection(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val busy by vm.backupBusy.collectAsState()
    val msg by vm.backupMsg.collectAsState()
    val auto by vm.autoBackup.collectAsState()
    val restoreInfo by vm.restoreInfo.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    // čo sa má stať po zadaní hesla
    var ask by remember { mutableStateOf<String?>(null) }          // "backup" | "restore" | "auto"
    var pwd by remember { mutableStateOf<CharArray?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var autoTree by remember { mutableStateOf<android.net.Uri?>(null) }
    var showKey by remember { mutableStateOf(false) }

    val createDoc = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(sk.miro.blocky.data.Backup.MIME)) { uri ->
        val p = pwd; pwd = null
        if (uri != null && p != null) vm.backupTo(uri, p)
    }
    val openDoc = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; ask = "restore" }
    }
    val pickTree = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { autoTree = uri; ask = "auto" }
    }

    SectionTitle("Záloha a obnova")
    BCard(padding = PaddingValues(16.dp)) {
        Text("Zašifrovaná záloha", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Všetky bločky, kategórie, rozpočty, nastavenia aj členstvo v domácnosti v jednom súbore chránenom vaším heslom. " +
            "Uložte ho mimo telefónu – na Disk Google, do e-mailu alebo na počítač.",
            fontSize = 13.sp, color = Pal.Muted, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ask = "backup" }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Green)) { Text("Zálohovať") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("*/*")) }, enabled = !busy, shape = RoundedCornerShape(14.dp)) {
                Text("Obnoviť zo zálohy")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Pal.Green)
        msg?.let { Text(it, Modifier.padding(top = 8.dp).clickable { vm.clearBackupMsg() }, fontSize = 13.sp,
            color = Pal.Ink, fontWeight = FontWeight.SemiBold) }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Pal.Track)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automaticky každý týždeň", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (auto.enabled) "Priečinok: ${auto.folder ?: "vybraný"}" +
                        (if (auto.lastAt > 0) " · posledná ${fmtDate(auto.lastAt)}" else "") +
                        (auto.lastError?.let { " · chyba: $it" } ?: "")
                     else "Do priečinka, ktorý vyberiete. Uchováva 4 posledné zálohy.",
                    fontSize = 13.sp, color = if (auto.lastError != null) Pal.Red else Pal.Muted)
            }
            Switch(checked = auto.enabled, onCheckedChange = { on -> if (on) pickTree.launch(null) else vm.disableAutoBackup() },
                colors = SwitchDefaults.colors(checkedTrackColor = Pal.Green))
        }
        Text("Priečinok v telefóne pri strate telefónu nepomôže – vyberte priečinok, ktorý sa zálohuje mimo telefónu " +
            "(napr. synchronizovaný s cloudom), alebo občas uložte zálohu ručne na Disk Google.",
            Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Pal.Muted, lineHeight = 16.sp)

        if (sync.enabled) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Pal.Track)
            Text("Kľúč domácnosti", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("S kľúčom sa do domácnosti vrátite z nového telefónu (Nastavenia → Domácnosť → Vložiť kód). " +
                "Kto ho má, vidí výdavky domácnosti – uložte ho bezpečne, napr. do správcu hesiel.",
                fontSize = 13.sp, color = Pal.Muted, lineHeight = 18.sp)
            OutlinedButton(onClick = { showKey = true }, Modifier.padding(top = 8.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Uložiť kľúč domácnosti")
            }
        }
    }

    when (ask) {
        "backup" -> BackupPasswordDialog("Heslo zálohy", confirm = true, onDismiss = { ask = null }) { p ->
            ask = null; pwd = p; createDoc.launch(sk.miro.blocky.data.Backup.fileName())
        }
        "restore" -> BackupPasswordDialog("Heslo zálohy", confirm = false, onDismiss = { ask = null; restoreUri = null }) { p ->
            ask = null; restoreUri?.let { vm.openBackup(it, p) }; restoreUri = null
        }
        "auto" -> BackupPasswordDialog("Heslo pre automatické zálohy", confirm = true, onDismiss = { ask = null; autoTree = null }) { p ->
            ask = null
            autoTree?.let { t ->
                val name = runCatching { android.provider.DocumentsContract.getTreeDocumentId(t).substringAfterLast(':') }.getOrNull()
                vm.enableAutoBackup(t, name?.ifBlank { "hlavný priečinok" }, p)
            }
            autoTree = null
        }
    }

    restoreInfo?.let { info ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            containerColor = Pal.Card,
            title = { Text("Obnoviť zo zálohy?") },
            text = {
                Text("Záloha z ${fmtDate(info.createdAt)}" + (if (info.household) ", vrátane domácnosti" else "") + ".\n\n" +
                    "Všetky súčasné údaje v tomto telefóne sa nahradia obsahom zálohy. Aplikácia sa potom reštartuje.")
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmRestore { restartApp(ctx) } }) { Text("Obnoviť", color = Pal.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Zrušiť") } }
        )
    }

    if (showKey) {
        val code = remember { vm.invite()?.encode() }
        AlertDialog(
            onDismissRequest = { showKey = false },
            containerColor = Pal.Card,
            title = { Text("Kľúč domácnosti") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Uložte ho do správcu hesiel alebo bezpečnej poznámky. Nezdieľajte ho s nikým mimo domácnosti.",
                        fontSize = 13.sp, color = Pal.Muted)
                    Text(code ?: "–", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showKey = false
                    if (code != null) ctx.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, code), "Uložiť kľúč domácnosti"))
                }) { Text("Uložiť / zdieľať") }
            },
            dismissButton = { TextButton(onClick = { showKey = false }) { Text("Zavrieť") } }
        )
    }
}

@Composable
private fun BackupPasswordDialog(title: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val ok = p1.length >= 8 && (!confirm || p1 == p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(p1, { p1 = it }, label = { Text("Heslo (min. 8 znakov)") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp))
                if (confirm) {
                    OutlinedTextField(p2, { p2 = it }, label = { Text("Heslo znova") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = p2.isNotEmpty() && p1 != p2, shape = RoundedCornerShape(14.dp))
                    Text("Heslo nikam neposielame a nedá sa obnoviť. Ak ho zabudnete, zálohu nebude možné otvoriť.",
                        fontSize = 12.sp, color = Pal.Red)
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onOk(p1.toCharArray()) }) { Text("Pokračovať") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

/** Reštart aplikácie po obnove zo zálohy (databáza a nastavenia sa načítajú znova). */
private fun restartApp(ctx: android.content.Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
    ctx.startActivity(android.content.Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
