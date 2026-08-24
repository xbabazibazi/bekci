package tr.bekci.core

/**
 * Bekçi sınıflandırma motoru — Swift uygulamasının birebir karşılığı.
 *
 * Bu dosya `ios/BekciCore/Sources/BekciCore/Classifier.swift` ile aynı
 * davranışı üretmek zorundadır. Sözleşme `shared/golden.json`'dır; her iki
 * taraf da aynı fixture'ı test eder. Bir ağırlığı burada değiştirirsen
 * Swift ve Python referansını da güncellemen gerekir.
 *
 * Android tarafında `core` modülü saf bir JVM kütüphanesidir — Android
 * SDK'sına bağımlı değildir, böylece testleri emülatörsüz ve saniyeler
 * içinde çalışır.
 */

// ─────────────────────────────────────────────────────────────
// Karar tipleri
// ─────────────────────────────────────────────────────────────

enum class FilterAction(val raw: String) {
    /** Karar veremedim; sistem mesajı bildiği gibi göstersin. */
    NONE("none"),
    ALLOW("allow"),
    JUNK("junk"),
    PROMOTION("promotion"),
    TRANSACTION("transaction");
}

enum class FilterSubAction(val raw: String) {
    NONE("none"),
    TRANSACTIONAL_FINANCE("transactionalFinance"),
    TRANSACTIONAL_ORDERS("transactionalOrders"),
    TRANSACTIONAL_CARRIER("transactionalCarrier"),
    PROMOTIONAL_OFFERS("promotionalOffers"),
    PROMOTIONAL_COUPONS("promotionalCoupons");
}

/**
 * iOS'ta en fazla 5 alt-aksiyon beyan edilebiliyor. Android'de böyle bir
 * sınır yok ama iki platformun aynı kategorileri üretmesi için liste
 * bilinçli olarak burada da uygulanıyor — kullanıcı telefon değiştirdiğinde
 * aynı kutuları görmeli.
 */
val DECLARED_SUB_ACTIONS: Set<FilterSubAction> = setOf(
    FilterSubAction.TRANSACTIONAL_FINANCE,
    FilterSubAction.TRANSACTIONAL_ORDERS,
    FilterSubAction.TRANSACTIONAL_CARRIER,
    FilterSubAction.PROMOTIONAL_OFFERS,
    FilterSubAction.PROMOTIONAL_COUPONS,
)

enum class Sensitivity(val raw: String, val junkAt: Int, val grayAt: Int) {
    CAREFUL("careful", 78, 55),
    BALANCED("balanced", 62, 42),
    STRICT("strict", 48, 32);

    val title: String
        get() = when (this) {
            CAREFUL -> "Temkinli"
            BALANCED -> "Dengeli"
            STRICT -> "Sıkı"
        }

    val subtitle: String
        get() = when (this) {
            CAREFUL -> "Emin olmadığında dokunmaz. Önerilen."
            BALANCED -> "Şüphelileri kampanya olarak işaretler."
            STRICT -> "Daha çok yakalar, yanlış pozitif riski artar."
        }
}

enum class SenderKind(val raw: String) {
    ALPHA("alpha"),
    SHORTCODE("shortcode"),
    TR_MOBILE("trMobile"),
    TR_NON_GEO("trNonGeo"),
    TR_GEO("trGeo"),
    INTERNATIONAL("international"),
    UNKNOWN("unknown");

    val label: String
        get() = when (this) {
            ALPHA -> "Alfanumerik başlık"
            SHORTCODE -> "Kısa numara"
            TR_MOBILE -> "Cep numarası"
            TR_NON_GEO -> "Hizmet numarası"
            TR_GEO -> "Sabit hat"
            INTERNATIONAL -> "Yurt dışı numarası"
            UNKNOWN -> "Bilinmiyor"
        }
}

data class Reason(
    val code: String,
    val title: String,
    val detail: String,
    val weight: Int,
)

data class Verdict(
    val action: FilterAction,
    val subAction: FilterSubAction = FilterSubAction.NONE,
    val risk: Int,
    val reasons: List<Reason> = emptyList(),
    val senderKind: SenderKind = SenderKind.UNKNOWN,
) {
    val isFraud: Boolean get() = action == FilterAction.JUNK && risk >= 70
}

/** Kullanıcının kendi kuralları. Model kararlarını her koşulda ezer. */
data class UserRules(
    val allowSenders: Set<String> = emptySet(),
    val blockSenders: Set<String> = emptySet(),
    val blockKeywords: Set<String> = emptySet(),
    val sensitivity: Sensitivity = Sensitivity.CAREFUL,
) {
    companion object {
        val DEFAULT = UserRules()

        /** Kural eşleşmesinde kullanılan normalize gönderen anahtarı. */
        fun key(sender: String): String =
            TurkishText.fold(sender).replace(" ", "").replace("-", "")
    }
}

// ─────────────────────────────────────────────────────────────
// Motor
// ─────────────────────────────────────────────────────────────

class Classifier(private val rules: UserRules = UserRules.DEFAULT) {

    fun classify(
        sender: String?,
        body: String?,
        @Suppress("UNUSED_PARAMETER") receiverCountry: String? = "TR",
    ): Verdict {
        val raw = body ?: ""
        val folded = TurkishText.fold(raw)
        val kind = senderKind(sender)
        val senderKey = sender?.let { UserRules.key(it) } ?: ""

        val reasons = mutableListOf<Reason>()
        fun add(code: String, title: String, detail: String, weight: Int) {
            reasons += Reason(code, title, detail, weight)
        }

        // ── 1. Kullanıcı kuralları her şeyi ezer ─────────────────────
        val group = if (kind == SenderKind.ALPHA) Lex.trustedHeaders[senderKey] else null

        if (senderKey.isNotEmpty() && senderKey in rules.allowSenders) {
            val sub = category(folded, Lex.trustedHeaders[senderKey]).second
            return Verdict(
                FilterAction.ALLOW, declared(sub), 0,
                listOf(
                    Reason(
                        "userAllow", "Sizin güvenli listenizde",
                        "Bu göndereni her zaman güven olarak işaretlediniz.", -100
                    )
                ),
                kind,
            )
        }

        if (senderKey.isNotEmpty() && senderKey in rules.blockSenders) {
            return Verdict(
                FilterAction.JUNK, FilterSubAction.NONE, 100,
                listOf(
                    Reason("userBlock", "Sizin engel listenizde",
                        "Bu göndereni engellemiştiniz.", 100)
                ),
                kind,
            )
        }

        for (keyword in rules.blockKeywords.sorted()) {
            if (keyword.isNotEmpty() && folded.contains(keyword)) {
                return Verdict(
                    FilterAction.JUNK, FilterSubAction.NONE, 100,
                    listOf(
                        Reason("userKeyword", "Engellediğiniz kelime",
                            "Mesajda “$keyword” geçiyor.", 100)
                    ),
                    kind,
                )
            }
        }

        val urls = hosts(raw)
        val hasIpUrl = IP_URL.containsMatchIn(raw)
        val hasUrl = urls.isNotEmpty() || hasIpUrl
        val trusted = group != null
        val hasBCode = B_CODE.containsMatchIn(raw)

        // ── 2. OTP koruması ──────────────────────────────────────────
        // Bu üründe en pahalı hata banka doğrulama kodunu çöpe atmaktır.
        if (OTP.containsMatchIn(folded) && !hasUrl && !CODE_HARVEST.containsMatchIn(folded)) {
            add("otp", "Doğrulama kodu",
                "Bağlantı içermeyen tek kullanımlık kod mesajı.", -60)
            val financeHit = TurkishText.matches(folded, Lex.finance).isNotEmpty()
            val sub = if (financeHit || group == Lex.Group.BANK)
                FilterSubAction.TRANSACTIONAL_FINANCE else FilterSubAction.NONE
            return Verdict(FilterAction.TRANSACTION, declared(sub), 0, reasons.toList(), kind)
        }

        // Kesin karar: numarası "değişen" bir yakın gibi davranıp acil para/
        // havale isteyen mesaj — CODE_HARVEST ile aynı ruhta, duyarlılık
        // eşiklerini beklemeden. İki bağımsız ifade grubu birlikte arandığı
        // için düşük yanlış-pozitif, zarar geri alınamaz olduğu için doğrudan uyarı.
        if (TurkishText.matches(folded, Lex.contactChange).isNotEmpty() &&
            TurkishText.matches(folded, Lex.moneyRequest).isNotEmpty()
        ) {
            return Verdict(
                FilterAction.JUNK, FilterSubAction.NONE, 92,
                listOf(
                    Reason(
                        "impostorContact", "Tanıdık taklidi",
                        "Numarası değişen bir yakınınız gibi davranıp acilen para istiyor. " +
                            "Göndermeden önce mutlaka o kişiyi bildiğiniz eski numarasından arayarak doğrulayın.",
                        92
                    )
                ),
                kind,
            )
        }

        // ── 3. Risk sinyalleri ───────────────────────────────────────
        var risk = 0

        urls.filter { it in Lex.shorteners }.sorted().firstOrNull()?.let {
            risk += 30
            add("shortener", "Kısaltılmış bağlantı",
                "$it adresi gerçek hedefi gizliyor. Kurumlar kısaltıcı kullanmaz.", 30)
        }

        if (hasIpUrl) {
            risk += 35
            add("ipUrl", "Sayısal adres",
                "Bağlantı alan adı yerine doğrudan IP adresine gidiyor.", 35)
        }

        if (urls.any { it.startsWith("xn--") || it.contains(".xn--") }) {
            risk += 35
            add("punycode", "Sahte alfabe",
                "Bağlantıda tanıdık harflere benzeyen farklı karakterler kullanılmış.", 35)
        }

        urls.filter { tld(it) in Lex.riskyTlds }.sorted().firstOrNull()?.let {
            risk += 18
            add("riskyTld", "Şüpheli alan adı uzantısı",
                "“.${tld(it)}” uzantısı dolandırıcılıkta sık kullanılıyor.", 18)
        }

        if (hasUrl && kind in setOf(
                SenderKind.TR_MOBILE, SenderKind.TR_GEO,
                SenderKind.INTERNATIONAL, SenderKind.UNKNOWN)
        ) {
            risk += 22
            add("urlFromNumber", "Sıradan numaradan bağlantı",
                "Kurumsal gönderim yerine kişisel görünümlü bir numaradan bağlantı geldi.", 22)
        }

        if (kind == SenderKind.INTERNATIONAL && hasUrl) {
            risk += 20
            add("intlUrl", "Yurt dışı kaynaklı",
                "Yurt dışı numarasından bağlantı içeren mesaj.", 20)
        }

        if (kind == SenderKind.TR_NON_GEO && hasUrl) {
            risk += 14
            add("nonGeoUrl", "0850 hattından bağlantı",
                "Hizmet numarasından gelen bağlantılı toplu gönderim.", 14)
        }

        // Bonuslar (birden fazla ifade) Reason.weight'e DE yazılır: aksi hâlde
        // gerekçelerin toplamı risk skorunu tutmuyor ve topReasons() aciliyet/
        // bahis sinyalini hak ettiğinden düşük sıralıyordu.
        val gambling = TurkishText.matches(folded, Lex.gambling)
        gambling.firstOrNull()?.let {
            val agirlik = 38 + (if (gambling.toSet().size >= 2) 12 else 0)
            risk += agirlik
            add("gambling", "Bahis/kumar içeriği",
                "Mesajda “$it” gibi yasa dışı bahis ifadeleri var.", agirlik)
        }

        val prize = TurkishText.matches(folded, Lex.prize)
        prize.firstOrNull()?.let {
            risk += 26
            add("prize", "Ödül vaadi",
                "“$it” gibi kazanç vaatleri dolandırıcılığın en yaygın kancasıdır.", 26)
        }

        val institutions = TurkishText.matches(folded, Lex.institutions)
        if (institutions.isNotEmpty() && !trusted) {
            risk += 30
            add("impersonation", "Resmî kurum taklidi",
                "“${institutions.first()}” adına yazılmış ama gönderen kayıtlı bir kurumsal başlık değil.", 30)
        }

        // Vishing: "bu numarayı arayın" bağlantı GEREKTİRMEZ. `!trusted`
        // koşulu, doğrulanmış bir kurumdan gelen "444 0 333'ü arayın" gibi
        // meşru yönlendirmeleri etkilemez.
        if (TurkishText.matches(folded, Lex.callback).isNotEmpty() && !trusted) {
            risk += 35
            add("callbackPhishing", "Sizi aramanızı istiyor",
                "Doğrulama için bir telefon numarasını aramanızı istiyor — bankalar bunu SMS ile istemez, " +
                    "şüpheniz varsa kartınızın arkasındaki resmî numarayı siz arayın.", 35)
        }

        // Kurumsal bir işlem anlatıyor ama gönderen doğrulanmış değil ve
        // mesaj bağlantı taşıyor — Türkiye'deki en yaygın oltalama kalıbı.
        if (hasUrl && !trusted && kind != SenderKind.SHORTCODE) {
            val businessHits = TurkishText.matches(folded, Lex.orders) +
                TurkishText.matches(folded, Lex.finance) +
                TurkishText.matches(folded, Lex.publicServices)
            if (businessHits.size >= 2) {
                risk += 22
                add("orgClaimFromStranger", "Kurumsal içerik, tanınmayan gönderen",
                    "Mesaj bir kurum işlemi anlatıyor ama kayıtlı kurumsal başlıktan gelmiyor.", 22)
            }
        }

        val urgency = TurkishText.matches(folded, Lex.urgency)
        urgency.firstOrNull()?.let {
            val agirlik = 14 + (if (urgency.toSet().size >= 2) 10 else 0)
            risk += agirlik
            add("urgency", "Aciliyet baskısı",
                "“$it” gibi ifadeler düşünmenizi engellemek için kullanılıyor.", agirlik)
        }

        if (TurkishText.matches(folded, Lex.redirectChannel).isNotEmpty() &&
            TurkishText.matches(folded, Lex.easyMoney).isNotEmpty()
        ) {
            // 58: link taşımayan "Telegram'dan yazın" varyantı bile (noUrl -10
            // sonrası net 48) sıkı modda junk_at'e (48) tam otursun diye — ama
            // careful/balanced'te TEK BAŞINA hâlâ junk_at'e ulaşmaz. "telegram"
            // tek kelime olarak meşru işletme mesajında da geçebileceği için
            // varsayılan modda kesin karar vermiyoruz.
            risk += 58
            add("thirdPartyRedirect", "Denetimsiz kanala yönlendiriyor",
                "Kazanç vaadiyle Telegram/WhatsApp gibi bir kanala yönlendiriyor — " +
                    "meşru bir iş teklifi sizi SMS filtresinin göremeyeceği bir yere taşımaya çalışmaz.", 58)
        }

        // ASCII kaçınma: Türkçe metin ama hiç Türkçe karakter yok.
        // Uzunluk KOD NOKTASI olarak ölçülür: `length` UTF-16 birimi sayar,
        // Swift'in `count`'u grafem kümesi. Emoji içeren bir mesajda (spam'de
        // çok yaygın) iki sayı ikiye katlanacak kadar ayrışır ve 40 eşiği iki
        // platformda farklı yerde tetiklenirdi.
        if (raw.codePointCount(0, raw.length) > 40 &&
            raw.none { it in TurkishText.TURKISH_CHARACTERS }
        ) {
            if (TurkishText.matches(folded, Lex.turkishMarkers).size >= 2) {
                risk += 16
                add("asciiEvasion", "Türkçe karakter yok",
                    "Filtrelerden kaçmak için mesaj kasıtlı olarak Türkçe harfsiz yazılmış.", 16)
            }
        }

        // Harf/büyük harf sayımı KOD NOKTASI üzerinden yürür. `raw.filter {
        // it.isLetter() }.length` UTF-16 birimi sayıyordu: BMP dışı harflerde
        // (spam'de filtre kaçınmak için kullanılan matematiksel kalın
        // "𝐊𝐀𝐙𝐀𝐍𝐃𝐈𝐍𝐈𝐙" gibi) her harf iki birim sayılıyor VE vekil yarıları
        // isUpperCase() göremediği için oran çöküyordu — kural Android'de
        // sessizce çalışmıyor, iOS'ta çalışıyordu.
        var harfSayisi = 0
        var buyukSayisi = 0
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (Character.isLetter(cp)) {
                harfSayisi++
                if (Character.isUpperCase(cp)) buyukSayisi++
            }
            i += Character.charCount(cp)
        }
        if (harfSayisi > 40 && buyukSayisi.toDouble() / harfSayisi > 0.75) {
            risk += 8
            add("shouting", "Tamamı büyük harf",
                "Dikkat çekmek için mesajın tamamı büyük yazılmış.", 8)
        }

        // Kesin karar: meşru hiçbir gönderen doğrulama kodunuzu geri istemez.
        if (CODE_HARVEST.containsMatchIn(folded)) {
            return Verdict(
                FilterAction.JUNK, FilterSubAction.NONE, 95,
                listOf(
                    Reason("codeHarvest", "Doğrulama kodunuzu istiyor",
                        "Hiçbir banka, kurum veya şirket size gönderilen kodu geri istemez. " +
                            "Bu mesaj hesabınızı ele geçirmeye çalışıyor.", 95)
                ),
                kind,
            )
        }

        if (kind == SenderKind.ALPHA && !hasBCode && hasUrl && !trusted) {
            risk += 12
            add("noBCode", "B kodu yok",
                "Yasal toplu gönderimlerde bulunması gereken operatör kodu eksik.", 12)
        }

        // ── 4. Güven azaltıcılar ─────────────────────────────────────
        if (trusted && hasBCode) {
            risk -= 45
            add("verifiedHeader", "Doğrulanmış gönderen",
                "Tanınan kurumsal başlık ve geçerli operatör B kodu.", -45)
        } else if (trusted) {
            // -18, -45 değil: Türkiye'de alfanumerik başlık TAKLİT EDİLEBİLİR
            // ve BTK yasal toplu gönderimde B kodunu zorunlu kılıyor. B kodu
            // olmayan bir "MIGROS" başlığı zayıf kanıttır; 78'lik çöp eşiğinin
            // karşısına -30 koymak, bit.ly + ödül vaadi + aciliyet taşıyan bir
            // mesajı varsayılan modda "işlem mesajı" saydırıyordu.
            // Meşru gönderene zarar vermez: URL/taklit sinyallerinin çoğu
            // zaten `!trusted` koşuluna bağlı.
            risk -= 18
            add("knownHeader", "Tanınan gönderen",
                "Bu başlık bilinen kurumlar listesinde ama operatör B kodu yok.", -18)
        }

        if (!hasUrl) {
            risk -= 10
            add("noUrl", "Bağlantı yok", "Mesajda tıklanabilir bir adres bulunmuyor.", -10)
        }

        if (kind == SenderKind.SHORTCODE && !hasUrl) {
            risk -= 12
            add("shortCode", "Kısa numara",
                "Operatör onaylı kısa numaradan gönderilmiş.", -12)
        }

        risk = risk.coerceIn(0, 100)

        // ── 5. Karar ─────────────────────────────────────────────────
        val (catAction, catSub) = category(folded, group)

        if (risk >= rules.sensitivity.junkAt) {
            return Verdict(FilterAction.JUNK, FilterSubAction.NONE, risk, topReasons(reasons), kind)
        }

        if (risk >= rules.sensitivity.grayAt) {
            // Gri bant: çöp demeye yetecek kadar emin değiliz.
            if (catAction == FilterAction.PROMOTION) {
                return Verdict(FilterAction.PROMOTION, declared(catSub), risk, topReasons(reasons), kind)
            }
            return Verdict(FilterAction.NONE, FilterSubAction.NONE, risk, topReasons(reasons), kind)
        }

        if (catAction != FilterAction.NONE) {
            return Verdict(catAction, declared(catSub), risk, topReasons(reasons), kind)
        }

        return Verdict(FilterAction.NONE, FilterSubAction.NONE, risk, topReasons(reasons), kind)
    }

    companion object {

        fun declared(sub: FilterSubAction): FilterSubAction =
            if (sub in DECLARED_SUB_ACTIONS) sub else FilterSubAction.NONE

        /**
         * Gerekçe sıralaması **kararlı** olmalı. `sortedByDescending` Kotlin'de
         * kararlıdır (stable sort), yani aynı ağırlıktaki iki gerekçe ekleniş
         * sırasını korur — Swift tarafında bu davranış elle sağlanıyor.
         */
        fun topReasons(reasons: List<Reason>): List<Reason> {
            val positives = reasons.filter { it.weight > 0 }
            if (positives.isNotEmpty()) {
                return positives.sortedByDescending { it.weight }.take(5)
            }
            return reasons.sortedBy { it.weight }.take(3)
        }

        fun category(folded: String, group: Lex.Group?): Pair<FilterAction, FilterSubAction> {
            // Sıra sabittir; eşitlikte listedeki ilk sıra kazanır.
            val scored = listOf(
                Triple(FilterAction.TRANSACTION, FilterSubAction.TRANSACTIONAL_FINANCE,
                    TurkishText.matches(folded, Lex.finance).size),
                Triple(FilterAction.TRANSACTION, FilterSubAction.TRANSACTIONAL_ORDERS,
                    TurkishText.matches(folded, Lex.orders).size),
                Triple(FilterAction.TRANSACTION, FilterSubAction.TRANSACTIONAL_CARRIER,
                    TurkishText.matches(folded, Lex.carrier).size),
                Triple(FilterAction.TRANSACTION, FilterSubAction.NONE,
                    TurkishText.matches(folded, Lex.health).size +
                        TurkishText.matches(folded, Lex.publicServices).size),
                Triple(FilterAction.PROMOTION, FilterSubAction.PROMOTIONAL_COUPONS,
                    TurkishText.matches(folded, Lex.promoCoupons).size * 2),
                Triple(FilterAction.PROMOTION, FilterSubAction.PROMOTIONAL_OFFERS,
                    TurkishText.matches(folded, Lex.promoOffers).size),
            )
            // maxByOrNull ilk maksimumu döndürür. Swift'in max(by:) ve
            // Python'un max() davranışı da eşitlikte ilkini korur — üç
            // uygulamanın aynı kategoriyi seçmesi buna bağlı.
            val best = scored.maxByOrNull { it.third }
                ?: return FilterAction.NONE to FilterSubAction.NONE

            if (best.third >= 2) return best.first to best.second

            // İçerik kararsız; doğrulanmış kurumsal başlık tek başına kanıttır.
            if (group != null) {
                if (best.third == 1 && group == Lex.Group.RETAIL) return best.first to best.second
                Lex.groupPrior[group]?.let { return it }
                if (best.third == 1) return best.first to best.second
            }
            return FilterAction.NONE to FilterSubAction.NONE
        }

        /**
         * Yalnızca ASCII rakam. `Char.isDigit()` Unicode ondalık rakamları
         * (٠١٢…) da kabul eder, Swift'in `isNumber`'ı ayrıca "²"/"½"yi sayar;
         * referans `[0-9]` kullanıyor. Şebekeden gelen numara ASCII'dir.
         */
        private fun isDigit(c: Char): Boolean = c in '0'..'9'

        fun senderKind(sender: String?): SenderKind {
            if (sender.isNullOrEmpty()) return SenderKind.UNKNOWN
            val s = sender.trim().replace(" ", "")
            if (s.any { it.isLetter() }) return SenderKind.ALPHA

            val digits = s.filter { isDigit(it) }
            if (digits.isEmpty()) return SenderKind.UNKNOWN

            if (s.startsWith("+") && !digits.startsWith("90")) return SenderKind.INTERNATIONAL
            if (!s.startsWith("+") && !digits.startsWith("0") && digits.length in 3..6) {
                return SenderKind.SHORTCODE
            }

            val local = (if (digits.startsWith("90")) digits.drop(2) else digits)
                .trimStart('0')
            val head = local.firstOrNull() ?: return SenderKind.UNKNOWN

            if (head == '5') return SenderKind.TR_MOBILE
            for (prefix in listOf("850", "444", "800", "900")) {
                if (local.startsWith(prefix)) return SenderKind.TR_NON_GEO
            }
            if (head in listOf('2', '3', '4')) return SenderKind.TR_GEO
            return SenderKind.UNKNOWN
        }

        fun tld(host: String): String = host.substringAfterLast('.', "")

        /**
         * Metindeki alan adlarını çıkarır. `https://` şart değildir — Türkçe
         * spam'de çıplak alan adı ("bet-xy7.co") çok yaygındır.
         */
        fun hosts(text: String): List<String> =
            URL.findAll(text).mapNotNull { match ->
                val host = match.groupValues.getOrNull(1)?.lowercase() ?: return@mapNotNull null
                // "18.450" gibi sayısal ifadeleri alan adı sanma.
                if (host.all { isDigit(it) || it == '.' }) return@mapNotNull null
                val t = tld(host)
                if (t.length < 2 || t.all { isDigit(it) }) return@mapNotNull null
                host
            }.toList()

        // Desenlerde \s \d \w \b KULLANILMAZ. Java regex bunları ASCII
        // olarak, ICU (Swift) ise Unicode olarak yorumlar. Örneğin ICU'nun
        // \s'i NBSP'yi sayar — bu fark yüzünden OTP koruması yalnızca
        // Android'de atlatılabiliyordu. Her yerde açık sınıf kullanıyoruz.
        // Nöbetçi vakalar: golden.json > otp-nbsp, bkod-turkce-bitisik.
        //
        // Boşluk sınıfı `[ \t\r\n]`dir — SATIR SONU DAHİL. Bir dönem `[ \t]`
        // yazıldığı için "Dogrulama kodunuz:\n729104" biçimindeki çok satırlı
        // gerçek OTP mesajları OTP korumasına girmiyor, dengeli/sıkı
        // duyarlılıkta çöpe gidebiliyordu. Nöbetçi vaka: otp-satir-sonu.
        // NBSP ailesi fold() içinde boşluğa çevrildiği için küme kapalıdır.

        private val URL = Regex(
            "(?:https?://|www\\.|(?<![\\p{L}\\p{Nd}_@.]))" +
                "((?:[a-z0-9\\u00a1-\\uffff]" +
                "(?:[a-z0-9\\u00a1-\\uffff-]{0,61}[a-z0-9\\u00a1-\\uffff])?\\.)+" +
                "[a-z\\u00a1-\\uffff]{2,24})(?:/[^ \\t\\r\\n]*)?",
            RegexOption.IGNORE_CASE,
        )

        private val IP_URL = Regex("""https?://[0-9]{1,3}(?:\.[0-9]{1,3}){3}""", RegexOption.IGNORE_CASE)

        /**
         * BTK'nın alfanumerik başlıklı SMS'ler için zorunlu kıldığı 4 haneli kod.
         * Komşuluk kontrolü Unicode harfleri de kapsar; `\b` kullanılsaydı
         * "ğB045" Java'da eşleşip Swift'te eşleşmezdi (15 puanlık ayrışma).
         */
        private val B_CODE = Regex("""(?<![\p{L}\p{Nd}_])B[0-9]{3}(?![\p{L}\p{Nd}_])""")

        private val OTP = Regex(
            """(dogrulama|onay|guvenlik|islem|giris|aktivasyon|tek kullanimlik)[ \t\r\n]*(kodunuz|kodu|sifreniz|sifre|parolasi)?[ \t\r\n]*[:\-]?[ \t\r\n]*[0-9]{4,8}"""
        )

        private val CODE_HARVEST = Regex(
            """(kodu|sifreyi|parolayi|dogrulama kodunu)[ \t\r\n]+(bize|bana|asagidaki|whatsapp|paylas|gonder|ilet)"""
        )
    }
}
