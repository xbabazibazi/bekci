package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tr.bekci.ui.components.BekciCard
import tr.bekci.ui.components.SectionLabel
import tr.bekci.ui.theme.Bekci

/**
 * Aydınlatma metni — uygulama İÇİNDE, çevrimdışı.
 *
 * Önceden Ayarlar'daki bu satırın hiçbir `onClick`'i yoktu: chevron
 * gösteriyor ama dokununca hiçbir şey olmuyordu (iOS tarafında ise
 * yayında olmayan `bekci.app/kvkk` adresine gidiyordu). Ağ izni bile
 * istemeyen bir üründe gizlilik metnini tarayıcıya göndermek kendi
 * iddiasıyla çelişirdi; metin uygulamanın içinde duruyor.
 *
 * **Bu metin bilgilendirmedir, hukuki görüş değildir.** Yayından önce
 * bir hukukçu incelemeli; bağış akışı gerçekten sunucuya gönderim
 * yapmaya başladığında VERBİS ve açık rıza yükümlülükleri yeniden
 * değerlendirilmelidir.
 */
@Composable
fun PrivacyScreen(onDone: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        item {
            Text(
                "Mesajlarınız telefonunuzdan çıkmıyor.",
                style = MaterialTheme.typography.headlineMedium, color = Bekci.colors.text,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            )
            Text(
                "Aşağıdakiler Bekçi'nin ne yaptığının ve ne yapmadığının tam listesidir.",
                style = MaterialTheme.typography.bodyMedium, color = Bekci.colors.text2,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
        }

        bolum("Toplanan veri", listOf(
            "Hiçbir kişisel veri toplanmıyor" to
                "Bekçi'nin sunucusu yok. Hesap açmanız istenmez, telefon numaranız veya cihaz kimliğiniz hiçbir yere gönderilmez.",
            "Uygulama internet izni istemez" to
                "Bekçi'nin izin listesinde INTERNET yok; teknik olarak ağa çıkamaz.",
            "Sınıflandırma cihazda yapılır" to
                "Gelen mesaj, telefonunuzun içindeki kural motorundan geçer. Mesajın kendisi cihazdan çıkmaz.",
        ))

        bolum("Cihazda saklananlar", listOf(
            "Kurallarınız" to
                "Güvenli/engelli gönderen listeleriniz ve engellediğiniz kelimeler telefonunuzda tutulur.",
            "Son mesajlar, şifreli" to
                "Gelen kutusu ekranının çalışabilmesi için son mesajlar cihazda şifreli olarak saklanır. Bu kayıtlar telefondan çıkmaz.",
            "Silme hakkınız" to
                "Ayarlar › Saklanan mesajları sil ile tek dokunuşta hepsini kaldırabilirsiniz.",
        ))

        bolum("Bağış akışı", listOf(
            "Yalnızca siz isterseniz" to
                "“Spam bağışla” ekranında gönderdiğiniz metin, ayrı ve açık onayınızla paylaşılır. Onay vermezseniz hiçbir şey gönderilmez.",
            "Yalnızca mesaj metni" to
                "Gönderen numarası, adınız veya cihaz kimliğiniz eklenmez. Metni göndermeden önce içindeki kişisel bilgileri silmeniz önerilir.",
            "Amaç" to
                "Bağışlanan metinler yalnızca filtrenin Türkçe dolandırıcılık kalıplarını daha iyi tanıması için kullanılır.",
        ))

        bolum("Yapılmayanlar", listOf(
            "Reklam ve izleme yok" to "Bekçi reklam göstermez, üçüncü taraf izleyici (SDK) içermez.",
            "Veri satışı yok" to "Hiçbir veri satılmaz veya üçüncü taraflarla paylaşılmaz.",
            "Konum yok" to "Bekçi konum bilginize erişmez.",
        ))

        item {
            Text(
                "Sorularınız için: kvkk@bekci.app",
                style = MaterialTheme.typography.labelMedium, color = Bekci.colors.text3,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
            )
            Text(
                "Bu metin bilgilendirme amaçlıdır ve son hâlini almadan önce hukuki incelemeden geçecektir.",
                style = MaterialTheme.typography.labelMedium, color = Bekci.colors.text3,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp),
            )
        }
    }
}

/** Başlık + kartlı madde listesi — Ayarlar ekranıyla aynı ritim. */
private fun androidx.compose.foundation.lazy.LazyListScope.bolum(
    baslik: String,
    maddeler: List<Pair<String, String>>,
) {
    item { SectionLabel(baslik) }
    item {
        BekciCard {
            maddeler.forEachIndexed { index, (ad, aciklama) ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(ad, style = MaterialTheme.typography.labelLarge, color = Bekci.colors.text)
                    Text(aciklama, style = MaterialTheme.typography.bodyMedium, color = Bekci.colors.text2)
                }
                if (index < maddeler.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 15.dp), color = Bekci.colors.line)
                }
            }
        }
    }
}
