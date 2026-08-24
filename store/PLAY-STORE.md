# Play Store yayın hazırlığı — Bekçi Android

**Durum: teknik olarak hazır.** Derleniyor, testleri geçiyor, imzalı AAB
üretiliyor. Kalanlar hesap/içerik işleri.

| | |
|---|---|
| Sürüm | 0.4.2 (versionCode 9) |
| Yayın paketi | `dagitim/bekci-0.4.2.aab` |
| Yan yükleme APK'sı | `dagitim/bekci-0.4.2.apk` |
| Uygulama kimliği | `tr.bekci` |
| minSdk / targetSdk | 26 / 35 |

Paketi yeniden üretmek:
```bash
cd android
./gradlew clean :core:test :app:lintRelease bundleRelease
```

---

## 1. En kritik adım: SMS izin başvurusu

Bekçi `RECEIVE_SMS`, `READ_SMS`, `SEND_SMS` kullanıyor. Bunlar Play'de
**kısıtlı izinlerdir** ve Play Console'daki **Permissions Declaration
Form** doldurulmadan uygulama yayınlanamaz.

**İyi haber:** Bekçi varsayılan SMS uygulaması olduğu için onaylı bir
çekirdek işlev sunuyor. Beyanda seçilecek gerekçe:

> **Default SMS handler** — uygulama, kullanıcının varsayılan SMS
> uygulaması olarak çalışır ve mesaj alma/gönderme/görüntüleme temel
> işlevini yerine getirir.

Bu, "spam filtresi istisnası" yolundan **daha güvenilirdir**: spam
istisnası her sürümde yeniden değerlendirilebilirken varsayılan işleyici
rolü açıkça tanımlı bir kategoridir.

### Forma yazılacak gerekçe metni

```
Bekçi, Türkçe SMS dolandırıcılığını tespit eden bir mesajlaşma
uygulamasıdır ve kullanıcının varsayılan SMS uygulaması olarak çalışır.

RECEIVE_SMS / SMS_DELIVER: Varsayılan SMS uygulaması olarak gelen
mesajları karşılamak ve sistem SMS sağlayıcısına yazmak için gereklidir.
Varsayılan uygulama olduğumuzda mesajı kaydetme sorumluluğu bize geçer;
bu izin olmadan kullanıcı mesajlarını kaybeder.

READ_SMS: Kullanıcının mevcut konuşma geçmişini uygulama içinde
göstermek için gereklidir. Mesajlaşma uygulamasının temel işlevidir.

SEND_SMS: Kullanıcının konuşma ekranından yanıt gönderebilmesi ve
"mesajla yanıtla" ile aramaları reddedebilmesi için gereklidir.

VERİ KULLANIMI: Mesaj içeriği cihazdan HİÇ ÇIKMAZ. Uygulama INTERNET
iznini bile talep etmez; manifestte yoktur. Sınıflandırma tamamen
cihaz içinde, kural tabanlı bir motorla yapılır. Sunucumuz, hesap
sistemimiz veya analitiğimiz yoktur. Mesajlar cihazda şifreli olarak
(EncryptedSharedPreferences) saklanır ve kullanıcı Ayarlar'dan tek
dokunuşla silebilir.
```

### Beyanda vurgulanacak
- Manifestte **INTERNET izni YOK** — bunu doğrulamak kolay ve güçlü bir argüman
- Kullanıcı, varsayılan uygulama olmayı **ayrı bir rıza ekranında** onaylıyor
- Uygulama varsayılan olmadan da (sınırlı biçimde) çalışıyor, dayatma yok

---

## 2. Data safety formu

Play'in veri güvenliği bölümü. Bekçi'nin ağ erişimi olmadığı için tamamı
"toplanmıyor".

| Soru | Cevap |
|---|---|
| Veri topluyor musunuz? | **Hayır** |
| Veri paylaşıyor musunuz? | **Hayır** |
| Veri aktarımda şifreleniyor mu? | Uygulanamaz — aktarım yok |
| Kullanıcı veri silmeyi talep edebilir mi? | **Evet** — Ayarlar › Saklanan mesajları sil |

**Cihazda saklanan** (toplama sayılmaz, ama beyanda dürüst olun):
SMS içeriği ve kullanıcı kuralları, `EncryptedSharedPreferences` ile
şifreli, yalnızca cihazda.

> **Dikkat:** `DonationQueue` bir gönderim ucu kazandığı anda bu tablo
> değişir. Bağış metni sunucuya gitmeye başlarsa "Kullanıcı içeriği →
> Diğer" olarak beyan edilmeli ve form güncellenmelidir. Şu an kuyruk
> yalnızca cihazda biriktiği için beyan gerekmiyor.

---

## 3. Mağaza listesi (Türkçe)

**Uygulama adı (30 karakter sınırı, kullanılan: 30):**
`Bekçi — SMS Dolandırıcı Filtre`

**Kısa açıklama (80 karakter sınırı, kullanılan: 79):**
`Sahte icra, bahis ve banka taklidi SMS'leri tanır. Her şey telefonunuzda kalır.`

> Önceki taslaklar (35 ve 85 karakter) Play'in sınırlarını aşıyordu —
> Play Console'a girerken kırpılır/hata verirdi. 2026-08-24'te düzeltildi.

**Tam açıklama:**

```
Sahte icra mesajları, bahis reklamları, kargo dolandırıcılığı ve banka
taklidi SMS'ler — Bekçi bunları tanır, gelen kutunuzdan ayırır ve neden
şüphelendiğini açıklar.

TÜRKÇE İÇİN TASARLANDI
Bekçi, Türkiye'ye özgü sinyalleri okur: BTK'nın zorunlu kıldığı operatör
B kodu, alfanumerik gönderici başlıkları, kısa numaralar ve 0850
hatları. Yurt dışı filtrelerinin göremediği kalıplar bunlar.

SPAM GELEN KUTUNUZDAN AYRILIR
Dolandırıcılık mesajları ana listenizden çıkar, ayrı bir bölüme gider.
Silinmez — istediğiniz zaman bakabilir, yanlış ayrılanı geri
alabilirsiniz.

SADECE "ÇÖP" DEMEZ, NEDENİNİ SÖYLER
Bir mesaj işaretlendiğinde gerekçeyi görürsünüz: kısaltılmış bağlantı,
resmî kurum taklidi, aciliyet baskısı, eksik B kodu.

DOĞRULAMA KODUNUZ ASLA ÇÖPE GİTMEZ
Bankanızdan gelen tek kullanımlık kodu kaybetmek, bir spam'i görmekten
çok daha pahalıdır. Bekçi bağlantı içermeyen doğrulama kodu mesajlarını
hiçbir koşulda çöp saymaz.

YENİ NESİL DOLANDIRICILIĞI DA TANIR
· "Anne telefonum kırıldı, acil para lazım" — tanıdık taklidi
· "Bu numarayı hemen arayın" — geri arama tuzağı
· "Günlük kazanç, Telegram'dan yazın" — sahte iş ilanı

KURALLARINIZ HER ŞEYİ EZER
Bir göndereni her zaman güvenli ya da her zaman engelli işaretleyin.
Kelime engelleyin. Duyarlılığı üç kademede ayarlayın.

SUNUCU YOK, HESAP YOK
Sınıflandırma tamamen telefonunuzda yapılır. Bekçi internet izni bile
istemez — manifestinde yoktur. Mesajlarınız hiçbir yere gönderilmez.

NELERİ YAPMAZ
· Resimli mesaj (MMS) desteklemez — geldiğinde bildirilir ama açılamaz
· RCS özellikleri (okundu bilgisi, yazıyor göstergesi) çalışmaz
Bunlar varsayılan mesaj uygulaması olmanın bedelidir ve kurulumda
açıkça söylenir.
```

**Sürüm notları (0.3.0):**
```
· Varsayılan mesaj uygulaması olarak spam'i gelen kutusundan ayırma
· Gerçek konuşma listesi ve yanıt gönderme
· Tanıdık taklidi, geri arama tuzağı ve sahte iş ilanı tespiti
· Akşam spam özeti
· Tanılama ekranı
```

---

## 4. Görseller

| Varlık | Boyut | Durum |
|---|---|---|
| Uygulama ikonu | 512×512 | ✅ `store/play-icon-512.png` |
| Özellik grafiği | 1024×500 | ✅ `store/play-feature-1024x500.png` |
| Telefon ekran görüntüsü | en az 2, 16:9 veya 9:16 | ⬜ cihazdan alınmalı |

Görselleri yeniden üretmek:
```bash
python tools/ikon_uret.py
python tools/magaza_gorsel_uret.py
```

**Ekran görüntüsü planı** (öneri, sırayla):
1. Kutu — spam ayrılmış hâliyle ("N konuşma · M spam ayrıldı")
2. Dolandırıcılık uyarısı — gerekçe listesiyle
3. Bugün ekranı
4. Kurallar — duyarlılık kademeleri
5. Tanılama

Ekran görüntüsünde **gerçek kişisel veri olmamalı**. Test cihazına golden
kümesindeki örnek mesajları göndererek temiz bir ekran hazırlayın.

---

## 5. Diğer form alanları

**İçerik derecelendirmesi:** Anket doldurulur; Bekçi'de şiddet, cinsellik,
kumar *oynatma* yok. Dikkat: "bahis" kelimesi uygulamada geçiyor ama
**engellemek için**; ankette kumar içeriği YOK denmeli.

**Hedef kitle:** 18+. SMS erişimi olan bir güvenlik aracı; çocuklara
yönelik değil, "Designed for Families" programına girmemeli.

**Kategori:** Araçlar (Tools) veya İletişim (Communication).
İletişim daha doğru — varsayılan mesaj uygulaması.

**Gizlilik politikası URL'i: ZORUNLU.** Uygulama içindeki metin
(`PrivacyScreen`) yeterli değildir, barındırılan bir sayfa gerekir.
Kaynak dosya artık `docs/index.html` (repo kökünde, GitHub Pages `main`
dalı `/docs` klasörü olarak yayınlanacak şekilde taşındı). Yayın adresi:

```
https://xbabazibazi.github.io/bekci/
```

Pages henüz repo ayarlarından ELLE açılmadı — GitHub'da Settings › Pages ›
Source: "Deploy from a branch" › Branch: `main` / `docs` seçilmeli (tek
seferlik, ~1 dakika sürer, birkaç dakika içinde adres canlı olur).

**Export compliance:** Bekçi kendi şifreleme algoritması içermez;
yalnızca platformun standart API'lerini kullanır.

---

## 6. Yayın öncesi kontrol

- [ ] `./gradlew clean :core:test :app:lintRelease bundleRelease` yeşil
- [ ] AAB gerçek cihazda test edildi (**internal testing** kanalı)
- [ ] Varsayılan uygulama akışı gerçek SMS ile denendi
- [ ] Gizlilik politikası URL'i yayında
- [ ] Permissions Declaration Form gönderildi
- [ ] Data safety formu dolduruldu
- [ ] Ekran görüntüleri hazır (kişisel veri içermiyor)
- [ ] Keystore ve `keystore.properties` **depo dışında yedeklendi**

> **Keystore uyarısı:** `bekci-release.jks` kaybedilirse Play'e bir daha
> güncelleme yayınlanamaz. Play App Signing'e kaydolmak bu riski azaltır
> (Google imza anahtarını saklar, siz yükleme anahtarını kullanırsınız) —
> ilk yüklemede bu seçenek sunulur, **kabul edilmesi önerilir**.

---

## 7. Bilinen sınırlar (incelemede sorulursa)

- **MMS işlenmiyor.** `MmsReceiver` bildirilmiş (rol için zorunlu) ama
  içeriği açmıyor; gelen MMS kullanıcıya bildirilir. Kurulumda ve
  Ayarlar'da açıkça yazıyor.
- **RCS çalışmaz.** Üçüncü taraf uygulamalara açık değil.
- **Bağış kuyruğunun sunucu ucu yok.** Metin cihazda birikir, hiçbir yere
  gitmez. Kullanıcıya "gönderildi" denmiyor.
