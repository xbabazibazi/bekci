# App Store yayın hazırlığı — Bekçi iOS

Bu dosya, Bekçi'yi App Store'a göndermek için gereken her şeyi ve
**gönderilmeden önce çözülmesi zorunlu engelleri** içerir.

---

## 1. Durdurucu engeller

Bunlar çözülmeden gönderim yapılmamalı. Sırayla.

### 1.1 iOS hiç derlenmedi — KRİTİK

Swift kodunun tamamı yazıldı ama **bir kez bile Xcode'dan geçmedi**.
Motorun Swift tarafı yalnızca Python referansıyla statik olarak
karşılaştırıldı; derleyici hatası, çalışma zamanı çökmesi veya golden
testi ayrışması olup olmadığı bilinmiyor.

```bash
brew install xcodegen
cd ios && xcodegen generate
cd BekciCore && swift test          # motor: 41 vakalık altın küme
open ../Bekci.xcodeproj              # derle + simülatörde çalıştır
```

`swift test` yeşil olana kadar başka hiçbir adıma geçilmemeli.

### 1.2 Faz 0 testi yapılmadı — KRİTİK

iOS 26'da `ILMessageFilterExtension`'ın çağrılmadığına dair geliştirici
bildirimleri var ve Apple açıklama yapmadı. **Uzantı tetiklenmiyorsa ürün
iOS'ta hiçbir işe yaramaz** ve mağazaya gönderilmesi kullanıcıyı
yanıltmak olur.

Gerçek cihazda ölçülecekler:
- Bilinmeyen numaradan gelen SMS uzantıyı tetikliyor mu? (`os_log` ile)
- Uzantının zirve bellek kullanımı (Xcode → Debug Gauges)
- RCS mesajları uzantıya düşüyor mu?

### 1.3 "Kutu" sekmesi gerçek mesaj gösteremiyor — İNCELEME RİSKİ

iOS'ta uygulama filtrelenen mesajları **hiç göremez** (Apple kısıtı).
Sekme yalnızca örnek veri ve kullanıcının kendi eklediği metinleri
gösteriyor. Apple 4.2 (Minimum Functionality) kapsamında "içi boş sekme"
olarak değerlendirilebilir.

**Karar gerekiyor** — üç seçenek:
1. Sekmeyi kaldır, uygulamayı 3 sekmeye indir (Bugün / Kurallar / Ayarlar)
2. Sekmeyi "Denemeler"e dönüştür: kullanıcı metin yapıştırır, motor kararını
   gösterir (zaten `DonateView`'da çalışan bir mekanizma var)
3. Olduğu gibi bırak ve reddedilirse tepki ver — önerilmez

**Öneri: 2.** Sekme hem dolu olur hem ürünün değerini gösterir hem de
Apple'ın "gerçek işlev" beklentisini karşılar.

### 1.4 Gizlilik politikası URL'i — ZORUNLU

App Store Connect **barındırılan bir URL** ister; uygulama içi metin
yeterli DEĞİLDİR. `PrivacyView`'daki metin yayına alınmalı
(ör. `https://bekci.app/gizlilik`). Alan adı henüz yayında değil.

### 1.5 Apple Developer Program

Yıllık 99 USD üyelik olmadan gönderim yapılamaz. Ekip kimliği alındıktan
sonra `ios/project.yml` içindeki `DEVELOPMENT_TEAM` doldurulmalı.

### 1.6 StoreKit ürünleri

`PaywallView` üç ürün kimliği kullanıyor; bunlar App Store Connect'te
**birebir aynı kimlikle** oluşturulmalı, yoksa satın alma ekranı boş gelir:

| Ürün | Kimlik | Tip |
|---|---|---|
| Ömür boyu | `tr.bekci.pro.lifetime` | Non-Consumable |
| Yıllık | `tr.bekci.pro.yearly` | Auto-Renewable Subscription |
| Aylık | `tr.bekci.pro.monthly` | Auto-Renewable Subscription |

Abonelikler için ayrıca bir **Subscription Group** ve her ürün için
yerelleştirilmiş ad/açıklama gerekiyor.

---

## 2. App Privacy (gizlilik etiketi)

App Store Connect'teki soru formunun cevapları. Bekçi'nin sunucusu ve ağ
erişimi olmadığı için tamamı "toplanmıyor".

| Soru | Cevap |
|---|---|
| Veri topluyor musunuz? | **Hayır** |
| Üçüncü taraf SDK var mı? | Hayır |
| İzleme (tracking) yapıyor mu? | Hayır — `NSPrivacyTracking = false` |
| Reklam kimliği kullanıyor mu? | Hayır |

**Dikkat:** Bağış akışı (`DonationQueue`) bir gönderim ucu eklendiği anda
bu tablo değişir — "Diğer Veri Türleri → Kullanıcı İçeriği → Uygulama
İşlevselliği" olarak beyan edilmesi ve etiketin güncellenmesi gerekir.
Şu an kuyruk yalnızca cihazda biriktiği için beyan gerekmiyor.

### Privacy Manifest (`PrivacyInfo.xcprivacy`)

iOS 17+ için gerekli. Bekçi hiçbir "required reason API" kullanmıyor;
`UserDefaults` kullanımı için `CA92.1` gerekçesi beyan edilmeli.

---

## 3. Mağaza metni (Türkçe)

**Ad:** Bekçi

**Alt başlık (30 karakter):**
`Türkçe SMS dolandırıcılık filtresi`

**Anahtar kelimeler (100 karakter):**
`sms,spam,dolandırıcılık,filtre,engelle,bahis,icra,kargo,oltalama,güvenlik,mesaj,koruma`

**Açıklama:**

```
Sahte icra mesajları, bahis reklamları, kargo dolandırıcılığı ve banka
taklidi SMS'ler — Bekçi bunları tanır ve neden şüphelendiğini açıklar.

TÜRKÇE İÇİN TASARLANDI
Bekçi, Türkiye'ye özgü sinyalleri okur: BTK'nın zorunlu kıldığı operatör
B kodu, alfanumerik gönderici başlıkları, kısa numaralar ve 0850
hatları. Yurt dışı filtrelerinin göremediği kalıplar bunlar.

SADECE "ÇÖP" DEMEZ, NEDENİNİ SÖYLER
Bir mesaj işaretlendiğinde gerekçeyi görürsünüz: kısaltılmış bağlantı,
resmî kurum taklidi, aciliyet baskısı, eksik B kodu. Kararı siz
verirsiniz.

DOĞRULAMA KODUNUZ ASLA ÇÖPE GİTMEZ
Bankanızdan gelen tek kullanımlık kodu kaybetmek, bir spam'i görmekten
çok daha pahalıdır. Bekçi bağlantı içermeyen doğrulama kodu mesajlarını
hiçbir koşulda çöp saymaz.

KURALLARINIZ HER ŞEYİ EZER
Bir göndereni her zaman güvenli ya da her zaman engelli işaretleyin.
Kelime engelleyin. Duyarlılığı üç kademede ayarlayın.

SUNUCU YOK, HESAP YOK
Sınıflandırma tamamen telefonunuzda yapılır. Bekçi'nin sunucusu yoktur;
mesajlarınız hiçbir yere gönderilmez, hesap açmanız istenmez.
```

**Sürüm notları (0.3.0):**
```
· Tanıdık taklidi (evlat dolandırıcılığı) tespiti
· "Bu numarayı arayın" tuzağı (vishing) tespiti
· Telegram/WhatsApp'a yönlendiren sahte iş ilanı tespiti
· Çok satırlı doğrulama kodu mesajları artık korunuyor
```

---

## 4. Apple inceleme ekibine not (App Review Notes)

Bu alan **boş bırakılmamalı** — SMS filtresi alışılmadık bir kategori ve
incelemeci uygulamanın nasıl test edileceğini bilmeli.

```
Bekçi bir ILMessageFilterExtension uygulamasıdır ve Türkçe SMS
dolandırıcılığını cihaz içinde sınıflandırır.

NASIL TEST EDİLİR
1. Ayarlar › Uygulamalar › Mesajlar › Bilinmeyen ve Önemsiz bölümünden
   "Bekçi"yi SMS filtresi olarak seçin.
2. Rehberde KAYITLI OLMAYAN bir numaradan test SMS'i gönderin. iOS,
   rehberdeki numaralardan gelen mesajları uzantıya iletmez.
3. Uygulamadaki "Spam bağışla" ekranına bir metin yapıştırarak motorun
   kararını anında görebilirsiniz — cihaz gerektirmez.

AĞ ERİŞİMİ
Uygulama ve uzantı hiçbir ağ isteği yapmaz.
ILMessageFilterExtensionNetworkURL bilinçli olarak tanımlanmamıştır;
deferQueryRequestToNetwork kullanılmaz. Sınıflandırma tamamen yereldir.

VERİ
Sunucu yoktur, hesap yoktur, analitik yoktur. Uygulama içindeki mesaj
listesi yalnızca kullanıcının kendi eklediği örneklerden oluşur; iOS
filtrelenen mesajları uygulamaya bildirmediği için gerçek mesaj
içeriğine erişimimiz yoktur.
```

---

## 5. Ekran görüntüleri

Zorunlu boyut: **6.7" (1290×2796)**. Diğerleri bundan türetilebilir.

Önerilen sıra (ilk ikisi en çok görülen):
1. Dolandırıcılık uyarısı — gerekçe listesiyle (ürünün farkı bu)
2. Bugün ekranı — koruma durumu
3. Kurallar — duyarlılık kademeleri
4. Kurulum rehberi
5. Gizlilik vurgusu — "sunucu yok"

Ekran görüntüsünde **gerçek kişisel veri olmamalı**; golden kümesindeki
örnek mesajlar kullanılabilir.

---

## 6. Export compliance (şifreleme beyanı)

Bekçi kendi şifreleme algoritması içermez; yalnızca iOS'un standart
`UserDefaults`/dosya korumasını kullanır.

`Info.plist`'e eklenirse her gönderimde soru sorulmaz:
```xml
<key>ITSAppUsesNonExemptEncryption</key>
<false/>
```

---

## 7. Gönderim sırası

1. `swift test` yeşil (bkz. 1.1)
2. Faz 0 cihaz testi geçti (bkz. 1.2)
3. Kutu sekmesi kararı uygulandı (bkz. 1.3)
4. Gizlilik politikası URL'i yayında (bkz. 1.4)
5. Developer Program üyeliği + `DEVELOPMENT_TEAM` dolduruldu
6. StoreKit ürünleri oluşturuldu ve Sandbox'ta test edildi
7. App Store Connect'te uygulama kaydı, gizlilik etiketi, metinler
8. Xcode → Archive → Distribute App → App Store Connect
9. TestFlight'ta en az bir gerçek cihazda uçtan uca deneme
10. İnceleme notlarıyla birlikte gönder

---

## 8. Android tarafı (karşılaştırma)

Android sürümü **yayına çok daha yakın**: derleniyor, testleri geçiyor,
imzalı APK üretiliyor ve varsayılan SMS uygulaması olarak gerçek gelen
kutusu, konuşma ekranı ve spam ayırma çalışıyor.

Play Store için ayrı gereksinimler: Permissions Declaration Form
(varsayılan SMS işleyicisi onaylı kullanımdır), 512×512 mağaza ikonu
(`store/play-icon-512.png` hazır), özellik grafiği ve gizlilik politikası
URL'i.

**Öneri: önce Android yayınlansın.** iOS'un iki durdurucu engeli
(derlenmemiş olması ve Faz 0) çözülene kadar App Store gönderimi erken.
