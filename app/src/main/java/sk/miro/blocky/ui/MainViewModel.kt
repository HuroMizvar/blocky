package sk.miro.blocky.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sk.miro.blocky.data.*
import sk.miro.blocky.domain.*
import sk.miro.blocky.sync.HouseholdSync
import sk.miro.blocky.data.PhoneAi
import sk.miro.blocky.sync.Invite
import sk.miro.blocky.sync.Member
import sk.miro.blocky.sync.SyncStatus
import java.io.File
import java.time.YearMonth

data class DashboardState(
    val months: List<YearMonth> = emptyList(),
    val month: YearMonth = Period.current(),
    val report: MonthReport? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val budgets: Map<Category, Long> = emptyMap(),
    val profile: HouseholdProfile = HouseholdProfile(),
    val loading: Boolean = true
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)

    private val selectedMonth = MutableStateFlow(Period.current())
    private val _profile = MutableStateFlow(repo.profile)
    val profile: StateFlow<HouseholdProfile> = _profile

    val receipts = repo.receipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val rows = repo.itemRows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<DashboardState> =
        combine(rows, repo.budgets, selectedMonth, _profile, FixedExpenses.list) { r, b, m, prof, fixed ->
            val budgets = b.associate { Category.fromName(it.category) to it.monthlyCents }
            val months = (Analytics.availableMonths(r) + Period.current()).distinct().sortedDescending()
            val report = Analytics.report(r, m, budgets, fixed = fixed)
            DashboardState(months, m, report, Analytics.recommendations(r, report, profile = prof), budgets, prof, loading = false)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    /** Súčet za aktuálny mesiac – zobrazuje sa na skeneri. */
    val monthTotal: StateFlow<Long> = combine(rows, _profile) { r, _ -> r }.map { r ->
        val now = Period.current()
        r.filter { Analytics.monthOf(it.issuedAt) == now && it.category != Category.ZALOHY.name }.sumOf { it.priceCents }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)


    fun selectMonth(m: YearMonth) { selectedMonth.value = m }


    fun addManual(merchant: String, cents: Long, category: Category, issuedAt: Long) =
        viewModelScope.launch { repo.addManual(merchant, cents, category, issuedAt) }

    fun recategorize(itemId: Long, normName: String, c: Category, all: Boolean) =
        viewModelScope.launch { repo.recategorize(itemId, normName, c, all) }

    /** Všetky položky (pre zoznam položiek kategórie / obchodu). */
    val allRows: StateFlow<List<ItemRow>> get() = rows

    fun deleteReceipt(id: Long) = viewModelScope.launch { repo.deleteReceipt(id) }

    // ---------- Vlastné kategórie ----------
    /** mení sa pri každej zmene vlastných kategórií – obrazovky sa prekreslia */
    val catsVersion: StateFlow<Int> get() = repo.customCatsVersion

    // ---------- Automatické rozpočty ----------
    /** Návrh rozpočtov z celej histórie míňania. */
    fun budgetSuggestions(): List<BudgetPlanner.Suggestion> = BudgetPlanner.plan(rows.value)
    /** Návrh rozpočtov z vybraného obdobia (prepočet na 30-dňový mesiac). */
    fun budgetSuggestions(from: java.time.LocalDate, to: java.time.LocalDate): List<BudgetPlanner.Suggestion> =
        BudgetPlanner.planRange(rows.value, from, to)
    /** dátum prvého výdavku v aplikácii */
    fun firstExpenseDate(): java.time.LocalDate? = BudgetPlanner.firstDate(rows.value)

    fun applyBudgets(list: List<BudgetPlanner.Suggestion>) = viewModelScope.launch {
        list.forEach { repo.setBudget(it.category, it.budgetCents) }
    }

    fun createCustomCategory(label: String, icon: String, color: Int): Category? {
        val c = repo.createCustomCategoryNow(label, icon, color) ?: return null
        viewModelScope.launch { repo.publishCreated(c) }
        return c
    }

    fun updateCustomCategory(c: Category, label: String, icon: String, color: Int) =
        viewModelScope.launch { repo.updateCustomCategory(c, label, icon, color) }

    fun deleteCustomCategory(c: Category) = viewModelScope.launch { repo.deleteCustomCategory(c) }
    fun updateManual(id: Long, merchant: String, cents: Long, c: Category) =
        viewModelScope.launch { repo.updateManual(id, merchant, cents, c) }

    /** Bločky so súčtom započítaných položiek (bez prečiarknutých). */
    val receiptList = repo.receiptList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Prečiarknuť / vrátiť položku (vrátený či reklamovaný tovar, omyl). */
    fun setExcluded(itemId: Long, excluded: Boolean) = viewModelScope.launch { repo.setExcluded(itemId, excluded) }
    fun setQuantity(itemId: Long, qty: Double) = viewModelScope.launch { repo.setQuantity(itemId, qty) }
    fun restoreItem(itemId: Long) = viewModelScope.launch { repo.restoreItem(itemId) }

    fun setBudget(c: Category, cents: Long?) = viewModelScope.launch { repo.setBudget(c, cents) }

    fun export(onReady: (File) -> Unit) = viewModelScope.launch { onReady(repo.exportCsv(rows.value)) }

    // ---------- Rýchle skenovanie ----------
    private var lastRaw: String? = null
    private var lastRawAt = 0L
    private var scanSeq = 0

    /** Okamžitá spätná väzba pre vibráciu – vyšle sa hneď pri rozpoznaní QR, nečaká na eKasa. */
    private val _vibe = MutableSharedFlow<BannerKind>(extraBufferCapacity = 4)
    val vibe: SharedFlow<BannerKind> = _vibe
    private val _immediate = MutableStateFlow<ScanBanner?>(null)
    private val _trackKey = MutableStateFlow<Pair<Int, String>?>(null)

    /** Pás so stavom posledného skenu: hneď „prijaté“, potom výsledok z eKasa. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val banner: StateFlow<ScanBanner?> = combine(
        _immediate,
        _trackKey.flatMapLatest { k -> k?.let { (id, key) -> repo.observePending(key).map { p -> p?.toBanner(id) } } ?: flowOf(null) }
    ) { imm, tracked -> imm ?: tracked }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val openPending = repo.openPending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Volá kamera pri každom rozpoznaní QR. Rovnaký kód sa ignoruje, kým je v zábere (a 4 s potom). */
    fun onQrFast(raw: String) {
        val now = System.currentTimeMillis()
        if (raw == lastRaw && now - lastRawAt < 4_000) { lastRawAt = now; return }
        lastRaw = raw; lastRawAt = now
        if (Invite.isInvite(raw)) {
            val inv = Invite.decode(raw)
            _vibe.tryEmit(if (inv != null) BannerKind.OK else BannerKind.ERROR)
            if (inv == null) _immediate.value = ScanBanner(++scanSeq, BannerKind.ERROR, "Neplatná pozvánka", "QR kód je poškodený.", null)
            else if (repo.sync.enabled) _immediate.value = ScanBanner(++scanSeq, BannerKind.INFO, "Už ste v domácnosti",
                "Najprv z nej odíďte v Nastaveniach, potom sa môžete pridať do inej.", null)
            else _pendingInvite.value = inv
            return
        }
        val id = ++scanSeq
        viewModelScope.launch {
            when (val r = repo.enqueue(raw)) {
                is EnqueueResult.Accepted -> { _vibe.tryEmit(BannerKind.OK); _immediate.value = null; _trackKey.value = id to r.key }
                is EnqueueResult.AlreadyHave -> { _vibe.tryEmit(BannerKind.DUPLICATE); _trackKey.value = null
                    _immediate.value = ScanBanner(id, BannerKind.DUPLICATE, "Tento bloček už máte!",
                        r.existing?.let { duplicateText(it) } ?: "Rovnaký bloček ste už naskenovali.", r.existing?.id) }
                is EnqueueResult.Invalid -> { _vibe.tryEmit(BannerKind.ERROR); _trackKey.value = null
                    _immediate.value = ScanBanner(id, BannerKind.ERROR, r.message, "Namierte na QR kód eKasa na bločku.", null) }
            }
        }
    }

    /**
     * E-bločky z e-mailu: zdieľaný PDF / obrázok / text alebo vybraný súbor.
     * QR kód sa hľadá v telefóne; nájdené bločky idú do rovnakej fronty ako pri skenovaní kamerou.
     */
    fun importReceipts(uris: List<Uri>, text: String? = null) {
        if (uris.isEmpty() && text.isNullOrBlank()) return
        val id = ++scanSeq
        _trackKey.value = null
        _immediate.value = ScanBanner(id, BannerKind.WAIT, "Hľadám bloček v súbore…", null, null)
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val codes = LinkedHashSet<String>()
            codes += ReceiptImport.codesInText(text)
            for (u in uris.take(20)) codes += ReceiptImport.codesInUri(ctx, u, null)
            if (codes.isEmpty()) {
                _vibe.tryEmit(BannerKind.ERROR)
                _immediate.value = ScanBanner(id, BannerKind.ERROR, "Bloček sa nenašiel",
                    "V súbore nie je QR kód eKasa. Pošlite PDF alebo obrázok e-bločku, nie len text e-mailu.", null)
                return@launch
            }
            if (codes.size == 1) { lastRaw = null; onQrFast(codes.first()); return@launch }
            var added = 0; var dup = 0; var bad = 0
            for (c in codes) when (repo.enqueue(c)) {
                is EnqueueResult.Accepted -> added++
                is EnqueueResult.AlreadyHave -> dup++
                is EnqueueResult.Invalid -> bad++
            }
            _vibe.tryEmit(if (added > 0) BannerKind.OK else BannerKind.DUPLICATE)
            _immediate.value = ScanBanner(id, if (added > 0) BannerKind.OK else BannerKind.DUPLICATE,
                if (added > 0) "Pridané bločky: $added" else "Tieto bločky už máte",
                listOfNotNull(
                    if (added > 0) "Položky sa načítajú z eKasa na pozadí." else null,
                    if (dup > 0) "Už uložené: $dup" else null,
                    if (bad > 0) "Neplatné: $bad" else null
                ).joinToString(" · "), null)
        }
    }

    fun clearBanner() { _immediate.value = null; _trackKey.value = null }

    /** „Skenovať ďalší“ – zavrie hlásenie a dovolí hneď načítať aj ten istý QR (ukáže sa upozornenie na duplicitu). */
    fun scanNext() { clearBanner(); lastRaw = null }

    // ---------- Typ prevádzky ----------
    val unknownMerchants = repo.unknownMerchants.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _skipped = MutableStateFlow<Set<String>>(emptySet())

    /** Otázka „čo je to za prevádzku?“ hneď po skene – ak prevádzku nepoznáme. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scanQuestion: StateFlow<MerchantEntity?> = banner
        .flatMapLatest { b ->
            val id = b?.receiptDbId
            if (b?.kind == BannerKind.OK && id != null) repo.observeReceipt(id) else flowOf(null)
        }
        .combine(unknownMerchants) { r, unk -> r?.merchantKey?.let { k -> unk.firstOrNull { it.mkey == k } } }
        .combine(_skipped) { m, sk -> m?.takeIf { it.mkey !in sk } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun answerMerchant(m: MerchantEntity, type: MerchantType) =
        viewModelScope.launch { repo.setMerchantType(m.mkey, m.name, type) }

    fun skipMerchant(m: MerchantEntity) { _skipped.value = _skipped.value + m.mkey }

    // ---------- AI analýza (text, ktorý používateľ sám zdieľa) ----------
    fun aiSummary(options: AiExportOptions): String {
        val st = state.value
        return AiExport.build(rows.value, st.budgets, st.profile, st.recommendations, options)
    }

    // ---------- Farebná téma ----------
    fun loadTheme() { AppTheme.id = runCatching { ThemeId.valueOf(repo.theme) }.getOrDefault(ThemeId.PAPER) }
    fun setTheme(t: ThemeId) { AppTheme.id = t; repo.theme = t.name }

    // ---------- Jazyk ----------
    fun setEnglish(on: Boolean) { repo.english = on }

    // ---------- Fixné výdavky ----------
    val fixedExpenses: StateFlow<List<FixedExpense>> = FixedExpenses.list
    fun saveFixed(e: FixedExpense) = viewModelScope.launch { repo.saveFixed(e) }
    fun deleteFixed(id: String) = viewModelScope.launch { repo.deleteFixed(id) }
    /** skutočná suma fixného výdavku v rozpočtovom mesiaci [m]; null = bežná suma */
    fun setFixedAmount(id: String, m: java.time.YearMonth, cents: Long?) = viewModelScope.launch {
        val e = FixedExpenses.list.value.firstOrNull { it.id == id } ?: return@launch
        val o = if (cents == null || cents == e.cents) e.overrides - m.toString() else e.overrides + (m.toString() to cents)
        repo.saveFixed(e.copy(overrides = o))
    }

    // ---------- Profil domácnosti ----------
    fun setProfile(p: HouseholdProfile) {
        val periodChanged = p.payDay != Period.payDay
        Period.payDay = p.payDay
        if (periodChanged) selectedMonth.value = Period.current()
        _profile.value = p; viewModelScope.launch { repo.saveProfile(p) }
    }

    // ---------- Spoločná domácnosť ----------
    val syncStatus: StateFlow<SyncStatus> = HouseholdSync.status
    val syncConfigured: Boolean get() = HouseholdSync.builtInConfigured
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members
    private val _syncMsg = MutableStateFlow<String?>(null)
    /** hlásenie o výsledku akcie (založenie, pripojenie, chyba) */
    val syncMsg: StateFlow<String?> = _syncMsg
    private val _busy = MutableStateFlow(false)
    val syncBusy: StateFlow<Boolean> = _busy

    /** Pozvánka naskenovaná skenerom – čaká na zadanie mena. */
    private val _pendingInvite = MutableStateFlow<Invite?>(null)
    val pendingInvite: StateFlow<Invite?> = _pendingInvite
    fun dismissInvite() { _pendingInvite.value = null }
    fun clearSyncMsg() { _syncMsg.value = null }

    fun invite(): Invite? = repo.sync.invite()

    private fun syncAction(okMsg: String?, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        _syncMsg.value = try { block(); okMsg } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
        _busy.value = false
        refreshMembers()
        _profile.value = repo.profile
    }

    fun createHousehold(name: String) = syncAction("Domácnosť je založená. Pozvite ďalších členov QR kódom.") { repo.sync.create(name.trim()) }
    fun joinHousehold(inv: Invite, name: String) = syncAction("Ste v domácnosti. Bločky sa zosynchronizovali.") {
        _pendingInvite.value = null
        repo.sync.join(inv, name.trim())
    }
    fun leaveHousehold() = syncAction("Odišli ste z domácnosti. Bločky ostali v tomto telefóne.") { repo.sync.leave() }
    fun deleteHouseholdData() = syncAction("Údaje domácnosti boli zo servera zmazané. V telefóne ostali.") { repo.sync.deleteServerData() }
    fun syncNow() = syncAction(null) {
        if (!repo.sync.syncNow()) throw Exception(HouseholdSync.status.value.error ?: "Synchronizácia zlyhala")
    }
    fun syncSoon() = repo.sync.schedule()

    val isAdmin: Boolean get() = repo.sync.isAdmin
    fun adminKey(): Invite? = repo.sync.adminKey()
    fun renameMember(name: String) = syncAction("Meno je zmenené aj pri vašich bločkoch.") { repo.sync.rename(name.trim()) }
    fun removeMember(m: Member) = syncAction("${m.name} bol(a) odstránený(á) z domácnosti.") { repo.sync.removeMember(m) }
    fun rotateHouseholdKey() = syncAction("Domácnosť má nový kľúč. Ostatných členov pozvite znova novým QR kódom – " +
        "starý kľúč (aj v stratenom telefóne) už nefunguje.") { repo.sync.rotateKey() }

    // ---------- Zaraďovanie pomocou AI ----------
    private val _ai = MutableStateFlow(repo.aiEnabled)
    val aiEnabled: StateFlow<Boolean> = _ai
    private val _phoneAi = MutableStateFlow(repo.phoneAiEnabled)
    val phoneAiEnabled: StateFlow<Boolean> = _phoneAi
    private val _phoneAiState = MutableStateFlow<PhoneAi.State?>(null)
    /** null = ešte nezistené */
    val phoneAiState: StateFlow<PhoneAi.State?> = _phoneAiState
    private val _aiMsg = MutableStateFlow<String?>(null)
    val aiMsg: StateFlow<String?> = _aiMsg

    fun setAiEnabled(on: Boolean) {
        _ai.value = on
        viewModelScope.launch(Dispatchers.IO) {
            _aiMsg.value = "Preraďujem bločky…"
            repo.setAi(on)
            _aiMsg.value = if (on) "Vstavaná AI je zapnutá, bločky sú preradené." else "AI je vypnutá, zaraďujú sa len pravidlá."
        }
    }

    fun setPhoneAiEnabled(on: Boolean) = viewModelScope.launch {
        if (on && _phoneAiState.value == PhoneAi.State.DOWNLOADABLE) {
            _aiMsg.value = "Sťahujem AI do telefónu… (môže to trvať niekoľko minút)"
            if (!PhoneAi.download()) { _aiMsg.value = "AI telefónu sa nepodarilo stiahnuť."; return@launch }
            _phoneAiState.value = PhoneAi.state()
        }
        repo.phoneAiEnabled = on
        _phoneAi.value = on
        if (on) refinePhoneAi() else _aiMsg.value = null
    }

    /** Na žiadosť používateľa zistí, či má telefón vlastnú AI. */
    fun checkPhoneAi() = viewModelScope.launch { _phoneAiState.value = PhoneAi.state() }

    /** AI telefónu zaradí čakajúce položky (len pri otvorenej aplikácii). */
    fun refinePhoneAi() = viewModelScope.launch {
        if (!repo.phoneAiEnabled) return@launch
        val n = try { repo.refineWithPhoneAi() } catch (e: Exception) { 0 }
        if (n > 0) _aiMsg.value = "AI telefónu zaradila $n položiek."
    }

    fun recategorizeAll() = viewModelScope.launch(Dispatchers.IO) {
        _aiMsg.value = "Preraďujem bločky…"
        repo.setAi(repo.aiEnabled)
        _aiMsg.value = "Hotovo – všetky bločky sú preradené (vaše ručné opravy ostali)."
    }

    /** Volá sa pri návrate do aplikácie. */
    fun onForeground() { syncSoon(); refinePhoneAi() }

    private val _hasAdmin = MutableStateFlow<Boolean?>(null)
    /** domácnosť má správcu (null = neznáme) */
    val householdHasAdmin: StateFlow<Boolean?> = _hasAdmin

    fun refreshMembers() = viewModelScope.launch {
        _members.value = try { repo.sync.members() } catch (e: Exception) { _members.value }
        _hasAdmin.value = repo.sync.householdHasAdmin()
    }

    fun claimAdmin() = syncAction("Ste správca domácnosti. Uložte si kľúč správcu.") { repo.sync.claimAdmin() }

    // ---------- Súkromie ----------
    private val _autoUpdate = MutableStateFlow(repo.autoUpdate)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate
    fun setAutoUpdate(on: Boolean) { repo.autoUpdate = on; _autoUpdate.value = on }

    fun deleteEverything(done: () -> Unit) = viewModelScope.launch {
        repo.deleteEverything()
        _profile.value = repo.profile
        _autoUpdate.value = repo.autoUpdate
        _mock.value = repo.mockMode
        clearBanner()
        done()
    }

    // ---------- Záloha a obnova ----------
    private val _backupMsg = MutableStateFlow<String?>(null)
    /** výsledok poslednej akcie so zálohou (zobrazí sa v Nastaveniach) */
    val backupMsg: StateFlow<String?> = _backupMsg
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy
    private val _autoBackup = MutableStateFlow(Backup.autoState(app))
    val autoBackup: StateFlow<Backup.AutoState> = _autoBackup
    /** otvorená záloha čakajúca na potvrdenie obnovy */
    private var pendingRestore: org.json.JSONObject? = null
    private val _restoreInfo = MutableStateFlow<Backup.Info?>(null)
    val restoreInfo: StateFlow<Backup.Info?> = _restoreInfo

    fun clearBackupMsg() { _backupMsg.value = null }

    fun backupTo(uri: Uri, password: CharArray) = viewModelScope.launch {
        _backupBusy.value = true
        _backupMsg.value = try {
            val bytes = Backup.create(getApplication(), password)
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("Súbor sa nedá zapísať.")
            }
            getApplication<Application>().getSharedPreferences("backup", 0).edit().putLong("lastManual", System.currentTimeMillis()).apply()
            "Záloha je uložená. Heslo si dobre zapamätajte – bez neho sa záloha nedá obnoviť."
        } catch (e: Exception) { "Zálohu sa nepodarilo uložiť: ${e.message}" }
        _backupBusy.value = false
    }

    fun openBackup(uri: Uri, password: CharArray) = viewModelScope.launch {
        _backupBusy.value = true
        try {
            val bytes = kotlinx.coroutines.withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Súbor sa nedá otvoriť.")
            }
            val payload = Backup.open(bytes, password)
            pendingRestore = payload
            _restoreInfo.value = Backup.info(payload)
        } catch (e: Exception) {
            _backupMsg.value = e.message ?: "Zálohu sa nepodarilo otvoriť."
        }
        _backupBusy.value = false
    }

    fun cancelRestore() { pendingRestore = null; _restoreInfo.value = null }

    /** Obnoví údaje zo zálohy; po dokončení treba aplikáciu reštartovať ([onRestart]). */
    fun confirmRestore(onRestart: () -> Unit) = viewModelScope.launch {
        val p = pendingRestore ?: return@launch
        _backupBusy.value = true
        try {
            Backup.restore(getApplication(), p)
            pendingRestore = null; _restoreInfo.value = null
            onRestart()
        } catch (e: Exception) {
            _backupMsg.value = e.message ?: "Obnova sa nepodarila."
            _backupBusy.value = false
        }
    }

    private val _backupPwd = MutableStateFlow(Backup.savedPassword(app))
    /** heslo záloh (zobrazí sa po ťuknutí na oko) */
    val backupPassword: StateFlow<String?> = _backupPwd
    fun setBackupPassword(p: CharArray) { Backup.setPassword(getApplication(), p); _backupPwd.value = String(p) }

    fun enableAutoBackup(tree: Uri, folderName: String?) = viewModelScope.launch {
        try {
            Backup.enableAuto(getApplication(), tree, folderName)
            _autoBackup.value = Backup.autoState(getApplication())
            _backupBusy.value = true
            Backup.runAuto(getApplication())   // prvá záloha hneď
            _backupMsg.value = "Automatická záloha je zapnutá. Prvá záloha je uložená v priečinku."
        } catch (e: Exception) {
            _backupMsg.value = "Automatickú zálohu sa nepodarilo nastaviť: ${e.message}"
        }
        _backupBusy.value = false
        _autoBackup.value = Backup.autoState(getApplication())
    }

    fun disableAutoBackup() {
        Backup.disableAuto(getApplication())
        _autoBackup.value = Backup.autoState(getApplication())
    }

    fun refreshAutoBackup() { _autoBackup.value = Backup.autoState(getApplication()) }

    fun deletePending(key: String) = viewModelScope.launch { repo.deletePending(key) }

    // ---------- Testovací režim ----------
    val demoCount = repo.demoCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val _mock = MutableStateFlow(repo.mockMode)
    val mockMode: StateFlow<Boolean> = _mock
    private val _testMsg = MutableStateFlow<String?>(null)
    val testMsg: StateFlow<String?> = _testMsg

    fun setMockMode(on: Boolean) { repo.mockMode = on; _mock.value = on }

    fun seedDemo() = viewModelScope.launch {
        _testMsg.value = "Generujem demo dáta…"
        val n = repo.seedDemo()
        selectedMonth.value = Period.current()
        _testMsg.value = "Nahraných $n demo bločkov (aktuálny + 3 predchádzajúce mesiace)."
    }

    fun clearDemo() = viewModelScope.launch {
        _testMsg.value = "Zmazaných ${repo.clearDemo()} demo bločkov."
    }


    // ---------- Aktualizácie ----------
    val updater = Updater(app)
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update
    private val _updatePrompt = MutableStateFlow(false)
    /** true = zobraziť dialóg „nová verzia“ (po automatickej kontrole pri štarte) */
    val updatePrompt: StateFlow<Boolean> = _updatePrompt

    init {
        viewModelScope.launch(Dispatchers.IO) { repo.migrateIfNeeded() }
        // po synchronizácii načítať profil domácnosti (mohol ho zmeniť iný člen)
        viewModelScope.launch { HouseholdSync.status.collect { if (!it.running) _profile.value = repo.profile } }
        repo.sync.schedule()
        // ML Kit sa volá LEN ak používateľ AI telefónu zapol (aj zistenie dostupnosti posiela Googlu diagnostiku)
        if (repo.phoneAiEnabled) viewModelScope.launch {
            _phoneAiState.value = PhoneAi.state()
            if (_phoneAiState.value != PhoneAi.State.AVAILABLE) { repo.phoneAiEnabled = false; _phoneAi.value = false }
            refinePhoneAi()
        }
        if (updater.enabled && repo.autoUpdate) checkUpdate(silent = true)
    }

    fun checkUpdate(silent: Boolean = false) = viewModelScope.launch {
        if (!silent) _update.value = UpdateUi.Checking
        _update.value = try {
            updater.check()?.let { UpdateUi.Available(it) } ?: UpdateUi.UpToDate
        } catch (e: Exception) {
            if (silent) UpdateUi.Idle else UpdateUi.Error(e.message ?: e.javaClass.simpleName)
        }
        if (_update.value is UpdateUi.Available) _updatePrompt.value = true
    }

    fun dismissUpdatePrompt() { _updatePrompt.value = false }

    fun downloadUpdate(info: UpdateInfo) = viewModelScope.launch {
        _update.value = UpdateUi.Downloading(info, 0f)
        _update.value = try {
            val file = updater.download(info) { p -> _update.value = UpdateUi.Downloading(info, p) }
            UpdateUi.ReadyToInstall(info, file)
        } catch (e: Exception) {
            UpdateUi.Error(e.message ?: e.javaClass.simpleName)
        }
    }
}

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUi
    data class Error(val message: String) : UpdateUi
}


enum class BannerKind { WAIT, OK, DUPLICATE, INFO, ERROR }

/** [scanId] sa mení len pri novom skene (na vibráciu), stav v rámci skenu sa mení cez [kind]. */
data class ScanBanner(val scanId: Int, val kind: BannerKind, val title: String, val sub: String?, val receiptDbId: Long?,
                      /** stav prišiel až po načítaní z eKasa (nie hneď pri skene) */
                      val fromFetch: Boolean = false)

fun PendingScanEntity.toBanner(scanId: Int): ScanBanner = toBannerRaw(scanId).copy(fromFetch = true)

private fun PendingScanEntity.toBannerRaw(scanId: Int): ScanBanner = when (status) {
    "DONE" -> if (receiptDbId != null) ScanBanner(scanId, BannerKind.OK, "Bloček naskenovaný", message, receiptDbId)
              else ScanBanner(scanId, BannerKind.DUPLICATE, "Tento bloček už máte!", null, null)
    "DUP" -> ScanBanner(scanId, BannerKind.DUPLICATE, "Tento bloček už máte!", message, receiptDbId)
    "FAILED" -> ScanBanner(scanId, BannerKind.ERROR, "Bloček sa nepodarilo načítať", message, null)
    else -> if (attempts == 0) ScanBanner(scanId, BannerKind.WAIT, "Bloček naskenovaný",
                "Načítavam položky… Môžete skenovať ďalší alebo zavrieť aplikáciu.", null)
            else ScanBanner(scanId, BannerKind.WAIT, "Bloček naskenovaný",
                (message ?: "Bez pripojenia.") + " Položky načítam automaticky neskôr.", null)
}
