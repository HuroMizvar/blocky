package sk.miro.blocky

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import sk.miro.blocky.ui.*

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    REPORT("report", "Prehľad", Icons.Default.BarChart),
    TIPS("tips", "Odporúčania", Icons.Default.Lightbulb),
    RECEIPTS("receipts", "Bločky", Icons.AutoMirrored.Filled.ReceiptLong),
    BUDGETS("budgets", "Rozpočty", Icons.Default.Savings)
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val returnToScan = mutableIntStateOf(0)
    private var stoppedAt = 0L

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) returnToScan.intValue++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockyTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val tab = Tab.entries.firstOrNull { it.route == route }
                val update by vm.update.collectAsState()
                val prompt by vm.updatePrompt.collectAsState()
                val mock by vm.mockMode.collectAsState()
                val hasUpdate = update is UpdateUi.Available || update is UpdateUi.ReadyToInstall

                // Dialóg po automatickej kontrole pri štarte – sťahovanie aj inštalácia priamo v ňom.
                // Na skeneri nerušiť – dialóg sa ukáže až v Prehľade / ostatných obrazovkách.
                if (route != "scan" && prompt && update !is UpdateUi.Idle && update !is UpdateUi.UpToDate) AlertDialog(
                    onDismissRequest = vm::dismissUpdatePrompt,
                    title = { Text("Nová verzia aplikácie") },
                    text = {
                        Column {
                            if (update is UpdateUi.Error) Text((update as UpdateUi.Error).message)
                            else UpdateActions(vm, update)
                        }
                    },
                    confirmButton = { TextButton(onClick = vm::dismissUpdatePrompt) { Text("Neskôr") } }
                )

                // Po návrate z pozadia (>1 min) vždy otvoriť skener
                val reset by returnToScan
                LaunchedEffect(reset) {
                    if (reset > 0 && nav.currentDestination?.route != "scan") nav.navigate("scan") {
                        popUpTo("scan") { inclusive = false }; launchSingleTop = true
                    }
                }

                NavHost(nav, startDestination = "scan") {
                    Tab.entries.forEach { t ->
                        composable(t.route) {
                            Scaffold(
                                topBar = {
                                    TopAppBar(title = { Text(t.label) }, actions = {
                                        if (t == Tab.REPORT || t == Tab.RECEIPTS) IconButton(onClick = ::exportCsv) {
                                            Icon(Icons.Default.Share, "Exportovať CSV")
                                        }
                                        IconButton(onClick = { nav.navigate("settings") }) {
                                            BadgedBox(badge = { if (hasUpdate || mock) Badge() }) {
                                                Icon(Icons.Default.Settings, "Nastavenia")
                                            }
                                        }
                                    })
                                },
                                bottomBar = {
                                    NavigationBar {
                                        Tab.entries.forEach { item ->
                                            NavigationBarItem(
                                                selected = tab == item,
                                                onClick = {
                                                    nav.navigate(item.route) {
                                                        popUpTo("scan") { saveState = true }
                                                        launchSingleTop = true; restoreState = true
                                                    }
                                                },
                                                icon = { Icon(item.icon, null) },
                                                label = { Text(item.label) }
                                            )
                                        }
                                    }
                                },
                                floatingActionButton = {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            nav.navigate("scan") { popUpTo("scan") { inclusive = false }; launchSingleTop = true }
                                        },
                                        icon = { Icon(Icons.Default.QrCodeScanner, null) },
                                        text = { Text("Skenovať") }
                                    )
                                }
                            ) { pad ->
                                when (t) {
                                    Tab.REPORT -> ReportScreen(vm, pad)
                                    Tab.TIPS -> RecommendationsScreen(vm, pad)
                                    Tab.RECEIPTS -> ReceiptsScreen(vm, pad) { id -> nav.navigate("receipt/$id") }
                                    Tab.BUDGETS -> BudgetsScreen(vm, pad)
                                }
                            }
                        }
                    }
                    composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                    composable("scan") {
                        ScannerScreen(vm, onOpenApp = {
                            nav.navigate(Tab.REPORT.route) { launchSingleTop = true }
                        }) { id -> nav.navigate("receipt/$id") }
                    }
                    composable("receipt/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReceiptDetailScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                }
            }
        }
    }

    private fun exportCsv() {
        vm.export { file ->
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Exportovať výdavky"))
        }
    }
}
