package tr.bekci.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tr.bekci.sms.SmsRole
import tr.bekci.ui.AppViewModel
import tr.bekci.ui.components.BekciCard
import tr.bekci.ui.components.PrimaryButton
import tr.bekci.ui.components.SectionLabel
import tr.bekci.ui.theme.Bekci
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tanılama — "mesaj geliyor mu?" sorusunun cevabı.
 *
 * Kullanıcı bir SMS filtresinin çalışıp çalışmadığını kendi başına test
 * edemez: kendisine spam göndermesi gerekir. Bu ekran onun yerine
 * zincirin her halkasını tek tek gösterir; bir şey çalışmıyorsa hangi
 * halkanın koptuğu görünür ve tahmin etmeye gerek kalmaz.
 */
@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshConversations() }

    fun izinVar(name: String) =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    val bildirimAcik = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val sonMesaj = vm.conversations.maxOfOrNull { it.lastAt }

    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)) {
                Text("Tanılama", style = MaterialTheme.typography.headlineMedium,
                    color = Bekci.colors.text)
                Text("Bekçi'nin çalışıp çalışmadığını buradan görebilirsiniz.",
                    fontSize = 12.5.sp, color = Bekci.colors.text3)
            }
        }

        item { SectionLabel("Kurulum") }
        item {
            BekciCard {
                Durum(
                    "Varsayılan mesaj uygulaması", SmsRole.isDefault(context),
                    olumlu = "Bekçi varsayılan — mesajlar buraya geliyor",
                    olumsuz = "Bekçi varsayılan DEĞİL — spam kutudan ayrılamaz",
                )
                Cizgi()
                Durum(
                    "SMS alma izni", izinVar(Manifest.permission.RECEIVE_SMS),
                    olumlu = "Verildi", olumsuz = "YOK — gelen mesaj görülemez",
                )
                Cizgi()
                Durum(
                    "Mesaj geçmişi okuma izni", izinVar(Manifest.permission.READ_SMS),
                    olumlu = "Verildi", olumsuz = "YOK — eski mesajlar listelenemez",
                )
                Cizgi()
                Durum(
                    "Bildirim izni",
                    bildirimAcik && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        izinVar(Manifest.permission.POST_NOTIFICATIONS)),
                    olumlu = "Açık", olumsuz = "KAPALI — mesaj gelince uyarı almazsınız",
                )
            }
        }

        item { SectionLabel("Okunan veri") }
        item {
            BekciCard {
                Satir("Görünen konuşma", "${vm.conversations.size}")
                Cizgi()
                Satir("Gelen kutusunda", "${vm.inboxThreads.size}")
                Cizgi()
                Satir("Spam'e ayrılan", "${vm.spamThreads.size}")
                Cizgi()
                Satir("Okunmamış", "${vm.unreadCount}")
                Cizgi()
                Satir(
                    "En son mesaj",
                    sonMesaj?.let {
                        SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(it))
                    } ?: "yok",
                )
            }
        }

        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Bildirimin gerçekten çalıştığını görmek için deneme bildirimi " +
                        "gönderebilirsiniz. Bu, sahte bir mesaj oluşturmaz — yalnızca " +
                        "bildirimi ve simge rozetini test eder.",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Bekci.colors.text2,
                )
                PrimaryButton("Deneme bildirimi gönder") { vm.sendTestNotification() }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Gerçek testi nasıl yaparsınız: kendi numaranıza bir SMS atın. " +
                        "Çoğu operatör buna izin verir. Mesaj birkaç saniye içinde " +
                        "yukarıdaki sayaçlarda ve Kutu ekranında görünmeli.",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Bekci.colors.text3,
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun Durum(baslik: String, tamam: Boolean, olumlu: String, olumsuz: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                .background(if (tamam) Bekci.colors.guardSoft else Bekci.colors.signalSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (tamam) "✓" else "!", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (tamam) Bekci.colors.guard else Bekci.colors.signal)
        }
        Column(Modifier.weight(1f)) {
            Text(baslik, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                color = Bekci.colors.text)
            Text(if (tamam) olumlu else olumsuz, fontSize = 11.5.sp, lineHeight = 17.sp,
                color = if (tamam) Bekci.colors.text3 else Bekci.colors.signal)
        }
    }
}

@Composable
private fun Satir(baslik: String, deger: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(baslik, fontSize = 13.sp, color = Bekci.colors.text)
        Text(deger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Bekci.colors.text2)
    }
}

@Composable
private fun Cizgi() = HorizontalDivider(Modifier.padding(start = 16.dp), color = Bekci.colors.line)
