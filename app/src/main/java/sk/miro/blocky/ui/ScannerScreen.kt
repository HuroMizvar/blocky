package sk.miro.blocky.ui

import android.Manifest
import android.app.Activity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

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
    val mock by vm.mockMode.collectAsState()
    val monthTotal by vm.monthTotal.collectAsState()
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var manual by remember { mutableStateOf(false) }

    LaunchedEffect(banner?.scanId) {
        if (banner != null) view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }
    LaunchedEffect(banner) {
        val b = banner ?: return@LaunchedEffect
        if (b.kind != BannerKind.WAIT) { delay(if (b.kind == BannerKind.ERROR) 8000 else 6000); vm.clearBanner() }
    }

    Box(Modifier.fillMaxSize().background(Pal.Camera)) {
        if (granted) {
            CameraPreview(onCamera = { camera = it }) { raw -> vm.onQrFast(raw) }
            ScanFrame(Modifier.align(Alignment.Center).offset(y = (-90).dp))
        } else {
            Column(Modifier.align(Alignment.Center).offset(y = (-90).dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aplikácia potrebuje prístup ku kamere.", color = Color.White, fontSize = 16.sp)
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(16.dp)) { Text("Povoliť kameru") }
            }
        }

        // horná lišta: suma za mesiac + svetlo
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(monthName(java.time.YearMonth.now()) + if (mock) " · TESTOVACÍ REŽIM" else "",
                    color = Color(0xFFD7DDD9), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(eur(monthTotal), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
            IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = if (torch) 0.35f else 0.14f))) {
                Icon(if (torch) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn, "Svetlo", tint = Color.White)
            }
        }

        // spodný panel
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Pal.Bg)
            .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val b = banner
            if (b != null) ScanBannerCard(b, onOpen = { id -> vm.clearBanner(); onOpenReceipt(id) }, onClose = vm::clearBanner)
            else HintCard()
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
                val path = androidx.compose.ui.graphics.Path().apply {
                    val x0 = if (left) sw / 2 else size.width - sw / 2
                    val y0 = if (top) sw / 2 else size.height - sw / 2
                    val xEnd = if (left) size.width else 0f
                    val yEnd = if (top) size.height else 0f
                    moveTo(x0, yEnd)
                    lineTo(x0, if (top) y0 + rad else y0 - rad)
                    quadraticTo(x0, y0, if (left) x0 + rad else x0 - rad, y0)
                    lineTo(xEnd, y0)
                }
                drawPath(path, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
private fun ScanBannerCard(b: ScanBanner, onOpen: (Long) -> Unit, onClose: () -> Unit) {
    val (circle, icon) = when (b.kind) {
        BannerKind.OK, BannerKind.WAIT -> Pal.Green to Icons.Default.Check
        BannerKind.INFO -> Pal.Muted to Icons.Default.Info
        BannerKind.ERROR -> Pal.Red to Icons.Default.PriorityHigh
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pal.Ink, maxLines = 2)
            b.sub?.let { Text(it, fontSize = 13.sp, color = Pal.Muted, maxLines = 2) }
        }
        when {
            b.kind == BannerKind.WAIT -> CircularProgressIndicator(Modifier.size(22.dp), color = Pal.Green, strokeWidth = 2.5.dp)
            b.receiptDbId != null -> TextButton(onClick = { onOpen(b.receiptDbId) }) {
                Text("Detail", color = Pal.Green, fontWeight = FontWeight.Bold)
            }
            else -> IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Zavrieť", tint = Pal.Muted) }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val latestOnQr by rememberUpdatedState(onQr)
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

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
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                scanner.process(img)
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { it.rawValue }?.let { raw ->
                            ContextCompat.getMainExecutor(ctx).execute { latestOnQr(raw) }
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                provider.unbindAll()
                onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }
        }, ContextCompat.getMainExecutor(ctx))
        view
    })
}
