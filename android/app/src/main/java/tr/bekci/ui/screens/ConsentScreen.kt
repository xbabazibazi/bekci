package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.bekci.ui.components.BekciCard
import tr.bekci.ui.components.PrimaryButton
import tr.bekci.ui.components.SectionLabel
import tr.bekci.ui.theme.Bekci

/**
 * Varsayılan mesaj uygulaması olma RIZA EKRANI.
 *
 * Neden ayrı bir ekran: Bekçi'yi varsayılan yapmak, kullanıcının günlük
 * mesajlaşma deneyimini değiştiren geri dönüşü olan bir karardır (MMS ve
 * RCS kaybı). Sistem penceresi bunu "Bekçi'yi varsayılan SMS uygulaması
 * yap?" diye tek satırda sorar — bedeli söylemez. Söylemek bize düşer.
 *
 * ## Neden otomatik yapamıyoruz
 *
 * Android, bir uygulamanın kendini varsayılan SMS uygulaması yapmasına
 * İZİN VERMEZ. `RoleManager.createRequestRoleIntent(ROLE_SMS)` her zaman
 * bir sistem penceresi açar ve onayı kullanıcı verir. Bu bilinçli bir
 * güvenlik sınırı: aksi hâlde kötü niyetli bir uygulama sessizce tüm
 * SMS'leri (banka doğrulama kodları dahil) ele geçirebilirdi.
 *
 * Bu ekran o sistem penceresini KALDIRMAZ, ona hazırlar: kullanıcı ne
 * onayladığını bilerek tek dokunuşta geçer.
 *
 * ## Play politikası
 *
 * SMS izinleri hassas kabul edilir. "Varsayılan SMS işleyicisi" onaylı bir
 * kullanımdır ama Play Console'da Permissions Declaration Form ile beyan
 * edilmelidir. Buradaki açık anlatım, o beyanın ekrandaki karşılığıdır:
 * kullanıcı neyi neden verdiğini görüyor.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit, onSkip: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            Column(Modifier.padding(20.dp)) {
                Text(
                    "Bekçi mesaj uygulamanız olacak",
                    fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    color = Bekci.colors.text, lineHeight = 31.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Spam'i gelen kutunuzdan gerçekten ayırabilmenin tek yolu bu. " +
                        "Devam etmeden önce neyin değişeceğini bilmelisiniz.",
                    fontSize = 13.5.sp, lineHeight = 20.sp, color = Bekci.colors.text2,
                )
            }

            Madde(
                "Mesajlarınız Bekçi'ye gelecek",
                "SMS'ler artık Bekçi'de görünür. Mevcut mesaj geçmişiniz olduğu " +
                    "gibi kalır ve listede görünmeye devam eder.",
                Bekci.colors.guard,
            )
            Madde(
                "Spam ana listeden ayrılacak",
                "Dolandırıcılık ve spam mesajlar ayrı bir bölüme gider. " +
                    "SİLİNMEZ — istediğiniz zaman bakabilir, yanlış ayrılanı geri alabilirsiniz.",
                Bekci.colors.guard,
            )
            Madde(
                "Resimli mesajlar (MMS) çalışmaz",
                "Bekçi yalnızca SMS destekler. Size resimli mesaj gönderilirse " +
                    "bildirim alırsınız ama açamazsınız.",
                Bekci.colors.signal,
            )
            Madde(
                "RCS özellikleri kapanır",
                "Okundu bilgisi, “yazıyor” göstergesi ve yüksek çözünürlüklü medya " +
                    "gibi RCS özellikleri üçüncü taraf uygulamalarda çalışmaz.",
                Bekci.colors.signal,
            )
            Madde(
                "İstediğiniz zaman geri alabilirsiniz",
                "Ayarlar › Varsayılan uygulamalar üzerinden eski uygulamanıza " +
                    "dönebilirsiniz. Mesajlarınız kaybolmaz.",
                Bekci.colors.guard,
            )

            SectionLabel("Verileriniz")
            BekciCard {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "Bekçi'nin sunucusu yoktur ve internet izni bile istemez. " +
                            "Mesajlarınız sınıflandırma dahil tamamen telefonunuzun içinde " +
                            "işlenir, hiçbir yere gönderilmez. Hesap açmanız istenmez.",
                        fontSize = 12.sp, lineHeight = 18.sp, color = Bekci.colors.text2,
                    )
                    Text(
                        "Ayrıntılı bilgi: Ayarlar › Aydınlatma metni ve KVKK",
                        fontSize = 11.5.sp, color = Bekci.colors.text3,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Onay kutusu. Buton yalnızca işaretlendiğinde etkinleşir —
            // "okumadan geç" akışını bilinçli olarak engelliyoruz.
            Box(Modifier.padding(horizontal = 20.dp)) {
                BekciCard {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { accepted = !accepted }
                            .padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                .background(if (accepted) Bekci.colors.guard else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (accepted) {
                                Icon(Icons.Filled.Check, null, tint = Color.White,
                                    modifier = Modifier.size(15.dp))
                            } else {
                                Box(
                                    Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                        .background(Bekci.colors.line)
                                )
                            }
                        }
                        Text(
                            "Yukarıdakileri okudum ve anladım. Bekçi'nin varsayılan mesaj " +
                                "uygulamam olmasını kabul ediyorum.",
                            fontSize = 13.sp, lineHeight = 19.sp, color = Bekci.colors.text,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        HorizontalDivider(color = Bekci.colors.line)

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Onaylıyorum, devam et", enabled = accepted, onClick = onAccept)
            Text(
                "Onaylayınca Android'in kendi penceresi açılacak; seçimi orada da " +
                    "onaylamanız gerekiyor. Bunu uygulama sizin adınıza yapamaz.",
                fontSize = 11.sp, lineHeight = 16.sp, color = Bekci.colors.text3,
            )
            TextButton(onSkip, Modifier.fillMaxWidth()) {
                Text("Şimdilik geç — sadece uyarsın", fontSize = 13.5.sp,
                    color = Bekci.colors.text3)
            }
        }
    }
}

@Composable
private fun Madde(baslik: String, aciklama: String, renk: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(renk)
                .padding(top = 6.dp),
        )
        Column {
            Text(baslik, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = Bekci.colors.text)
            Text(aciklama, fontSize = 12.5.sp, lineHeight = 18.sp,
                color = Bekci.colors.text2)
        }
    }
}
