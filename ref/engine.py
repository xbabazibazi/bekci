"""
Bekçi — sınıflandırma motoru REFERANS uygulaması.

Bu dosya ürünün parçası değildir. Amacı, Swift ve Kotlin uygulamalarının
birebir aynı davranışı üretmesi gereken algoritmayı tek bir yerde tanımlamak
ve kural ağırlıklarını gerçek Türkçe SMS örnekleriyle test edip ayarlamaktır.

Swift  : ios/BekciCore/Sources/BekciCore/Classifier.swift
Kotlin : android/core/src/main/java/tr/bekci/core/Classifier.kt
Golden : shared/golden.json  (her iki proje de bu dosyayı test eder)
"""
from __future__ import annotations
import json
import re
import unicodedata
from dataclasses import dataclass, field
from enum import Enum

# ──────────────────────────────────────────────────────────────
# Çıktı tipleri — Apple ILMessageFilterAction / SubAction ile eşleşir
# ──────────────────────────────────────────────────────────────

class Action(str, Enum):
    NONE = "none"                # karar veremedim → sistem bildiği gibi yapsın
    ALLOW = "allow"              # bu mesaj temiz
    JUNK = "junk"
    PROMOTION = "promotion"
    TRANSACTION = "transaction"


class SubAction(str, Enum):
    NONE = "none"
    T_FINANCE = "transactionalFinance"
    T_ORDERS = "transactionalOrders"
    T_CARRIER = "transactionalCarrier"
    P_OFFERS = "promotionalOffers"
    P_COUPONS = "promotionalCoupons"


# Apple aynı anda en fazla 5 alt-aksiyon beyanına izin veriyor.
# Beyan etmediğimiz bir alt-aksiyonu döndürürsek iOS onu atar ve
# yalnızca ana aksiyonu uygular. Bu yüzden listeyi kodda sabitliyoruz.
DECLARED_SUBACTIONS = {
    SubAction.T_FINANCE, SubAction.T_ORDERS, SubAction.T_CARRIER,
    SubAction.P_OFFERS, SubAction.P_COUPONS,
}


class Sensitivity(str, Enum):
    CAREFUL = "careful"     # temkinli — varsayılan
    BALANCED = "balanced"
    STRICT = "strict"


class SenderKind(str, Enum):
    ALPHA = "alpha"              # alfanumerik başlık: GARANTI, BONUS-VIP
    SHORTCODE = "shortcode"      # 3-6 haneli kısa numara
    TR_MOBILE = "trMobile"       # +90 5xx
    TR_NONGEO = "trNonGeo"       # 0850 / 0212 dışı hizmet numaraları
    TR_GEO = "trGeo"             # coğrafi sabit hat
    INTERNATIONAL = "international"
    UNKNOWN = "unknown"


@dataclass
class Reason:
    code: str
    title: str
    detail: str
    weight: int


@dataclass
class Verdict:
    action: Action
    sub_action: SubAction
    risk: int
    reasons: list[Reason] = field(default_factory=list)
    sender_kind: SenderKind = SenderKind.UNKNOWN

    def to_dict(self):
        return {
            "action": self.action.value,
            "subAction": self.sub_action.value,
            "risk": self.risk,
            "senderKind": self.sender_kind.value,
            "reasonCodes": [r.code for r in self.reasons],
        }


@dataclass
class UserRules:
    allow_senders: set[str] = field(default_factory=set)   # normalize edilmiş
    block_senders: set[str] = field(default_factory=set)
    block_keywords: set[str] = field(default_factory=set)  # küçük harf, TR-katlanmış
    sensitivity: Sensitivity = Sensitivity.CAREFUL


# ──────────────────────────────────────────────────────────────
# Sözlükler
# ──────────────────────────────────────────────────────────────

# Türkiye'de yaygın, doğrulanmış alfanumerik gönderici başlıkları.
# Bu liste uygulamayla birlikte gelir ve güncellenebilir.
# Türkiye'de yaygın, doğrulanmış alfanumerik gönderici başlıkları ve ait
# oldukları grup. Grup, mesaj metni kategoriye oturmadığında ön bilgi
# (prior) olarak kullanılır: GARANTI'den gelen her mesaj finans kokar.
# Anahtarlar fold() edilmiş ve tire/boşluktan arındırılmış hâlde tutulur.
BANK, CARGO, CARRIER_G, PUBLIC_G, RETAIL = "bank", "cargo", "carrier", "public", "retail"

TRUSTED_HEADERS = {
    # Bankalar / ödeme
    **{h: BANK for h in (
        "garanti", "garantibbva", "isbank", "isbankasi", "akbank", "yapikredi",
        "ziraat", "ziraatbank", "halkbank", "vakifbank", "denizbank", "teb",
        "qnbfinansbank", "ingbank", "enpara", "papara", "ininal", "tosla", "iyzico",
        "sekerbank", "odeabank", "fibabanka", "burganbank", "kuveytturk",
        "albarakaturk", "turkiyefinans", "vakifkatilim", "ziraatkatilim",
    )},
    # Kargo / lojistik
    **{h: CARGO for h in (
        "yurticikargo", "araskargo", "mngkargo", "suratkargo", "ptkargo",
        "hepsijet", "trendyolexpress", "sendeo", "kolaygelsin", "ceva", "dhl", "ups",
    )},
    # Operatörler
    **{h: CARRIER_G for h in (
        "turkcell", "vodafone", "turktelekom", "ttmobil", "bimcell", "pttcell",
    )},
    # Kamu
    **{h: PUBLIC_G for h in (
        "edevlet", "mhrs", "gib", "sgk", "nvi", "afad", "meb", "ptt", "uyap",
        "esaglik", "enabiz", "iys", "btk", "tcdd", "ibb",
    )},
    # Büyük ticari — hem işlem hem kampanya gönderir, ön bilgi verilmez
    **{h: RETAIL for h in (
        "trendyol", "hepsiburada", "getir", "yemeksepeti", "migros", "a101",
        "bim", "sok", "n11", "amazon", "mediamarkt", "teknosa", "vatan",
        "defacto", "lcwaikiki", "boyner", "decathlon", "ikea",
    )},
}

GROUP_PRIOR = {
    BANK: ("transaction", "transactionalFinance"),
    CARGO: ("transaction", "transactionalOrders"),
    CARRIER_G: ("transaction", "transactionalCarrier"),
    PUBLIC_G: ("transaction", "none"),
}

FINANCE = {
    "kart", "harcama", "bakiye", "hesab", "havale", "eft", "iban", "kredi",
    "taksit", "ekstre", "borc", "odeme", "tahsilat", "pos", "atm", "faiz",
    "vadeli", "yatirim", "fon", "hisse", "doviz", "islem", "limit", "puan",
}

ORDERS = {
    "kargo", "gonderi", "siparis", "teslim", "sube", "kurye", "takip",
    "paket", "iade", "dagitim", "zimmet", "barkod",
}

CARRIER = {
    "paket", "internet", "gb", "dakika", "kontor", "fatura", "tarife",
    "hat", "abonelik", "kullanim", "yenilendi", "kalan", "aidat",
}

HEALTH = {"randevu", "mhrs", "hastane", "poliklinik", "recete", "asi", "tahlil", "eczane"}

PUBLIC = {"edevlet", "vergi", "beyanname", "harc", "trafik", "ceza", "nufus",
          "adres", "secmen", "askerlik", "uyap", "icra", "tebligat"}

PROMO_OFFERS = {"indirim", "kampanya", "firsat", "sepet", "fiyat", "avantaj",
                "ucretsiz", "kargo bedava", "son gun", "net", "hediye"}

PROMO_COUPONS = {"kupon", "kupon kodu", "indirim kodu", "promosyon kodu",
                 "hediye ceki", "bonus puan", "cekilise katil"}

GAMBLING = {
    "bahis", "bonus", "casino", "kumar", "iddaa", "rulet", "slot", "poker",
    "jackpot", "deneme bonusu", "yatirim bonusu", "cevrimsiz", "giris adresi",
    "guncel giris", "bet", "kayip bonusu", "freespin", "kacak",
}

URGENCY = {
    "24 saat", "48 saat", "son uyari", "son kez", "acilen", "acil",
    "hemen", "derhal", "aksi halde", "aksi takdirde", "kapatilacak",
    "iptal edilecek", "el konulacak", "haciz", "yasal islem", "gecikmeden",
    "sinirli sure", "bugun son", "kacirmayin",
}

# Kurum taklidi: bu kelimeler geçiyorsa gönderen doğrulanmış bir
# alfanumerik başlık OLMALIDIR. Değilse taklit sayılır.
INSTITUTION_WORDS = {
    "icra dairesi", "icra mudurlugu", "emniyet", "savcilik", "mahkeme",
    "vergi dairesi", "sgk", "e-devlet", "edevlet", "nufus mudurlugu",
    "banka", "bankasi", "merkez bankasi", "gelir idaresi", "adalet bakanligi",
    "kargo sirketi", "tapu", "noter",
}

PRIZE = {
    "kazandiniz", "tebrikler", "odulunuz", "odul kazandiniz", "hediyeniz var",
    "cekilis kazanani", "sansli", "ucretsiz iphone", "para odulu", "buyuk ikramiye",
}

# Vishing (geri arama tuzağı): SMS bağlantı taşımaz, kurbanı SAHTE bir
# numarayı aramaya yönlendirir — motorun "bağlantı yok = güvenli" varsayımını
# BİLEREK istismar eder. Bu yüzden ayrı bir sinyal: has_url'e bağlı değil.
CALLBACK = {
    "bu numarayi arayin", "numarayi hemen arayin", "hemen arayin",
    "dogrulamak icin arayin", "arayarak dogrulayin", "bu hatti arayin",
    "guvenlik hattini arayin", "musteri temsilcimizi arayin",
}

# "Tanıdık taklidi" (evlat/yakın dolandırıcılığı): dolandırıcı bilinmeyen bir
# numaradan bir yakınmışçasına yazar — sahte bir gerekçeyle (telefon/numara
# değişti) tanıdık olduğuna inandırır, sonra acilen para/havale ister. Link
# TAŞIMAZ, saf sosyal mühendisliktir; motorun URL merkezli sinyallerinin
# hiçbiri buna değmez. İKİ BAĞIMSIZ ifade grubunun BİRLİKTE geçmesi arandığı
# için (gerekçe + para talebi) günlük "IBAN'ımı atıyorum" mesajlarıyla
# karışma riski düşük — biri diğeri olmadan sıradan bir borç isteği olabilir.
CONTACT_CHANGE = {
    "telefonum kirildi", "telefonum dustu", "telefonumu kaybettim",
    "numaram degisti", "yeni numaramdan yaziyorum", "bu numaradan yaziyorum",
    "hattim degisti", "eski numarama ulasamiyorum",
}
MONEY_REQUEST = {
    "acil param lazim", "acil paraya ihtiyacim", "borc para",
    "gonderir misin", "havale yapar misin", "ibanimi atiyorum",
    "hesabima gonder", "ibanima gonder", "para gonderir misin",
}

# Sahte iş/yatırım vaadi + üçüncü kanala (Telegram/WhatsApp) yönlendirme.
# Bu kalıbın Telegram tarafı özellikle URL_RE'yi atlatır: "@kullaniciadi"
# bir alan adı değildir, motorun bağlantı tespiti bunu hiç görmez. `redirect`
# TEK BAŞINA zayıf sinyaldir (bir işletme meşru şekilde "Telegram kanalımız"
# diyebilir) — bu yüzden EASY_MONEY ile birlikte arandığı için ağırlık
# görece düşük tutuldu (gambling/punycode ayarındaki "kesin" sinyallerden
# farklı olarak duyarlılığa saygılı kalır).
REDIRECT_CHANNEL = {"telegram", "whatsapp grubu", "whatsapptan yazin", "wa.me", "t.me"}
EASY_MONEY = {
    "kazanc firsati", "ek gelir", "ev hanimlarina", "evden calis",
    "gunluk kazan", "danismanimizdan", "uzman danisman",
    "garanti kazanc", "yatirim danismanligi",
}

SHORTENERS = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
    "cutt.ly", "rb.gy", "shorturl.at", "rebrand.ly", "s.id", "tiny.cc",
    "bit.do", "u.to", "v.gd", "shrtco.de", "gg.gg", "kisa.link", "adf.ly",
}

RISKY_TLDS = {
    "top", "xyz", "icu", "click", "link", "live", "online", "site", "website",
    "shop", "buzz", "cyou", "sbs", "rest", "quest", "monster", "bar", "cfd",
}

# ──────────────────────────────────────────────────────────────
# Metin normalizasyonu
# ──────────────────────────────────────────────────────────────

_TR_FOLD = str.maketrans({
    "ı": "i", "İ": "i", "I": "i", "i": "i",
    # Görünmez boşluklar: NBSP, dar NBSP, rakam boşluğu, BOM.
    # Regex lehçeleri bunları farklı sayar; kaynakta tekilleştiriyoruz.
    "\u00a0": " ", "\u202f": " ", "\u2007": " ", "\ufeff": " ",
    "ş": "s", "Ş": "s", "ğ": "g", "Ğ": "g",
    "ü": "u", "Ü": "u", "ö": "o", "Ö": "o", "ç": "c", "Ç": "c",
    "â": "a", "Â": "a", "î": "i", "Î": "i", "û": "u", "Û": "u",
})

TR_CHARS = set("ıİşŞğĞüÜöÖçÇ")


def fold(s: str) -> str:
    """Türkçe'ye duyarlı küçültme + aksan katlama.

    Python'un lower()'ı 'I' → 'i' yapar ama Türkçe'de 'I' → 'ı' olmalıdır.
    Sözlük eşleşmesinde her ikisini de 'i'ye katladığımız için sorun çıkmaz;
    önemli olan Swift ve Kotlin tarafının da AYNI katlamayı yapmasıdır.
    """
    return s.translate(_TR_FOLD).lower()


# Desenlerde \s \d \w \b KULLANILMAZ — Swift (ICU) ve Kotlin (Java) bunları
# farklı yorumlar: ICU'nun \s'i NBSP'yi, \d'si Unicode rakamlarını, \b'si
# Türkçe harfleri sayar, Java'nınki saymaz. Referans da aynı açık sınıfları
# kullanmalı, yoksa referans "spec" olmaktan çıkar: bu dosya bir dönem \s
# kullandığı için satır sonu içeren gerçek bir OTP mesajı referansta korunup
# iki üründe çöpe gidiyordu (golden vakası: otp-satir-sonu).
#
# Boşluk sınıfı { boşluk, tab, CR, LF } ile kapalıdır: NBSP ailesi fold()
# içinde normal boşluğa çevrildiği için burada ayrıca sayılmasına gerek yok.
_WS = r"[ \t\r\n]"

URL_RE = re.compile(
    # (?<!\w) ≈ ürünlerdeki (?<![\p{L}\p{Nd}_@.]) — Python re'de Unicode
    # özellik kaçışı (\p{L}) yok, \w en yakın karşılığıdır.
    r"(?:(?:https?://)|(?:www\.)|(?<![\w@.]))"
    r"((?:[a-z0-9¡-￿](?:[a-z0-9¡-￿-]{0,61}[a-z0-9¡-￿])?\.)+"
    r"[a-z¡-￿]{2,24})"
    r"(?:/[^ \t\r\n]*)?",
    re.IGNORECASE,
)
IPURL_RE = re.compile(r"https?://[0-9]{1,3}(?:\.[0-9]{1,3}){3}", re.IGNORECASE)
# `\bB\d{3}\b` ile aynı anlam, açık hâli: komşuda sözcük karakteri olmasın.
BCODE_RE = re.compile(r"(?<!\w)B[0-9]{3}(?!\w)")
OTP_RE = re.compile(
    r"(dogrulama|onay|guvenlik|islem|giris|aktivasyon|tek kullanimlik)" + _WS + r"*"
    r"(kodunuz|kodu|sifreniz|sifre|parolasi)?" + _WS + r"*[:\-]?" + _WS + r"*[0-9]{4,8}",
)
CODE_HARVEST_RE = re.compile(
    r"(kodu|sifreyi|parolayi|dogrulama kodunu)" + _WS + r"+"
    r"(bize|bana|asagidaki|whatsapp|paylas|gonder|ilet)",
)


def find_urls(text: str) -> list[str]:
    hits: list[str] = []
    for m in URL_RE.finditer(text):
        host = m.group(1).lower()
        # "18.450 TL" gibi sayısal ifadeleri alan adı sanma
        if re.fullmatch(r"[0-9.]+", host):
            continue
        tld = host.rsplit(".", 1)[-1]
        if len(tld) < 2 or tld.isdigit():
            continue
        hits.append(host)
    return hits


def classify_sender(sender: str | None) -> SenderKind:
    if not sender:
        return SenderKind.UNKNOWN
    s = sender.strip().replace(" ", "")
    if any(ch.isalpha() for ch in s):
        return SenderKind.ALPHA
    # Yalnızca ASCII rakam: şebekeden gelen numara her zaman ASCII'dir ve
    # \d / Character.isDigit / Character.isNumber üç platformda farklı
    # genişlikte Unicode rakam kümesi kabul ederdi.
    digits = re.sub(r"[^0-9]", "", s)
    if not digits:
        return SenderKind.UNKNOWN
    if s.startswith("+") and not digits.startswith("90"):
        return SenderKind.INTERNATIONAL
    if not s.startswith("+") and not digits.startswith("0") and 3 <= len(digits) <= 6:
        return SenderKind.SHORTCODE
    local = (digits[2:] if digits.startswith("90") else digits).lstrip("0")
    if local.startswith("5"):
        return SenderKind.TR_MOBILE
    if local.startswith(("850", "444", "800", "900")):
        return SenderKind.TR_NONGEO
    if local[:1] in {"2", "3", "4"}:
        return SenderKind.TR_GEO
    return SenderKind.UNKNOWN


def _has_word_prefix(hay: str, term: str) -> bool:
    """`term`, metinde bir sözcüğün başında geçiyor mu?

    `\b` kullanılmıyor: Python ve Java alt çizgiyi sözcük karakteri sayar,
    Swift/Kotlin uygulamalarındaki isLetter/isNumber kontrolü saymaz.
    """
    i = hay.find(term)
    while i >= 0:
        if i == 0 or not (hay[i - 1].isalpha() or hay[i - 1].isdigit()):
            return True
        i = hay.find(term, i + len(term))
    return False


def has_any(folded: str, lexicon: set[str]) -> list[str]:
    out = []
    for w in lexicon:
        if " " in w:
            if w in folded:
                out.append(w)
        elif _has_word_prefix(folded, w):
            out.append(w)
    # Sıralı döndür: gerekçe metninde hits[0] kullanılıyor, üç uygulamanın
    # aynı örnek kelimeyi göstermesi buna bağlı.
    return sorted(out)


# ──────────────────────────────────────────────────────────────
# Motor
# ──────────────────────────────────────────────────────────────

THRESHOLDS = {
    Sensitivity.CAREFUL: (78, 55),
    Sensitivity.BALANCED: (62, 42),
    Sensitivity.STRICT: (48, 32),
}


def classify(sender: str | None, body: str | None,
             receiver_country: str | None = "TR",
             rules: UserRules | None = None) -> Verdict:
    rules = rules or UserRules()
    body = body or ""
    f = fold(body)
    kind = classify_sender(sender)
    sender_key = fold((sender or "").strip()).replace(" ", "").replace("-", "")

    reasons: list[Reason] = []

    def add(code, title, detail, weight):
        reasons.append(Reason(code, title, detail, weight))

    # ── 1. Kullanıcı kuralları her şeyi ezer ────────────────────
    if sender_key and sender_key in rules.allow_senders:
        return Verdict(Action.ALLOW, _declared(_category(f, TRUSTED_HEADERS.get(sender_key))[1]), 0,
                       [Reason("userAllow", "Sizin güvenli listenizde",
                               "Bu göndereni her zaman güven olarak işaretlediniz.", -100)], kind)

    if sender_key and sender_key in rules.block_senders:
        return Verdict(Action.JUNK, SubAction.NONE, 100,
                       [Reason("userBlock", "Sizin engel listenizde",
                               "Bu göndereni engellemiştiniz.", 100)], kind)

    # sorted(): iki kelime birden eşleşirse gerekçede hangisinin gösterileceği
    # sabit olmalı. Ürünler de .sorted() ile dolaşıyor.
    for kw in sorted(rules.block_keywords):
        if kw and kw in f:
            return Verdict(Action.JUNK, SubAction.NONE, 100,
                           [Reason("userKeyword", "Engellediğiniz kelime",
                                   f"Mesajda “{kw}” geçiyor.", 100)], kind)

    urls = find_urls(body)
    has_ip_url = bool(IPURL_RE.search(body))
    has_url = bool(urls) or has_ip_url
    group = TRUSTED_HEADERS.get(sender_key) if kind == SenderKind.ALPHA else None
    trusted = group is not None
    bcode = bool(BCODE_RE.search(body))

    # ── 2. OTP koruması — yanlış pozitifin en pahalı olduğu yer ──
    # Doğrulama kodu içeren, bağlantı taşımayan mesaj asla çöpe gitmez.
    if OTP_RE.search(f) and not has_url and not CODE_HARVEST_RE.search(f):
        add("otp", "Doğrulama kodu", "Bağlantı içermeyen tek kullanımlık kod mesajı.", -60)
        sub = SubAction.T_FINANCE if (has_any(f, FINANCE) or group == BANK) else SubAction.NONE
        return Verdict(Action.TRANSACTION, _declared(sub), 0, reasons, kind)

    # Kesin karar: numarası "değişen" bir yakın gibi davranıp acil para/
    # havale isteyen mesaj — codeHarvest ile aynı ruhta (aşağıda), duyarlılık
    # eşiklerini beklemeden. Gerekçe: iki bağımsız ifade grubu birlikte
    # aranıyor (düşük yanlış-pozitif), zarar geri alınamaz (havale gitti mi
    # gitti) — riski puanlayıp "belki" demek yerine doğrudan uyar.
    if has_any(f, CONTACT_CHANGE) and has_any(f, MONEY_REQUEST):
        return Verdict(Action.JUNK, SubAction.NONE, 92,
                       [Reason("impostorContact", "Tanıdık taklidi",
                               "Numarası değişen bir yakınınız gibi davranıp acilen para istiyor. "
                               "Göndermeden önce mutlaka o kişiyi bildiğiniz eski numarasından arayarak doğrulayın.",
                               92)], kind)

    # ── 3. Risk sinyalleri ──────────────────────────────────────
    risk = 0

    short_hits = sorted(u for u in urls if u in SHORTENERS)
    if short_hits:
        risk += 30
        add("shortener", "Kısaltılmış bağlantı",
            f"{short_hits[0]} adresi gerçek hedefi gizliyor. Kurumlar kısaltıcı kullanmaz.", 30)

    if has_ip_url:
        risk += 35
        add("ipUrl", "Sayısal adres",
            "Bağlantı alan adı yerine doğrudan IP adresine gidiyor.", 35)

    puny = [u for u in urls if u.startswith("xn--") or ".xn--" in u]
    if puny:
        risk += 35
        add("punycode", "Sahte alfabe",
            "Bağlantıda tanıdık harflere benzeyen farklı karakterler kullanılmış.", 35)

    risky = sorted(u for u in urls if u.rsplit(".", 1)[-1] in RISKY_TLDS)
    if risky:
        risk += 18
        add("riskyTld", "Şüpheli alan adı uzantısı",
            f"“.{risky[0].rsplit('.', 1)[-1]}” uzantısı dolandırıcılıkta sık kullanılıyor.", 18)

    if has_url and kind in {SenderKind.TR_MOBILE, SenderKind.TR_GEO,
                            SenderKind.INTERNATIONAL, SenderKind.UNKNOWN}:
        risk += 22
        add("urlFromNumber", "Sıradan numaradan bağlantı",
            "Kurumsal gönderim yerine kişisel görünümlü bir numaradan bağlantı geldi.", 22)

    if kind == SenderKind.INTERNATIONAL and has_url:
        risk += 20
        add("intlUrl", "Yurt dışı kaynaklı",
            "Yurt dışı numarasından bağlantı içeren mesaj.", 20)

    if kind == SenderKind.TR_NONGEO and has_url:
        risk += 14
        add("nonGeoUrl", "0850 hattından bağlantı",
            "Hizmet numarasından gelen bağlantılı toplu gönderim.", 14)

    # Bonuslar (birden fazla ifade) Reason.weight'e DE yazılır: aksi hâlde
    # gerekçelerin toplamı risk skorunu tutmuyor ve _top() sıralaması
    # aciliyet/bahis sinyalini hak ettiğinden düşük gösteriyordu.
    gamb = has_any(f, GAMBLING)
    if gamb:
        agirlik = 38 + (12 if len(set(gamb)) >= 2 else 0)
        risk += agirlik
        add("gambling", "Bahis/kumar içeriği",
            f"Mesajda “{gamb[0]}” gibi yasa dışı bahis ifadeleri var.", agirlik)

    prize = has_any(f, PRIZE)
    if prize:
        risk += 26
        add("prize", "Ödül vaadi",
            f"“{prize[0]}” gibi kazanç vaatleri dolandırıcılığın en yaygın kancasıdır.", 26)

    inst = has_any(f, INSTITUTION_WORDS)
    if inst and not trusted:
        risk += 30
        add("impersonation", "Resmî kurum taklidi",
            f"“{inst[0]}” adına yazılmış ama gönderen kayıtlı bir kurumsal başlık değil.", 30)

    # Vishing: "bu numarayı arayın" bağlantı GEREKTİRMEZ — kurban bir kez
    # arayınca dolandırıcı sesli aramada devam eder. `!trusted` koşulu,
    # doğrulanmış bir kurumdan gelen "444 0 333'ü arayın" gibi meşru
    # yönlendirmeleri (bkz. bankadan-uyari golden vakası) etkilemez.
    callback = has_any(f, CALLBACK)
    if callback and not trusted:
        risk += 35
        add("callbackPhishing", "Sizi aramanızı istiyor",
            "Doğrulama için bir telefon numarasını aramanızı istiyor — bankalar bunu SMS ile istemez, "
            "şüpheniz varsa kartınızın arkasındaki resmî numarayı siz arayın.", 35)

    # Kurumsal bir işlem anlatıyor (kargo, banka, fatura) ama gönderen
    # doğrulanmış bir kurumsal başlık değil ve bağlantı taşıyor.
    if has_url and not trusted and kind not in {SenderKind.SHORTCODE}:
        biz = has_any(f, ORDERS) + has_any(f, FINANCE) + has_any(f, PUBLIC)
        if len(biz) >= 2:
            risk += 22
            add("orgClaimFromStranger", "Kurumsal içerik, tanınmayan gönderen",
                "Mesaj bir kurum işlemi anlatıyor ama kayıtlı kurumsal başlıktan gelmiyor.", 22)

    urg = has_any(f, URGENCY)
    if urg:
        agirlik = 14 + (10 if len(set(urg)) >= 2 else 0)
        risk += agirlik
        add("urgency", "Aciliyet baskısı",
            f"“{urg[0]}” gibi ifadeler düşünmenizi engellemek için kullanılıyor.", agirlik)

    if has_any(f, REDIRECT_CHANNEL) and has_any(f, EASY_MONEY):
        # 58: link taşımayan "Telegram'dan yazın" varyantı bile (noUrl -10
        # sonrası net 48) sıkı modda junk_at'e (48) tam otursun diye — ama
        # careful/balanced'te TEK BAŞINA (URL/impersonation gibi ikinci bir
        # sinyal olmadan) hâlâ junk_at'e ulaşmaz. "telegram" tek kelime
        # olarak meşru işletme mesajında da geçebileceği için TEK sinyalle
        # varsayılan modda kesin karar vermiyoruz — kullanıcı sıkı modu
        # seçtiyse bu riski göze almış sayılır.
        risk += 58
        add("thirdPartyRedirect", "Denetimsiz kanala yönlendiriyor",
            "Kazanç vaadiyle Telegram/WhatsApp gibi bir kanala yönlendiriyor — "
            "meşru bir iş teklifi sizi SMS filtresinin göremeyeceği bir yere taşımaya çalışmaz.", 58)

    # ASCII kaçınma: Türkçe metin ama hiç Türkçe karakter yok
    if len(body) > 40 and not (TR_CHARS & set(body)):
        tr_markers = has_any(f, {"icin", "ile", "olarak", "tarihinde", "sayin",
                                 "hesabiniz", "adiniza", "islem", "odeme", "guncel"})
        if len(tr_markers) >= 2:
            risk += 16
            add("asciiEvasion", "Türkçe karakter yok",
                "Filtrelerden kaçmak için mesaj kasıtlı olarak Türkçe harfsiz yazılmış.", 16)

    letters = [c for c in body if c.isalpha()]
    if len(letters) > 40 and sum(c.isupper() for c in letters) / len(letters) > 0.75:
        risk += 8
        add("shouting", "Tamamı büyük harf", "Dikkat çekmek için mesajın tamamı büyük yazılmış.", 8)

    if CODE_HARVEST_RE.search(f):
        # Kesin karar: meşru hiçbir gönderen doğrulama kodunuzu geri istemez.
        return Verdict(Action.JUNK, SubAction.NONE, 95,
                       [Reason("codeHarvest", "Doğrulama kodunuzu istiyor",
                               "Hiçbir banka, kurum veya şirket size gönderilen kodu geri istemez. "
                               "Bu mesaj hesabınızı ele geçirmeye çalışıyor.", 95)], kind)

    if kind == SenderKind.ALPHA and not bcode and has_url and not trusted:
        risk += 12
        add("noBCode", "B kodu yok",
            "Yasal toplu gönderimlerde bulunması gereken operatör kodu eksik.", 12)

    # ── 4. Güven azaltıcılar ────────────────────────────────────
    if trusted and bcode:
        risk -= 45
        add("verifiedHeader", "Doğrulanmış gönderen",
            "Tanınan kurumsal başlık ve geçerli operatör B kodu.", -45)
    elif trusted:
        # -18, -45 değil: Türkiye'de alfanumerik başlık TAKLİT EDİLEBİLİR ve
        # BTK yasal toplu gönderimde B kodunu zorunlu kılıyor. B kodu olmayan
        # bir "MIGROS" başlığı zayıf kanıttır; 78'lik çöp eşiğinin karşısına
        # -30 koymak, bit.ly + ödül vaadi + aciliyet taşıyan bir mesajı
        # varsayılan modda "işlem mesajı" saydırıyordu.
        # Meşru gönderene zarar vermez: URL/taklit sinyallerinin çoğu zaten
        # `!trusted` koşuluna bağlı, tanınan başlık bu puanları hiç toplamıyor.
        risk -= 18
        add("knownHeader", "Tanınan gönderen",
            "Bu başlık bilinen kurumlar listesinde ama operatör B kodu yok.", -18)

    if not has_url:
        risk -= 10
        add("noUrl", "Bağlantı yok", "Mesajda tıklanabilir bir adres bulunmuyor.", -10)

    if kind == SenderKind.SHORTCODE and not has_url:
        risk -= 12
        add("shortCode", "Kısa numara", "Operatör onaylı kısa numaradan gönderilmiş.", -12)

    risk = max(0, min(100, risk))

    # ── 5. Karar ────────────────────────────────────────────────
    junk_at, gray_at = THRESHOLDS[rules.sensitivity]
    cat_action, cat_sub = _category(f, group)

    if risk >= junk_at:
        return Verdict(Action.JUNK, SubAction.NONE, risk, _top(reasons), kind)

    if risk >= gray_at:
        # Gri bant: çöp demeye yetecek kadar emin değiliz.
        # Pazarlama sinyali varsa kampanyaya, yoksa hiç dokunma.
        if cat_action == Action.PROMOTION:
            return Verdict(Action.PROMOTION, _declared(cat_sub), risk, _top(reasons), kind)
        return Verdict(Action.NONE, SubAction.NONE, risk, _top(reasons), kind)

    if cat_action != Action.NONE:
        return Verdict(cat_action, _declared(cat_sub), risk, _top(reasons), kind)

    # Kategoriye oturtamadık ve riskli de değil → sistemi bozma.
    return Verdict(Action.NONE, SubAction.NONE, risk, _top(reasons), kind)


def _declared(sub: SubAction) -> SubAction:
    """Beyan etmediğimiz alt-aksiyonu döndürmeyiz; iOS zaten atardı."""
    return sub if sub in DECLARED_SUBACTIONS else SubAction.NONE


def _top(reasons: list[Reason]) -> list[Reason]:
    return sorted([r for r in reasons if r.weight > 0], key=lambda r: -r.weight)[:5] \
        or sorted(reasons, key=lambda r: r.weight)[:3]


def _category(f: str, group: str | None = None) -> tuple[Action, SubAction]:
    scores = {
        (Action.TRANSACTION, SubAction.T_FINANCE): len(has_any(f, FINANCE)),
        (Action.TRANSACTION, SubAction.T_ORDERS): len(has_any(f, ORDERS)),
        (Action.TRANSACTION, SubAction.T_CARRIER): len(has_any(f, CARRIER)),
        (Action.TRANSACTION, SubAction.NONE): len(has_any(f, HEALTH)) + len(has_any(f, PUBLIC)),
        (Action.PROMOTION, SubAction.P_COUPONS): len(has_any(f, PROMO_COUPONS)) * 2,
        (Action.PROMOTION, SubAction.P_OFFERS): len(has_any(f, PROMO_OFFERS)),
    }
    best, score = max(scores.items(), key=lambda kv: kv[1])
    if score >= 2:
        return best
    # İçerik kararsız. Gönderen doğrulanmış bir kurumsal başlıksa,
    # başlığın kendisi tek başına yeterli bir kategori kanıtıdır.
    if group:
        if score == 1 and group == RETAIL:
            return best
        prior = GROUP_PRIOR.get(group)
        if prior:
            return (Action(prior[0]), SubAction(prior[1]))
        if score == 1:
            return best
    return (Action.NONE, SubAction.NONE)


# ──────────────────────────────────────────────────────────────
# Komut satırı
#
#   python engine.py                 → shared/golden.json'ı doğrula (SÖZLEŞME)
#   python engine.py corpus.json     → ayar korpusu, yalnızca action bakılır
#   python engine.py --uret          → golden.json beklentilerini yeniden üret
#
# Çıktıda ASCII işaret kullanılır: Windows konsolu (cp1254) '✓' karakterini
# kodlayamıyor ve betik UnicodeEncodeError ile düşüyordu.
# ──────────────────────────────────────────────────────────────

ROOT = __import__("pathlib").Path(__file__).resolve().parent.parent
GOLDEN = ROOT / "shared" / "golden.json"
# Gradle golden.json'ı derleme sırasında shared/'ten kopyalıyor; Swift paketi
# kendi kopyasını bundle ediyor. --uret ikisini de birlikte tazeler ki
# üç uygulama asla farklı bir fixture okumasın.
GOLDEN_KOPYALARI = (
    ROOT / "ios" / "BekciCore" / "Tests" / "BekciCoreTests" / "golden.json",
    ROOT / "android" / "core" / "src" / "test" / "resources" / "golden.json",
)

GOLDEN_ALANLARI = ("expectAction", "expectSubAction", "expectRisk",
                   "expectSenderKind", "expectTopReason")


def _beklenti(case: dict) -> dict:
    v = classify(case["sender"], case["body"])
    return {
        "expectAction": v.action.value,
        "expectSubAction": v.sub_action.value,
        "expectRisk": v.risk,
        "expectSenderKind": v.sender_kind.value,
        "expectTopReason": v.reasons[0].code if v.reasons else None,
    }


def golden_dogrula() -> int:
    """Referansın golden.json'ın TAMAMINI (risk/subAction/gerekçe dahil)
    karşıladığını doğrular.

    Swift ve Kotlin testleri bu beş alanı kontrol ediyordu ama referansın
    kendisi yalnızca corpus.json'daki `action`'a bakıyordu — yani referans
    ile ürünler arasındaki bir ayrışma Python tarafında görünmüyordu.
    """
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    fails = 0
    for case in golden["cases"]:
        got = _beklenti(case)
        diff = {k: (case[k], got[k]) for k in GOLDEN_ALANLARI
                if k in case and case[k] != got[k]}
        if diff:
            fails += 1
            print(f"X  {case['id']}")
            for k, (beklenen, cikan) in diff.items():
                print(f"     {k}: golden={beklenen!r} referans={cikan!r}")
        else:
            print(f"OK {case['id']:24} {got['expectAction']:11} "
                  f"risk={got['expectRisk']:3} sub={got['expectSubAction']}")
    print(f"\n{len(golden['cases']) - fails}/{len(golden['cases'])} vaka golden ile örtüşüyor")
    return fails


def golden_uret() -> int:
    """Vaka girdilerini (id/sender/body) korur, beklentileri yeniden üretir
    ve iki test kopyasını da tazeler."""
    golden = json.loads(GOLDEN.read_text(encoding="utf-8"))
    degisen = []
    for case in golden["cases"]:
        yeni = _beklenti(case)
        if any(case.get(k) != yeni[k] for k in GOLDEN_ALANLARI):
            degisen.append(case["id"])
        case.update(yeni)
    metin = json.dumps(golden, ensure_ascii=False, indent=2) + "\n"
    for hedef in (GOLDEN, *GOLDEN_KOPYALARI):
        hedef.write_text(metin, encoding="utf-8")
        print(f"yazildi: {hedef.relative_to(ROOT)}")
    print(f"\n{len(degisen)} vakanin beklentisi degisti"
          + (f": {', '.join(degisen)}" if degisen else ""))
    return 0


def korpus_dogrula(yol: str) -> int:
    corpus = json.loads(__import__("pathlib").Path(yol).read_text(encoding="utf-8"))
    fails = 0
    for case in corpus:
        v = classify(case["sender"], case["body"])
        if v.action.value != case["expect"]:
            fails += 1
            print(f"X  {case['id']:24} beklenen={case['expect']:11} "
                  f"cikan={v.action.value:11} risk={v.risk:3}  "
                  f"[{', '.join(r.code for r in v.reasons)}]")
        else:
            print(f"OK {case['id']:24} {v.action.value:11} risk={v.risk:3} "
                  f"sub={v.sub_action.value}")
    print(f"\n{len(corpus) - fails}/{len(corpus)} geçti")
    return fails


if __name__ == "__main__":
    import sys
    arg = sys.argv[1] if len(sys.argv) > 1 else None
    if arg == "--uret":
        sys.exit(golden_uret())
    sys.exit(1 if (korpus_dogrula(arg) if arg else golden_dogrula()) else 0)
