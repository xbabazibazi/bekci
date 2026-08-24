package tr.bekci.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tr.bekci.core.Classifier
import tr.bekci.core.FilterAction
import tr.bekci.core.TurkishText
import tr.bekci.core.UserRules
import tr.bekci.data.Conversation
import tr.bekci.data.DonationQueue
import tr.bekci.data.MessageRepository
import tr.bekci.data.Prefs
import tr.bekci.data.ReportEntry
import tr.bekci.data.RuleStore
import tr.bekci.data.SmsProvider
import tr.bekci.data.StoredMessage
import tr.bekci.sms.SmsRole
import tr.bekci.sms.notifyMessage

enum class InboxFilter(val title: String) {
    ALL("Tümü"), FINANCE("Finans"), ORDERS("Kargo"),
    CARRIER("Operatör"), PROMO("Kampanya"), JUNK("Çöp");

    fun matches(v: tr.bekci.core.Verdict): Boolean = when (this) {
        ALL -> true
        FINANCE -> v.subAction == tr.bekci.core.FilterSubAction.TRANSACTIONAL_FINANCE
        ORDERS -> v.subAction == tr.bekci.core.FilterSubAction.TRANSACTIONAL_ORDERS
        CARRIER -> v.subAction == tr.bekci.core.FilterSubAction.TRANSACTIONAL_CARRIER
        PROMO -> v.action == tr.bekci.core.FilterAction.PROMOTION
        JUNK -> v.action == tr.bekci.core.FilterAction.JUNK
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ruleStore = RuleStore(app)
    private val repository = MessageRepository(app)
    private val prefs = Prefs(app)
    private val donations = DonationQueue(app)
    private val smsProvider = SmsProvider(app)

    var rules by mutableStateOf(ruleStore.load())
        private set
    var messages by mutableStateOf(repository.all())
        private set
    var setupDone by mutableStateOf(prefs.setupDone())
        private set
    var isPro by mutableStateOf(prefs.isPro())
        private set
    var fraudNotifications by mutableStateOf(prefs.fraudNotifications())
        private set
    var falsePositives by mutableStateOf(prefs.falsePositives())
        private set
    var donationCount by mutableStateOf(donations.count())
        private set

    /**
     * Telefonun GERÇEK konuşmaları (sistem SMS sağlayıcısından).
     *
     * Bekçi varsayılan mesaj uygulamasıyken gösterilmesi gereken budur —
     * kendi şifreli deposu yalnızca Bekçi kurulduktan sonrasını bilir.
     * Boş kalırsa ya izin verilmemiştir ya da gerçekten mesaj yoktur;
     * arayüz ikisini ayırt edebilsin diye `isDefaultSms` de taşınıyor.
     */
    var conversations by mutableStateOf(emptyList<Conversation>())
        private set
    var isDefaultSms by mutableStateOf(SmsRole.isDefault(app))
        private set

    /** Spam olmayan konuşmalar — ana liste. */
    val inboxThreads: List<Conversation>
        get() = conversations.filterNot { it.verdict.action == FilterAction.JUNK }

    /**
     * Ayrılmış spam. SİLİNMEZ, yalnızca ana listeden çıkarılır: mesaj
     * sağlayıcıda duruyor ve kullanıcı yanlış işaretlendiğini söylerse
     * geri getirilebiliyor.
     *
     * Ayırma ölçütü `action == JUNK`, **`isFraud` DEĞİL.** İkisi farklı
     * kavramlar: `isFraud` (junk *ve* risk ≥ 70) "kırmızı tehlike ekranı
     * göster" demek, ayırma ise "motor bunu çöp saydı" demek. `isFraud`
     * kullanıldığı sürece duyarlılık ayarı ayırmayı HİÇ etkilemiyordu —
     * sıkı modda eşik 48'e inse bile 70 altındaki çöpler kutuda kalıyordu.
     * Ölçülen fark: temkinli/dengeli/sıkı için 14/14/14 yerine 14/15/17.
     */
    val spamThreads: List<Conversation>
        get() = conversations.filter { it.verdict.action == FilterAction.JUNK }

    /**
     * Sağlayıcıyı yeniden okur. Ekran her öne geldiğinde çağrılır: mesaj
     * uygulama kapalıyken de gelebilir ve kullanıcı kural değiştirdiğinde
     * sınıflandırma anında tazelenmelidir.
     */
    /** Okunmamış mesaj sayısı — simge rozetiyle aynı kaynak. */
    var unreadCount by mutableStateOf(0)
        private set

    fun refreshConversations() {
        isDefaultSms = SmsRole.isDefault(getApplication())
        conversations = smsProvider.conversations()
        unreadCount = smsProvider.unreadCount()
    }

    /**
     * Tanılama ekranındaki deneme bildirimi.
     *
     * SAHTE MESAJ OLUŞTURMAZ — sağlayıcıya hiçbir şey yazmaz. Yalnızca
     * bildirim yolunu ve simge rozetini test eder; kullanıcının gelen
     * kutusunu uydurma veriyle kirletmek, bu üründe kabul edilemez.
     */
    fun sendTestNotification() {
        notifyMessage(
            getApplication(),
            sender = "Bekçi",
            body = "Deneme bildirimi. Bunu görüyorsanız mesaj bildirimleri çalışıyor.",
            unreadCount = unreadCount,
        )
    }

    fun openThread(threadId: Long): List<tr.bekci.data.ThreadMessage> {
        smsProvider.markRead(threadId)
        // Konuşma açıldı: o gönderenin bildirimi kalkmalı, yoksa kullanıcı
        // okuduğu mesajın bildirimini elle silmek zorunda kalır ve simge
        // rozeti de düşmez.
        val address = conversations.firstOrNull { it.threadId == threadId }?.address
        if (address != null) {
            NotificationManagerCompat.from(getApplication()).cancel(address.hashCode())
        }
        refreshConversations()
        return smsProvider.messages(threadId)
    }

    /**
     * SMS gönderir ve GÖNDERİLENLER'e yazar.
     *
     * Varsayılan mesaj uygulamasının iki ayrı sorumluluğu var: mesajı
     * şebekeye vermek ve sağlayıcıya kaydetmek. İkincisi atlanırsa mesaj
     * gider ama konuşmada hiç görünmez — kullanıcı gönderdiğinden şüphe eder.
     *
     * @return gönderim başlatılabildiyse true; arayüz false'ta hata gösterir.
     */
    fun sendSms(address: String, text: String): Boolean {
        if (address.isBlank() || text.isBlank()) return false
        val ok = smsProvider.send(address, text)
        if (ok) refreshConversations()
        return ok
    }

    /** Şifreli depo kurulamadıysa mesaj saklanmıyor; kullanıcıya söylenir. */
    val storageAvailable: Boolean get() = repository.isAvailable

    private val classifier get() = Classifier(rules)

    /**
     * Sağlayıcı değiştiğinde ARAYÜZÜ ANINDA TAZELER.
     *
     * Bu olmadan liste yalnızca ekran ilk açıldığında doluyordu: uygulama
     * açıkken gelen mesaj görünmüyor, arka plandan dönüldüğünde eski liste
     * kalıyordu. `SmsDeliverReceiver` mesajı sağlayıcıya yazınca gözlemci
     * tetikleniyor ve mesaj anında listede beliriyor.
     */
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshConversations()
    }

    init {
        // DEMO MESAJ TOHUMLAMASI KALDIRILDI. Bekçi artık telefonun gerçek
        // mesaj geçmişini okuyor; sahte örnekler kullanıcının kendi
        // mesajlarının yerine geçip "mesajım nerede" sorusuna yol açıyordu.

        // TEK SEFERLİK taban çizgisi. Burada (her açılışta çalışan
        // init'te) yapılması bilinçli: hem kurulumdan yeni geçen hem de
        // ZATEN varsayılan olup bu bayrağı hiç görmemiş eski kurulumlar
        // (ör. bu güncellemeden önce varsayılan yapılmış bir telefon) tek
        // bir yerden yakalanıyor. `inboxBaselined` bayrağı ikinci
        // çalışmayı engelliyor.
        if (SmsRole.isDefault(app) && !prefs.inboxBaselined()) {
            smsProvider.markAllRead()
            prefs.setInboxBaselined(true)
        }

        getApplication<Application>().contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI, true, smsObserver,
        )
        refreshConversations()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
    }

    // ── İstatistik ──────────────────────────────────────────────────
    //
    // Sayaçlar GERÇEK konuşmalardan türetilir. Önceden Bekçi'nin kendi
    // deposundan (ve demo tohumundan) hesaplanıyordu; kullanıcı gerçek
    // trafiğiyle ilgisiz sayılar görüyordu.

    val sortedThisWeek: Int
        get() {
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            return conversations.count {
                it.lastAt > weekAgo && it.verdict.action != FilterAction.NONE
            }
        }

    val fraudBlocked: Int get() = spamThreads.size

    /** Bugün spam'e ayrılan konuşmalar — akşam özetinin de kaynağı. */
    val spamToday: List<Conversation>
        get() {
            val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            return spamThreads.filter { it.lastAt > dayAgo }
        }

    val needsAttention: List<Conversation>
        get() = spamThreads.sortedByDescending { it.lastAt }.take(3)

    fun categoryCounts(): List<Pair<InboxFilter, Int>> =
        InboxFilter.entries.drop(1).map { filter ->
            filter to conversations.count { filter.matches(it.verdict) }
        }

    fun messages(filter: InboxFilter): List<StoredMessage> =
        messages.filter { filter.matches(it.verdict) }.sortedByDescending { it.receivedAt }

    fun message(id: String): StoredMessage? = messages.firstOrNull { it.id == id }

    // ── Eylemler ────────────────────────────────────────────────────

    fun alwaysTrust(sender: String) = updateRules {
        it.copy(
            allowSenders = it.allowSenders + UserRules.key(sender),
            blockSenders = it.blockSenders - UserRules.key(sender),
        )
    }

    fun alwaysBlock(sender: String) = updateRules {
        it.copy(
            blockSenders = it.blockSenders + UserRules.key(sender),
            allowSenders = it.allowSenders - UserRules.key(sender),
        )
    }

    fun removeRule(key: String) = updateRules {
        it.copy(allowSenders = it.allowSenders - key, blockSenders = it.blockSenders - key)
    }

    /**
     * Ücretsiz sürümde engellenen kelime sayısı sınırlı — Pro'da sınırsız.
     * Gönderen kuralları (Her zaman güven/engelle) BİLEREK sınırlanmadı:
     * onlar bir uyarı ekranından alınan güvenlik eylemidir, kelime listesi
     * gibi elle kurulan bir özelleştirme değil.
     *
     * @return false ise limit doldu, arayüz Pro'ya yönlendirmeli.
     */
    fun addBlockedKeyword(keyword: String): Boolean {
        val normalized = TurkishText.fold(keyword).trim()
        if (normalized.isEmpty()) return true
        if (keywordLimitReached) return false
        updateRules { it.copy(blockKeywords = it.blockKeywords + normalized) }
        return true
    }

    val keywordLimitReached: Boolean
        get() = !isPro && rules.blockKeywords.size >= FREE_MAX_KEYWORDS

    /** Kuralları taşınabilir JSON'a çevirir — paylaşma/yedekleme için (Pro). */
    fun exportRules(): String = ruleStore.export(rules)

    /**
     * İçe aktarma ÜZERİNE YAZMAZ, BİRLEŞTİRİR: bir aile üyesinin paylaştığı
     * listeyi almak kendi kurduğunuz kuralları silmemeli. Duyarlılık
     * seviyeniz de korunur.
     *
     * @return false ise metin okunamadı, arayüz hata göstermeli.
     */
    fun importRules(raw: String): Boolean {
        val imported = ruleStore.import(raw) ?: return false
        updateRules {
            it.copy(
                allowSenders = it.allowSenders + imported.allowSenders,
                blockSenders = it.blockSenders + imported.blockSenders,
                blockKeywords = it.blockKeywords + imported.blockKeywords,
            )
        }
        return true
    }

    /**
     * Son N günün rapor verisi (Pro). Mesaj GÖVDESİ taşınmaz, yalnızca
     * gönderen/zaman/karar — bkz. [SmsProvider.messagesSince].
     */
    fun reportSince(days: Int): List<ReportEntry> =
        smsProvider.messagesSince(System.currentTimeMillis() - days * 24L * 60 * 60 * 1000)

    fun removeBlockedKeyword(keyword: String) = updateRules {
        it.copy(blockKeywords = it.blockKeywords - keyword)
    }

    fun setSensitivity(value: tr.bekci.core.Sensitivity) = updateRules {
        it.copy(sensitivity = value)
    }

    /** Kullanıcı "yanlış işaretlendi" dedi — ürünün en değerli geri bildirimi. */
    fun reportFalsePositive(message: StoredMessage) {
        prefs.bumpFalsePositives()
        falsePositives = prefs.falsePositives()
        alwaysTrust(message.sender)
    }

    /**
     * `setFraudNotifications` ADI KULLANILAMAZ: `fraudNotifications`
     * property'sinin ürettiği JVM setter'ı da aynı imzaya sahip
     * (`setFraudNotifications(Z)V`) ve derleyici "platform declaration
     * clash" verir. Bu yüzden eylem adı fiil hâlinde — dosyadaki
     * `reportFalsePositive` / `alwaysTrust` deseniyle de tutarlı.
     */
    fun toggleFraudNotifications(value: Boolean) {
        prefs.setFraudNotifications(value)
        fraudNotifications = value
    }

    /**
     * Bağış metni gerçekten saklanır. "Gönderdim" hissi verip hiçbir şey
     * yapmamak, gizlilik iddiası eden bir üründe en kötü güven ihlali olurdu.
     */
    fun donate(text: String, category: String) {
        donations.enqueue(text, category, System.currentTimeMillis())
        donationCount = donations.count()
    }

    /** Kullanıcının saklanan mesajları silme hakkı. */
    fun clearStoredMessages() {
        repository.clear()
        messages = emptyList()
    }

    /**
     * Rıza metninin sürümü. Metin değişirse ARTIRILMALI — eski onay yeni
     * metni kapsamaz ve kullanıcıya yeniden sorulur.
     */
    val consentVersion = CONSENT_VERSION

    fun recordConsent() = prefs.recordConsent(CONSENT_VERSION)

    fun completeSetup() {
        prefs.setSetupDone(true)
        setupDone = true
    }

    private companion object {
        /** Rıza metni her değiştiğinde artır. */
        const val CONSENT_VERSION = 1

        /**
         * Ücretsiz sürümde en fazla kaç engellenen kelime kuralı eklenebilir.
         * Gerçek kullanımda neredeyse kimse buraya ulaşmaz; sınır güç
         * kullanıcıları için bir Pro teşviki, günlük kullanım için engel
         * değil.
         */
        const val FREE_MAX_KEYWORDS = 12
    }

    fun activatePro() {
        prefs.setPro(true)
        isPro = true
    }

    /**
     * Kurallar değişince saklanan tüm mesajlar yeniden değerlendirilir;
     * aksi halde kullanıcı kural ekler ve hiçbir şeyin değişmediğini görür.
     */
    private fun updateRules(transform: (UserRules) -> UserRules) {
        rules = transform(rules)
        ruleStore.save(rules)
        val c = classifier
        val updated = messages.map {
            it.copy(verdict = c.classify(it.sender, it.body))
        }
        repository.replaceAll(updated)
        messages = updated
        // Gerçek konuşmalar da yeni kurala göre YENİDEN sınıflandırılmalı;
        // aksi hâlde bir göndereni ThreadScreen'den engelleyince konuşma
        // Kutu'ya dönene kadar hâlâ eski (yanlış) bölümde görünürdü.
        refreshConversations()
    }

    // DEMO MESAJ TOHUMLAMASI SİLİNDİ (2026-08-21). Bekçi telefonun gerçek
    // mesaj geçmişini okuduğu için örnekler artık kullanıcının kendi
    // mesajlarının yerine geçiyor ve "mesajım nerede" sorusuna yol açıyordu.
    // Boş kutu, sahte kutudan iyidir.
}
