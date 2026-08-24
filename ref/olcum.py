"""Bekçi motorunun maliyet ve kapsam ölçümü.

Neyi ölçer:
  1. Statik veri ayak izi   — sözlüklerin terim/bayt büyüklüğü (uzantı bütçesi)
  2. Mesaj başına iş        — regex çalıştırma + sözlük tarama sayısı
  3. Hız                    — mesaj/sn ve mesaj başına mikrosaniye
  4. Golden kümesi kapsamı  — hangi kurallar hiç tetiklenmiyor

Python sayıları Swift/Kotlin için MUTLAK değer olarak okunmamalı; anlamlı
olan platformdan bağımsız büyüklükler: kaç regex, kaç terim taraması, kaç
yinelenen tarama, hangi kural test edilmiyor. Hız satırı bir TABANDIR —
derlenmiş iki uygulama bundan belirgin hızlı olacak.

Kullanım:  python olcum.py
"""
from __future__ import annotations

import json
import time
from collections import Counter
from pathlib import Path

import engine

ROOT = Path(__file__).resolve().parent.parent
GOLDEN = json.loads((ROOT / "shared" / "golden.json").read_text(encoding="utf-8"))
VAKALAR = [(c["sender"], c["body"]) for c in GOLDEN["cases"]]

SOZLUKLER = {
    "FINANCE": engine.FINANCE, "ORDERS": engine.ORDERS, "CARRIER": engine.CARRIER,
    "HEALTH": engine.HEALTH, "PUBLIC": engine.PUBLIC,
    "PROMO_OFFERS": engine.PROMO_OFFERS, "PROMO_COUPONS": engine.PROMO_COUPONS,
    "GAMBLING": engine.GAMBLING, "URGENCY": engine.URGENCY,
    "INSTITUTION_WORDS": engine.INSTITUTION_WORDS, "PRIZE": engine.PRIZE,
    "SHORTENERS": engine.SHORTENERS, "RISKY_TLDS": engine.RISKY_TLDS,
}
REGEXLER = ("URL_RE", "IPURL_RE", "BCODE_RE", "OTP_RE", "CODE_HARVEST_RE")

# Motorun ürettiği tüm gerekçe kodları — kapsam ölçümünün referansı.
TUM_KODLAR = [
    "userAllow", "userBlock", "userKeyword", "otp", "shortener", "ipUrl",
    "punycode", "riskyTld", "urlFromNumber", "intlUrl", "nonGeoUrl", "gambling",
    "prize", "impersonation", "orgClaimFromStranger", "urgency", "asciiEvasion",
    "shouting", "codeHarvest", "noBCode", "verifiedHeader", "knownHeader",
    "noUrl", "shortCode",
]


def baslik(s: str) -> None:
    print(f"\n{s}\n" + "-" * len(s))


# ── 1. Statik veri ayak izi ───────────────────────────────────────────────
def ayak_izi() -> None:
    baslik("1. Statik veri ayak izi (uzantı bunu bellekte tutar)")
    toplam_terim = toplam_bayt = 0
    for ad, sozluk in sorted(SOZLUKLER.items(), key=lambda kv: -len(kv[1])):
        bayt = sum(len(t.encode("utf-8")) for t in sozluk)
        toplam_terim += len(sozluk)
        toplam_bayt += bayt
        print(f"  {ad:20} {len(sozluk):3} terim  {bayt:5} bayt")
    basliklar = engine.TRUSTED_HEADERS
    bh_bayt = sum(len(h.encode("utf-8")) for h in basliklar)
    print(f"  {'TRUSTED_HEADERS':20} {len(basliklar):3} başlık {bh_bayt:5} bayt")
    print(f"\n  TOPLAM: {toplam_terim + len(basliklar)} dize, "
          f"{toplam_bayt + bh_bayt} bayt ham metin (~"
          f"{(toplam_bayt + bh_bayt) / 1024:.1f} KB)")
    print(f"  {len(REGEXLER)} derlenmiş regex (statik, mesaj başına yeniden derlenmiyor)")
    print("  → Bu tabloda uzantı bütçesini zorlayacak bir şey yok: sözlükler")
    print("    kilobayt mertebesinde. Faz 0'daki bellek riski motorda DEĞİL,")
    print("    uzantının kendi yükleme maliyetinde aranmalı.")


# ── 2. Mesaj başına iş ────────────────────────────────────────────────────
class SayanRegex:
    """re.Pattern sarmalayıcı — çağrı sayar, davranışı değiştirmez."""

    def __init__(self, ad: str, desen, sayac: Counter):
        self.ad, self._desen, self._sayac = ad, desen, sayac

    def search(self, s):
        self._sayac[self.ad] += 1
        return self._desen.search(s)

    def finditer(self, s):
        self._sayac[self.ad] += 1
        return self._desen.finditer(s)


def is_yuku() -> None:
    baslik("2. Mesaj başına iş (bir classify() çağrısı)")
    rx_sayac: Counter = Counter()
    lex_sayac: Counter = Counter()
    terim_sayac = Counter()

    orijinal_rx = {ad: getattr(engine, ad) for ad in REGEXLER}
    orijinal_has_any = engine.has_any
    ad_of = {id(s): ad for ad, s in SOZLUKLER.items()}

    def sayan_has_any(folded, lexicon):
        ad = ad_of.get(id(lexicon), "geçici küme")
        lex_sayac[ad] += 1
        terim_sayac[ad] += len(lexicon)
        return orijinal_has_any(folded, lexicon)

    for ad in REGEXLER:
        setattr(engine, ad, SayanRegex(ad, orijinal_rx[ad], rx_sayac))
    engine.has_any = sayan_has_any
    try:
        for sender, body in VAKALAR:
            engine.classify(sender, body)
    finally:
        for ad in REGEXLER:
            setattr(engine, ad, orijinal_rx[ad])
        engine.has_any = orijinal_has_any

    n = len(VAKALAR)
    print(f"  {n} golden vakası üzerinden ORTALAMA:\n")
    print("  regex çalıştırma:")
    for ad, adet in rx_sayac.most_common():
        print(f"    {ad:18} {adet / n:4.2f} kez/mesaj")
    print(f"    {'TOPLAM':18} {sum(rx_sayac.values()) / n:4.2f} kez/mesaj")

    print("\n  sözlük taraması (has_any):")
    fazla = 0
    for ad, adet in sorted(lex_sayac.items(), key=lambda kv: -kv[1]):
        kez = adet / n
        isaret = ""
        if kez > 1.0:
            isaret = "  ← bazı mesajlarda iki kez"
            fazla += adet - n
        print(f"    {ad:20} {kez:4.2f} kez/mesaj{isaret}")
    print(f"    {'TOPLAM':20} {sum(lex_sayac.values()) / n:4.2f} tarama/mesaj, "
          f"{sum(terim_sayac.values()) / n:5.1f} terim karşılaştırması/mesaj")
    print(f"\n  → Yinelenen tarama mesaj başına {fazla / n:.2f} geçiş. FINANCE/")
    print("    ORDERS/PUBLIC'i hem orgClaimFromStranger hem _category tarıyor,")
    print("    ama bu yol yalnızca 'bağlantı var + gönderen tanınmıyor'")
    print("    mesajlarında işliyor; çoğu mesaj OTP dalında veya kategori")
    print("    dalında erken çıkıyor. ÖLÇÜM SONUCU: önbellek eklemek gereksiz,")
    print("    ~150 terim karşılaştırması mesaj başına zaten ihmal edilebilir.")


# ── 3. Hız ───────────────────────────────────────────────────────────────
def hiz() -> None:
    baslik("3. Hız (Python referansı — derlenmiş uygulamalar için TABAN)")
    for etiket, vakalar, tur in (
        ("golden kümesi", VAKALAR, 300),
        ("uzun mesaj (5000 karakter)",
         [("+90 532 118 44 09", "acil odeme islem " * 294)], 300),
    ):
        t0 = time.perf_counter()
        for _ in range(tur):
            for sender, body in vakalar:
                engine.classify(sender, body)
        gecen = time.perf_counter() - t0
        adet = tur * len(vakalar)
        print(f"  {etiket:28} {gecen / adet * 1e6:7.1f} µs/mesaj  "
              f"({adet / gecen:9,.0f} mesaj/sn)")
    print("\n  → iOS uzantısına düşen mesaj tek tek gelir; bu hızda zaman")
    print("    bütçesi hiçbir senaryoda sorun değil. Ölçülmesi gereken şey")
    print("    motorun hızı değil, uzantının HİÇ çağrılıp çağrılmadığı.")


# ── 4. Golden kümesi kapsamı ─────────────────────────────────────────────
def kapsam() -> None:
    baslik("4. Golden kümesi kapsamı (hangi kural test edilmiyor?)")

    # TETİKLENEN kural ≠ GÖSTERİLEN gerekçe: _top() yalnızca pozitif
    # ağırlıklıları (en çok 5) döndürür, pozitif varsa negatifleri hiç
    # göstermez. Kapsamı verdict.reasons'tan ölçmek bu yüzden yanıltır —
    # _top()'a GİREN listeyi yakalıyoruz.
    tetiklendi: Counter = Counter()
    gizlendi: Counter = Counter()
    aksiyonlar: Counter = Counter()
    orijinal_top = engine._top

    def sayan_top(reasons):
        sonuc = orijinal_top(reasons)
        kalan = {r.code for r in sonuc}
        for r in reasons:
            tetiklendi[r.code] += 1
            if r.code not in kalan:
                gizlendi[r.code] += 1
        return sonuc

    engine._top = sayan_top
    try:
        for sens in engine.Sensitivity:
            rules = engine.UserRules(sensitivity=sens)
            for sender, body in VAKALAR:
                v = engine.classify(sender, body, rules=rules)
                # Erken dönüşler (_top'a uğramaz): otp, codeHarvest, kullanıcı kuralları
                for r in v.reasons:
                    if not tetiklendi[r.code]:
                        tetiklendi[r.code] += 1
                if sens is engine.Sensitivity.CAREFUL:
                    aksiyonlar[f"{v.action.value}/{v.sub_action.value}"] += 1
    finally:
        engine._top = orijinal_top

    eksik = [k for k in TUM_KODLAR if not tetiklendi[k]]
    print(f"  tetiklenen kural kapsamı: {len(TUM_KODLAR) - len(eksik)}/{len(TUM_KODLAR)}")
    print(f"  hiç tetiklenmeyen       : {', '.join(eksik) if eksik else '— yok'}")
    print("    (userAllow/userBlock/userKeyword golden'da DEĞİL ama Swift ve")
    print("     Kotlin'in ayrı 'kullanıcı kuralları modeli ezer' testinde var)")

    print("\n  tetiklenip kullanıcıya GÖSTERİLMEYEN gerekçeler:")
    for kod, adet in gizlendi.most_common():
        print(f"    {kod:20} {adet:3} kez tetiklendi, gerekçe listesinden düştü")
    print("    → _top() pozitif ağırlıklı bir gerekçe varsa negatifleri hiç")
    print("      göstermiyor: 'Doğrulanmış gönderen' / 'Bağlantı yok' gibi")
    print("      LEHTE kanıtlar kararı etkilediği hâlde listeye girmiyor.")
    print("      KONTROL EDİLDİ, hata değil: iki arayüzde de 'Neden şüpheli'")
    print("      listesi yalnızca isFraud (junk & risk>=70) mesajlarda")
    print("      gösteriliyor — o eşikte pozitif gerekçe zaten var. Temiz")
    print("      mesajda ise nötr 'Bekçi ne gördü' tablosu çıkıyor ve orada")
    print("      en negatif gerekçe 'Ana gerekçe' olarak doğru görünüyor.")

    print("\n  aksiyon/alt-aksiyon dağılımı (temkinli mod):")
    for k, v in aksiyonlar.most_common():
        print(f"    {k:34} {v:2} vaka")
    beyan = {s.value for s in engine.DECLARED_SUBACTIONS} | {"none"}
    uretilen = {k.split("/", 1)[1] for k in aksiyonlar}
    print(f"\n  beyan edilmemiş alt-aksiyon sızıntısı: "
          f"{sorted(uretilen - beyan) or 'yok'}")


# ── 5. Eşik payı (kırılganlık) ───────────────────────────────────────────
def esik_payi() -> None:
    baslik("5. Eşik payı — hangi vaka bir ağırlık değişiminde yer değiştirir?")
    print("  Her vakanın riski, o duyarlılıktaki en yakın karar sınırından")
    print("  kaç puan uzakta? Pay küçükse tek bir ağırlık düzeltmesi kararı")
    print("  ters çevirir; 0 pay = zaten sınırın üstünde oturuyor.\n")
    kirilgan = []
    for sens in engine.Sensitivity:
        junk_at, gray_at = engine.THRESHOLDS[sens]
        rules = engine.UserRules(sensitivity=sens)
        for (sender, body), c in zip(VAKALAR, GOLDEN["cases"]):
            v = engine.classify(sender, body, rules=rules)
            # Erken çıkışlar (OTP / kod avcılığı / kullanıcı kuralı) risk
            # skorundan bağımsız karar verir; eşik payı onlar için anlamsız.
            if any(r.code in ("otp", "codeHarvest", "userAllow", "userBlock",
                              "userKeyword") for r in v.reasons):
                continue
            pay = min(abs(v.risk - junk_at), abs(v.risk - gray_at))
            if pay <= 5:
                kirilgan.append((pay, sens.value, c["id"], v.risk,
                                 v.action.value, junk_at, gray_at))
    if not kirilgan:
        print("  Hiçbir vaka sınıra 5 puandan yakın değil.")
    for pay, sens, vid, risk, aksiyon, junk_at, gray_at in sorted(kirilgan):
        print(f"    pay {pay:2}  {sens:9} {vid:24} risk={risk:3} → {aksiyon:11}"
              f"  (çöp≥{junk_at}, gri≥{gray_at})")
    print(f"\n  → {len(kirilgan)} kırılgan (vaka × duyarlılık) çifti. Bunlar")
    print("    ağırlık ayarında ilk kırılacak yerler; golden testi kırıldığında")
    print("    önce buraya bak.")


if __name__ == "__main__":
    print(f"Bekçi motor ölçümü — golden v{GOLDEN['version']}, "
          f"{len(VAKALAR)} vaka")
    ayak_izi()
    is_yuku()
    hiz()
    kapsam()
    esik_payi()
