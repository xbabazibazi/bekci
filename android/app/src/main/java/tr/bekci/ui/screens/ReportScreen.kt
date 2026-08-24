package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.bekci.core.FilterAction
import tr.bekci.data.ReportEntry
import tr.bekci.ui.AppViewModel
import tr.bekci.ui.InboxFilter
import tr.bekci.ui.components.BekciCard
import tr.bekci.ui.components.PrimaryButton
import tr.bekci.ui.components.SectionLabel
import tr.bekci.ui.theme.Bekci

private const val REPORT_DAYS = 56
private val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Haftalık dolandırıcılık trendi (Pro). Veri KALICI OLARAK SAKLANMAZ —
 * her açılışta sistem SMS sağlayıcısından son 8 haftalık mesajlar okunup
 * anında sınıflandırılır (bkz. [SmsProvider.messagesSince]). Ayrı bir
 * istatistik deposu tutmamak bilinçli: "az veri sakla" ilkesiyle tutarlı
 * ve kural değiştiğinde rapor da anında güncel çıkar.
 */
@Composable
fun ReportScreen(vm: AppViewModel, onDone: () -> Unit) {
    if (!vm.isPro) {
        LockedReport(onDone)
        return
    }

    val entries = remember { vm.reportSince(REPORT_DAYS) }
    val weekly = remember(entries) { weeklyJunkCounts(entries) }
    val categories = remember(entries) {
        InboxFilter.entries.drop(1).map { filter -> filter to entries.count { filter.matches(it.verdict) } }
    }
    val topSenders = remember(entries) {
        entries.filter { it.verdict.action == FilterAction.JUNK }
            .groupingBy { it.sender }.eachCount()
            .entries.sortedByDescending { it.value }.take(5)
    }

    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                Text("Dolandırıcılık raporu", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text)
                Text("Son 8 hafta", fontSize = 12.5.sp, color = Bekci.colors.text3)
            }
        }

        item { SectionLabel("Haftalık çöpe ayrılan") }
        item {
            BekciCard {
                Column(Modifier.padding(vertical = 16.dp)) {
                    WeeklyBarChart(weekly)
                }
            }
        }

        item { SectionLabel("Dönem içinde kategori dağılımı") }
        item {
            BekciCard {
                categories.forEachIndexed { index, (filter, count) ->
                    StatRow(filter.title, "$count")
                    if (index < categories.lastIndex) {
                        HorizontalDivider(Modifier.padding(horizontal = 15.dp), color = Bekci.colors.line)
                    }
                }
            }
        }

        item { SectionLabel("En çok engellenen gönderenler") }
        item {
            BekciCard {
                if (topSenders.isEmpty()) {
                    Text("Bu dönemde ayrılan bir gönderen olmadı.",
                        fontSize = 12.5.sp, color = Bekci.colors.text3,
                        modifier = Modifier.padding(15.dp))
                } else {
                    topSenders.forEachIndexed { index, entry ->
                        StatRow(entry.key, "${entry.value} mesaj")
                        if (index < topSenders.lastIndex) {
                            HorizontalDivider(Modifier.padding(horizontal = 15.dp), color = Bekci.colors.line)
                        }
                    }
                }
            }
            Text(
                "Bu rapor sadece bu cihazda hesaplanır ve saklanmaz; her açılışta yeniden çıkarılır.",
                fontSize = 11.5.sp, color = Bekci.colors.text3,
                modifier = Modifier.padding(horizontal = 34.dp, vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun WeeklyBarChart(counts: List<Int>) {
    val maxV = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val maxBarHeight = 92.dp
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { index, c ->
            Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$c", fontSize = 10.sp, color = Bekci.colors.text3)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(BAR_WIDTH)
                        .height(maxBarHeight * (c.toFloat() / maxV.toFloat()).coerceAtLeast(0.03f))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(if (index == counts.lastIndex) Bekci.colors.signal else Bekci.colors.signalSoft),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (index == counts.lastIndex) "Bu hf" else "-${counts.lastIndex - index}h",
                    fontSize = 9.5.sp, color = Bekci.colors.text3,
                )
            }
        }
    }
}

private val BAR_WIDTH = 22.dp

@Composable
private fun StatRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 13.sp, color = Bekci.colors.text2)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
    }
}

/** [0] = en eski hafta, [lastIndex] = bu hafta. */
private fun weeklyJunkCounts(entries: List<ReportEntry>): List<Int> {
    val now = System.currentTimeMillis()
    val buckets = IntArray(8)
    entries.forEach { e ->
        if (e.verdict.action == FilterAction.JUNK) {
            val weeksAgo = ((now - e.at) / WEEK_MS).toInt().coerceIn(0, 7)
            buckets[7 - weeksAgo]++
        }
    }
    return buckets.toList()
}

@Composable
private fun LockedReport(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(18.dp)).background(Bekci.colors.amberSoft).padding(16.dp),
        ) { Icon(Icons.Outlined.BarChart, null, tint = Bekci.colors.amber) }
        Spacer(Modifier.height(15.dp))
        Text("Dolandırıcılık raporu Pro'da", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = Bekci.colors.text)
        Spacer(Modifier.height(6.dp))
        Text("Haftalık trend, kategori dağılımı ve en çok engellenen gönderenler.",
            fontSize = 13.sp, color = Bekci.colors.text3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Geri dön", onClick = onDone)
    }
}
