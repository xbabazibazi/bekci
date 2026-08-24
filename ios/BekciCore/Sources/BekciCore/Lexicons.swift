import Foundation

// MARK: - Türkçe metin normalizasyonu

public enum TurkishText {

    /// Türkçe'ye duyarlı küçültme + aksan katlama.
    ///
    /// Swift'in `lowercased()`'ı 'I' → 'i' yapar, Türkçe'de ise 'I' → 'ı' olmalıdır.
    /// Sözlük eşleşmesinde ikisini de 'i'ye katladığımız için ayrım kaybolmaz;
    /// kritik olan Kotlin ve Python referansının **aynı** katlamayı yapmasıdır.
    /// Ayrışma olursa `shared/golden.json` testi kırılır.
    public static func fold(_ s: String) -> String {
        var out = String()
        out.reserveCapacity(s.count)
        for ch in s {
            switch ch {
            case "ı", "İ", "I", "i": out.append("i")
            case "ş", "Ş":           out.append("s")
            case "ğ", "Ğ":           out.append("g")
            case "ü", "Ü":           out.append("u")
            case "ö", "Ö":           out.append("o")
            case "ç", "Ç":           out.append("c")
            // Görünmez boşluklar: NBSP, dar NBSP, rakam boşluğu, BOM.
            // Regex lehçeleri bunları farklı sayar; kaynakta tekilleştiriyoruz.
            case "\u{00a0}", "\u{202f}", "\u{2007}", "\u{feff}": out.append(" ")
            case "â", "Â":           out.append("a")
            case "î", "Î":           out.append("i")
            case "û", "Û":           out.append("u")
            default:                 out.append(contentsOf: ch.lowercased())
            }
        }
        return out
    }

    public static let turkishCharacters = Set("ıİşŞğĞüÜöÖçÇ")

    /// Katlanmış metinde sözlük araması yapar.
    ///
    /// Tek kelimeler sözcük başı sınırıyla aranır ("bet" → "bethesda"ya
    /// takılmaz ama "bahis" → "bahisler"i yakalar). Boşluk içeren ifadeler
    /// doğrudan alt dizi olarak aranır.
    public static func matches(_ folded: String, _ lexicon: Set<String>) -> [String] {
        var hits: [String] = []
        for term in lexicon {
            if term.contains(" ") {
                if folded.contains(term) { hits.append(term) }
            } else if hasWordPrefix(folded, term) {
                hits.append(term)
            }
        }
        return hits.sorted()
    }

    /// `term` metinde bir sözcüğün başında geçiyor mu?
    private static func hasWordPrefix(_ haystack: String, _ term: String) -> Bool {
        guard !term.isEmpty else { return false }
        var search = haystack[haystack.startIndex...]
        while let r = search.range(of: term) {
            let isStart = r.lowerBound == haystack.startIndex
            let prevOK: Bool
            if isStart {
                prevOK = true
            } else {
                let prev = haystack[haystack.index(before: r.lowerBound)]
                prevOK = !(prev.isLetter || prev.isNumber)
            }
            if prevOK { return true }
            guard r.upperBound < search.endIndex else { break }
            search = search[r.upperBound...]
        }
        return false
    }
}

// MARK: - Sözlükler
//
// Tüm terimler fold() edilmiş hâlde yazılır: küçük harf, Türkçe karaktersiz.
// Kotlin tarafındaki `Lexicons.kt` ile birebir aynı olmalıdır.

public enum Lex {

    public enum Group: String, Codable, Sendable {
        case bank, cargo, carrier, publicSector, retail
    }

    /// Doğrulanmış alfanumerik gönderici başlıkları ve grupları.
    /// Grup, mesaj metni bir kategoriye oturmadığında ön bilgi olarak kullanılır:
    /// GARANTI'den gelen her mesaj finans kokar, içerik zayıf olsa bile.
    public static let trustedHeaders: [String: Group] = {
        var m: [String: Group] = [:]
        for h in ["garanti", "garantibbva", "isbank", "isbankasi", "akbank",
                  "yapikredi", "ziraat", "ziraatbank", "halkbank", "vakifbank",
                  "denizbank", "teb", "qnbfinansbank", "ingbank", "enpara",
                  "papara", "ininal", "tosla", "iyzico", "sekerbank", "odeabank",
                  "fibabanka", "burganbank", "kuveytturk", "albarakaturk",
                  "turkiyefinans", "vakifkatilim", "ziraatkatilim"] { m[h] = .bank }
        for h in ["yurticikargo", "araskargo", "mngkargo", "suratkargo", "ptkargo",
                  "hepsijet", "trendyolexpress", "sendeo", "kolaygelsin",
                  "ceva", "dhl", "ups"] { m[h] = .cargo }
        for h in ["turkcell", "vodafone", "turktelekom", "ttmobil",
                  "bimcell", "pttcell"] { m[h] = .carrier }
        for h in ["edevlet", "mhrs", "gib", "sgk", "nvi", "afad", "meb", "ptt",
                  "uyap", "esaglik", "enabiz", "iys", "btk", "tcdd", "ibb"] { m[h] = .publicSector }
        for h in ["trendyol", "hepsiburada", "getir", "yemeksepeti", "migros",
                  "a101", "bim", "sok", "n11", "amazon", "mediamarkt", "teknosa",
                  "vatan", "defacto", "lcwaikiki", "boyner", "decathlon",
                  "ikea"] { m[h] = .retail }
        return m
    }()

    /// Grubun tek başına ima ettiği kategori.
    /// `retail` yoktur — perakende hem işlem hem kampanya gönderir, içerik karar versin.
    public static let groupPrior: [Group: (FilterAction, FilterSubAction)] = [
        .bank:         (.transaction, .transactionalFinance),
        .cargo:        (.transaction, .transactionalOrders),
        .carrier:      (.transaction, .transactionalCarrier),
        .publicSector: (.transaction, .none),
    ]

    public static let finance: Set<String> = [
        "kart", "harcama", "bakiye", "hesab", "havale", "eft", "iban", "kredi",
        "taksit", "ekstre", "borc", "odeme", "tahsilat", "pos", "atm", "faiz",
        "vadeli", "yatirim", "fon", "hisse", "doviz", "islem", "limit", "puan",
    ]

    public static let orders: Set<String> = [
        "kargo", "gonderi", "siparis", "teslim", "sube", "kurye", "takip",
        "paket", "iade", "dagitim", "zimmet", "barkod",
    ]

    public static let carrier: Set<String> = [
        "paket", "internet", "gb", "dakika", "kontor", "fatura", "tarife",
        "hat", "abonelik", "kullanim", "yenilendi", "kalan", "aidat",
    ]

    public static let health: Set<String> = [
        "randevu", "mhrs", "hastane", "poliklinik", "recete", "asi",
        "tahlil", "eczane",
    ]

    public static let publicServices: Set<String> = [
        "edevlet", "vergi", "beyanname", "harc", "trafik", "ceza", "nufus",
        "adres", "secmen", "askerlik", "uyap", "icra", "tebligat",
    ]

    public static let promoOffers: Set<String> = [
        "indirim", "kampanya", "firsat", "sepet", "fiyat", "avantaj",
        "ucretsiz", "kargo bedava", "son gun", "net", "hediye",
    ]

    public static let promoCoupons: Set<String> = [
        "kupon", "kupon kodu", "indirim kodu", "promosyon kodu",
        "hediye ceki", "bonus puan", "cekilise katil",
    ]

    public static let gambling: Set<String> = [
        "bahis", "bonus", "casino", "kumar", "iddaa", "rulet", "slot", "poker",
        "jackpot", "deneme bonusu", "yatirim bonusu", "cevrimsiz",
        "giris adresi", "guncel giris", "bet", "kayip bonusu", "freespin", "kacak",
    ]

    public static let urgency: Set<String> = [
        "24 saat", "48 saat", "son uyari", "son kez", "acilen", "acil",
        "hemen", "derhal", "aksi halde", "aksi takdirde", "kapatilacak",
        "iptal edilecek", "el konulacak", "haciz", "yasal islem",
        "gecikmeden", "sinirli sure", "bugun son", "kacirmayin",
    ]

    /// Bu kelimeler geçiyorsa gönderen doğrulanmış bir kurumsal başlık
    /// OLMALIDIR. Değilse taklit sayılır — Türkiye'deki en yaygın kalıp.
    public static let institutions: Set<String> = [
        "icra dairesi", "icra mudurlugu", "emniyet", "savcilik", "mahkeme",
        "vergi dairesi", "sgk", "e-devlet", "edevlet", "nufus mudurlugu",
        "banka", "bankasi", "merkez bankasi", "gelir idaresi",
        "adalet bakanligi", "kargo sirketi", "tapu", "noter",
    ]

    public static let prize: Set<String> = [
        "kazandiniz", "tebrikler", "odulunuz", "odul kazandiniz",
        "hediyeniz var", "cekilis kazanani", "sansli", "ucretsiz iphone",
        "para odulu", "buyuk ikramiye",
    ]

    /// Vishing (geri arama tuzağı): SMS bağlantı taşımaz, kurbanı SAHTE bir
    /// numarayı aramaya yönlendirir — motorun "bağlantı yok = güvenli"
    /// varsayımını BİLEREK istismar eder.
    public static let callback: Set<String> = [
        "bu numarayi arayin", "numarayi hemen arayin", "hemen arayin",
        "dogrulamak icin arayin", "arayarak dogrulayin", "bu hatti arayin",
        "guvenlik hattini arayin", "musteri temsilcimizi arayin",
    ]

    /// "Tanıdık taklidi" (evlat/yakın dolandırıcılığı): dolandırıcı bilinmeyen
    /// bir numaradan bir yakınmışçasına yazar (telefon/numara değişti
    /// gerekçesiyle), sonra acilen para/havale ister. İKİ BAĞIMSIZ grup
    /// birlikte arandığı için günlük "IBAN'ımı atıyorum" mesajlarıyla
    /// karışma riski düşük.
    public static let contactChange: Set<String> = [
        "telefonum kirildi", "telefonum dustu", "telefonumu kaybettim",
        "numaram degisti", "yeni numaramdan yaziyorum", "bu numaradan yaziyorum",
        "hattim degisti", "eski numarama ulasamiyorum",
    ]
    public static let moneyRequest: Set<String> = [
        "acil param lazim", "acil paraya ihtiyacim", "borc para",
        "gonderir misin", "havale yapar misin", "ibanimi atiyorum",
        "hesabima gonder", "ibanima gonder", "para gonderir misin",
    ]

    /// Sahte iş/yatırım vaadi + üçüncü kanala (Telegram/WhatsApp) yönlendirme.
    /// `redirect` TEK BAŞINA zayıf sinyaldir (bir işletme meşru şekilde
    /// "Telegram kanalımız" diyebilir) — bu yüzden `easyMoney` ile birlikte
    /// arandığı için ağırlık görece düşük tutuldu.
    public static let redirectChannel: Set<String> = [
        "telegram", "whatsapp grubu", "whatsapptan yazin", "wa.me", "t.me",
    ]
    public static let easyMoney: Set<String> = [
        "kazanc firsati", "ek gelir", "ev hanimlarina", "evden calis",
        "gunluk kazan", "danismanimizdan", "uzman danisman",
        "garanti kazanc", "yatirim danismanligi",
    ]

    public static let shorteners: Set<String> = [
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "cutt.ly", "rb.gy", "shorturl.at", "rebrand.ly", "s.id", "tiny.cc",
        "bit.do", "u.to", "v.gd", "shrtco.de", "gg.gg", "kisa.link", "adf.ly",
    ]

    public static let riskyTLDs: Set<String> = [
        "top", "xyz", "icu", "click", "link", "live", "online", "site",
        "website", "shop", "buzz", "cyou", "sbs", "rest", "quest",
        "monster", "bar", "cfd",
    ]

    /// Türkçe metin işareti — ASCII kaçınma tespitinde kullanılır.
    public static let turkishMarkers: Set<String> = [
        "icin", "ile", "olarak", "tarihinde", "sayin", "hesabiniz",
        "adiniza", "islem", "odeme", "guncel",
    ]
}
