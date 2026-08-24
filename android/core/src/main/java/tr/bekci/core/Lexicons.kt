package tr.bekci.core

/**
 * Türkçe metin normalizasyonu ve sözlükler.
 * Swift karşılığı: `ios/BekciCore/Sources/BekciCore/Lexicons.swift`
 *
 * İki taraf birebir aynı olmak zorunda; ayrışırsa `shared/golden.json`
 * testi kırılır.
 */
object TurkishText {

    /**
     * Türkçe'ye duyarlı küçültme + aksan katlama.
     *
     * Kotlin'in `lowercase()`'i varsayılan locale'e göre davranır ve
     * cihazın diline göre 'I' → 'i' ya da 'ı' üretebilir. Bu, aynı mesajın
     * farklı telefonlarda farklı sınıflandırılması demektir. Bu yüzden
     * dönüşüm elle, locale'den bağımsız yapılıyor.
     */
    fun fold(s: String): String {
        val out = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                'ı', 'İ', 'I', 'i' -> out.append('i')
                'ş', 'Ş' -> out.append('s')
                'ğ', 'Ğ' -> out.append('g')
                'ü', 'Ü' -> out.append('u')
                'ö', 'Ö' -> out.append('o')
                'ç', 'Ç' -> out.append('c')
                // Görünmez boşluklar: NBSP, dar NBSP, rakam boşluğu, BOM.
                // Regex lehçeleri bunları farklı sayar; kaynakta tekilleştiriyoruz.
                '\u00a0', '\u202f', '\u2007', '\uFEFF' -> out.append(' ')
                'â', 'Â' -> out.append('a')
                'î', 'Î' -> out.append('i')
                'û', 'Û' -> out.append('u')
                else -> out.append(ch.lowercaseChar())
            }
        }
        return out.toString()
    }

    val TURKISH_CHARACTERS: Set<Char> =
        setOf('ı', 'İ', 'ş', 'Ş', 'ğ', 'Ğ', 'ü', 'Ü', 'ö', 'Ö', 'ç', 'Ç')

    /**
     * Katlanmış metinde sözlük araması. Tek kelimeler sözcük başı sınırıyla
     * aranır ("bahis" → "bahisler" eşleşir, "kabahat" eşleşmez); boşluk
     * içeren ifadeler doğrudan alt dizi olarak aranır.
     *
     * Sonuç sıralı döner — gerekçe metninde `first()` kullanıldığı için
     * platformlar arası tutarlılık buna bağlı.
     */
    fun matches(folded: String, lexicon: Set<String>): List<String> {
        val hits = ArrayList<String>()
        for (term in lexicon) {
            if (term.contains(" ")) {
                if (folded.contains(term)) hits += term
            } else if (hasWordPrefix(folded, term)) {
                hits += term
            }
        }
        hits.sort()
        return hits
    }

    private fun hasWordPrefix(haystack: String, term: String): Boolean {
        if (term.isEmpty()) return false
        var from = 0
        while (true) {
            val index = haystack.indexOf(term, from)
            if (index < 0) return false
            // Kod noktası üzerinden kontrol: BMP dışı bir karakter (emoji)
            // hemen solda olduğunda Char.isLetterOrDigit() vekil karakteri
            // görür ve Swift'ten ayrışırdı.
            val prevOk = index == 0 ||
                !Character.isLetterOrDigit(haystack.codePointBefore(index))
            if (prevOk) return true
            from = index + term.length
            if (from >= haystack.length) return false
        }
    }
}

object Lex {

    enum class Group { BANK, CARGO, CARRIER, PUBLIC_SECTOR, RETAIL }

    /**
     * Doğrulanmış alfanumerik gönderici başlıkları ve grupları.
     * Grup, mesaj metni kategoriye oturmadığında ön bilgi olarak kullanılır.
     * Anahtarlar `UserRules.key()` formatındadır: katlanmış, tiresiz, boşluksuz.
     */
    val trustedHeaders: Map<String, Group> = buildMap {
        listOf(
            "garanti", "garantibbva", "isbank", "isbankasi", "akbank", "yapikredi",
            "ziraat", "ziraatbank", "halkbank", "vakifbank", "denizbank", "teb",
            "qnbfinansbank", "ingbank", "enpara", "papara", "ininal", "tosla",
            "iyzico", "sekerbank", "odeabank", "fibabanka", "burganbank",
            "kuveytturk", "albarakaturk", "turkiyefinans", "vakifkatilim",
            "ziraatkatilim",
        ).forEach { put(it, Group.BANK) }

        listOf(
            "yurticikargo", "araskargo", "mngkargo", "suratkargo", "ptkargo",
            "hepsijet", "trendyolexpress", "sendeo", "kolaygelsin", "ceva",
            "dhl", "ups",
        ).forEach { put(it, Group.CARGO) }

        listOf(
            "turkcell", "vodafone", "turktelekom", "ttmobil", "bimcell", "pttcell",
        ).forEach { put(it, Group.CARRIER) }

        listOf(
            "edevlet", "mhrs", "gib", "sgk", "nvi", "afad", "meb", "ptt", "uyap",
            "esaglik", "enabiz", "iys", "btk", "tcdd", "ibb",
        ).forEach { put(it, Group.PUBLIC_SECTOR) }

        listOf(
            "trendyol", "hepsiburada", "getir", "yemeksepeti", "migros", "a101",
            "bim", "sok", "n11", "amazon", "mediamarkt", "teknosa", "vatan",
            "defacto", "lcwaikiki", "boyner", "decathlon", "ikea",
        ).forEach { put(it, Group.RETAIL) }
    }

    /** Grubun tek başına ima ettiği kategori. RETAIL yok — içerik karar verir. */
    val groupPrior: Map<Group, Pair<FilterAction, FilterSubAction>> = mapOf(
        Group.BANK to (FilterAction.TRANSACTION to FilterSubAction.TRANSACTIONAL_FINANCE),
        Group.CARGO to (FilterAction.TRANSACTION to FilterSubAction.TRANSACTIONAL_ORDERS),
        Group.CARRIER to (FilterAction.TRANSACTION to FilterSubAction.TRANSACTIONAL_CARRIER),
        Group.PUBLIC_SECTOR to (FilterAction.TRANSACTION to FilterSubAction.NONE),
    )

    val finance = setOf(
        "kart", "harcama", "bakiye", "hesab", "havale", "eft", "iban", "kredi",
        "taksit", "ekstre", "borc", "odeme", "tahsilat", "pos", "atm", "faiz",
        "vadeli", "yatirim", "fon", "hisse", "doviz", "islem", "limit", "puan",
    )

    val orders = setOf(
        "kargo", "gonderi", "siparis", "teslim", "sube", "kurye", "takip",
        "paket", "iade", "dagitim", "zimmet", "barkod",
    )

    val carrier = setOf(
        "paket", "internet", "gb", "dakika", "kontor", "fatura", "tarife",
        "hat", "abonelik", "kullanim", "yenilendi", "kalan", "aidat",
    )

    val health = setOf(
        "randevu", "mhrs", "hastane", "poliklinik", "recete", "asi",
        "tahlil", "eczane",
    )

    val publicServices = setOf(
        "edevlet", "vergi", "beyanname", "harc", "trafik", "ceza", "nufus",
        "adres", "secmen", "askerlik", "uyap", "icra", "tebligat",
    )

    val promoOffers = setOf(
        "indirim", "kampanya", "firsat", "sepet", "fiyat", "avantaj",
        "ucretsiz", "kargo bedava", "son gun", "net", "hediye",
    )

    val promoCoupons = setOf(
        "kupon", "kupon kodu", "indirim kodu", "promosyon kodu",
        "hediye ceki", "bonus puan", "cekilise katil",
    )

    val gambling = setOf(
        "bahis", "bonus", "casino", "kumar", "iddaa", "rulet", "slot", "poker",
        "jackpot", "deneme bonusu", "yatirim bonusu", "cevrimsiz",
        "giris adresi", "guncel giris", "bet", "kayip bonusu", "freespin", "kacak",
    )

    val urgency = setOf(
        "24 saat", "48 saat", "son uyari", "son kez", "acilen", "acil",
        "hemen", "derhal", "aksi halde", "aksi takdirde", "kapatilacak",
        "iptal edilecek", "el konulacak", "haciz", "yasal islem",
        "gecikmeden", "sinirli sure", "bugun son", "kacirmayin",
    )

    /**
     * Bu kelimeler geçiyorsa gönderen doğrulanmış bir kurumsal başlık
     * olmalıdır. Değilse taklit sayılır — Türkiye'deki en yaygın kalıp.
     */
    val institutions = setOf(
        "icra dairesi", "icra mudurlugu", "emniyet", "savcilik", "mahkeme",
        "vergi dairesi", "sgk", "e-devlet", "edevlet", "nufus mudurlugu",
        "banka", "bankasi", "merkez bankasi", "gelir idaresi",
        "adalet bakanligi", "kargo sirketi", "tapu", "noter",
    )

    val prize = setOf(
        "kazandiniz", "tebrikler", "odulunuz", "odul kazandiniz",
        "hediyeniz var", "cekilis kazanani", "sansli", "ucretsiz iphone",
        "para odulu", "buyuk ikramiye",
    )

    /**
     * Vishing (geri arama tuzağı): SMS bağlantı taşımaz, kurbanı SAHTE bir
     * numarayı aramaya yönlendirir — motorun "bağlantı yok = güvenli"
     * varsayımını BİLEREK istismar eder.
     */
    val callback = setOf(
        "bu numarayi arayin", "numarayi hemen arayin", "hemen arayin",
        "dogrulamak icin arayin", "arayarak dogrulayin", "bu hatti arayin",
        "guvenlik hattini arayin", "musteri temsilcimizi arayin",
    )

    /**
     * "Tanıdık taklidi" (evlat/yakın dolandırıcılığı): dolandırıcı bilinmeyen
     * bir numaradan bir yakınmışçasına yazar (telefon/numara değişti
     * gerekçesiyle), sonra acilen para/havale ister. İKİ BAĞIMSIZ grup
     * birlikte arandığı için günlük "IBAN'ımı atıyorum" mesajlarıyla
     * karışma riski düşük.
     */
    val contactChange = setOf(
        "telefonum kirildi", "telefonum dustu", "telefonumu kaybettim",
        "numaram degisti", "yeni numaramdan yaziyorum", "bu numaradan yaziyorum",
        "hattim degisti", "eski numarama ulasamiyorum",
    )
    val moneyRequest = setOf(
        "acil param lazim", "acil paraya ihtiyacim", "borc para",
        "gonderir misin", "havale yapar misin", "ibanimi atiyorum",
        "hesabima gonder", "ibanima gonder", "para gonderir misin",
    )

    /**
     * Sahte iş/yatırım vaadi + üçüncü kanala (Telegram/WhatsApp) yönlendirme.
     * `redirectChannel` TEK BAŞINA zayıf sinyaldir — bu yüzden `easyMoney`
     * ile birlikte arandığı için ağırlık görece düşük tutuldu.
     */
    val redirectChannel = setOf("telegram", "whatsapp grubu", "whatsapptan yazin", "wa.me", "t.me")
    val easyMoney = setOf(
        "kazanc firsati", "ek gelir", "ev hanimlarina", "evden calis",
        "gunluk kazan", "danismanimizdan", "uzman danisman",
        "garanti kazanc", "yatirim danismanligi",
    )

    val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "cutt.ly", "rb.gy", "shorturl.at", "rebrand.ly", "s.id", "tiny.cc",
        "bit.do", "u.to", "v.gd", "shrtco.de", "gg.gg", "kisa.link", "adf.ly",
    )

    val riskyTlds = setOf(
        "top", "xyz", "icu", "click", "link", "live", "online", "site",
        "website", "shop", "buzz", "cyou", "sbs", "rest", "quest",
        "monster", "bar", "cfd",
    )

    /** Türkçe metin işareti — ASCII kaçınma tespitinde kullanılır. */
    val turkishMarkers = setOf(
        "icin", "ile", "olarak", "tarihinde", "sayin", "hesabiniz",
        "adiniza", "islem", "odeme", "guncel",
    )
}
