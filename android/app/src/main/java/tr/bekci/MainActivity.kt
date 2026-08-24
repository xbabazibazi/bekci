package tr.bekci

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.bekci.sms.EXTRA_THREAD_ID
import tr.bekci.sms.SmsRole
import tr.bekci.sms.SpamDigest
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import tr.bekci.ui.AppViewModel
import tr.bekci.ui.InboxFilter
import tr.bekci.ui.screens.*
import tr.bekci.ui.theme.Bekci
import tr.bekci.ui.theme.BekciTheme

class BekciApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Günlük özet alarmı. Tekrar kurmak zararsız: aynı PendingIntent
        // kullanıldığı için mevcut alarmın yerine geçer, çoğalmaz.
        SpamDigest.schedule(this)
    }
}

class MainActivity : ComponentActivity() {

    /**
     * Bildirimden gelen konuşma kimliği. `mutableStateOf` olması bilinçli:
     * Compose tarafı bunu okuyup değiştiğinde otomatik tepki verebilsin.
     *
     * Soğuk başlangıçta [onCreate], sıcak başlangıçta (uygulama zaten
     * açıkken bildirime dokunulduğunda) [onNewIntent] doldurur —
     * `FLAG_ACTIVITY_SINGLE_TOP` sayesinde ikinci durumda `onCreate` TEKRAR
     * ÇAĞRILMAZ, bu yüzden ikisi de ayrı ayrı ele alınmalı.
     */
    private val pendingThreadId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingThreadId.value = threadIdFrom(intent)
        setContent { BekciTheme { BekciApp(pendingThreadId) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingThreadId.value = threadIdFrom(intent)
    }

    private fun threadIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_THREAD_ID, -1L)?.takeIf { it > 0 }
}

@Composable
private fun BekciApp(
    pendingThreadId: MutableState<Long?> = mutableStateOf(null),
    vm: AppViewModel = viewModel(),
) {
    val nav = rememberNavController()

    val context = LocalContext.current

    fun finishSetup() {
        vm.completeSetup()
        // Kullanıcının asıl işi mesajlarına bakmak — kurulum bitince
        // doğrudan Kutu'ya iniyoruz, ayrı bir "Bugün" ara durağı gerekmiyor.
        nav.navigate("inbox/ALL") { popUpTo("onboarding") { inclusive = true } }
    }

    // Bildirime dokunarak açılış: konuşma kimliği geldiyse doğrudan o
    // konuşmaya git. Soğuk başlangıçta ilk kompozisyonda, sıcak başlangıçta
    // (uygulama açıkken bildirime dokunulduğunda) onNewIntent'in
    // güncellediği anda tetiklenir. Bir kez tüketilince null'a çekiliyor ki
    // ekran döndürme gibi bir yeniden kompozisyonda tekrar navigate
    // edilmesin.
    LaunchedEffect(pendingThreadId.value) {
        val id = pendingThreadId.value ?: return@LaunchedEffect
        if (vm.setupDone) {
            nav.navigate("thread/$id") { launchSingleTop = true }
        }
        pendingThreadId.value = null
    }

    // Varsayılan SMS uygulaması rolü. Sonuç ne olursa olsun kuruluma devam
    // ediyoruz: kullanıcı reddederse Bekçi filtre kipinde çalışmayı sürdürür
    // (SmsReceiver), uygulamayı işlevsiz bırakmıyoruz.
    val roleRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { finishSetup() }

    // SMS ve bildirim izinleri birlikte isteniyor: kullanıcıyı iki ayrı
    // sistem penceresiyle yormamak için. İzinlerden sonra RIZA EKRANI
    // gelir; rol sistem penceresi ancak oradaki onaydan sonra açılır.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (SmsRole.isDefault(context)) finishSetup() else nav.navigate("consent")
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.hierarchy?.firstOrNull()?.route
    val showBar = vm.setupDone && route in setOf("today", "inbox/{filter}", "rules", "settings")

    Scaffold(
        containerColor = Bekci.colors.paper,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Bekci.colors.card) {
                    listOf(
                        Triple("today", "Bugün", Icons.Outlined.Shield),
                        Triple("inbox/ALL", "Kutu", Icons.Outlined.Inbox),
                        Triple("rules", "Kurallar", Icons.Outlined.Tune),
                        Triple("settings", "Ayarlar", Icons.Outlined.Settings),
                    ).forEach { (dest, label, icon) ->
                        NavigationBarItem(
                            selected = route?.startsWith(dest.substringBefore("/")) == true,
                            onClick = {
                                nav.navigate(dest) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Bekci.colors.guard,
                                selectedTextColor = Bekci.colors.guard,
                                indicatorColor = Bekci.colors.guardSoft,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // TEK NavHost. Kurulum bitince ikinci bir NavHost kurmak aynı
        // controller üzerinde setGraph'ı tekrar çağırır ve geri yığını
        // bozar; başlangıç hedefini koşullu seçiyoruz.
        //
        // Uygulama simgesine her dokunuşta açılış ekranı KUTU'dur, Bugün
        // değil — kullanıcının asıl işi mesajlarına bakmak. "Bugün" alt
        // sekmede durmaya devam ediyor, yalnızca varsayılan giriş değişti.
        NavHost(
            nav,
            startDestination = if (vm.setupDone) "inbox/ALL" else "onboarding",
            modifier = Modifier.padding(padding),
        ) {
            composable("onboarding") { OnboardingScreen { nav.navigate("setup") } }
            composable("setup") {
                SetupScreen(
                    onRequestPermission = {
                        // Varsayılan SMS uygulaması olarak ihtiyaç duyulan
                        // izinlerin TAMAMI tek seferde isteniyor. Parça parça
                        // sormak, kullanıcıyı arka arkaya pencerelerle yorar
                        // ve birini reddettiğinde uygulama yarım çalışır.
                        val wanted = buildList {
                            add(Manifest.permission.RECEIVE_SMS)   // gelen SMS
                            add(Manifest.permission.READ_SMS)      // geçmişi okuma
                            add(Manifest.permission.SEND_SMS)      // yanıt gönderme
                            add(Manifest.permission.RECEIVE_MMS)   // gelen MMS
                            add(Manifest.permission.READ_CONTACTS) // numara yerine isim
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissions.launch(wanted.toTypedArray())
                    },
                    onSkip = { finishSetup() },
                )
            }
            composable("consent") {
                ConsentScreen(
                    onAccept = {
                        // Rıza KAYDEDİLİR: hangi metne, ne zaman onay
                        // verildiği sonradan sorulabilmeli.
                        vm.recordConsent()
                        val intent = SmsRole.requestIntent(context)
                        if (intent != null) {
                            roleRequest.launch(intent)
                        } else {
                            // Cihaz rolü sunmuyor (ör. telefon donanımı yok).
                            // Sessizce yutmuyoruz: kurulum tamamlanır ve
                            // Ayarlar'daki durum satırı gerçeği söyler.
                            finishSetup()
                        }
                    },
                    onSkip = { finishSetup() },
                )
            }
            composable("today") {
                TodayScreen(
                    vm,
                    onOpen = { nav.navigate("message/$it") },
                    onRoute = { nav.navigate(it) },
                    onOpenThread = { nav.navigate("thread/$it") },
                )
            }
            composable("inbox/{filter}") { backStack ->
                val filter = runCatching {
                    InboxFilter.valueOf(backStack.arguments?.getString("filter") ?: "ALL")
                }.getOrDefault(InboxFilter.ALL)
                InboxScreen(
                    vm, filter,
                    onOpen = { nav.navigate("message/$it") },
                    onOpenThread = { nav.navigate("thread/$it") },
                )
            }
            composable("thread/{id}") { backStack ->
                val threadId = backStack.arguments?.getString("id")?.toLongOrNull()
                if (threadId == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    // Konuşma açılınca okundu işaretlenir; mesajlar yalnızca
                    // ilk kompozisyonda okunur, her yeniden çizimde değil.
                    val messages = remember(threadId) { vm.openThread(threadId) }
                    val conversation = remember(threadId) {
                        vm.conversations.firstOrNull { it.threadId == threadId }
                    }
                    val address = conversation?.address.orEmpty()
                    ThreadScreen(
                        conversation = conversation,
                        messages = messages,
                        isPro = vm.isPro,
                        onSend = { text -> vm.sendSms(address, text) },
                        // İşaretledikten sonra geri dönülüyor: konuşma
                        // spam/gelen kutusu arasında yer değiştirdiği için
                        // aynı ekranda kalmak eski hâli göstermeye devam
                        // ederdi.
                        onBlock = { vm.alwaysBlock(address); nav.popBackStack() },
                        onTrust = { vm.alwaysTrust(address); nav.popBackStack() },
                        onUpgrade = { nav.navigate("paywall") },
                    )
                }
            }
            composable("rules") { RulesScreen(vm) { nav.navigate(it) } }
            composable("settings") { SettingsScreen(vm) { nav.navigate(it) } }
            composable("message/{id}") { backStack ->
                val message = vm.message(backStack.arguments?.getString("id").orEmpty())
                if (message == null) {
                    // Kompozisyon sırasında navigasyon bir yan etkidir;
                    // doğrudan çağrılırsa yeniden kompozisyonda tekrarlanır.
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    MessageDetailScreen(vm, message,
                        onUpgrade = { nav.navigate("paywall") },
                        onDone = { nav.popBackStack() })
                }
            }
            composable("donate") {
                DonateScreen(onDonate = vm::donate) { nav.popBackStack() }
            }
            composable("paywall") { PaywallScreen(vm) { nav.popBackStack() } }
            composable("report") { ReportScreen(vm) { nav.popBackStack() } }
            // Uygulama İÇİ aydınlatma metni. Tarayıcıya bir adres açmak,
            // internet izni bile istemeyen bir üründe tutarsız olurdu.
            composable("privacy") { PrivacyScreen { nav.popBackStack() } }
            composable("diagnostics") { DiagnosticsScreen(vm) }
        }
    }
}
