package sk.miro.blocky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.miro.blocky.data.ItemRow
import sk.miro.blocky.domain.Analytics
import sk.miro.blocky.domain.Category
import sk.miro.blocky.domain.MerchantType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val D = DateTimeFormatter.ofPattern("d. M.")

/**
 * Všetky položky jednej kategórie alebo jedného obchodu v danom mesiaci.
 * Ťuknutie na položku → zmena kategórie; ikona bločku → otvorí celý bloček.
 */
@Composable
fun ItemsScreen(vm: MainViewModel, kind: String, key: String, month: YearMonth,
                onBack: () -> Unit, onOpenReceipt: (Long) -> Unit) {
    val all by vm.allRows.collectAsState()
    var picking by remember { mutableStateOf<ItemRow?>(null) }
    val isCat = kind == "cat"
    val cat = if (isCat) Category.fromName(key) else null
    val rows = all.filter {
        Analytics.monthOf(it.issuedAt) == month && it.category != Category.ZALOHY.name &&
            (if (isCat) it.category == key else it.merchantName == key)
    }.sortedByDescending { it.issuedAt }
    val total = rows.sumOf { it.priceCents }

    picking?.let { r ->
        CategoryPickerDialog(r.name, Category.fromName(r.category), onDismiss = { picking = null }) { c, allSame ->
            vm.recategorize(r.itemId, r.normName, c, allSame); picking = null
        }
    }

    Scaffold(containerColor = Pal.Bg) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
            top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                IconButton(onClick = onBack, Modifier.offset(x = (-12).dp).padding(top = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť")
                }
            }
            item {
                BCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cat != null) CatBadge(cat, 44)
                        else MerchantBadge(MerchantType.fromName(rows.firstOrNull()?.merchantType), 44)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat?.label ?: shortMerchant(key), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(monthLabel(month), fontSize = 13.sp, color = Pal.Muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(eur(total), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Text("${rows.size} ${if (rows.size == 1) "položka" else if (rows.size in 2..4) "položky" else "položiek"} · " +
                        "ťuknutím na položku zmeníte kategóriu", fontSize = 12.sp, color = Pal.Muted)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            if (rows.isEmpty()) item {
                Text("Žiadne položky.", Modifier.padding(8.dp), color = Pal.Muted)
            }
            items(rows, key = { it.itemId }) { r ->
                val c = Category.fromName(r.category)
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(16.dp)).background(Pal.Card)
                    .clickable { picking = r }.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(c, 34)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val qty = if (r.quantity != 1.0) " · ${fmtQ(r.quantity)} ×" else ""
                        Text(Instant.ofEpochMilli(r.issuedAt).atZone(ZoneId.systemDefault()).format(D) + " · " +
                            (if (isCat) shortMerchant(r.merchantName) else c.label) + qty,
                            fontSize = 12.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(eur(r.priceCents), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onOpenReceipt(r.receiptDbId) }) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, "Otvoriť bloček", tint = Pal.Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun fmtQ(q: Double): String =
    if (q % 1.0 == 0.0) q.toLong().toString() else "%.3f".format(SK, q).trimEnd('0').trimEnd(',')
