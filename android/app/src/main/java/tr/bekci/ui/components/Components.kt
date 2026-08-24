package tr.bekci.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.bekci.core.FilterAction
import tr.bekci.core.FilterSubAction
import tr.bekci.core.Verdict
import tr.bekci.data.StoredMessage
import tr.bekci.ui.theme.Bekci
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale("tr")),
        style = MaterialTheme.typography.labelSmall,
        color = Bekci.colors.text3,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 9.dp),
    )
}

@Composable
fun BekciCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(Bekci.shapeMedium)
            .background(Bekci.colors.card)
            .border(1.dp, Bekci.colors.line, Bekci.shapeMedium),
        content = content,
    )
}

/**
 * Bugün ekranının kalbi. "0 yanlış işaretleme" rakamı bilinçli olarak
 * eşit ağırlıkta gösteriliyor — kullanıcının asıl korkusu spam görmek
 * değil, banka mesajını kaçırmaktır.
 */
@Composable
fun ShieldCard(sorted: Int, fraudBlocked: Int, falsePositives: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(Bekci.shapeLarge)
            .background(Brush.linearGradient(listOf(Color(0xFF0F6B4F), Color(0xFF0A4A37))))
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
    ) {
        Text("BU HAFTA AYIKLANAN",
            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
        Text("$sorted",
            fontSize = 46.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp, color = Color.White,
            modifier = Modifier.padding(top = 9.dp))
        Text("mesaj yerli yerine kondu",
            fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Color.White.copy(alpha = 0.18f))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("$fraudBlocked", "dolandırıcılık girişimi", Modifier.weight(1f))
            Stat("$falsePositives", "yanlış işaretleme", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(caption, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

// ── Kategori rozeti ─────────────────────────────────────────────────

@Composable
fun CategoryBadge(verdict: Verdict) {
    val label = when {
        verdict.isFraud -> "Dolandırıcılık"
        verdict.action == FilterAction.JUNK -> "Çöp"
        verdict.subAction == FilterSubAction.TRANSACTIONAL_FINANCE -> "Finans"
        verdict.subAction == FilterSubAction.TRANSACTIONAL_ORDERS -> "Kargo"
        verdict.subAction == FilterSubAction.TRANSACTIONAL_CARRIER -> "Operatör"
        verdict.subAction == FilterSubAction.PROMOTIONAL_OFFERS -> "Kampanya"
        verdict.subAction == FilterSubAction.PROMOTIONAL_COUPONS -> "Kupon"
        verdict.action == FilterAction.TRANSACTION -> "Bilgilendirme"
        verdict.action == FilterAction.PROMOTION -> "Kampanya"
        verdict.action == FilterAction.ALLOW -> "Güvenli"
        else -> "Sınıflandırılmadı"
    }
    Text(
        label.uppercase(Locale("tr")),
        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
        color = verdictTint(verdict),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(verdictSoft(verdict))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun verdictTint(v: Verdict): Color = when (v.action) {
    FilterAction.JUNK -> Bekci.colors.signal
    FilterAction.PROMOTION -> Bekci.colors.amber
    FilterAction.NONE -> Bekci.colors.text3
    else -> Bekci.colors.guard
}

@Composable
fun verdictSoft(v: Verdict): Color = when (v.action) {
    FilterAction.JUNK -> Bekci.colors.signalSoft
    FilterAction.PROMOTION -> Bekci.colors.amberSoft
    FilterAction.NONE -> Bekci.colors.line
    else -> Bekci.colors.guardSoft
}

// ── Gerekçe dökümü (ThreadScreen + MessageDetailScreen ortak) ────────

/**
 * Motorun bulduğu gerekçeler. BAŞ GEREKÇE HER ZAMAN ücretsiz gösterilir —
 * bir güvenlik uyarısının "neden" kısmını paywall'un arkasına koymak bu
 * ürünün amacıyla çelişir. Yalnızca ikinci ve sonraki sinyaller (motorun
 * gördüğü TÜM işaretler) Pro'da açılır.
 */
@Composable
fun ReasonsCard(verdict: Verdict, isPro: Boolean, onUpgrade: () -> Unit) {
    val reasons = verdict.reasons
    if (reasons.isEmpty()) return
    val visible = if (isPro) reasons else reasons.take(1)
    val hidden = reasons.size - visible.size

    SectionLabel(if (verdict.isFraud) "Neden şüpheli" else "Bekçi ne gördü")
    BekciCard {
        visible.forEachIndexed { index, reason ->
            ReasonRow(reason)
            if (index < visible.lastIndex || hidden > 0) {
                HorizontalDivider(Modifier.padding(start = 46.dp), color = Bekci.colors.line)
            }
        }
        if (hidden > 0) {
            SettingRow(
                icon = { Icon(Icons.Filled.Lock, null, tint = Bekci.colors.amber, modifier = Modifier.size(15.dp)) },
                title = "+$hidden diğer işaret",
                subtitle = "Tüm sinyalleri ve risk skorunu Pro'da gör",
                trailing = { Icon(Icons.Filled.ChevronRight, null, tint = Bekci.colors.text3, modifier = Modifier.size(16.dp)) },
                onClick = onUpgrade,
            )
        }
    }
}

@Composable
private fun ReasonRow(reason: tr.bekci.core.Reason) {
    Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape)
            .background(Bekci.colors.signalSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, null, tint = Bekci.colors.signal, modifier = Modifier.size(11.dp))
        }
        Column {
            Text(reason.title, fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
            Text(reason.detail, fontSize = 12.sp, lineHeight = 17.sp,
                color = Bekci.colors.text2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ── Mesaj satırı ────────────────────────────────────────────────────

@Composable
fun MessageRow(message: StoredMessage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(verdictSoft(message.verdict)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (message.verdict.isFraud) "!" else message.initials,
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                color = verdictTint(message.verdict),
            )
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.sender,
                    style = MaterialTheme.typography.labelLarge,
                    color = Bekci.colors.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                Text(timeLabel(message.receivedAt),
                    fontSize = 11.5.sp, fontWeight = FontWeight.Medium,
                    color = Bekci.colors.text3)
            }
            Text(message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.verdict.isFraud) Bekci.colors.signal else Bekci.colors.text2,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 6.dp)) { CategoryBadge(message.verdict) }
        }
    }
}

private fun timeLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    return when {
        now - millis < day -> SimpleDateFormat("HH:mm", Locale("tr")).format(Date(millis))
        now - millis < 2 * day -> "Dün"
        else -> SimpleDateFormat("d MMM", Locale("tr")).format(Date(millis))
    }
}

// ── Satır ve butonlar ───────────────────────────────────────────────

@Composable
fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(Bekci.colors.chip),
            contentAlignment = Alignment.Center,
        ) { icon() }

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Bekci.colors.text)
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = Bekci.colors.text3,
                    modifier = Modifier.padding(top = 1.dp))
            }
        }
        value?.let { Text(it, fontSize = 13.sp, color = Bekci.colors.text3) }
        trailing?.invoke()
    }
}

@Composable
fun PrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint ?: Bekci.colors.guard,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(vertical = 15.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Bekci.colors.card,
            contentColor = tint ?: Bekci.colors.text,
        ),
        contentPadding = PaddingValues(vertical = 13.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(title: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Bekci.colors.text else Bekci.colors.card)
            .then(if (selected) Modifier else Modifier.border(1.dp, Bekci.colors.line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val content = if (selected) Bekci.colors.paper else Bekci.colors.text2
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = content)
        count?.let {
            Text("$it", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                color = content.copy(alpha = 0.55f))
        }
    }
}
