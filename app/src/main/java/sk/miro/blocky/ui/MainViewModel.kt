package sk.miro.blocky.ui

import android.app.Application
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
    val month: YearMonth = YearMonth.now(),
    val report: MonthReport? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val budgets: Map<Category, Long> = emptyMap(),
    val profile: HouseholdProfile = HouseholdProfile(),
    val loading: Boolean = true
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val _profile = MutableStateFlow(repo.profile)
    val profile: StateFlow<HouseholdProfile> = _profile

    val receipts = repo.receipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val rows = repo.itemRows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<DashboardState> =
        combine(rows, repo.budgets, selectedMonth, _profile) { r, b, m, prof ->
            val budgets = b.associate { Category.fromName(it.category) to it.monthlyCents }
            val months = (Analytics.availableMonths(r) + YearMonth.now()).distinct().sortedDescending()
            val report = Analytics.report(r, m, budgets)
            DashboardState(months, m, report, Analytics.recommendations(r, report, profile = prof), budgets, prof, loading = false)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    /** Súčet za aktuálny mesiac – zobrazuje sa na skeneri. */
    val monthTotal: StateFlow<Long> = rows.map { r ->
        val now = YearMonth.now()
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

    // ---------- Profil domácnosti ----------
    fun setProfile(p: HouseholdProfile) { _profile.value = p; viewModelScope.launch { repo.saveProfile(p) } }

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

    fun refreshMembers() = viewModelScope.launch {
        _members.value = try { repo.sync.members() } catch (e: Exception) { _members.value }
    }

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
        selectedMonth.value = YearMonth.now()
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
