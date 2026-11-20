package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Testovacie bločky pre nového používateľa: okno pri prvom spustení, otázka na trvalé odmietnutie,
 * otázka po prvom vlastnom bločku, pruh v Prehľade, značka TEST a časť v Nastaveniach.
 */

private val TestAmberBg = Color(0xFFFBEFD6)
private val TestAmberLine = Color(0xFFEBCB8B)
private val TestAmberInk = Color(0xFF8A5A12)
private val DangerRed = Color(0xFFB3261E)

/** Všetky okná testovacích bločkov – volá sa raz v MainActivity. */
@Composable
fun TestDataDialogs(vm: MainViewModel) {
    val welcome by vm.welcome.collectAsState()
    val askRemove by vm.askRemoveDemo.collectAsState()
    var askForever by remember { mutableStateOf(false) }

    if (welcome && !askForever) BigDialog(
        icon = Icons.Default.ReceiptLong,
        title = "Ďakujeme, že ste si nainštalovali aplikáciu Bločky",
        texts = listOf(
            "Aby ste si mohli pozrieť, ako aplikácia funguje a ako zobrazuje nákupy, pripravili sme pre vás sadu fiktívnych testovacích bločkov.",
            "Testovacie bločky môžete potom v nastaveniach vymazať z aplikácie."
        ),
        primary = "Chcem vyskúšať" to { vm.loadTestData() },
        secondary = "Nechcem vyskúšať" to { askForever = true },
        onDismiss = { askForever = true }
    )

    if (askForever) AlertDialog(
        onDismissRequest = { askForever = false; vm.declineTestData(false) },
        containerColor = Pal.Card, shape = RoundedCornerShape(28.dp),
        title = { Text("Vymazať testovacie bločky natrvalo?", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Áno – testovacie bločky sa nenahrajú a ponuka zmizne aj z Nastavení.", fontSize = 15.sp, color = Pal.Muted, lineHeight = 22.sp)
                Text("Nie – testovacie bločky si môžete kedykoľvek zapnúť alebo vymazať v Nastaveniach → Nastavenie aplikácie.",
                    fontSize = 15.sp, color = Pal.Muted, lineHeight = 22.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { askForever = false; vm.declineTestData(true) }) {
                Text("Áno, vymazať", color = DangerRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = { askForever = false; vm.declineTestData(false) }) { Text("Nie") } }
    )

    if (askRemove) BigDialog(
        icon = Icons.Default.Check,
        title = "Naskenovali ste prvý vlastný bloček",
        texts = listOf(
            "Chcete teraz vymazať testovacie bločky, aby sa fiktívne nákupy nemiešali s vašimi skutočnými výdavkami?",
            "Vymaže sa len testovacia sada. Váš bloček ostane."
        ),
        primary = "Vymazať testovacie" to { vm.removeTestData(false) },
        secondary = "Ponechať zatiaľ" to { vm.keepTestData() },
        onDismiss = { vm.keepTestData() }
    )
}

@Composable
private fun BigDialog(icon: ImageVector, title: String, texts: List<String>,
                      primary: Pair<String, () -> Any?>, secondary: Pair<String, () -> Any?>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card, shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Pal.GreenSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Pal.Green, modifier = Modifier.size(28.dp))
            }
        },
        title = { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 27.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                texts.forEach { Text(it, fontSize = 15.sp, color = Pal.Muted, lineHeight = 22.sp) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { primary.second() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Green, contentColor = Pal.OnGreen)) {
                    Text(primary.first, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { secondary.second() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Track, contentColor = Pal.Ink)) {
                    Text(secondary.first, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

/** Oranžový pruh v Prehľade, kým sú nahraté testovacie bločky. */
@Composable
fun TestDataBanner(vm: MainViewModel) {
    val count by vm.demoCount.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    if (count <= 0) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(TestAmberBg)
        .border(1.dp, TestAmberLine, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Science, null, tint = TestAmberInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Testovacie bločky · fiktívne nákupy na vyskúšanie", Modifier.weight(1f), fontSize = 13.sp,
            color = TestAmberInk, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
        Text("Vymazať", Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirm = true }.padding(6.dp),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TestAmberInk)
    }
    if (confirm) ConfirmRemove(onDismiss = { confirm = false }) { confirm = false; vm.removeTestData(false) }
}

/** Značka TEST pri testovacom bločku. */
@Composable
fun TestBadge() {
    Text("TEST", Modifier.clip(RoundedCornerShape(6.dp)).background(TestAmberBg).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TestAmberInk)
}

@Composable
private fun ConfirmRemove(forever: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pal.Card,
        title = {
            Text(if (forever) "Úplne odstrániť testovacie bločky?" else "Vymazať testovacie bločky?", fontWeight = FontWeight.Bold)
        },
        text = {
            Text((if (forever) "Testovacie bločky sa vymažú a ponuka zmizne aj z Nastavení. " else "") +
                "Vymažú sa testovacie bločky, ukážkové rozpočty a ukážkové fixné výdavky. Vaše vlastné bločky ostanú.",
                fontSize = 14.sp, color = Pal.Muted, lineHeight = 20.sp)
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (forever) "Odstrániť" else "Vymazať", color = DangerRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

/** Časť „Testovacie bločky“ v Nastaveniach → Nastavenie aplikácie (nezobrazí sa, ak ich používateľ úplne odstránil). */
@Composable
fun TestDataSection(vm: MainViewModel) {
    val state by vm.demoState.collectAsState()
    val count by vm.demoCount.collectAsState()
    val busy by vm.demoBusy.collectAsState()
    var confirm by remember { mutableStateOf<Boolean?>(null) }   // false = vymazať, true = úplne odstrániť
    if (state == "removed" && count == 0) return
    HorizontalDivider(Modifier.padding(top = 20.dp))
    SectionTitle("Testovacie bločky")
    BCard(padding = PaddingValues(16.dp)) {
        if (count > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TestBadge(); Spacer(Modifier.width(8.dp))
                Text("Zapnuté · $count ${receiptWord(count)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Vymažú sa len testovacie bločky (a ich ukážkové rozpočty a fixné výdavky). Vaše vlastné bločky ostanú.",
                Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Pal.Muted, lineHeight = 18.sp)
            OutlinedButton(onClick = { confirm = false }, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy,
                shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, DangerRed)) {
                Text("Vymazať testovacie bločky", color = DangerRed, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Sada fiktívnych nákupov za posledné 3 mesiace – vyskúšate si Prehľad, Rozpočty, Tipy aj report bez vlastných bločkov.",
                fontSize = 13.sp, color = Pal.Muted, lineHeight = 18.sp)
            Button(onClick = vm::loadTestData, Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !busy,
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Pal.Green, contentColor = Pal.OnGreen)) {
                Text("Zapnúť testovacie bločky", fontWeight = FontWeight.Bold)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Pal.Green)
        TextButton(onClick = { confirm = true }, Modifier.padding(top = 4.dp), enabled = !busy) {
            Text("Úplne odstrániť testovacie bločky z aplikácie a nastavení", color = Pal.Muted, fontSize = 13.sp)
        }
    }
    confirm?.let { forever ->
        ConfirmRemove(forever, onDismiss = { confirm = null }) { confirm = null; vm.removeTestData(forever) }
    }
}

