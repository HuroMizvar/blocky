package sk.miro.blocky

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import sk.miro.blocky.ui.*

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    REPORT("report", "Prehľad", Icons.Default.BarChart),
    TIPS("tips", "Tipy", Icons.Default.Lightbulb),
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
        vm.syncSoon()   // spoločná domácnosť: stiahnuť bločky ostatných členov
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // svetlý dizajn → tmavé ikony v stavovom a navigačnom riadku
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        )
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
                    containerColor = Pal.Card,
                    title = { Text("Nová verzia aplikácie") },
                    text = {
                        Column {
                            if (update is UpdateUi.Error) Text((update as UpdateUi.Error).message)
                            else UpdateActions(vm, update)
                        }
                    },
                    confirmButton = { TextButton(onClick = vm::dismissUpdatePrompt) { Text("Neskôr") } }
                )

                HouseholdDialogs(vm)

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
                            val actions: @Composable RowScope.() -> Unit = {
                                if (t == Tab.REPORT || t == Tab.RECEIPTS) IconButton(onClick = ::exportCsv) {
                                    Icon(Icons.Default.IosShare, "Exportovať CSV", tint = Pal.Ink)
                                }
                                IconButton(onClick = { nav.navigate("settings") }) {
                                    BadgedBox(badge = { if (hasUpdate || mock) Badge(containerColor = Pal.Green) }) {
                                        Icon(Icons.Default.Settings, "Nastavenia", tint = Pal.Ink)
                                    }
                                }
                            }
                            Scaffold(
                                containerColor = Pal.Bg,
                                bottomBar = {
                                    BottomBar(current = tab,
                                        onTab = { item ->
                                            nav.navigate(item.route) {
                                                popUpTo("scan") { saveState = true }
                                                launchSingleTop = true; restoreState = true
                                            }
                                        },
                                        onScan = { nav.navigate("scan") { popUpTo("scan") { inclusive = false }; launchSingleTop = true } })
                                }
                            ) { pad ->
                                when (t) {
                                    Tab.REPORT -> ReportScreen(vm, pad,
                                        onOpenTips = { nav.navigate(Tab.TIPS.route) { popUpTo("scan") { saveState = true }; launchSingleTop = true } },
                                        onOpenBudgets = { nav.navigate(Tab.BUDGETS.route) { popUpTo("scan") { saveState = true }; launchSingleTop = true } },
                                        onScan = { nav.navigate("scan") { popUpTo("scan") { inclusive = false }; launchSingleTop = true } },
                                        onOpenItems = { kind, key, month -> nav.navigate("items/$kind/${Uri.encode(key)}/$month") },
                                        actions = actions)
                                    Tab.TIPS -> RecommendationsScreen(vm, pad, actions)
                                    Tab.RECEIPTS -> ReceiptsScreen(vm, pad, onOpen = { id -> nav.navigate("receipt/$id") }, actions = actions)
                                    Tab.BUDGETS -> BudgetsScreen(vm, pad, actions)
                                }
                            }
                        }
                    }
                    composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }, onExport = ::exportCsv) }
                    composable("scan") {
                        ScannerScreen(vm, onOpenApp = {
                            nav.navigate(Tab.REPORT.route) { launchSingleTop = true }
                        }) { id -> nav.navigate("receipt/$id") }
                    }
                    composable("items/{kind}/{key}/{month}") { e ->
                        val a = e.arguments!!
                        ItemsScreen(vm, a.getString("kind")!!, a.getString("key")!!,
                            java.time.YearMonth.parse(a.getString("month")!!),
                            onBack = { nav.popBackStack() }, onOpenReceipt = { id -> nav.navigate("receipt/$id") })
                    }
                    composable("receipt/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        ReceiptDetailScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
                    }
                }
            }
        }
    }

    @Composable
    private fun BottomBar(current: Tab?, onTab: (Tab) -> Unit, onScan: () -> Unit) {
        Column(Modifier.fillMaxWidth().background(Pal.Card)) {
            HorizontalDivider(color = Pal.Line)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                // Poradie: Prehľad · Bločky · Skenuj · Rozpočty · Tipy
                val slots = listOf(Tab.REPORT, Tab.RECEIPTS, null, Tab.BUDGETS, Tab.TIPS)
                slots.forEach { t ->
                    Box(Modifier.weight(if (t == null) 1.3f else 1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (t == null) {
                            // výrazné zelené tlačidlo – ikona aj nápis sú vnútri, nič sa neoreže
                            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(58.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Pal.Green, spotColor = Pal.Green)
                                .clip(RoundedCornerShape(20.dp)).background(Pal.Green)
                                .clickable(onClickLabel = "Skenovať bloček", onClick = onScan),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(2.dp))
                                Text("Skenuj", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                        } else {
                            val sel = current == t
                            val col = if (sel) Pal.Green else Pal.Muted
                            Column(Modifier.fillMaxSize().clickable { onTab(t) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(t.icon, null, tint = col, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(t.label, color = col, fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                            }
                        }
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
