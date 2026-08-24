package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
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
import tr.bekci.ui.AppViewModel
import tr.bekci.ui.components.*
import tr.bekci.ui.theme.Bekci

@Composable
fun OnboardingScreen(onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Bekci.colors.guard),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Shield, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Mesaj kutunuz\nkendini toplasın.",
                style = MaterialTheme.typography.displayLarge, color = Bekci.colors.text)
            Spacer(Modifier.height(10.dp))
            Text("Bekçi gelen mesajları Finans, Kargo, Operatör ve Kampanya olarak ayırır — dolandırıcılık girişimlerini işaretler.",
                fontSize = 14.5.sp, lineHeight = 22.sp, color = Bekci.colors.text2)

            Spacer(Modifier.height(22.dp))
            Feature(Icons.Outlined.Memory, Bekci.colors.guard, Bekci.colors.guardSoft,
                "Her şey telefonunuzda",
                "Mesajlarınız cihazdan çıkmaz. Sunucumuz yok, hesap yok, ağ isteği yok.")
            HorizontalDivider(color = Bekci.colors.line)
            Feature(Icons.Outlined.Warning, Bekci.colors.signal, Bekci.colors.signalSoft,
                "Dolandırıcılığı adıyla söyler",
                "Sahte icra, kargo ve banka mesajlarını neden şüphelendiğini açıklayarak gösterir.")
            HorizontalDivider(color = Bekci.colors.line)
            Feature(Icons.Outlined.LocalOffer, Bekci.colors.amber, Bekci.colors.amberSoft,
                "Türkçe için eğitildi",
                "B kodu, kısa numara ve alfanumerik başlık gibi Türkiye'ye özgü sinyalleri okur.")
        }

        Column(Modifier.background(Bekci.colors.paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Kuruluma başla", onClick = onNext)
            Text("Kurulum 3 adım sürer · Ücretsiz denemede kart istenmez",
                fontSize = 11.5.sp, color = Bekci.colors.text3,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProFeature(title: String, detail: String) {
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Bekci.colors.amberSoft),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = Bekci.colors.amber, modifier = Modifier.size(12.dp))
        }
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
            Text(detail, fontSize = 11.5.sp, lineHeight = 15.sp, color = Bekci.colors.text3,
                modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, soft: Color, title: String, detail: String) {
    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(soft),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
            Text(detail, fontSize = 12.5.sp, lineHeight = 18.sp, color = Bekci.colors.text2,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * Kurulum: ürünün en büyük kayıp noktası. Android'de tek zorunlu adım SMS
 * izni; pil kısıtlaması adımı isteğe bağlı ama üretici katmanları (Xiaomi,
 * Samsung) yüzünden pratikte kritik.
 */
@Composable
fun SetupScreen(onRequestPermission: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Bekçi'yi devreye alın",
                fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Bekci.colors.text)
            Spacer(Modifier.height(10.dp))
            Text("Android, mesajları okuyabilmesi için Bekçi'ye izin vermenizi ister. İzni istediğiniz zaman geri alabilirsiniz.",
                fontSize = 13.5.sp, lineHeight = 20.sp, color = Bekci.colors.text2)

            Spacer(Modifier.height(14.dp))
            Step(1, true, "Bekçi'yi yükleyin", "Tamamlandı.", null)
            Step(2, false, "SMS iznini verin",
                "Sistem bir izin penceresi açacak. “İzin ver”i seçin.",
                "Uygulama izinleri › SMS › İzin ver")
            Step(3, false, "Bekçi'yi varsayılan mesaj uygulaması yapın",
                "Spam'i gelen kutunuzdan gerçekten ayırabilmesi için gerekli. " +
                    "Vermezseniz Bekçi çalışmaya devam eder ama mesajı yalnızca etiketler.",
                "Varsayılan uygulamalar › SMS › Bekçi")
            Step(4, false, "Pil kısıtlamasını kaldırın",
                "Arka planda çalışabilmesi için pil optimizasyonunun dışında tutun.",
                "Ayarlar › Pil › Kısıtlanmamış")

            BekciCard(Modifier.padding(horizontal = 0.dp)) {
                Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = Bekci.colors.guard,
                        modifier = Modifier.size(18.dp))
                    Text("Bu izin yalnızca cihaz içinde kullanılır. Bekçi'nin sunucusu yoktur; mesajlarınız hiçbir yere gönderilmez.",
                        fontSize = 11.5.sp, lineHeight = 17.sp, color = Bekci.colors.text3)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Varsayılan uygulama olmanın BEDELİ. Kullanıcı bunu kurulumda
            // öğrenmeli; sonradan fark etmek kötü bir sürpriz olur.
            BekciCard(Modifier.padding(horizontal = 0.dp)) {
                Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Icon(Icons.Outlined.Warning, null, tint = Bekci.colors.amber,
                        modifier = Modifier.size(18.dp))
                    Text("Bekçi yalnızca SMS içindir. Varsayılan yaparsanız resimli mesajlar " +
                        "(MMS) gösterilmez — geldiğinde size bildirilir ama açılamaz. RCS " +
                        "özellikleri (okundu bilgisi, yazıyor göstergesi) de çalışmaz.",
                        fontSize = 11.5.sp, lineHeight = 17.sp, color = Bekci.colors.text3)
                }
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("İzin ver", onClick = onRequestPermission)
            TextButton(onSkip, Modifier.fillMaxWidth()) {
                Text("Sonra hatırlat", fontSize = 13.5.sp, color = Bekci.colors.text3)
            }
        }
    }
}

@Composable
private fun Step(number: Int, done: Boolean, title: String, detail: String, path: String?) {
    Row(Modifier.padding(vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(27.dp).clip(CircleShape)
            .background(if (done) Bekci.colors.guard else Bekci.colors.text),
            contentAlignment = Alignment.Center) {
            if (done) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else {
                Text("$number", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                    color = Bekci.colors.paper)
            }
        }
        Column {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text)
            Text(detail, fontSize = 12.5.sp, lineHeight = 18.sp, color = Bekci.colors.text2,
                modifier = Modifier.padding(top = 3.dp))
            path?.let {
                Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Bekci.colors.text,
                    modifier = Modifier.padding(top = 7.dp).clip(RoundedCornerShape(7.dp))
                        .background(Bekci.colors.chip)
                        .padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
    }
}

/**
 * Android'de de modelin veri kaynağı gönüllü bağıştır. Gelen SMS'lerden
 * otomatik veri toplamıyoruz: bu, "mesajlarınız cihazdan çıkmaz" vaadini
 * bozardı. Rıza ayrı ve açık.
 */
@Composable
fun DonateScreen(onDonate: (String, String) -> Unit, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Bahis") }
    var consented by remember { mutableStateOf(true) }
    val categories = listOf("Bahis", "Sahte kurum", "Sahte kargo", "Kripto", "Diğer")

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Kaçırdığımız bir\nmesaj mı var?",
                fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp,
                color = Bekci.colors.text)
            Spacer(Modifier.height(10.dp))
            Text("Yakalayamadığımız spam'i buraya yapıştırın. Türkçe spam veri seti çok küçük — sizin katkınız modeli doğrudan iyileştirir.",
                fontSize = 13.5.sp, lineHeight = 20.sp, color = Bekci.colors.text2)

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("Mesajı buraya yapıştırın…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                shape = Bekci.shapeMedium,
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                categories.forEach { item ->
                    Chip(item, null, item == category) { category = item }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(Bekci.shapeMedium).background(Bekci.colors.card)
                    .border(1.dp, Bekci.colors.line, Bekci.shapeMedium)
                    .clickable { consented = !consented }.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (consented) Bekci.colors.guard else Color.Transparent)
                    .border(1.5.dp, if (consented) Color.Transparent else Bekci.colors.line,
                        RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center) {
                    if (consented) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                Text("Bu mesajın yalnızca metnini Bekçi ile paylaşmayı kabul ediyorum. Telefon numaram, adım veya cihaz kimliğim gönderilmez. Mesajı göndermeden önce içindeki kişisel bilgileri silmem gerektiğini biliyorum.",
                    fontSize = 12.sp, lineHeight = 18.sp, color = Bekci.colors.text2)
            }

            Spacer(Modifier.height(12.dp))
            Text("Bağışlanan metinler yalnızca model eğitimi için kullanılır ve üçüncü taraflarla paylaşılmaz. İstediğiniz zaman geri çekebilirsiniz.",
                fontSize = 11.5.sp, lineHeight = 17.sp, color = Bekci.colors.text3)
        }

        Box(Modifier.padding(20.dp)) {
            PrimaryButton("Bağışla ve gönder",
                enabled = consented && text.isNotBlank()) {
                onDonate(text, category)
                onDone()
            }
        }
    }
}

/**
 * Türkiye pazarında abonelik dönüşümü %1 bandında; bu yüzden ömür boyu
 * tek seferlik satın alma en üstte ve varsayılan seçili.
 */
@Composable
fun PaywallScreen(vm: AppViewModel, onDone: () -> Unit) {
    data class Plan(val title: String, val price: String, val detail: String, val best: Boolean)
    val plans = listOf(
        Plan("Ömür boyu", "₺1.199", "Tek seferlik ödeme, abonelik yok", true),
        Plan("Yıllık", "₺499", "Ayda ₺41,58 · istediğiniz zaman iptal", false),
        Plan("Aylık", "₺69,99", "Denemek için", false),
    )
    var selected by remember { mutableStateOf(plans.first()) }

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            ShieldCard(vm.sortedThisWeek, vm.fraudBlocked, vm.falsePositives)

            Column(Modifier.padding(20.dp)) {
                Text("Bekçi Pro", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = Bekci.colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Dolandırıcılık tespiti ve spam ayırma her zaman ücretsizdir. " +
                        "Pro, güç kullanıcıları için ek katman ekler:",
                    fontSize = 13.5.sp, lineHeight = 20.sp, color = Bekci.colors.text2,
                )

                Spacer(Modifier.height(14.dp))
                ProFeature("Sınırsız engellenen kelime", "Ücretsizde 12 ile sınırlı")
                ProFeature("Motorun bulduğu tüm sinyaller", "Baş gerekçe her zaman ücretsiz gösterilir, tam döküm + risk skoru Pro'da")
                ProFeature("Haftalık/aylık dolandırıcılık raporu", "Trend grafiği ve en çok engellenen gönderenler")
                ProFeature("Kuralları yedekle ve paylaş", "Yeni telefona veya bir aile üyesine — sunucu olmadan, dosya olarak")
                ProFeature("Öncelikli destek", "Sorularınız önce yanıtlanır")

                Spacer(Modifier.height(16.dp))
                plans.forEach { plan ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(Bekci.shapeMedium)
                            .background(if (plan == selected) Bekci.colors.guardSoft else Bekci.colors.card)
                            .border(1.5.dp,
                                if (plan == selected) Bekci.colors.guard else Bekci.colors.line,
                                Bekci.shapeMedium)
                            .clickable { selected = plan }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(plan.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = Bekci.colors.text)
                            if (plan.best) {
                                Spacer(Modifier.width(8.dp))
                                Text("EN ÇOK TERCİH EDİLEN",
                                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.clip(RoundedCornerShape(5.dp))
                                        .background(Bekci.colors.guard)
                                        .padding(horizontal = 7.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(plan.price, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                color = Bekci.colors.text)
                        }
                        Text(plan.detail, fontSize = 12.sp, color = Bekci.colors.text2,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }

                BekciCard(Modifier.padding(horizontal = 0.dp)) {
                    Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = Bekci.colors.guard,
                            modifier = Modifier.size(18.dp))
                        Text("Bekçi'nin sunucusu yok. Ödeme Google Play üzerinden yapılır; biz kart bilgisi görmeyiz.",
                            fontSize = 11.5.sp, lineHeight = 17.sp, color = Bekci.colors.text3)
                    }
                }
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("${selected.title} al · ${selected.price}") {
                // Gerçek uygulamada Google Play Billing akışı burada başlar.
                vm.activatePro(); onDone()
            }
            TextButton(onDone, Modifier.fillMaxWidth()) {
                Text("Denemeye devam et", fontSize = 13.5.sp, color = Bekci.colors.text3)
            }
        }
    }
}
