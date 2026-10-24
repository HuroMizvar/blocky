package sk.miro.blocky.scan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.miro.blocky.MainActivity
import sk.miro.blocky.R
import sk.miro.blocky.data.EkasaQr
import sk.miro.blocky.data.EnqueueResult
import sk.miro.blocky.data.Repository
import sk.miro.blocky.domain.I18n

/**
 * Plávajúci skener: žlto-oranžový rámček nad všetkými aplikáciami. Používateľ otvorí e-mail, PDF alebo
 * obrázok s e-bločkom, rámček naň posunie a aplikácia QR kód eKasa v rámčeku prečíta a bloček pridá.
 *
 * Obraz obrazovky sa spracúva len v pamäti telefónu (MediaProjection → ZXing), nikam sa neukladá ani neodosiela.
 * Rámček sa dá posúvať (za horný pás), zväčšovať (rohový úchyt) a zavrieť krížikom.
 */
class ScreenScanService : Service() {

    companion object {
        const val EXTRA_RESULT = "result"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "sk.miro.blocky.STOP_SCREEN_SCAN"
        private const val CHANNEL = "screen_scan"
        private const val NOTIF_ID = 42
        private const val FRAME_COLOR = 0xFFF5A524.toInt()   // žlto-oranžová
        private const val OK_COLOR = 0xFF2BB673.toInt()

        /** Compose stav – tlačidlo na skeneri ukazuje, či plávajúci skener beží */
        private val runningState = androidx.compose.runtime.mutableStateOf(false)
        var running: Boolean
            get() = runningState.value
            private set(v) { runningState.value = v }

        fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenScanService::class.java)
                .putExtra(EXTRA_RESULT, resultCode).putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("screen-scan").apply { start() }
    private val bg = Handler(worker.looper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var screenW = 0
    private var screenH = 0

    private var frame: FrameView? = null
    private var bar: LinearLayout? = null
    private var handle: View? = null
    private var status: TextView? = null
    private lateinit var frameLp: WindowManager.LayoutParams
    private lateinit var barLp: WindowManager.LayoutParams
    private lateinit var handleLp: WindowManager.LayoutParams

    private val seen = HashSet<String>()
    private var added = 0
    private var duplicates = 0
    private var repo: Repository? = null

    private val qr = GenericMultipleBarcodeReader(MultiFormatReader())
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
    private fun tr(s: String) = I18n.tr(s)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null || !canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }

        repo = Repository(applicationContext)   // načíta aj jazyk aplikácie
        // Android 14+: služba na popredí musí bežať skôr, než sa získa MediaProjection
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIF_ID, notification())
        running = true

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = try { mpm.getMediaProjection(resultCode, data) } catch (e: Exception) { null }
        if (p == null) { stopSelf(); return START_NOT_STICKY }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopSelf() } }
        }, main)

        wm = getSystemService(WindowManager::class.java)
        startCapture(p)
        showOverlay()
        bg.postDelayed(scanTick, 600)
        return START_NOT_STICKY
    }

    // ---------- zachytávanie obrazovky ----------

    private fun startCapture(p: MediaProjection) {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= 30) {
            val b = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            screenW = b.width(); screenH = b.height()
        } else {
            @Suppress("DEPRECATION")
            getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
            screenW = metrics.widthPixels; screenH = metrics.heightPixels
        }
        val r = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        reader = r
        display = p.createVirtualDisplay("blocky-scan", screenW, screenH, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, bg)
    }

    /** Každých ~0,7 s prečíta oblasť v rámčeku a hľadá v nej QR kódy eKasa. */
    private val scanTick = object : Runnable {
        override fun run() {
            try { scanOnce() } catch (_: Exception) { }
            if (running) bg.postDelayed(this, 700)
        }
    }

    private fun scanOnce() {
        val f = frame ?: return
        val image = reader?.acquireLatestImage() ?: return
        try {
            val loc = IntArray(2)
            f.getLocationOnScreen(loc)
            val inset = dp(6f)
            val left = (loc[0] + inset).coerceIn(0, screenW - 1)
            val top = (loc[1] + inset).coerceIn(0, screenH - 1)
            val w = (f.width - 2 * inset).coerceAtMost(screenW - left)
            val h = (f.height - 2 * inset).coerceAtMost(screenH - top)
            if (w < 40 || h < 40) return
            val plane = image.planes[0]
            val buf = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            // jas (luminancia) z RGBA – rovnaké ako pri kamere
            val lum = ByteArray(w * h)
            for (y in 0 until h) {
                val row = (top + y) * rowStride
                for (x in 0 until w) {
                    val i = row + (left + x) * pixelStride
                    val r = buf.get(i).toInt() and 0xFF
                    val g = buf.get(i + 1).toInt() and 0xFF
                    val b = buf.get(i + 2).toInt() and 0xFF
                    lum[y * w + x] = ((r * 77 + g * 150 + b * 29) shr 8).toByte()
                }
            }
            val source = PlanarYUVLuminanceSource(lum, w, h, 0, 0, w, h, false)
            val results = try { qr.decodeMultiple(BinaryBitmap(HybridBinarizer(source)), hints) } catch (_: Exception) { emptyArray<com.google.zxing.Result>() }
            for (res in results) {
                val text = res.text?.trim() ?: continue
                if (EkasaQr.parse(text) == null || !seen.add(text)) continue
                main.post { onFound(text) }
            }
        } finally {
            image.close()
        }
    }

    private fun onFound(raw: String) {
        val r = repo ?: return
        scope.launch {
            val res = withContext(Dispatchers.IO) { r.enqueue(raw) }
            when (res) {
                is EnqueueResult.Accepted -> { added++; flash(OK_COLOR); vibrate(80) }
                is EnqueueResult.AlreadyHave -> { duplicates++; flash(FRAME_COLOR); vibrate(40) }
                is EnqueueResult.Invalid -> Unit
            }
            updateStatus()
        }
    }

    private fun updateStatus() {
        val s = if (added == 0 && duplicates == 0) tr("Posuňte rámček na QR kód bločku")
            else tr("Pridané: {}").replace("{}", "$added") + if (duplicates > 0) " · " + tr("Už máte: {}").replace("{}", "$duplicates") else ""
        status?.text = s
    }

    private fun flash(color: Int) {
        frame?.color = color
        frame?.invalidate()
        main.postDelayed({ frame?.color = FRAME_COLOR; frame?.invalidate() }, 700)
    }

    private fun vibrate(ms: Long) {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // ---------- plávajúci rámček ----------

    private fun overlayLp(w: Int, h: Int, touchable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val size = (screenW * 0.7f).toInt()
        val x0 = (screenW - size) / 2
        val y0 = (screenH * 0.3f).toInt()

        // rámček – nedotykový, aby sa dalo pod ním posúvať e-mail / PDF
        val fv = FrameView(this).apply { color = FRAME_COLOR; stroke = dp(4f).toFloat(); radius = dp(18f).toFloat() }
        frameLp = overlayLp(size, size, false).apply { x = x0; y = y0 }
        wm.addView(fv, frameLp)
        frame = fv

        // horný pás: posúvanie rámčeka + stav + krížik
        val barH = dp(40f)
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(4f), 0)
            background = GradientDrawable().apply { setColor(FRAME_COLOR); cornerRadius = dp(14f).toFloat() }
        }
        val grip = TextView(this).apply { text = "⋮⋮"; setTextColor(0xFF2A1A00.toInt()); textSize = 16f; setPadding(0, 0, dp(8f), 0) }
        val st = TextView(this).apply {
            setTextColor(0xFF2A1A00.toInt()); textSize = 13f; maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val close = TextView(this).apply {
            text = "✕"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = tr("Vypnúť skener")
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2A1A00.toInt()) }
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f))
            setOnClickListener { stopSelf() }
        }
        b.addView(grip); b.addView(st); b.addView(close)
        barLp = overlayLp(maxOf(size, dp(220f)), barH, true).apply { x = x0; y = y0 - barH - dp(4f) }
        wm.addView(b, barLp)
        bar = b; status = st
        updateStatus()

        // posúvanie za pás
        var dx = 0f; var dy = 0f
        b.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = frameLp.x - e.rawX; dy = frameLp.y - e.rawY; true }
                MotionEvent.ACTION_MOVE -> { moveTo((e.rawX + dx).toInt(), (e.rawY + dy).toInt()); true }
                else -> false
            }
        }

        // úchyt v pravom dolnom rohu: zmena veľkosti
        val hs = dp(36f)
        val hv = HandleView(this).apply { color = FRAME_COLOR; contentDescription = tr("Zmeniť veľkosť") }
        handleLp = overlayLp(hs, hs, true)
        wm.addView(hv, handleLp)
        handle = hv
        placeHandle()
        var sw = 0; var sh = 0; var sx = 0f; var sy = 0f
        hv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sw = frameLp.width; sh = frameLp.height; sx = e.rawX; sy = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    resize((sw + e.rawX - sx).toInt(), (sh + e.rawY - sy).toInt()); true
                }
                else -> false
            }
        }
    }

    private fun moveTo(x: Int, y: Int) {
        val barH = barLp.height + dp(4f)
        frameLp.x = x.coerceIn(-frameLp.width / 2, screenW - frameLp.width / 2)
        frameLp.y = y.coerceIn(barH, screenH - dp(80f))
        barLp.x = frameLp.x; barLp.y = frameLp.y - barH
        frame?.let { wm.updateViewLayout(it, frameLp) }
        bar?.let { wm.updateViewLayout(it, barLp) }
        placeHandle()
    }

    private fun resize(w: Int, h: Int) {
        frameLp.width = w.coerceIn(dp(120f), screenW)
        frameLp.height = h.coerceIn(dp(120f), screenH)
        barLp.width = maxOf(frameLp.width, dp(220f))
        frame?.let { wm.updateViewLayout(it, frameLp) }
        bar?.let { wm.updateViewLayout(it, barLp) }
        placeHandle()
    }

    private fun placeHandle() {
        handleLp.x = frameLp.x + frameLp.width - handleLp.width * 2 / 3
        handleLp.y = frameLp.y + frameLp.height - handleLp.height * 2 / 3
        handle?.let { wm.updateViewLayout(it, handleLp) }
    }

    // ---------- notifikácia ----------

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Plávajúci skener"), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, ScreenScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(tr("Plávajúci skener je zapnutý"))
            .setContentText(tr("Obraz sa spracúva len v telefóne."))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, tr("Vypnúť"), stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        bg.removeCallbacksAndMessages(null)
        listOf(frame, bar, handle).forEach { v -> v?.let { runCatching { wm.removeView(it) } } }
        frame = null; bar = null; handle = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        worker.quitSafely()
        scope.cancel()
        // ak sme niečo pridali, načítanie položiek beží na pozadí (FetchWorker)
        super.onDestroy()
    }

    /** Žlto-oranžový rámček s rohmi. */
    private class FrameView(context: Context) : View(context) {
        var color = Color.YELLOW
        var stroke = 8f
        var radius = 30f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        override fun onDraw(canvas: Canvas) {
            paint.color = color
            paint.strokeWidth = stroke
            val h = stroke / 2
            rect.set(h, h, width - h, height - h)
            paint.alpha = 255
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    /** Okrúhly úchyt na zmenu veľkosti. */
    private class HandleView(context: Context) : View(context) {
        var color = Color.YELLOW
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A1A00.toInt(); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        override fun onDraw(canvas: Canvas) {
            fill.color = color
            val r = minOf(width, height) / 2f
            canvas.drawCircle(width / 2f, height / 2f, r, fill)
            val c = width / 2f; val d = r * 0.4f
            canvas.drawLine(c - d, c + d, c + d, c - d, line)
            canvas.drawLine(c + d * 0.1f, c - d, c + d, c - d, line)
            canvas.drawLine(c + d, c - d, c + d, c - d * 0.1f, line)
            canvas.drawLine(c - d, c + d * 0.1f, c - d, c + d, line)
            canvas.drawLine(c - d, c + d, c - d * 0.1f, c + d, line)
        }
    }
}
