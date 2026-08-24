# Bekçi

Türkçe SMS spam ve dolandırıcılık filtresi. iOS + Android, tek marka tasarımı, sıfır sunucu.

```
bekci/
├── shared/
│   ├── golden.json           ← üç uygulamanın ortak sözleşmesi (34 vaka)
│   └── engine-reference.py   ← algoritmanın referans uygulaması
├── ios/
│   ├── BekciCore/            ← Swift Package: motor + testler (bağımsız derlenir)
│   ├── BekciFilter/          ← ILMessageFilterExtension
│   └── BekciApp/             ← SwiftUI uygulama
├── android/
│   ├── core/                 ← saf JVM modülü: motor + testler
│   └── app/                  ← Compose uygulama + SmsReceiver
└── ref/                      ← geliştirme sırasında kullanılan test korpusu
```

---

## Hızlı başlangıç

> **Android:** `android/gradlew` depoda; ek kurulum gerekmez.
> **iOS:** `.xcodeproj` depoda tutulmaz, `ios/project.yml`den üretilir
> (`brew install xcodegen && cd ios && xcodegen generate`).

### Motoru test et (en hızlı yol, Xcode/Android Studio gerekmez)

```bash
# Swift
cd ios/BekciCore && swift test

# Kotlin
cd android && ./gradlew :core:test

# Python referansı — golden kümesinin TAMAMINI doğrular
cd ref && python3 engine.py

# ...ve ağırlık ayarı yaparken kullanılan korpus (yalnızca action'a bakar)
cd ref && python3 engine.py corpus.json

# Maliyet + kapsam ölçümü: statik ayak izi, mesaj başına iş, hız,
# hangi kural test edilmiyor, hangi vaka karar sınırına yakın
cd ref && python3 olcum.py
```

Üçü de aynı `shared/golden.json` dosyasını okur (Gradle onu her derlemede
`shared/`ten kopyalar). Biri geçip diğeri kalıyorsa uygulamalar ayrışmış demektir.
Python tarafı da artık aksiyon/alt-aksiyon/risk/gönderen tipi/baş gerekçe
beşlisinin tamamını karşılaştırır — bir dönem yalnızca `action`'a baktığı için
referans ile ürünler arasındaki bir ayrışma Python'da görünmüyordu.

**Kümede altı nöbetçi vaka var — silmeyin.** `otp-nbsp`,
`bkod-turkce-bitisik`, `otp-normal-rakam`, `otp-satir-sonu`,
`kod-avcisi-satir-sonu` ve `shouting-bmp-disi`, platform lehçe farklarını
yakalamak için duruyor:

- ICU'nun (Swift) `\s`'i NBSP'yi sayar, Java'nınki (Kotlin) saymaz; `\b`'si
  Türkçe harfleri sayar, Java'nınki saymaz. Bu fark yüzünden OTP koruması bir
  dönem yalnızca Android'de atlatılabiliyordu.
- Kaçış yerine açık sınıf kullanmak tek başına yetmiyor: sınıfın **doğru**
  olması gerekiyor. `[ \t]` yazıldığı dönemde kodu ayrı satıra koyan (yani
  operatörlerin sıkça gönderdiği) gerçek bir banka OTP'si korumaya hiç
  girmiyor, dengeli/sıkı duyarlılıkta çöpe gidiyordu. Boşluk sınıfı her üç
  uygulamada `[ \t\r\n]`dir ve NBSP ailesi `fold()` içinde normal boşluğa
  çevrildiği için bu küme kapalıdır.

Ürün motorlarında `\s \d \w \b` hiç kullanılmaz — hepsi açık karakter
sınıfıdır. Referans da aynı sınıfları kullanır; tek istisna, URL desenindeki
`(?<![\w@.])` geriye-bakışıdır (Python `re`'de `\p{L}` yoktur, `\w` en yakın
karşılıktır — ürünler orada `[\p{L}\p{Nd}_@.]` yazar).

Uzunluk ve harf sayımları her üç tarafta **kod noktası** üzerinden ölçülür:
Swift'in `count`'u grafem kümesi, Kotlin'in `length`'i UTF-16 birimi sayar ve
emoji ya da BMP dışı harf içeren bir mesajda bu iki sayı ikiye katlanacak
kadar ayrışır. `shouting` kuralı bu yüzden bir dönem Android'de **sessizce
hiç çalışmıyordu**: matematiksel kalın harflerin (`𝐊𝐀𝐙𝐀𝐍𝐃𝐈𝐍𝐈𝐙` — spam'de
filtre kaçınmak için kullanılıyor) vekil yarıları `isUpperCase()` görmüyor.
Aynı gerekçeyle rakam kontrolleri ASCII `[0-9]` ile sınırlıdır
(`Character.isNumber` "²"yi de sayardı).

Bir kuralın bonusu varsa (`gambling` +12, `urgency` +10 — birden fazla ifade
geçtiğinde) bonus `Reason.weight`'e **de** yazılır. Aksi hâlde gerekçelerin
toplamı risk skorunu tutmuyor ve `topReasons()` sinyali hak ettiğinden düşük
sıralıyor: `sahte-banka-link` vakasında gerekçeler +70 gösterirken risk 80'di
ve baş gerekçe yanlış seçiliyordu.

### iOS uygulamasını kurmak

Depoda `.xcodeproj` yok — elle üretilmiş bir proje dosyası kırılgan olur ve
tek satırlık bir çakışmada okunamaz hâle gelir. Proje `ios/project.yml`
spec'inden üretilir; hedefler, App Group ve yetkiler o dosyada okunabilir
hâlde durur.

```bash
brew install xcodegen        # bir kez
cd ios && xcodegen generate  # Bekci.xcodeproj üretir
open Bekci.xcodeproj
```

Üretilen projede yapılacak **tek elle iş**: Xcode › Signing & Capabilities
altında kendi geliştirici ekibini (Team) seç. App Group
(`group.tr.bekci.shared`), uzantı gömme ve `RuleStore.swift`'in iki hedefe
de üye olması spec tarafından zaten kuruluyor.

### Android uygulamasını kurmak

```bash
cd android
./gradlew :app:assembleDebug     # imzasız, hızlı
./gradlew :app:assembleRelease   # R8 küçültmeli, imzalı
```

Android Studio'da `android/` klasörünü aç. Wrapper depoda olduğu için ayrı
bir Gradle kurulumu gerekmez.

**İmzalama.** `android/keystore.properties` ve `bekci-release.jks`
`.gitignore`'dadır ve depoda YOKTUR. Kendi anahtarını üret:

```bash
keytool -genkeypair -v -keystore android/bekci-release.jks -alias bekci \
        -keyalg RSA -keysize 4096 -validity 10000
```

sonra `android/keystore.properties` dosyasını `storeFile` / `storePassword`
/ `keyAlias` / `keyPassword` alanlarıyla oluştur. Dosya yoksa release yine
derlenir ama **imzasız** çıkar ve derleme günlüğüne uyarı düşer.

> Bu anahtar kaybedilirse Play Store'a bir daha güncelleme yayınlanamaz —
> farklı anahtarla imzalanan APK, Play tarafından başka bir uygulama sayılır.
> Anahtarı ve parolasını depo dışında yedekle.

**İkonlar** tek kaynaktan üretilir (`tools/ikon_uret.py`): marka işareti
orada tanımlı, tüm mipmap yoğunlukları, App Store 1024px ve Play 512px
ikonları `python tools/ikon_uret.py` ile tazelenir.

---

## Mimari

### Sınıflandırma motoru tek yerde tanımlı, üç yerde uygulanıyor

Motor saf kural tabanlıdır. Model yok — bilinçli:

- **iOS'ta uzantı bütçesi çok dar.** Filtre uzantısı ana uygulamadan çok daha
  düşük bellekle çalışır ve aşarsa öldürülür. BERT sınıfı bir model orada
  yaşayamaz.
- **Türkçe veri yok.** Kamuya açık en büyük Türkçe SMS spam kümesi 4.751
  mesaj ve 2021 tarihli. 2026'nın spam'i (bahis, sahte icra, kripto) orada yok.
  Modelle başlamak, elde olmayan veriyle başlamak olurdu.

Kural motoru bugün mesajların büyük çoğunluğunu çözüyor; model, veri birikince
ikinci katman olarak eklenecek (yol haritası aşağıda).

**İki katmanlı karar:**

1. Kesin kurallar (erken çıkış): kullanıcı beyaz/kara listesi → OTP koruması →
   **tanıdık taklidi (evlat dolandırıcılığı)** → kod avcılığı tespiti.
2. Ağırlıklı risk skoru (0–100) + kategori sözlükleri.

**Sinyaller:** kısaltılmış bağlantı, IP adresli URL, punycode, riskli TLD,
kişisel numaradan bağlantı, yurt dışı kaynak, 0850 hattı, bahis sözlüğü,
ödül vaadi, resmî kurum taklidi, kurumsal içerik + tanınmayan gönderen,
aciliyet baskısı, ASCII kaçınma, tamamı büyük harf, eksik B kodu,
**vishing (geri arama tuzağı)**, **üçüncü kanala (Telegram/WhatsApp)
yönlendirme + kazanç vaadi**.

**Güven azaltıcılar:** doğrulanmış alfanumerik başlık, geçerli B kodu,
bağlantı yokluğu, kısa numara.

### Bağlantı gerektirmeyen dolandırıcılık kalıpları

Motorun risk sinyallerinin büyük çoğunluğu `hasURL`'e bağlıdır — çünkü çoğu
oltalama bir bağlantı ister. Ama en tehlikeli iki dolandırıcılık türü
**bilerek** bağlantı taşımaz (filtreden kaçmanın yolu budur), bu yüzden
2026-08-19'da iki yeni kural eklendi:

- **`impostorContact` (tanıdık taklidi / evlat dolandırıcılığı) — kesin
  karar, `codeHarvest` ile aynı ruhta.** Dolandırıcı bilinmeyen bir
  numaradan bir yakınmışçasına yazar ("telefonum kırıldı", "numaram
  değişti") ve acilen para/havale ister. İKİ bağımsız ifade grubunun
  (`CONTACT_CHANGE` + `MONEY_REQUEST`) BİRLİKTE geçmesi arandığı için günlük
  "IBAN'ımı atıyorum, gönderir misin" mesajlarıyla karışma riski düşük.
  Duyarlılık eşiklerini beklemez, zarar (gönderilen havale) geri alınamaz
  olduğu için doğrudan `junk` döner. `testImpostorContactAlwaysJunk` /
  `tanidik taklidi her duyarlilikta cope gider` bunu kilitliyor.
- **`callbackPhishing` (vishing) — ağırlıklı sinyal, `!trusted` korumalı.**
  "Bu numarayı arayın" isteyen mesajlar önceden **aktif olarak yanlış
  güven veriyordu**: bağlantı yokluğu `noUrl −10` "güven" puanı kazandırıyor,
  mesaj FINANCE sözlüğüyle (`hesap`, `işlem`) örtüştüğü için gerçek bir
  banka bildirimi gibi `transaction/transactionalFinance` döndürüyordu. Artık
  temkinli/dengeli modda `none`'a (artık yanlış güven yok), sıkı modda
  `junk`'a düşüyor. `!trusted` koşulu doğrulanmış bir kurumdan gelen "444 0
  333'ü arayın" gibi meşru yönlendirmeleri (bkz. `bankadan-uyari` golden
  vakası) etkilemiyor.
- **`thirdPartyRedirect` (Telegram/WhatsApp iş/yatırım dolandırıcılığı) —
  ağırlıklı sinyal (58).** "Telegram'dan yazın" / "WhatsApp grubumuza
  katılın" + "günlük kazanç fırsatı" / "ev hanımlarına özel" gibi bir
  kazanç vaadinin BİRLİKTE geçmesi aranıyor. **Bilinçli sınır — ama tam
  görünmez değil:** `REDIRECT_CHANNEL`'daki "telegram" tek kelime olarak
  meşru işletme mesajlarında da geçebileceği için TEK BAŞINA (ikinci bir
  URL/impersonation sinyali olmadan) careful/balanced modda kesin karara
  yetmiyor — link taşımayan saf Telegram-kullanıcı-adı kalıpları
  (`@kullaniciadi`, `URL_RE` bunu alan adı saymaz) o modlarda `none` kalır
  (nöbetçi vaka: `is-firsati-telegram-yonlendirme`, risk 48). Ama **sıkı
  modda** (junk_at 48) aynı mesaj artık yakalanıyor — kullanıcı sıkı modu
  seçtiğinde bu riski göze almış sayılır. Link taşıyan varyant (`wa.me/…`)
  bağlantı sinyaliyle birleşince (risk 80) artık **temkinli modda da**
  `junk` (nöbetçi vaka: `yatirim-whatsapp-yonlendirme`).

### Türkiye'ye özgü iki sinyal

**B kodu.** BTK, alfanumerik başlıklı SMS'lerin sonuna operatörü tanımlayan
4 haneli bir kod (`B001`, `B012` …) eklenmesini zorunlu kılıyor. Yasal toplu
gönderimde bulunur, dolandırıcıda bulunmaz. Truecaller'ın modelinde bu sinyal
yok — yerel avantajın en somut hâli.

B kodunun **yokluğu** da ayrı bir bilgidir: tanınan bir başlık geçerli B kodu
taşıyorsa güven indirimi −45, taşımıyorsa yalnızca −18. Aradaki fark bilinçli
olarak büyük tutuldu, çünkü alfanumerik başlık Türkiye'de taklit edilebiliyor —
−30 verildiği dönemde bit.ly bağlantısı, ödül vaadi ve aciliyet baskısı taşıyan
bir "MIGROS" mesajı varsayılan modda **işlem mesajı** sayılıyordu (nöbetçi
vaka: `bkod-turkce-bitisik`). Meşru gönderene bu sertlik zarar vermez: URL ve
taklit sinyallerinin çoğu zaten "gönderen tanınmıyor" koşuluna bağlı, tanınan
bir başlık o puanları hiç toplamıyor.

**Gönderen tipi.** `+90 5xx` (cep), `0850`/`444` (hizmet numarası), 3–6 haneli
kısa numara, alfanumerik başlık ve yurt dışı numarası tamamen farklı risk
profillerine sahip. "İcra Dairesi" diye yazan bir mesajın cep telefonundan
gelmesi tek başına güçlü bir taklit kanıtıdır.

### Yanlış pozitif, yanlış negatiften pahalıdır

Banka doğrulama kodunu çöpe atarsan kullanıcı uygulamayı siler ve bir yıldız
verir. Bunun için üç savunma var:

1. **OTP erken çıkışı.** Doğrulama kodu içeren ve bağlantı taşımayan mesaj
   hiçbir koşulda çöp olamaz — duyarlılık "sıkı"ya alınmış olsa bile.
   `testOTPNeverJunk` bunu kilitliyor.
2. **`none` ile `allow` ayrımı.** `allow` "bu mesaj temiz" iddiasıdır; `none`
   "karar veremiyorum, sistem bildiği gibi yapsın" demektir. Motor emin
   olmadığında `none` döner. Gri bant (55–77 risk) tam olarak bunun için var.
3. **Kullanıcı kuralları modeli ezer.** Beyaz liste risk skorundan bağımsız
   çalışır ve kural değişince saklanan tüm mesajlar yeniden değerlendirilir.

### Sıfır sunucu, ama "kayıt yok" değil

Sınıflandırma tamamen cihazda. iOS uzantısı `deferQueryRequestToNetwork`
**kullanmıyor**; `Info.plist`'te `ILMessageFilterExtensionNetworkURL` bilerek
yok. Android tarafında `SmsReceiver` ağa çıkmıyor.

**Dikkat: "mesaj cihazdan çıkmaz" ile "hiçbir şey saklanmıyor" aynı şey
değil.** Android'de uygulama gelen mesajları sınıflandırıp cihazda tutuyor
(son 200 mesaj, `EncryptedSharedPreferences` ile şifreli). Bu, gelen kutusu
ekranının çalışması için gerekli. Ayarlar'da "Saklanan mesajları sil" düğmesi
ve saklanan mesaj sayısı açıkça gösteriliyor; onboarding metni de "kayıt yok"
demiyor. iOS'ta böyle bir depo yok — Apple zaten mesajları uygulamaya
vermiyor, orada saklanan tek şey kullanıcının bağış akışıyla eklediği metinler.

Bunun KVKK karşılığı önemli: veri hiç veri sorumlusunun sistemine gelmiyorsa
işleme tartışması çok daha lehinize olur. Telemetri, hata raporu veya
"spam bildir" özelliği mesaj içeriğini sunucuya taşıdığı anda aydınlatma,
açık rıza ve muhtemelen VERBİS yükümlülükleri doğar. Bu yüzden bağış akışı
ayrı bir ekran ve ayrı bir rıza olarak tasarlandı.

---

## Platform kısıtları — koda yansıyan hâliyle

### iOS

| Kısıt | Koddaki karşılığı |
|---|---|
| Yalnızca rehberde olmayan gönderenler uzantıya düşer | Uygulama, tüm mesajları gördüğü izlenimi vermez |
| iMessage'a erişim yok | — |
| Mesaj silinemez, yalnızca sekmeye yönlendirilir | `Kutu` ekranındaki dipnot bunu açıkça söyler |
| Aynı anda tek filtre aktif olabilir | Kurulum ekranı bunu baştan söyler |
| En fazla 5 alt-aksiyon beyan edilebilir | `declaredSubActions` + `testOnlyDeclaredSubActionsEscape` |
| **Uzantı paylaşılan konteynere yazamaz** | `RuleStore`: uygulama yazar, uzantı okur — tek yön |
| Kullanıcının Junk hareketini bildiren API yok | Model verisi yalnızca gönüllü bağış akışından gelir |
| Uygulama filtrelenen mesajı hiç görmez | **"Bugün" ekranında sayaç yok** — `GuardCard` kurulum/kural durumu gösterir |

Uzantının aldığı veri: `sender`, `messageBody`, `receiverISOCountryCode`.
Üçü de `classify()`'a geçiriliyor.

### İki platform aynı görünür, aynı ŞEYİ İDDİA ETMEZ

Prototipteki "Bugün" ekranı iki telefonda piksel piksel aynı çizilmişti ve
ortasında "bu hafta 147 mesaj ayıklandı · 6 dolandırıcılık · 0 yanlış
işaretleme" kartı vardı. **Android'de bu sayılar gerçek** — `SmsReceiver`
mesajı görüyor, sınıflandırıyor ve cihazda saklıyor. **iOS'ta olamaz:**
uzantı paylaşılan konteynere yazamıyor, uygulamanın da mesaja erişimi yok.
O kart iOS'ta yalnızca ilk açılış örneklerini sayıyordu ve ödeme ekranı da
aynı karta dayandığı için uydurma bir rakamla ikna ediyordu.

Bu yüzden asimetri **bilinçlidir**:

| | Android | iOS |
|---|---|---|
| Haftalık sayaç, dolandırıcılık sayısı | var, gerçek | **yok** |
| "0 yanlış işaretleme" | prefs'te kalıcı, kullanıcı düzeltmesinden | **yok** (doğrulanamaz iddia) |
| Ana ekran kartı | `ShieldCard` — sayaçlar | `GuardCard` — kurulum durumu, duyarlılık, kullanıcının kural sayısı |
| Gelen kutusu | gerçek mesajlar | yalnızca kullanıcının kendi eklediği; örnekler `ShowcaseNotice` ile etiketli |

iOS'a sayaç "eksik" diye geri eklenmemeli. Eklenebilmesi, ancak Faz 0
testinde uzantıdan uygulamaya meşru bir veri yolu bulunursa gündeme gelir.

### Bağış ekranı: kişisel gelen kutusuna bağlanmadı, bilinçli olarak

`DonateView`'daki `AppState.classifyAndStore(sender:body:)` çağrısı hiçbir
yerden çağrılmıyordu — ilk bakışta "unutulmuş bağlantı" gibi görünüyor ama
**bağlamak yanlış olurdu**: formun rıza metni "telefon numaram… gönderilmez"
diyor ve bilerek gönderen alanı almıyor, `StoredMessage`/`classifyAndStore`
ise gönderen zorunlu kılıyor (avatar baş harfi, gönderen tipi rozeti buna
dayanıyor). Ayrıca bağış (anonim, model eğitimi için `DonationQueue`) ile
kişisel gelen kutusu (`AppState.messages`) zaten kasıtlı iki ayrı sistem —
birleştirmek "bağışladığım mesaj" ile "benim mesajım" kavramlarını
karıştırırdı.

Onun yerine `DonateView`'a **anlık yerel önizleme** eklendi: kullanıcı
yazarken motorun mesajı hâlâ nasıl gördüğü (`Classifier().classify(sender:
nil, body:)`, varsayılan kurallarla — kullanıcının KENDİ ayarlarıyla değil)
gösteriliyor. Hiçbir yere kaydedilmiyor. Bu, ekranın kendi vaadiyle
("Kaçırdığımız bir mesaj mı var?") birebir örtüşüyor: motor zaten çöp
görüyorsa bağışa gerek yok, görmüyorsa kullanıcı neden bağışladığını somut
görüyor. Ölü kod olan `classifyAndStore` kaldırıldı.

### Android

Android'de Bekçi **varsayılan mesaj uygulaması** olur. Bunun sebebi
tek başına şudur: spam'i gelen kutusundan gerçekten ayırmanın başka yolu
yok — `SMS_RECEIVED` dinleyen bir uygulama mesajı silemez veya taşıyamaz.

| Kısıt | Koddaki karşılığı |
|---|---|
| Varsayılan olmadan mesaj ayıklanamaz | `SmsDeliverReceiver` (`SMS_DELIVER`) + `SmsRole` ile rol istenir |
| Varsayılan olunca mesajı KAYDETMEK bizim işimiz | `SmsDeliverReceiver` her mesajı önce `Telephony.Sms.Inbox`'a yazar |
| `SMS_RECEIVED` varsayılana da gider | `SmsReceiver` başındaki `isDefault` guard'ı çift işlemeyi önler |
| Rol için 4 bileşen zorunlu | `SmsDeliverReceiver`, `MmsReceiver`, `ComposeActivity`, `RespondViaMessageService` — biri eksikse Android uygulamayı listede hiç göstermez |
| **MMS desteklenmiyor** | `MmsReceiver` içeriği açmaz; geldiğini kullanıcıya bildirir — sessizce kaybolmaz |
| RCS'e üçüncü taraf erişimi yok | Varsayılan olununca RCS özellikleri kaybedilir; kurulumda açıkça söylenir |
| SMS izni Play politikasına tabi | "Varsayılan SMS işleyicisi" onaylı kullanımdır — spam-filtresi istisnasından daha güvenilir yol |

**Spam silinmez.** Sınıflandırma sonucu ne olursa olsun mesaj sağlayıcıya
yazılır; ayrım yalnızca Bekçi'nin arayüzünde yapılır (`spamThreads` ayrı
bölümde). Sebebi ürünün kendi ilkesi: yanlış pozitif en pahalı hatadır ve
bir mesajı sessizce yok etmek geri alınamaz. Kullanıcı ileride başka bir
mesajlaşma uygulamasına geçerse geçmişi de yerinde durur.
| Çok parçalı SMS ayrı PDU'lar hâlinde gelir | `SmsReceiver` parçaları birleştirip tek mesaj olarak sınıflandırır |

---

## Motorun davranışını değiştirmek

Ağırlıkları veya sözlükleri değiştireceksen sıra şu:

1. `ref/engine.py` üzerinde değiştir, `ref/corpus.json`'a yeni vaka ekle.
2. `python3 engine.py corpus.json` — hepsi geçene kadar ayarla.
3. Vaka `shared/golden.json`'a da girsin (id/sender/body yeterli), sonra
   `python3 engine.py --uret` — beklentileri referanstan üretir ve iki test
   kopyasını (`ios/BekciCore/Tests/…`, `android/core/src/test/resources/`)
   `shared/`tekiyle birlikte tazeler. Üç dosya bayt bayt aynı olmalı.
4. Aynı değişikliği `Classifier.swift` ve `Classifier.kt`'ye taşı.
5. `swift test` + `./gradlew :core:test` — ikisi de geçmeli.

`--uret` çıktısında "0 vakanin beklentisi degisti" görüyorsan davranışı
değiştirmemişsin demektir; bir kuralı bilerek değiştirdiysen hangi vakaların
kaydığını orada okursun.

Bir tarafı unutursan golden test kırılır. Amaç bu.

---

## Yol haritası

**Faz 0 — Kod yazmadan önce yapılması gereken test (BLOKE EDİCİ)**

Apple geliştirici forumlarında iOS 26.0/26.1'de `ILMessageFilterExtension`'ın
çağrılmadığı veya düzensiz çalıştığı bildirimleri var ve Apple açıklama
yapmamış. Boş bir uzantıyla güncel iOS'ta uçtan uca tetiklenme testi
yapılmadan bu projeye devam edilmemeli. Aynı testte ölçülmesi gerekenler:

- uzantının gerçek bellek tavanı
- RCS mesajlarının uzantıya düşüp düşmediği

**Faz 1 — MVP (bu depo)**
Kural motoru, kurulum rehberi, kategori kutusu, dolandırıcılık gerekçeleri,
kullanıcı kuralları, bağış akışı, ödeme.

**Faz 2 — Model**
Küçük bir sınıflandırıcı (karakter n-gram + lojistik regresyon veya damıtılmış
küçük MLP; Core ML / TFLite, birkaç MB). Türkçe'nin eklemeli yapısı için
karakter n-gram kelime tabanlıdan belirgin daha iyi çalışır. Veri kaynakları:

1. Honeypot hatları — birkaç prepaid hat, çeşitli sitelere kayıt, sistematik
   toplama. En verimli ve en taze kaynak.
2. Gönüllü bağış akışı (uygulamada hazır).
3. Sentetik üretim — başlangıç için, tek başına yetmez.

Lisans uyarısı: Hugging Face'teki `BaranKanat/BerTurk-SpamSMS` modelinin
lisansı çelişkili (YAML etiketi `apache-2.0`, gövde metni ticari kullanımı
yasaklıyor). Kullanma. Temel `dbmdz/bert-base-turkish-*` modelleri MIT —
kendi verinle kendin eğit.

**Faz 3 — Android genişletme**
Play Permissions Declaration Form başvurusu erken yapılmalı; onay süreci uzun
ve sonucu belirsiz.

---

## Durum

**Android çalışır durumda.** Temiz derleme, lint ve motor testleri geçiyor;
imzalı release APK üretiliyor (v2 + v3 imza, R8 küçültmeli ~2,1 MB).

```
./gradlew clean :core:test :app:lintRelease :app:assembleRelease
```

**iOS derlenmedi.** Kod ve proje spec'i hazır ama bu depoda hiç Xcode'dan
geçmedi — `xcodegen generate` sonrası `swift test` + derleme bir Mac'te
doğrulanmalı. Motorun Swift tarafı yalnızca statik olarak Python referansıyla
karşılaştırıldı.

## Yapılmamış olanlar

- **Faz 0 testi (BLOKE EDİCİ, yukarıdaki yol haritasına bakın)** — iOS'ta
  uzantının gerçekten çağrılıp çağrılmadığı ölçülmedi.
- iOS tarafının derlenmesi ve `swift test` sonucu
- StoreKit / Play Billing'in gerçek ürün kimlikleriyle uçtan uca testi
- Bağış kuyruğunun sunucu ucu — `DonationQueue` her iki platformda da
  cihazda kalıcı biriktirir, henüz hiçbir yere göndermez. Uç eklendiğinde
  aydınlatma metni ve VERBİS yükümlülükleri yeniden değerlendirilmeli.
- Aydınlatma metninin hukuki incelemesi — ekran uygulamada var
  (`PrivacyView` / `PrivacyScreen`) ama metin bilgilendirme düzeyinde
- Play Console "Permissions Declaration Form" başvurusu (onay süreci uzun)
- CI yapılandırması
- Erişilebilirlik geçişi (VoiceOver / TalkBack etiketleri kısmen var)
- Yerelleştirme — arayüz şu an yalnızca Türkçe, metinler kodda gömülü
