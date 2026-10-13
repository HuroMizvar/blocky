package sk.miro.blocky.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sk.miro.blocky.data.*
import sk.miro.blocky.domain.*
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
    fun setProfile(p: HouseholdProfile) { repo.profile = p; _profile.value = p }

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
