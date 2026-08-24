package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import tr.bekci.BuildConfig
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.bekci.sms.SmsRole
import tr.bekci.core.FilterAction
import tr.bekci.core.Sensitivity
import tr.bekci.data.Conversation
import tr.bekci.data.StoredMessage
import tr.bekci.ui.AppViewModel
import tr.bekci.ui.InboxFilter
import tr.bekci.ui.components.*
import tr.bekci.ui.theme.Bekci
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Bugün ───────────────────────────────────────────────────────────

@Composable
fun TodayScreen(
    vm: AppViewModel,
    onOpen: (String) -> Unit,
    onRoute: (String) -> Unit,
    onOpenThread: (Long) -> Unit = {},
) {
    // Mesaj uygulama kapalıyken gelir; ekran göründüğünde tazelenmeli.
    // Uygulama AÇIKKEN gelenler ise ContentObserver ile anında düşüyor.
    LaunchedEffect(Unit) { vm.refreshConversations() }

    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
                Text("Bugün", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text)
                Text(SimpleDateFormat("d MMMM, EEEE", Locale("tr")).format(Date()),
                    fontSize = 12.5.sp, color = Bekci.colors.text3)
            }
            ShieldCard(vm.sortedThisWeek, vm.fraudBlocked, vm.falsePositives)
        }

        if (vm.needsAttention.isNotEmpty()) {
            item { SectionLabel("Dikkat isteyen") }
            item {
                BekciCard {
                    vm.needsAttention.forEachIndexed { index, c ->
                        ConversationRow(c) { onOpenThread(c.threadId) }
                        if (index < vm.needsAttention.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 70.dp), color = Bekci.colors.line)
                        }
                    }
                }
            }
        }

        item { SectionLabel("Kategori dağılımı") }
        item {
            val counts = vm.categoryCounts()
            BekciCard {
                counts.forEachIndexed { index, (filter, count) ->
                    SettingRow(
                        icon = { Icon(iconFor(filter), null, tint = tintFor(filter), modifier = Modifier.size(17.dp)) },
                        title = filter.title,
                        value = "$count",
                        trailing = { Icon(Icons.Filled.ChevronRight, null, tint = Bekci.colors.text3, modifier = Modifier.size(16.dp)) },
                        onClick = { onRoute("inbox/${filter.name}") },
                    )
                    if (index < counts.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                    }
                }
            }
        }

        item { SectionLabel("Bekçi'yi güçlendir") }
        item {
            BekciCard {
                SettingRow(
                    icon = { Icon(Icons.Outlined.FavoriteBorder, null, tint = Bekci.colors.guard, modifier = Modifier.size(17.dp)) },
                    title = "Spam bağışla",
                    subtitle = "Yakalayamadığımız bir mesajı gönder, model gelişsin",
                    onClick = { onRoute("donate") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.BarChart, null, tint = Bekci.colors.amber, modifier = Modifier.size(17.dp)) },
                    title = "Dolandırıcılık raporu",
                    subtitle = "Haftalık trend, en çok engellenenler",
                    value = if (vm.isPro) null else "PRO",
                    onClick = { onRoute(if (vm.isPro) "report" else "paywall") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = Bekci.colors.amber, modifier = Modifier.size(17.dp)) },
                    title = "Bekçi Pro",
                    subtitle = if (vm.isPro) "Etkin · teşekkürler" else "Deneme sürüyor",
                    onClick = { onRoute("paywall") },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun iconFor(filter: InboxFilter) = when (filter) {
    InboxFilter.FINANCE -> Icons.Outlined.AccountBalance
    InboxFilter.ORDERS -> Icons.Outlined.LocalShipping
    InboxFilter.CARRIER -> Icons.Outlined.CellTower
    InboxFilter.PROMO -> Icons.Outlined.LocalOffer
    InboxFilter.JUNK -> Icons.Outlined.Warning
    InboxFilter.ALL -> Icons.Outlined.Inbox
}

@Composable
private fun tintFor(filter: InboxFilter) = when (filter) {
    InboxFilter.PROMO -> Bekci.colors.amber
    InboxFilter.JUNK -> Bekci.colors.signal
    else -> Bekci.colors.guard
}

// ── Kutu ────────────────────────────────────────────────────────────

/**
 * Kutu iki kipte çalışır:
 *
 * - **Varsayılan mesaj uygulamasıyken** telefonun GERÇEK konuşmaları
 *   gösterilir ve spam ana listeden ayrılır. Kullanıcının beklediği budur.
 * - **Değilken** Bekçi'nin kendi kaydettiği mesajlar kategori filtreleriyle
 *   listelenir; gelen kutusuna karışma yetkisi olmadığı için ayırma yapılamaz.
 */
@Composable
fun InboxScreen(
    vm: AppViewModel,
    initial: InboxFilter,
    onOpen: (String) -> Unit,
    onOpenThread: (Long) -> Unit = {},
) {
    // Mesaj uygulama kapalıyken de gelir; ekran her göründüğünde tazelenmeli.
    LaunchedEffect(Unit) { vm.refreshConversations() }

    if (vm.isDefaultSms) {
        ConversationInbox(vm, onOpenThread)
    } else {
        LegacyInbox(vm, initial, onOpen)
    }
}

/** Gerçek gelen kutusu — sistem sağlayıcısından, spam ayrılmış. */
@Composable
private fun ConversationInbox(vm: AppViewModel, onOpenThread: (Long) -> Unit) {
    var showSpam by remember { mutableStateOf(false) }
    val inbox = vm.inboxThreads
    val spam = vm.spamThreads

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)) {
            Text("Mesajlar", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text)
            Text(
                if (spam.isEmpty()) "${inbox.size} konuşma"
                else "${inbox.size} konuşma · ${spam.size} spam ayrıldı",
                fontSize = 12.5.sp, color = Bekci.colors.text3,
            )
        }

        if (inbox.isEmpty() && spam.isEmpty()) {
            EmptyInbox()
            return@Column
        }

        LazyColumn {
            if (spam.isNotEmpty()) {
                item {
                    // Spam ayrı ve KAPALI başlar: ana listeyi kirletmez ama
                    // gizlenmiş de değil — kullanıcı ne ayrıldığını görebilmeli.
                    BekciCard {
                        SettingRow(
                            icon = {
                                Icon(Icons.Outlined.Warning, null, tint = Bekci.colors.signal,
                                    modifier = Modifier.size(17.dp))
                            },
                            title = "Spam ve dolandırıcılık",
                            subtitle = "${spam.size} konuşma ana listeden ayrıldı",
                            value = if (showSpam) "Gizle" else "Göster",
                            onClick = { showSpam = !showSpam },
                        )
                    }
                }
                if (showSpam) {
                    item {
                        BekciCard {
                            spam.forEachIndexed { index, c ->
                                ConversationRow(c) { onOpenThread(c.threadId) }
                                if (index < spam.lastIndex) {
                                    HorizontalDivider(Modifier.padding(start = 70.dp), color = Bekci.colors.line)
                                }
                            }
                        }
                    }
                }
            }

            if (inbox.isNotEmpty()) {
                item { SectionLabel("Gelen kutusu") }
                item {
                    BekciCard {
                        inbox.forEachIndexed { index, c ->
                            ConversationRow(c) { onOpenThread(c.threadId) }
                            if (index < inbox.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 70.dp), color = Bekci.colors.line)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Spam mesajlar silinmez, yalnızca ana listeden ayrılır. " +
                        "Yanlış ayrıldığını düşündüğünüz bir konuşmayı açıp “Her zaman güven” diyebilirsiniz.",
                    fontSize = 11.5.sp, color = Bekci.colors.text3,
                    modifier = Modifier.padding(horizontal = 34.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    // Kırmızı vurgu, ayırma ölçütüyle AYNI olmalı (`action == JUNK`).
    // `isFraud` kullanılsaydı sıkı modda ayrılmış bir konuşma spam
    // bölümünde yeşil görünürdü.
    val isJunk = c.verdict.action == FilterAction.JUNK
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isJunk) Bekci.colors.signalSoft else Bekci.colors.guardSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(c.initials, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (isJunk) Bekci.colors.signal else Bekci.colors.guard)
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.address, fontSize = 14.sp,
                    fontWeight = if (c.unread) FontWeight.Bold else FontWeight.SemiBold,
                    color = Bekci.colors.text, maxLines = 1, modifier = Modifier.weight(1f))
                Text(
                    SimpleDateFormat("d MMM", Locale("tr")).format(Date(c.lastAt)),
                    fontSize = 11.sp, color = Bekci.colors.text3,
                )
            }
            Text(c.lastBody, fontSize = 12.5.sp, maxLines = 2,
                color = if (isJunk) Bekci.colors.signal else Bekci.colors.text2)
            if (c.verdict.action != FilterAction.NONE) {
                Spacer(Modifier.height(4.dp))
                CategoryBadge(c.verdict)
            }
        }
    }
}

@Composable
private fun EmptyInbox() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Bekci.colors.guardSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.VerifiedUser, null, tint = Bekci.colors.guard) }
        Spacer(Modifier.height(15.dp))
        Text("Mesaj görünmüyor", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = Bekci.colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "Mesaj geçmişinizi okuyabilmek için SMS iznine ihtiyaç var. " +
                "Ayarlar › Uygulama izinleri üzerinden verebilirsiniz.",
            fontSize = 13.sp, color = Bekci.colors.text3,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@Composable
private fun LegacyInbox(vm: AppViewModel, initial: InboxFilter, onOpen: (String) -> Unit) {
    var filter by remember { mutableStateOf(initial) }
    val items = vm.messages(filter)

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
            Text("Kutu", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text)
            Text("${vm.messages.size} mesaj", fontSize = 12.5.sp, color = Bekci.colors.text3)
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            InboxFilter.entries.forEach { option ->
                Chip(option.title, vm.messages(option).size, option == filter) { filter = option }
            }
        }

        if (items.isEmpty()) {
            EmptyState(filter)
        } else {
            LazyColumn {
                item {
                    BekciCard {
                        items.forEachIndexed { index, message ->
                            MessageRow(message) { onOpen(message.id) }
                            if (index < items.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 70.dp), color = Bekci.colors.line)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Bekçi mesajlarınızı silmez veya taşımaz. Android'de mesajı gelen kutusundan " +
                            "ayıklamak yalnızca varsayılan mesaj uygulamasının yetkisindedir.",
                        fontSize = 11.5.sp, color = Bekci.colors.text3,
                        modifier = Modifier.padding(horizontal = 34.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filter: InboxFilter) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Bekci.colors.guardSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.VerifiedUser, null, tint = Bekci.colors.guard) }
        Spacer(Modifier.height(15.dp))
        Text(if (filter == InboxFilter.JUNK) "Hiç çöp yok" else "Burada bir şey yok",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
        Spacer(Modifier.height(6.dp))
        Text(if (filter == InboxFilter.JUNK) "Bu kategoriye düşen bir mesaj olmadı."
             else "Yeni mesajlar geldikçe burada görünecek.",
            fontSize = 13.sp, color = Bekci.colors.text3)
    }
}

// ── Mesaj detayı ────────────────────────────────────────────────────

@Composable
fun MessageDetailScreen(vm: AppViewModel, message: StoredMessage, onUpgrade: () -> Unit, onDone: () -> Unit) {
    val v = message.verdict

    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            if (v.isFraud) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(Bekci.shapeLarge).background(Bekci.colors.signal)
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp))
                        Text("YÜKSEK RİSK", style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f))
                    }
                    Text("Bu bir dolandırıcılık girişimi",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(top = 6.dp))
                    Text(headline(v.reasons.firstOrNull()?.code),
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.93f),
                        modifier = Modifier.padding(top = 5.dp))
                }
            } else {
                Row(Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                        .background(verdictSoft(v)), contentAlignment = Alignment.Center) {
                        Text(message.initials, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = verdictTint(v))
                    }
                    Column {
                        Text(message.sender, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            color = Bekci.colors.text)
                        Spacer(Modifier.height(4.dp))
                        CategoryBadge(v)
                    }
                }
            }
        }

        item {
            SelectionContainer {
                Text(message.body,
                    fontSize = 14.5.sp, lineHeight = 21.sp, color = Bekci.colors.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (v.isFraud) Bekci.colors.signalSoft else Bekci.colors.card)
                        .border(1.dp, Bekci.colors.line, RoundedCornerShape(18.dp))
                        .padding(15.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.sender, fontSize = 11.5.sp, color = Bekci.colors.text3)
                Text(SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(message.receivedAt)),
                    fontSize = 11.5.sp, color = Bekci.colors.text3)
            }
        }

        if (v.isFraud) {
            item { ReasonsCard(v, vm.isPro, onUpgrade) }
        } else {
            item { SectionLabel("Bekçi ne gördü") }
            item {
                BekciCard {
                    Evidence("Gönderen tipi", v.senderKind.label)
                    HorizontalDivider(Modifier.padding(horizontal = 15.dp), color = Bekci.colors.line)
                    // Risk skoru sayısal değeri Pro'da: teknik meraklısı için
                    // bir ayrıntı, güvenlik kararının kendisi değil — karar
                    // zaten kategori rozetinde herkese açık gösteriliyor.
                    if (vm.isPro) {
                        Evidence("Risk skoru", "${v.risk} / 100")
                    } else {
                        SettingRow(
                            icon = { Icon(Icons.Filled.Lock, null, tint = Bekci.colors.amber, modifier = Modifier.size(15.dp)) },
                            title = "Risk skoru",
                            subtitle = "Pro'da gör",
                            onClick = onUpgrade,
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 15.dp), color = Bekci.colors.line)
                    Evidence("Karar", v.action.raw +
                        if (v.subAction.raw == "none") "" else " · ${v.subAction.raw}")
                }
            }
        }

        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (v.isFraud) {
                    PrimaryButton("Bu göndereni engelle", tint = Bekci.colors.signal) {
                        vm.alwaysBlock(message.sender); onDone()
                    }
                    TextButton({ vm.reportFalsePositive(message); onDone() },
                        Modifier.fillMaxWidth()) {
                        Text("Yanlış işaretlendi, bu güvenli",
                            fontSize = 13.5.sp, color = Bekci.colors.text3)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SecondaryButton("Her zaman güven", Modifier.weight(1f)) {
                            vm.alwaysTrust(message.sender); onDone()
                        }
                        SecondaryButton("Spam bildir", Modifier.weight(1f), Bekci.colors.signal) {
                            vm.alwaysBlock(message.sender); onDone()
                        }
                    }
                }
            }
        }
    }
}

/** Genel bir "bu spam" uyarısı ikna etmiyor; somut olan ediyor. */
private fun headline(topReasonCode: String?): String = when (topReasonCode) {
    "codeHarvest" -> "Doğrulama kodunuzu istiyor. Hiçbir kurum bunu yapmaz — kodu kimseyle paylaşmayın."
    "impersonation" -> "Resmî bir kurum size SMS ile bağlantı göndermez. Bağlantıya dokunmayın."
    "gambling" -> "Yasa dışı bahis reklamı. Türkiye'de bu siteler suç konusudur."
    "prize" -> "Kazandığınız bir ödül yok. Bu kalıp, bilgilerinizi almak için kullanılıyor."
    else -> "Bu mesajdaki bağlantıya dokunmayın ve içindeki numarayı aramayın."
}

@Composable
private fun Evidence(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontSize = 12.sp, color = Bekci.colors.text3)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text2)
    }
}

// ── Kurallar ────────────────────────────────────────────────────────

@Composable
fun RulesScreen(vm: AppViewModel, onRoute: (String) -> Unit = {}) {
    var showKeywordDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var keywordLimitHit by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importDraft by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                Text("Kurallar", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text)
                Text("Model kararlarını sizin kurallarınız ezer. Buraya eklediğiniz bir gönderen veya kelime, risk skorundan bağımsız uygulanır.",
                    fontSize = 13.sp, lineHeight = 19.sp, color = Bekci.colors.text2,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }

        item { SectionLabel("Her zaman güven · ${vm.rules.allowSenders.size}") }
        item {
            BekciCard {
                RuleList(vm.rules.allowSenders.sorted(), Icons.Filled.Check,
                    Bekci.colors.guard, Bekci.colors.guardSoft) { vm.removeRule(it) }
            }
        }

        item { SectionLabel("Her zaman engelle · ${vm.rules.blockSenders.size}") }
        item {
            BekciCard {
                RuleList(vm.rules.blockSenders.sorted(), Icons.Filled.Close,
                    Bekci.colors.signal, Bekci.colors.signalSoft) { vm.removeRule(it) }
            }
        }

        item {
            SectionLabel(
                "Engellenen kelimeler · ${vm.rules.blockKeywords.size}" +
                    if (vm.isPro) "" else " / 12",
            )
        }
        item {
            BekciCard {
                RuleList(vm.rules.blockKeywords.sorted(), Icons.Filled.Close,
                    Bekci.colors.signal, Bekci.colors.signalSoft,
                    subtitle = "Anahtar kelime") { vm.removeBlockedKeyword(it) }
                if (!vm.isPro && vm.keywordLimitReached) {
                    SettingRow(
                        icon = { Icon(Icons.Filled.Lock, null, tint = Bekci.colors.amber, modifier = Modifier.size(15.dp)) },
                        title = "Kelime limitine ulaştınız",
                        subtitle = "Sınırsız kelime için Pro'ya geçin",
                        onClick = { onRoute("paywall") },
                    )
                } else {
                    SettingRow(
                        icon = { Icon(Icons.Filled.Add, null, tint = Bekci.colors.guard, modifier = Modifier.size(17.dp)) },
                        title = "Kelime ekle",
                        onClick = { showKeywordDialog = true },
                    )
                }
            }
        }

        item { SectionLabel("Yedekleme") }
        item {
            BekciCard {
                SettingRow(
                    icon = {
                        Icon(
                            if (vm.isPro) Icons.Outlined.IosShare else Icons.Filled.Lock, null,
                            tint = if (vm.isPro) Bekci.colors.guard else Bekci.colors.amber,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                    title = "Kuralları dışa aktar",
                    subtitle = "Yeni telefona veya bir aile üyesine gönderin. Sunucu yok, dosya olarak paylaşılır.",
                    value = if (vm.isPro) null else "PRO",
                    onClick = {
                        if (vm.isPro) {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, vm.exportRules())
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Bekçi kuralları")
                            }
                            context.startActivity(android.content.Intent.createChooser(send, "Kuralları paylaş"))
                        } else {
                            onRoute("paywall")
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = {
                        Icon(
                            if (vm.isPro) Icons.Outlined.Publish else Icons.Filled.Lock, null,
                            tint = if (vm.isPro) Bekci.colors.guard else Bekci.colors.amber,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                    title = "Kuralları içe aktar",
                    subtitle = "Paylaşılan bir yedeği yapıştırın; mevcut kurallarınızla birleşir.",
                    value = if (vm.isPro) null else "PRO",
                    onClick = { if (vm.isPro) showImportDialog = true else onRoute("paywall") },
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        item { SectionLabel("Duyarlılık") }
        item {
            BekciCard {
                Sensitivity.entries.forEachIndexed { index, level ->
                    SettingRow(
                        icon = {
                            Icon(
                                when (level) {
                                    Sensitivity.CAREFUL -> Icons.Outlined.Shield
                                    Sensitivity.BALANCED -> Icons.Outlined.Tune
                                    Sensitivity.STRICT -> Icons.Outlined.Warning
                                },
                                null,
                                tint = if (vm.rules.sensitivity == level) Bekci.colors.guard else Bekci.colors.text3,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        title = level.title,
                        subtitle = level.subtitle,
                        trailing = {
                            if (vm.rules.sensitivity == level) {
                                Icon(Icons.Filled.Check, null, tint = Bekci.colors.guard,
                                    modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { vm.setSensitivity(level) },
                    )
                    if (index < Sensitivity.entries.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }

    if (showKeywordDialog) {
        AlertDialog(
            onDismissRequest = { showKeywordDialog = false; draft = "" },
            title = { Text("Engellenecek kelime") },
            text = {
                Column {
                    Text("Bu kelimeyi içeren her mesaj çöpe gider. Türkçe karakter farkı dikkate alınmaz.",
                        fontSize = 13.sp, color = Bekci.colors.text2)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(draft, { draft = it },
                        placeholder = { Text("örn. bahis") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    if (vm.addBlockedKeyword(draft)) {
                        draft = ""; showKeywordDialog = false
                    } else {
                        keywordLimitHit = true
                    }
                }) { Text("Ekle") }
            },
            dismissButton = {
                TextButton({ draft = ""; showKeywordDialog = false }) { Text("Vazgeç") }
            },
        )
    }

    if (keywordLimitHit) {
        AlertDialog(
            onDismissRequest = { keywordLimitHit = false },
            title = { Text("Kelime limitine ulaştınız") },
            text = { Text("Ücretsiz sürümde en fazla 12 engellenen kelime eklenebilir. Sınırsız kelime için Pro'ya geçin.") },
            confirmButton = {
                TextButton({ keywordLimitHit = false; showKeywordDialog = false; onRoute("paywall") }) {
                    Text("Pro'ya geç")
                }
            },
            dismissButton = { TextButton({ keywordLimitHit = false }) { Text("Vazgeç") } },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importDraft = ""; importError = false },
            title = { Text("Kuralları içe aktar") },
            text = {
                Column {
                    Text("Paylaşılan yedek metnini buraya yapıştırın. Mevcut kurallarınız korunur, yenileri eklenir.",
                        fontSize = 13.sp, color = Bekci.colors.text2)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(importDraft, { importDraft = it; importError = false },
                        placeholder = { Text("Yedek metnini yapıştırın") })
                    if (importError) {
                        Text("Bu metin okunamadı. Bekçi'den dışa aktarılmış bir yedek olduğundan emin olun.",
                            fontSize = 11.5.sp, color = Bekci.colors.signal,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton({
                    if (vm.importRules(importDraft)) {
                        showImportDialog = false; importDraft = ""; importError = false
                    } else {
                        importError = true
                    }
                }) { Text("İçe aktar") }
            },
            dismissButton = {
                TextButton({ showImportDialog = false; importDraft = ""; importError = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun ColumnScope.RuleList(
    keys: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    soft: Color,
    subtitle: String = "Gönderen",
    onRemove: (String) -> Unit,
) {
    if (keys.isEmpty()) {
        Text("Henüz kural yok. Bir mesajı açıp “Her zaman güven” veya “Spam bildir” diyerek ekleyebilirsiniz.",
            fontSize = 12.sp, lineHeight = 17.sp, color = Bekci.colors.text3,
            modifier = Modifier.padding(15.dp))
        return
    }
    keys.forEachIndexed { index, key ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(soft),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(key, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
                Text(subtitle, fontSize = 11.5.sp, color = Bekci.colors.text3)
            }
            Box(Modifier.size(26.dp).clip(CircleShape)
                .background(Bekci.colors.chip)
                .clickable { onRemove(key) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Kaldır", tint = Bekci.colors.text3,
                    modifier = Modifier.size(13.dp))
            }
        }
        if (index < keys.lastIndex) {
            HorizontalDivider(Modifier.padding(start = 57.dp), color = Bekci.colors.line)
        }
    }
}

// ── Ayarlar ─────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(vm: AppViewModel, onRoute: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 14.dp))

            // Gizlilik iddiası en üstte: ürünün ana satış argümanı burada
            // kanıtlanıyor, dipnota gömülmüyor.
            BekciCard {
                Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Memory, null, tint = Bekci.colors.guard,
                        modifier = Modifier.size(22.dp))
                    Column {
                        Text("Çevrimdışı çalışıyor", style = MaterialTheme.typography.labelLarge,
                            color = Bekci.colors.guard)
                        // Ölçülen bir sayı değil, mimari bir gerçek:
                        // manifest'te INTERNET izni yok, ağ API'si kullanılmıyor.
                        Text("İnternet izni istenmiyor", fontSize = 11.5.sp, color = Bekci.colors.text3)
                    }
                }
            }
        }

        item { SectionLabel("Filtre") }
        item {
            val context = LocalContext.current
            val isDefault = SmsRole.isDefault(context)
            BekciCard {
                // Ürünün en belirleyici ayarı: varsayılan uygulama DEĞİLKEN
                // Bekçi spam'i gelen kutusundan ayıklayamaz, yalnızca
                // etiketler. Kullanıcının bunu buradan görebilmesi ve tek
                // dokunuşla değiştirebilmesi gerekiyor.
                SettingRow(
                    icon = {
                        Icon(
                            if (isDefault) Icons.Outlined.Shield else Icons.Outlined.Warning, null,
                            tint = if (isDefault) Bekci.colors.guard else Bekci.colors.amber,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                    title = if (isDefault) "Varsayılan mesaj uygulaması" else "Bekçi varsayılan değil",
                    subtitle = if (isDefault) "Spam gelen kutusundan ayrılıyor"
                    else "Spam yalnızca etiketleniyor, kutudan ayrılmıyor",
                    value = if (isDefault) null else "Değiştir",
                    // Sistem penceresini DOĞRUDAN açmıyoruz: kullanıcı MMS/RCS
                    // bedelini görmeden onaylamamalı. Kurulumdaki rıza ekranının
                    // aynısına yönlendiriliyor.
                    onClick = if (isDefault) null else fun() { onRoute("consent") },
                )
                // Ürünün kapsamı kalıcı olarak görünür olmalı: kullanıcı
                // aylar sonra "resimli mesajım nerede" dediğinde cevabı
                // burada bulmalı, destek aramamalı.
                if (isDefault) {
                    HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                    SettingRow(
                        icon = {
                            Icon(Icons.Outlined.Info, null, tint = Bekci.colors.text2,
                                modifier = Modifier.size(17.dp))
                        },
                        title = "Yalnızca SMS",
                        subtitle = "Resimli mesaj (MMS) gösterilmez; geldiğinde bildirilir",
                    )
                }
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.Tune, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Duyarlılık", value = vm.rules.sensitivity.title,
                    onClick = { onRoute("rules") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.Notifications, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Dolandırıcılık bildirimi", subtitle = "Yüksek riskte anında uyar",
                    trailing = {
                        Switch(vm.fraudNotifications, { vm.toggleFraudNotifications(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Bekci.colors.guard))
                    },
                )
            }
        }

        item { SectionLabel("Gizlilik") }
        item {
            BekciCard {
                SettingRow(
                    icon = { Icon(Icons.Outlined.Memory, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Cihaz içi işleme", subtitle = "Kapatılamaz — mimarinin parçası",
                    trailing = { Icon(Icons.Filled.Lock, null, tint = Bekci.colors.guard, modifier = Modifier.size(16.dp)) },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.Delete, null, tint = Bekci.colors.signal, modifier = Modifier.size(17.dp)) },
                    title = "Saklanan mesajları sil",
                    subtitle = if (vm.storageAvailable)
                        "Cihazda şifreli olarak ${vm.messages.size} mesaj tutuluyor"
                    else "Şifreli depo kurulamadı — mesaj saklanmıyor",
                    onClick = { vm.clearStoredMessages() },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.Policy, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Aydınlatma metni ve KVKK",
                    trailing = { Icon(Icons.Filled.ChevronRight, null, tint = Bekci.colors.text3, modifier = Modifier.size(16.dp)) },
                    onClick = { onRoute("privacy") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.Info, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Tanılama",
                    subtitle = "Bekçi çalışıyor mu, neresi eksik",
                    trailing = { Icon(Icons.Filled.ChevronRight, null, tint = Bekci.colors.text3, modifier = Modifier.size(16.dp)) },
                    onClick = { onRoute("diagnostics") },
                )
            }
        }

        item { SectionLabel("Bekçi") }
        item {
            val context = LocalContext.current
            BekciCard {
                SettingRow(
                    icon = { Icon(Icons.Outlined.AutoAwesome, null, tint = Bekci.colors.amber, modifier = Modifier.size(17.dp)) },
                    title = "Bekçi Pro",
                    subtitle = if (vm.isPro) "Etkin · teşekkürler" else "Deneme sürüyor",
                    onClick = { onRoute("paywall") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.FavoriteBorder, null, tint = Bekci.colors.guard, modifier = Modifier.size(17.dp)) },
                    title = "Spam bağışla",
                    onClick = { onRoute("donate") },
                )
                HorizontalDivider(Modifier.padding(start = 59.dp), color = Bekci.colors.line)
                SettingRow(
                    icon = { Icon(Icons.Outlined.MailOutline, null, tint = Bekci.colors.text2, modifier = Modifier.size(17.dp)) },
                    title = "Destek",
                    subtitle = if (vm.isPro) "Öncelikli e-posta desteği" else "E-posta ile ulaşın",
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:destek@bekci.tr")
                            putExtra(android.content.Intent.EXTRA_SUBJECT,
                                if (vm.isPro) "Bekçi Pro destek" else "Bekçi destek")
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            // Sürüm ELLE yazılmaz: `build.gradle.kts`'teki versionName
            // okunur, yoksa ekran gerçek sürümden sapar (bir dönem 0.1.0
            // gömülüydü ve uygulama 0.2.0 iken de öyle görünüyordu).
            Text(
                "Bekçi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                fontSize = 11.5.sp, color = Bekci.colors.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            )
        }
    }
}
