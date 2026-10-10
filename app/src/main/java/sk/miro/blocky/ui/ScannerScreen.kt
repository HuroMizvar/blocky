package sk.miro.blocky.ui

import android.Manifest
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
import androidx.compose.foundation.border
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: MainViewModel, onOpenApp: () -> Unit, onOpenReceipt: (Long) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val banner by vm.banner.collectAsState()
    val mock by vm.mockMode.collectAsState()
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var manual by remember { mutableStateOf(false) }

    // vibrácia pri každom novom skene; po dokončení pás po chvíli zmizne
    LaunchedEffect(banner?.scanId) {
        if (banner != null) view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }
    LaunchedEffect(banner) {
        val b = banner ?: return@LaunchedEffect
        if (b.kind != BannerKind.WAIT) { delay(if (b.kind == BannerKind.ERROR) 8000 else 5000); vm.clearBanner() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (mock) "Bločky · TEST" else "Bločky") },
            actions = {
                IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) {
                    Icon(if (torch) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn, "Svetlo")
                }
                FilledTonalButton(onClick = onOpenApp, Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Default.BarChart, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Prehľad")
                }
            }
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Color.Black)) {
            if (granted) {
                CameraPreview(onCamera = { camera = it }) { raw -> vm.onQrFast(raw) }
                Box(Modifier.align(Alignment.Center).size(260.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp)))
                if (mock) Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("Testovací režim: akýkoľvek QR → demo nákup", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aplikácia potrebuje prístup ku kamere.", color = Color.White)
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Povoliť kameru") }
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val b = banner
                if (b != null) ScanBannerCard(b, onOpen = { id -> vm.clearBanner(); onOpenReceipt(id) }, onClose = vm::clearBanner)
                else Text("Namierte na QR kód v spodnej časti bločku", color = Color.White)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { manual = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Bez QR? Zadať výdavok ručne") }
            }
        }
    }

    if (manual) ManualEntryDialog(onDismiss = { manual = false }) { m, c, cat ->
        vm.addManual(m, c, cat, System.currentTimeMillis()); manual = false
    }
}

@Composable
private fun ScanBannerCard(b: ScanBanner, onOpen: (Long) -> Unit, onClose: () -> Unit) {
    val (bg, fg) = when (b.kind) {
        BannerKind.OK, BannerKind.WAIT -> Color(0xFF2E7D32) to Color.White
        BannerKind.INFO -> Color(0xFF37474F) to Color.White
        BannerKind.ERROR -> Color(0xFFC62828) to Color.White
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (b.kind == BannerKind.WAIT) {
                CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                b.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (b.receiptDbId != null) TextButton(onClick = { onOpen(b.receiptDbId) },
                colors = ButtonDefaults.textButtonColors(contentColor = fg)) { Text("Detail") }
            else if (b.kind != BannerKind.WAIT) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Zavrieť") }
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
