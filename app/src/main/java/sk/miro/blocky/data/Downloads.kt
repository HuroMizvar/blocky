package sk.miro.blocky.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/** Uloženie súboru do Stiahnutých súborov (priečinok Bločky) bez povolení – Android 10+. */
object Downloads {
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Vráti adresu uloženého súboru; null na Androide 8–9 (vtedy výber miesta) alebo pri chybe. */
    fun save(context: Context, name: String, mime: String, write: (OutputStream) -> Unit): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Bločky")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
        return runCatching {
            cr.openOutputStream(uri)!!.use(write)
            cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri
        }.getOrElse { cr.delete(uri, null, null); null }
    }
}
