package tr.bekci.core

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gelen kutusu / spam ayrımının sözleşmesi.
 *
 * Android arayüzü konuşmaları `verdict.action == JUNK` ölçütüyle ayırır
 * (`AppViewModel.spamThreads`). Bu test o kuralı motora karşı kilitler.
 *
 * ## Neden `isFraud` DEĞİL
 *
 * `isFraud` = `action == JUNK && risk >= 70` ve amacı farklıdır: mesaj
 * detayında kırmızı tehlike ekranı gösterilsin mi kararıdır. Ayırmada
 * kullanıldığı dönemde **duyarlılık ayarı ayırmayı hiç etkilemiyordu** —
 * sıkı modda eşik 48'e inse bile 70 altındaki çöpler ana listede
 * kalıyordu. Bu testin `duyarlilik arttikca daha cok konusma ayrilir`
 * kısmı tam olarak o regresyonu yakalar.
 */
class InboxSeparationTest {

    private fun goldenCases(): List<Triple<String, String, String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden.json")
            ?: error("golden.json test kaynaklarında bulunamadı")
        val array = JSONObject(stream.bufferedReader().readText()).getJSONArray("cases")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Triple(o.getString("id"), o.getString("sender"), o.getString("body"))
        }
    }

    private fun ayrilanlar(sensitivity: Sensitivity): Set<String> {
        val classifier = Classifier(UserRules(sensitivity = sensitivity))
        return goldenCases().filter { (_, sender, body) ->
            classifier.classify(sender, body).action == FilterAction.JUNK
        }.map { it.first }.toSet()
    }

    /**
     * Duyarlılık ayarının GÖRÜNÜR bir karşılığı olmalı. Kullanıcı "Sıkı"yı
     * seçtiğinde daha çok konuşmanın ayrılmasını bekler; sayı sabit
     * kalıyorsa ayar bir yalandır.
     */
    @Test
    fun `duyarlilik arttikca daha cok konusma ayrilir`() {
        val careful = ayrilanlar(Sensitivity.CAREFUL)
        val balanced = ayrilanlar(Sensitivity.BALANCED)
        val strict = ayrilanlar(Sensitivity.STRICT)

        assertTrue(careful.size < strict.size,
            "Duyarlılık ayırmayı etkilemiyor: temkinli=${careful.size}, sıkı=${strict.size}")

        // Kademeli olmalı: sıkı, dengelinin ayırdığı her şeyi ayırmalı.
        assertTrue(balanced.containsAll(careful), "dengeli, temkinlinin ayırdıklarını kaçırdı")
        assertTrue(strict.containsAll(balanced), "sıkı, dengelinin ayırdıklarını kaçırdı")
    }

    /**
     * Varsayılan (temkinli) modda açıkça dolandırıcılık olan mesajlar
     * ana listeden ayrılmalı. Bu liste ürünün en temel vaadidir.
     */
    @Test
    fun `bariz dolandiricilik varsayilan modda ayrilir`() {
        val ayrilan = ayrilanlar(Sensitivity.CAREFUL)
        listOf(
            "sahte-icra", "bahis-bonus", "sahte-kargo", "sahte-banka-link",
            "kod-avcisi", "ip-adresi", "kripto-vaadi", "sahte-edevlet",
            "sahte-odul", "punycode", "yurtdisi-bahis",
            "kod-avcisi-satir-sonu", "evlat-dolandiriciligi",
        ).forEach { id ->
            assertTrue(id in ayrilan, "$id varsayılan modda ana listede kaldı")
        }
    }

    /**
     * Meşru mesaj HİÇBİR duyarlılıkta ayrılmamalı. Bir banka bildirimini
     * spam'e atmak, bir spam'i kaçırmaktan çok daha pahalıdır.
     */
    @Test
    fun `mesru mesajlar hicbir duyarlilikta ayrilmaz`() {
        val korunmasi = listOf(
            "banka-harcama", "banka-otp", "edevlet-otp", "kargo-teslim",
            "operator-paket", "mhrs-randevu", "kisa-numara-bilgi",
            "gercek-icra-uyap", "banka-borc-hatirlatma", "operator-fatura-link",
            "eczane-randevu", "bankadan-uyari", "vergi-son-gun",
            "kisisel-mesaj", "kisa-bilgi", "kisa-tesekkur",
            "otp-nbsp", "otp-normal-rakam", "otp-satir-sonu",
        )
        Sensitivity.entries.forEach { sensitivity ->
            val ayrilan = ayrilanlar(sensitivity)
            korunmasi.forEach { id ->
                assertEquals(false, id in ayrilan,
                    "$sensitivity: meşru mesaj '$id' spam'e ayrıldı")
            }
        }
    }
}
