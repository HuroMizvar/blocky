package sk.miro.blocky.data

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.catch
import sk.miro.blocky.domain.Category

/**
 * AI zabudovaná v telefóne (Google Gemini Nano cez ML Kit GenAI) – len na podporovaných telefónoch.
 * Názvy položiek spracuje priamo v telefóne. ML Kit však posiela Googlu diagnostické údaje
 * (model telefónu, verzia aplikácie, veľkosť vstupu, chybové kódy – nie text položiek), preto je voliteľná.
 * Android ju dovolí používať len pri otvorenej aplikácii.
 */
object PhoneAi {
    enum class State { UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE }

    @Volatile private var client: GenerativeModel? = null

    private fun model(): GenerativeModel = client ?: Generation.getClient().also { client = it }

    /** Zistí, či telefón AI má. Bezpečné aj na telefónoch bez podpory (vráti UNAVAILABLE). */
    suspend fun state(): State = try {
        when (model().checkStatus()) {
            FeatureStatus.AVAILABLE -> State.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> State.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> State.DOWNLOADING
            else -> State.UNAVAILABLE
        }
    } catch (t: Throwable) { State.UNAVAILABLE }

    suspend fun isReady(): Boolean = state() == State.AVAILABLE

    /** Stiahne model AI do telefónu (robí to systém, raz). Vráti true pri úspechu. */
    suspend fun download(): Boolean = try {
        var ok = false
        model().download().catch { }.collect { s ->
            if (s is DownloadStatus.DownloadCompleted) ok = true
        }
        ok || state() == State.AVAILABLE
    } catch (t: Throwable) { false }

    private val allowed get() = Category.visible.filter { it != Category.ZALOHY && it != Category.ZLAVY }

    /** Zaradí názvy položiek. Vráti zoznam v rovnakom poradí (null = AI nevedela), alebo null pri chybe. */
    suspend fun classify(names: List<String>): List<Category?>? = try {
        val prompt = buildString {
            append("Si pomocník na triedenie nákupov zo slovenských pokladničných bločkov. ")
            append("Názvy sú často skrátené (napr. \"MLK POLOT.1,5%\" = mlieko polotučné). ")
            append("Každú položku zaraď do jednej kategórie podľa kódu:\n")
            allowed.forEach { append(it.name).append(" = ").append(it.label).append('\n') }
            append("\nOdpovedz LEN riadkami v tvare: číslo|KÓD. Ak si nie si istý, použi INE.\n\nPoložky:\n")
            names.forEachIndexed { i, n -> append(i + 1).append(". ").append(n.take(60)).append('\n') }
        }
        val text = model().generateContent(prompt).candidates.firstOrNull()?.text ?: ""
        val out = arrayOfNulls<Category>(names.size)
        val line = Regex("""^\s*(\d+)\s*[.|:)\-]*\s*\|?\s*([A-Z_]+)""")
        text.lines().forEach { l ->
            val m = line.find(l) ?: return@forEach
            val idx = m.groupValues[1].toInt() - 1
            val cat = allowed.firstOrNull { it.name == m.groupValues[2] }
            if (idx in names.indices && cat != null && cat != Category.INE) out[idx] = cat
        }
        out.toList()
    } catch (t: Throwable) { null }
}
