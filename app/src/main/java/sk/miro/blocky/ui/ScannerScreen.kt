package sk.miro.blocky.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import sk.miro.blocky.data.ScanResult
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: MainViewModel, onBack: () -> Unit, onOpenReceipt: (Long) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) { onDispose { vm.resetScan() } }

    val scan by vm.scan.collectAsState()
    val mock by vm.mockMode.collectAsState()
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var manual by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (mock) "Skenovať (TEST)" else "Skenovať bloček") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť") } },
            actions = {
                IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }) {
                    Icon(if (torch) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn, "Svetlo")
                }
            }
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(Color.Black)) {
            if (granted) {
                CameraPreview(onCamera = { camera = it }) { raw ->
                    if (vm.scan.value is ScanUi.Idle) vm.onQr(raw)
                }
                Box(Modifier.align(Alignment.Center).size(260.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp)))
                if (mock) Surface(Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("Testovací režim: akýkoľvek QR → demo nákup", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Text("Namierte na QR kód v spodnej časti bločku",
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp), color = Color.White)
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aplikácia potrebuje prístup ku kamere.", color = Color.White)
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Povoliť kameru") }
                }
            }
            OutlinedButton(onClick = { manual = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Zadať výdavok ručne") }

            if (scan is ScanUi.Loading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Načítavam bloček z eKasa…", color = Color.White)
                    }
                }
            }
        }
    }

    (scan as? ScanUi.Done)?.result?.let { res ->
        AlertDialog(
            onDismissRequest = { vm.resetScan() },
            title = {
                Text(when (res) {
                    is ScanResult.Saved -> "Bloček uložený"
                    is ScanResult.Duplicate -> "Už naskenované"
                    is ScanResult.Error -> "Nepodarilo sa"
                })
            },
            text = {
                Text(when (res) {
                    is ScanResult.Saved -> "${res.merchant}\n${res.items} položiek · ${eur(res.totalCents)}"
                    is ScanResult.Duplicate -> "Tento bloček už máte uložený."
                    is ScanResult.Error -> res.message
                })
            },
            confirmButton = {
                TextButton(onClick = { vm.resetScan() }) { Text("Skenovať ďalší") }
            },
            dismissButton = {
                if (res is ScanResult.Saved) TextButton(onClick = { vm.resetScan(); onOpenReceipt(res.receiptDbId) }) {
                    Text("Zobraziť položky")
                }
            }
        )
    }

    if (manual) ManualEntryDialog(onDismiss = { manual = false }) { m, c, cat ->
        vm.addManual(m, c, cat, System.currentTimeMillis()); manual = false; onBack()
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
