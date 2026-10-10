package sk.miro.blocky.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sk.miro.blocky.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    /** true = sťahuje sa cez GitHub API s tokenom (súkromný repozitár) */
    val viaApi: Boolean,
    val sizeBytes: Long
)

class UpdateException(message: String) : Exception(message)

/**
 * Kontroluje najnovší GitHub Release (vytvára ho workflow .github/workflows/build.yml)
 * a ak má vyššie číslo buildu ako nainštalovaná verzia, stiahne APK a spustí inštaláciu.
 */
class Updater(private val context: Context) {
    private val repo = BuildConfig.GH_REPO
    private val token = BuildConfig.GH_TOKEN
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val enabled: Boolean get() = repo.isNotBlank()
    val currentVersion: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val repoName: String get() = repo

    private fun Request.Builder.auth() = apply {
        if (token.isNotBlank()) header("Authorization", "Bearer $token")
    }

    /** Vráti novšiu verziu, alebo null ak je aplikácia aktuálna. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) throw UpdateException("Aktualizácie sú vypnuté – APK nebolo zostavené cez GitHub Actions.")
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .auth().build()
        http.newCall(req).execute().use { resp ->
            when (resp.code) {
                200 -> Unit
                404 -> throw UpdateException(
                    if (token.isBlank()) "Release sa nenašiel. Ak je repozitár súkromný, pridajte secret UPDATE_TOKEN (pozri README)."
                    else "Zatiaľ neexistuje žiadny release."
                )
                401, 403 -> throw UpdateException("GitHub odmietol prístup (${resp.code}). Skontrolujte token / limit požiadaviek.")
                else -> throw UpdateException("GitHub vrátil chybu ${resp.code}.")
            }
            val json = JSONObject(resp.body?.string().orEmpty())
            val tag = json.optString("tag_name")
            val code = Regex("\\d+").findAll(tag).lastOrNull()?.value?.toIntOrNull() ?: return@withContext null
            if (code <= currentVersion) return@withContext null
            val assets = json.optJSONArray("assets") ?: throw UpdateException("Release $tag neobsahuje APK.")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { asset = a; break }
            }
            val apk = asset ?: throw UpdateException("Release $tag neobsahuje APK.")
            val viaApi = token.isNotBlank()
            UpdateInfo(
                versionCode = code,
                tag = tag,
                notes = json.optString("body").trim(),
                apkUrl = if (viaApi) apk.optString("url") else apk.optString("browser_download_url"),
                viaApi = viaApi,
                sizeBytes = apk.optLong("size")
            )
        }
    }

    /** Stiahne APK do cache; [onProgress] dostáva 0..1. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "blocky-${info.versionCode}.apk")
        val req = Request.Builder().url(info.apkUrl).apply {
            if (info.viaApi) { header("Accept", "application/octet-stream"); auth() }
        }.build()
        // OkHttp pri presmerovaní na iný server (úložisko GitHubu) hlavičku Authorization automaticky zahodí.
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw UpdateException("Sťahovanie zlyhalo (${resp.code}).")
            val body = resp.body ?: throw UpdateException("Prázdna odpoveď.")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }

    /** Android vyžaduje jednorazové povolenie „Inštalovať neznáme aplikácie“ pre túto appku. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
