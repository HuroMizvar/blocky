package sk.miro.blocky.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** Vibrácia nezávislá od systémového nastavenia „hmatová odozva“. */
private fun vibrate(context: Context, pattern: LongArray) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else context.getSystemService(Vibrator::class.java)
    if (v == null || !v.hasVibrator()) return
    val effect = if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
        else VibrationEffect.createWaveform(longArrayOf(0) + pattern, -1)
    // Ako upozornenie (nie „dotyková odozva“) – niektoré telefóny (Samsung) dotykovú vibráciu potláčajú.
    if (Build.VERSION.SDK_INT >= 33)
        v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_NOTIFICATION))
    else vibrateLegacy(v, effect)
}

@Suppress("DEPRECATION")
private fun vibrateLegacy(v: Vibrator, effect: VibrationEffect) =
    v.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())

private val VIB_OK = longArrayOf(90)                         // jedno krátke
private val VIB_DUP = longArrayOf(120, 110, 120, 110, 120)   // tri – pozor, duplicita
private val VIB_ERR = longArrayOf(350)                       // dlhé

@Composable
fun ScannerScreen(vm: MainViewModel, onOpenApp: () -> Unit, onOpenReceipt: (Long) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    // svetlé ikony stavového riadku nad tmavou kamerou
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.isAppearanceLightStatusBars = false
        onDispose { ctl?.isAppearanceLightStatusBars = true }
    }

    val banner by vm.banner.collectAsState()
    val question by vm.scanQuestion.collectAsState()
    val mock by vm.mockMode.collectAsState()
    val monthTotal by vm.monthTotal.collectAsState()
    var torch by remember { mutableStateOf(false) }   // blesk je predvolene vypnutý
    var camera by remember { mutableStateOf<Camera?>(null) }
    var manual by remember { mutableStateOf(false) }
    // e-bloček z e-mailu uložený v telefóne (PDF / obrázok)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importReceipts(uris)
    }

    // Vibrácia hneď pri rozpoznaní QR (nečaká na odpoveď eKasa).
    LaunchedEffect(Unit) {
        vm.vibe.collect { k ->
            vibrate(context, when (k) {
                BannerKind.DUPLICATE -> VIB_DUP
                BannerKind.ERROR -> VIB_ERR
                else -> VIB_OK
            })
        }
    }
    // Duplicita / chyba zistená až po načítaní z eKasa (offline bločky).
    LaunchedEffect(banner?.scanId, banner?.kind) {
        val b = banner ?: return@LaunchedEffect
        if (b.fromFetch) when (b.kind) {
            BannerKind.DUPLICATE -> vibrate(context, VIB_DUP)
            BannerKind.ERROR -> vibrate(context, VIB_ERR)
            else -> Unit
        }
    }
    // Hotové hlásenie po chvíli zmizne (nie počas otázky na prevádzku).
    LaunchedEffect(banner, question) {
        val b = banner ?: return@LaunchedEffect
        if (question == null && b.kind != BannerKind.WAIT) {
            delay(if (b.kind == BannerKind.OK) 8000 else 12000); vm.clearBanner()
        }
    }

    Box(Modifier.fillMaxSize().background(Pal.Camera)) {
        if (granted) {
            CameraPreview(onCamera = { camera = it }) { raw -> vm.onQrFast(raw) }
            ScanFrame(Modifier.align(Alignment.Center).offset(y = (-100).dp))
        } else {
            Column(Modifier.align(Alignment.Center).offset(y = (-100).dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aplikácia potrebuje prístup ku kamere.", color = Color.White, fontSize = 16.sp)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(16.dp)) { Text("Povoliť kameru") }
            }
        }

        // horná lišta: suma za mesiac + blesk
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(monthName(sk.miro.blocky.domain.Period.current()) + if (mock) " · TESTOVACÍ REŽIM" else "",
                    color = Color(0xFFD7DDD9), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(eur(monthTotal), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
            IconButton(
                onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(if (torch) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.14f))
            ) {
                Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    if (torch) "Vypnúť blesk" else "Zapnúť blesk", tint = if (torch) Pal.Ink else Color.White)
            }
        }

        // spodný panel
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Pal.Bg)
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val b = banner
            if (b != null) ScanBannerCard(b,
                onOpen = { id -> vm.clearBanner(); onOpenReceipt(id) },
                onNext = vm::scanNext)
            else HintCard()
            OutlinedButton(onClick = { filePicker.launch(arrayOf("application/pdf", "image/*")) },
                Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Pal.Card, contentColor = Pal.Ink)) {
                Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("E-bloček z e-mailu (PDF / obrázok)", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenApp, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Ink, contentColor = Color.White)) {
                    Icon(Icons.Default.BarChart, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Prehľad", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { manual = true }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Neutral, contentColor = Pal.Ink)) {
                    Text("Zadať ručne", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    question?.let { m ->
        MerchantTypeDialog(m, onPick = { t -> vm.answerMerchant(m, t) }, onLater = { vm.skipMerchant(m) })
    }

    if (manual) ManualEntryDialog(onDismiss = { manual = false }) { m, c, cat ->
        vm.addManual(m, c, cat, System.currentTimeMillis()); manual = false
    }
}

/** Rohové značky zameriavača. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    val w = 4.dp; val len = 40.dp; val r = 18.dp
    Box(modifier.size(240.dp)) {
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { a ->
            Box(Modifier.align(a).size(len).drawBehind {
                val sw = w.toPx(); val rad = r.toPx()
                val left = a == Alignment.TopStart || a == Alignment.BottomStart
                val top = a == Alignment.TopStart || a == Alignment.TopEnd
                val path = Path().apply {
                    val x0 = if (left) sw / 2 else size.width - sw / 2
                    val y0 = if (top) sw / 2 else size.height - sw / 2
                    val xEnd = if (left) size.width else 0f
                    val yEnd = if (top) size.height else 0f
                    moveTo(x0, yEnd)
                    lineTo(x0, if (top) y0 + rad else y0 - rad)
                    quadraticTo(x0, y0, if (left) x0 + rad else x0 - rad, y0)
                    lineTo(xEnd, y0)
                }
                drawPath(path, Color.White, style = Stroke(width = sw, cap = StrokeCap.Round))
            })
        }
    }
}

@Composable
private fun HintCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Pal.Track), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.QrCodeScanner, null, tint = Pal.Ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Namierte na QR kód", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pal.Ink)
            Text("je v spodnej časti bločku", fontSize = 13.sp, color = Pal.Muted)
        }
    }
}

@Composable
private fun ScanBannerCard(b: ScanBanner, onOpen: (Long) -> Unit, onNext: () -> Unit) {
    val dup = b.kind == BannerKind.DUPLICATE
    val (circle, icon) = when (b.kind) {
        BannerKind.OK, BannerKind.WAIT -> Pal.Green to Icons.Default.Check
        BannerKind.DUPLICATE -> Color(0xFFE08A00) to Icons.Default.ContentCopy
        BannerKind.INFO -> Pal.Muted to Icons.Default.Info
        BannerKind.ERROR -> Pal.Red to Icons.Default.PriorityHigh
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(if (dup) Color(0xFFFFF3DC) else Pal.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (dup) Color(0xFF8A4B00) else Pal.Ink, maxLines = 2)
                b.sub?.let { Text(it, fontSize = 13.sp, color = Pal.Muted, maxLines = 3) }
            }
            if (b.kind == BannerKind.WAIT)
                CircularProgressIndicator(Modifier.size(22.dp), color = Pal.Green, strokeWidth = 2.5.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onNext, Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Green, contentColor = Color.White)) {
                Icon(Icons.Default.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("Skenovať ďalší", fontWeight = FontWeight.Bold)
            }
            if (b.receiptDbId != null) OutlinedButton(onClick = { onOpen(b.receiptDbId) }, Modifier.height(46.dp),
                shape = RoundedCornerShape(14.dp)) {
                Text(if (dup) "Pôvodný" else "Detail", color = Pal.Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Kamera + čítanie QR cez ZXing priamo v telefóne.
 * Žiadne Google služby, žiadne sieťové spojenie – obraz z kamery neopustí zariadenie.
 */
@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onQr: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            ))
        }
    }
    val latestOnQr by rememberUpdatedState(onQr)
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
        val providerFuture = ProcessCameraProvider.getInstance(ctx)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                try {
                    val plane = proxy.planes[0]   // jas (Y) – na QR stačí
                    val buf = plane.buffer
                    val w = proxy.width; val h = proxy.height; val stride = plane.rowStride
                    val data = ByteArray(w * h)
                    buf.rewind()
                    if (stride == w) buf.get(data, 0, w * h)
                    else for (row in 0 until h) { buf.position(row * stride); buf.get(data, row * w, w) }
                    val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
                    val text = try {
                        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
                    } catch (_: NotFoundException) { null } finally { reader.reset() }
                    if (!text.isNullOrBlank()) ContextCompat.getMainExecutor(ctx).execute { latestOnQr(text) }
                } catch (_: Exception) {
                } finally { proxy.close() }
            }
            runCatching {
                provider.unbindAll()
                onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
}
