package sk.miro.blocky.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import sk.miro.blocky.sync.HouseholdSync
import sk.miro.blocky.sync.Invite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_FMT = DateTimeFormatter.ofPattern("d. M. HH:mm")

/** Farba člena domácnosti – odvodená z mena, rovnaká na všetkých zariadeniach. */
fun memberColor(name: String): Color {
    val palette = listOf(0xFF1F7A4D, 0xFF2D6A8E, 0xFF9A5B06, 0xFF7A3E9D, 0xFFB3361B, 0xFF3C4BA0, 0xFF0F7C7C, 0xFF8E5A2D)
    return Color(palette[Math.floorMod(name.hashCode(), palette.size)])
}

/** Kruh s iniciálou člena. */
@Composable
fun MemberDot(name: String, size: Int = 18) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(memberColor(name)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, fontSize = (size * 0.55).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HouseholdSection(vm: MainViewModel) {
    val st by vm.syncStatus.collectAsState()
    val members by vm.members.collectAsState()
    val busy by vm.syncBusy.collectAsState()
    var askName by remember { mutableStateOf(false) }
    var pasteCode by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(st.enabled) { if (st.enabled) vm.refreshMembers() }

    SectionTitle("Spoločná domácnosť")
    if (!st.enabled) {
        BCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = Pal.Green, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("Spoločné výdavky pre celú rodinu", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            listOf(
                "Bločky môže nahrávať až ${HouseholdSync.MAX_MEMBERS} členov – vy, partner, deti, aj váš tablet.",
                "Pri každom bločku vidno, kto ho nahral.",
                "Ak niekto naskenuje bloček, ktorý už nahral iný člen, aplikácia upozorní.",
                "Údaje sa šifrujú v telefóne. Server (Google Firebase, EÚ) vidí len zašifrovaný text.",
                "Je to voliteľné – bez domácnosti ostáva všetko len v tomto telefóne."
            ).forEach { Text("• $it", fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(12.dp))
            if (!vm.syncConfigured) Text(
                "Server zatiaľ nie je nastavený, preto sa domácnosť nedá založiť (pripojiť sa cez pozvánku funguje). Postup je v návode.",
                fontSize = 12.sp, color = Pal.Amber, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = { askName = true }, enabled = vm.syncConfigured && !busy, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) { Text("Založiť domácnosť") }
            Spacer(Modifier.height(6.dp))
            Text("Pridať sa k existujúcej: naskenujte pozývací QR kód člena domácnosti bežným skenerom bločkov.",
                fontSize = 12.sp, color = Pal.Muted)
            TextButton(onClick = { pasteCode = true }) { Text("Vložiť kód pozvánky") }
        }
    } else {
        BCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Pal.Green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ste v domácnosti ako ${st.memberName ?: "?"}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Členovia (${members.size}/${HouseholdSync.MAX_MEMBERS})", fontSize = 13.sp, color = Pal.Muted, fontWeight = FontWeight.SemiBold)
            members.forEach { m ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberDot(m.name, 26)
                    Spacer(Modifier.width(10.dp))
                    Text(m.name + if (m.me) " (toto zariadenie)" else "", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (st.running || busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Sync, null, tint = if (st.error != null) Pal.Red else Pal.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(when {
                    st.running || busy -> "Synchronizujem…"
                    st.error != null -> "Chyba: ${st.error}"
                    st.lastSyncAt > 0 -> "Synchronizované " + Instant.ofEpochMilli(st.lastSyncAt).atZone(ZoneId.systemDefault()).format(SYNC_FMT)
                    else -> "Zatiaľ nesynchronizované"
                }, fontSize = 12.sp, color = if (st.error != null) Pal.Red else Pal.Muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showInvite = true }, shape = RoundedCornerShape(14.dp)) { Text("Pozvať člena") }
                OutlinedButton(onClick = vm::syncNow, enabled = !busy, shape = RoundedCornerShape(14.dp)) { Text("Synchronizovať") }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { confirmLeave = true }) { Text("Odísť z domácnosti") }
            TextButton(onClick = { confirmDelete = true }) { Text("Zmazať údaje domácnosti zo servera", color = Pal.Red) }
        }
    }

    if (askName) NameDialog("Založiť domácnosť", "Vaše meno vidia ostatní členovia pri bločkoch, ktoré nahráte.",
        "Založiť", onDismiss = { askName = false }) { vm.createHousehold(it); askName = false }
    if (pasteCode) PasteInviteDialog(onDismiss = { pasteCode = false }) { inv, name -> pasteCode = false; vm.joinHousehold(inv, name) }
    if (showInvite) vm.invite()?.let { InviteDialog(it) { showInvite = false } }
    if (confirmLeave) ConfirmDialog("Odísť z domácnosti?",
        "Synchronizácia sa vypne. Všetky bločky ostanú v tomto zariadení, údaje na serveri ostanú ostatným členom.",
        "Odísť", onDismiss = { confirmLeave = false }) { confirmLeave = false; vm.leaveHousehold() }
    if (confirmDelete) ConfirmDialog("Zmazať údaje zo servera?",
        "Zo servera sa zmažú všetky bločky a nastavenia domácnosti pre všetkých členov a domácnosť zanikne. " +
            "V jednotlivých zariadeniach bločky ostanú. Nedá sa to vrátiť.",
        "Zmazať", danger = true, onDismiss = { confirmDelete = false }) { confirmDelete = false; vm.deleteHouseholdData() }
}

/** Dialógy domácnosti viditeľné kdekoľvek v aplikácii (pozvánka zo skenera, priebeh, výsledok). */
@Composable
fun HouseholdDialogs(vm: MainViewModel) {
    val inv by vm.pendingInvite.collectAsState()
    val busy by vm.syncBusy.collectAsState()
    val msg by vm.syncMsg.collectAsState()
    inv?.let { i ->
        NameDialog("Pridať sa k domácnosti",
            "Naskenovali ste pozvánku do spoločnej domácnosti. Vaše bločky sa spoja s bločkami ostatných členov. " +
                "Zadajte meno, ktoré uvidia pri vašich bločkoch.",
            "Pridať sa", onDismiss = vm::dismissInvite) { vm.joinHousehold(i, it) }
    }
    if (busy && inv == null) BusyDialog {
        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Pal.Card).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text("Pracujem… môže to chvíľu trvať.", fontSize = 15.sp)
        }
    }
    msg?.let { m ->
        AlertDialog(onDismissRequest = vm::clearSyncMsg, containerColor = Pal.Card,
            title = { Text("Spoločná domácnosť") }, text = { Text(m) },
            confirmButton = { TextButton(onClick = vm::clearSyncMsg) { Text("OK") } })
    }
}

@Composable
private fun BusyDialog(content: @Composable () -> Unit) =
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) { content() }

@Composable
private fun NameDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Pal.Card,
        title = { Text(title) },
        text = {
            Column {
                Text(text, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Meno (napr. Miro, Jana)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(name) }, enabled = name.isNotBlank()) { Text(confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } })
}

@Composable
private fun PasteInviteDialog(onDismiss: () -> Unit, onJoin: (Invite, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val inv = Invite.decode(code)
    AlertDialog(onDismissRequest = onDismiss, containerColor = Pal.Card,
        title = { Text("Kód pozvánky") },
        text = {
            Column {
                Text("Vložte kód, ktorý vám poslal člen domácnosti (začína „${Invite.PREFIX}“).", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(code, { code = it.trim() }, label = { Text("Kód") }, maxLines = 3,
                    isError = code.isNotEmpty() && inv == null)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("Vaše meno") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { inv?.let { onJoin(it, name) } }, enabled = inv != null && name.isNotBlank()) {
            Text("Pridať sa", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } })
}

@Composable
private fun InviteDialog(invite: Invite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = remember(invite) { invite.encode() }
    val bmp = remember(code) { qrBitmap(code, 640) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Pal.Card,
        title = { Text("Pozvať člena") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nový člen nainštaluje aplikáciu Bločky a týmto QR kódom sa pridá – stačí ho naskenovať bežným skenerom bločkov.",
                    fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Image(bmp.asImageBitmap(), "Pozývací QR kód", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(12.dp))
                Text("Kód obsahuje kľúč domácnosti – kto ho má, vidí vaše spoločné výdavky. Ukazujte ho len členom. " +
                    "Uložte si ho aj ako zálohu: ak stratíte všetky zariadenia, novým telefónom sa ním vrátite k údajom.",
                    fontSize = 12.sp, color = Pal.Amber, lineHeight = 16.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Hotovo", fontWeight = FontWeight.Bold) } },
        dismissButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code) }
                context.startActivity(Intent.createChooser(send, "Poslať kód pozvánky"))
            }) { Text("Poslať ako text") }
        })
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false,
                          onDismiss: () -> Unit, onOk: () -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, containerColor = Pal.Card,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onOk) { Text(confirm, color = if (danger) Pal.Red else Pal.Green, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } })

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF17201B.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
