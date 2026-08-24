package tr.bekci.core

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `shared/golden.json` üç uygulamanın (Kotlin, Swift, Python referansı)
 * ortak sözleşmesidir. Bu test kırılıyorsa ya bir kuralı bilerek değiştirdin
 * — o zaman fixture'ı yeniden üret ve Swift tarafını da güncelle — ya da
 * platformlar arasında istemeden bir ayrışma oluştu.
 */
class ClassifierGoldenTest {

    private data class Case(
        val id: String, val sender: String, val body: String,
        val action: String, val subAction: String,
        val risk: Int, val senderKind: String, val topReason: String?,
    )

    private fun loadGolden(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden.json")
            ?: error("golden.json test kaynaklarında bulunamadı")
        val root = JSONObject(stream.bufferedReader().readText())
        val array = root.getJSONArray("cases")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Case(
                id = o.getString("id"),
                sender = o.getString("sender"),
                body = o.getString("body"),
                action = o.getString("expectAction"),
                subAction = o.getString("expectSubAction"),
                risk = o.getInt("expectRisk"),
                senderKind = o.getString("expectSenderKind"),
                topReason = if (o.isNull("expectTopReason")) null else o.getString("expectTopReason"),
            )
        }
    }

    @Test
    fun `altin kume birebir eslesir`() {
        val cases = loadGolden()
        assertTrue(cases.size > 20, "Altın küme fazla küçülmüş")

        val classifier = Classifier()
        val failures = mutableListOf<String>()

        for (c in cases) {
            val v = classifier.classify(c.sender, c.body)
            if (v.action.raw != c.action) failures += "${c.id}: aksiyon ${v.action.raw} ≠ ${c.action}"
            if (v.subAction.raw != c.subAction) failures += "${c.id}: alt-aksiyon ${v.subAction.raw} ≠ ${c.subAction}"
            if (v.risk != c.risk) failures += "${c.id}: risk ${v.risk} ≠ ${c.risk}"
            if (v.senderKind.raw != c.senderKind) failures += "${c.id}: gönderen ${v.senderKind.raw} ≠ ${c.senderKind}"
            c.topReason?.let { expected ->
                val actual = v.reasons.firstOrNull()?.code
                if (actual != expected) failures += "${c.id}: baş gerekçe $actual ≠ $expected"
            }
        }

        assertTrue(failures.isEmpty(), "\n" + failures.joinToString("\n"))
    }

    @Test
    fun `beyan edilmemis alt-aksiyon disari sizmaz`() {
        val classifier = Classifier()
        for (c in loadGolden()) {
            val v = classifier.classify(c.sender, c.body)
            if (v.subAction != FilterSubAction.NONE) {
                assertTrue(v.subAction in DECLARED_SUB_ACTIONS,
                    "${c.id}: beyan edilmemiş alt-aksiyon ${v.subAction.raw}")
            }
        }
    }

    /** Ürünün en pahalı hatası: doğrulama kodunu çöpe atmak. */
    @Test
    fun `dogrulama kodu asla cope gitmez`() {
        val classifier = Classifier(UserRules(sensitivity = Sensitivity.STRICT))
        listOf(
            "AKBANK" to "Dogrulama kodunuz: 481925. Bu kodu kimseyle paylasmayin.",
            "E-DEVLET" to "e-Devlet giris dogrulama kodunuz: 725104",
            "+90 850 111 22 33" to "Islem kodunuz 90210. ACIL! HEMEN GIRIN, SON UYARI!",
            "WHATSAPP" to "Guvenlik kodunuz: 442113",
            // Çok satırlı: kod ayrı satırda. Desenler `[ \t]` kullandığı sürece
            // bu mesaj OTP korumasına GİRMİYOR ve (bankasi + aciliyet + ASCII
            // kaçınma + büyük harf = risk 68) dengeli/sıkı modda çöpe gidiyordu.
            "+90 212 909 77 12" to
                "SAYIN MUSTERIMIZ, XYZ BANKASI ISLEM GUVENLIK KODUNUZ\n729104\n" +
                "BU KODU KIMSEYLE PAYLASMAYIN. ISLEMI HEMEN ONAYLAYIN, SON KEZ.",
        ).forEach { (sender, body) ->
            assertNotEquals(FilterAction.JUNK, classifier.classify(sender, body).action,
                "OTP çöpe atıldı: $body")
        }
    }

    /**
     * "Tanıdık taklidi" (evlat dolandırıcılığı) her duyarlılıkta çöp
     * sayılmalı — bu bir eşik hesabı değil, kesin karardır (bkz.
     * Classifier.kt `impostorContact`). Biri yarın bu bloğu risk bölümüne
     * taşıyıp eşiğe bağımlı hâle getirirse test hemen kırılsın diye var.
     */
    @Test
    fun `tanidik taklidi her duyarlilikta cope gider`() {
        val messages = listOf(
            "+90 538 902 11 47" to
                "Anne telefonum kirildi bu numaradan yaziyorum, acil param lazim, " +
                    "gonderir misin? IBAN'imi atiyorum.",
            "+90 505 220 11 34" to
                "Numaram degisti, eski hattima ulasamiyorum. Borc param var, " +
                    "hemen havale yapar misin?",
        )
        for (sensitivity in Sensitivity.entries) {
            val classifier = Classifier(UserRules(sensitivity = sensitivity))
            for ((sender, body) in messages) {
                val v = classifier.classify(sender, body)
                assertEquals(FilterAction.JUNK, v.action, "$sensitivity: tanıdık taklidi kaçtı: $body")
                assertEquals("impostorContact", v.reasons.firstOrNull()?.code)
            }
        }
    }

    @Test
    fun `kullanici kurallari modeli ezer`() {
        val spam = "1000TL DENEME BONUSU! GUNCEL GIRIS: bet-xy7.co"

        assertEquals(FilterAction.ALLOW,
            Classifier(UserRules(allowSenders = setOf("bonusvip")))
                .classify("BONUS-VIP", spam).action)

        assertEquals(FilterAction.JUNK,
            Classifier(UserRules(blockSenders = setOf("garanti")))
                .classify("GARANTI", "Kartinizdan 100 TL harcama yapilmistir. B012").action)

        assertEquals(FilterAction.JUNK,
            Classifier(UserRules(blockKeywords = setOf("kampanya")))
                .classify("MIGROS", "Yeni kampanya basladi!").action)
    }

    @Test
    fun `duyarlilik monoton artar`() {
        val cases = loadGolden()
        val counts = listOf(Sensitivity.CAREFUL, Sensitivity.BALANCED, Sensitivity.STRICT)
            .map { level ->
                val c = Classifier(UserRules(sensitivity = level))
                cases.count { c.classify(it.sender, it.body).action == FilterAction.JUNK }
            }
        assertTrue(counts[0] <= counts[1] && counts[1] <= counts[2], "eşikler monoton değil: $counts")
    }

    @Test
    fun `gonderen tipi dogru tespit edilir`() {
        listOf(
            "GARANTI" to SenderKind.ALPHA,
            "BONUS-VIP" to SenderKind.ALPHA,
            "5555" to SenderKind.SHORTCODE,
            "182" to SenderKind.SHORTCODE,
            "+90 532 118 44 09" to SenderKind.TR_MOBILE,
            "05321184409" to SenderKind.TR_MOBILE,
            "+90 850 304 11 22" to SenderKind.TR_NON_GEO,
            "08503041122" to SenderKind.TR_NON_GEO,
            "+90 212 909 77 12" to SenderKind.TR_GEO,
            "+1 202 555 0134" to SenderKind.INTERNATIONAL,
            "+44 7700 900123" to SenderKind.INTERNATIONAL,
            "" to SenderKind.UNKNOWN,
        ).forEach { (sender, expected) ->
            assertEquals(expected, Classifier.senderKind(sender), "gönderen: $sender")
        }
    }

    /**
     * Locale bağımsızlık: Kotlin'in varsayılan `lowercase()`'i Türkçe locale'de
     * 'I' → 'ı' üretir. Aynı mesaj farklı telefonda farklı sınıflanmamalı.
     */
    @Test
    fun `turkce katlama locale bagimsizdir`() {
        assertEquals("istanbul", TurkishText.fold("İSTANBUL"))
        assertEquals("igdir", TurkishText.fold("Iğdır"))
        assertEquals("cekilis", TurkishText.fold("ÇEKİLİŞ"))
        assertEquals("supheli", TurkishText.fold("ŞÜPHELİ"))
        assertEquals(TurkishText.fold("ılık"), TurkishText.fold("ILIK"))
    }

    @Test
    fun `sozcuk siniri dogru calisir`() {
        assertTrue(TurkishText.matches("bahisler basladi", setOf("bahis")).isNotEmpty())
        assertTrue(TurkishText.matches("kabahat isledi", setOf("bahis")).isEmpty())
        assertTrue(TurkishText.matches("bet sitesi", setOf("bet")).isNotEmpty())
    }

    @Test
    fun `bos ve bozuk girdide cokmez`() {
        val c = Classifier()
        assertEquals(FilterAction.NONE, c.classify(null, null).action)
        assertEquals(FilterAction.NONE, c.classify("", "").action)
        assertEquals(FilterAction.NONE, c.classify("?????", "a".repeat(5000)).action)
    }

    @Test
    fun `url tespiti calisir`() {
        assertTrue(Classifier.hosts("Detay: t.co/xkz2f").contains("t.co"))
        assertTrue(Classifier.hosts("https://garanti-dogrulama.top/giris").contains("garanti-dogrulama.top"))
        // Para tutarı alan adı sanılmamalı — aksi halde her banka mesajı riskli olur.
        assertTrue(Classifier.hosts("Borcunuz 18.450 TL").isEmpty())
    }
}
