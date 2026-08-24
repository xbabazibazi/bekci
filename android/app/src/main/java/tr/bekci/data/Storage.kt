package tr.bekci.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tr.bekci.core.FilterAction
import tr.bekci.core.FilterSubAction
import tr.bekci.core.Reason
import tr.bekci.core.SenderKind
import tr.bekci.core.Sensitivity
import tr.bekci.core.UserRules
import tr.bekci.core.Verdict
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Mesaj gövdeleri banka bakiyesi, doğrulama kodu ve kişisel yazışma içerir.
 * Cihazdan çıkmıyor olmaları yeterli değil — diske düz metin yazılmamalı.
 *
 * Şifreli depo kurulamazsa (bazı üretici ROM'larında Keystore sorunlu)
 * sessizce düz depoya düşmek yerine ne olduğunu belli ediyoruz: uygulama
 * çalışmaya devam eder ama mesaj saklamaz.
 */
internal fun encryptedPrefs(context: Context, name: String): SharedPreferences? =
    runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, name, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

// ── Kurallar ────────────────────────────────────────────────────────

/**
 * Kullanıcı kuralları. Hem UI hem de `SmsReceiver` okur; yazma yalnızca UI'dan.
 *
 * `core` modülü Android'e ve kotlinx.serialization'a bağımlı olmadığı için
 * `UserRules` burada ayrı bir DTO üzerinden serileştiriliyor.
 */
@Serializable
private data class RulesDto(
    val allowSenders: Set<String> = emptySet(),
    val blockSenders: Set<String> = emptySet(),
    val blockKeywords: Set<String> = emptySet(),
    val sensitivity: String = Sensitivity.CAREFUL.raw,
)

class RuleStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("bekci.rules", Context.MODE_PRIVATE)

    fun load(): UserRules {
        val raw = prefs.getString(KEY, null) ?: return UserRules.DEFAULT
        return runCatching {
            val dto = json.decodeFromString<RulesDto>(raw)
            UserRules(
                allowSenders = dto.allowSenders,
                blockSenders = dto.blockSenders,
                blockKeywords = dto.blockKeywords,
                sensitivity = Sensitivity.entries.firstOrNull { it.raw == dto.sensitivity }
                    ?: Sensitivity.CAREFUL,
            )
        }.getOrDefault(UserRules.DEFAULT)
    }

    fun save(rules: UserRules) {
        val dto = RulesDto(
            rules.allowSenders, rules.blockSenders,
            rules.blockKeywords, rules.sensitivity.raw,
        )
        prefs.edit().putString(KEY, json.encodeToString(dto)).apply()
    }

    /**
     * Kuralları taşınabilir JSON'a çevirir (Pro: "kuralları yedekle/paylaş").
     * Sunucu YOK — dosya/metin olarak paylaşılır, hiçbir yere yüklenmez.
     */
    fun export(rules: UserRules): String =
        json.encodeToString(RulesDto(rules.allowSenders, rules.blockSenders, rules.blockKeywords, rules.sensitivity.raw))

    /** Bozuk/yabancı bir metin gelirse null döner; çağıran kullanıcıya hata gösterir. */
    fun import(raw: String): UserRules? = runCatching {
        val dto = json.decodeFromString<RulesDto>(raw)
        UserRules(
            allowSenders = dto.allowSenders,
            blockSenders = dto.blockSenders,
            blockKeywords = dto.blockKeywords,
            sensitivity = Sensitivity.entries.firstOrNull { it.raw == dto.sensitivity } ?: Sensitivity.CAREFUL,
        )
    }.getOrNull()

    private companion object { const val KEY = "rules" }
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("bekci.prefs", Context.MODE_PRIVATE)

    fun fraudNotifications() = prefs.getBoolean("fraudNotifications", true)
    fun setFraudNotifications(value: Boolean) =
        prefs.edit().putBoolean("fraudNotifications", value).apply()

    fun setupDone() = prefs.getBoolean("setupDone", false)
    fun setSetupDone(value: Boolean) = prefs.edit().putBoolean("setupDone", value).apply()

    fun isPro() = prefs.getBoolean("isPro", false)
    fun setPro(value: Boolean) = prefs.edit().putBoolean("isPro", value).apply()

    fun falsePositives() = prefs.getInt("falsePositives", 0)
    fun bumpFalsePositives() =
        prefs.edit().putInt("falsePositives", falsePositives() + 1).apply()

    /**
     * "Okunmamış mesaj" taban çizgisi çekildi mi?
     *
     * Bekçi varsayılan SMS uygulaması olduğunda, önceki uygulamanın hiç
     * okundu işaretlemediği yıllar öncesine ait mesajlar vardır (bazı
     * uygulamalar yalnızca kendi açtığı konuşmayı işaretler). Bu bayrak
     * olmadan rozet, tek bir yeni mesaj geldiğinde bile o eski birikimi
     * sayıp anlamsız bir rakam (ör. 99+) gösterir. TEK SEFERLİK sıfırlama
     * bu bayrakla korunuyor — her açılışta tekrar çalışmamalı.
     */
    fun inboxBaselined() = prefs.getBoolean("inboxBaselined", false)
    fun setInboxBaselined(value: Boolean) = prefs.edit().putBoolean("inboxBaselined", value).apply()

    /**
     * Varsayılan uygulama olma rızasının kaydı.
     *
     * Sürüm numarasıyla birlikte tutuluyor: rıza metni ileride değişirse
     * (ör. MMS desteği gelir ya da yeni bir veri kullanımı eklenirse) eski
     * onay o yeni metni kapsamaz ve kullanıcıya yeniden sorulmalıdır.
     * Zaman damgası, "ne zaman onay verildi" sorusunun cevabıdır.
     */
    fun consentVersion() = prefs.getInt("consentVersion", 0)
    fun consentAt() = prefs.getLong("consentAt", 0L)
    fun recordConsent(version: Int) = prefs.edit()
        .putInt("consentVersion", version)
        .putLong("consentAt", System.currentTimeMillis())
        .apply()
}

// ── Mesajlar ────────────────────────────────────────────────────────

data class StoredMessage(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val verdict: Verdict,
) {
    val initials: String
        get() = sender.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "#" }
}

@Serializable
private data class MessageDto(
    val id: String, val sender: String, val body: String, val receivedAt: Long,
    val action: String, val subAction: String, val risk: Int, val senderKind: String,
    val reasons: List<ReasonDto> = emptyList(),
)

@Serializable
private data class ReasonDto(val code: String, val title: String, val detail: String, val weight: Int)

/**
 * Basit, dosya tabanlı mesaj deposu.
 *
 * Room kullanılmadı: saklanan veri küçük (son N mesaj), şema sorguya ihtiyaç
 * duymuyor ve `SmsReceiver` içinde Room'un başlatma maliyetinden kaçınmak
 * istiyoruz. Veri büyürse Room'a geçmek tek dosyalık bir değişiklik.
 */
class MessageRepository(context: Context) {

    private val prefs = encryptedPrefs(context, "bekci.messages.enc")

    /** Şifreli depo kurulamadıysa mesaj saklamıyoruz. */
    val isAvailable: Boolean get() = prefs != null

    @Synchronized
    fun all(): List<StoredMessage> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<MessageDto>>(raw).map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun insert(sender: String, body: String, receivedAt: Long, verdict: Verdict) {
        val updated = (listOf(
            StoredMessage(UUID.randomUUID().toString(), sender, body, receivedAt, verdict)
        ) + all()).take(MAX_MESSAGES)
        write(updated)
    }

    @Synchronized
    fun replaceAll(messages: List<StoredMessage>) = write(messages.take(MAX_MESSAGES))

    /** Kullanıcının "saklanan mesajları sil" eylemi. Geri alınamaz. */
    @Synchronized
    fun clear() {
        prefs?.edit()?.remove(KEY)?.commit()
    }

    private fun write(messages: List<StoredMessage>) {
        // apply() asenkron; SmsReceiver goAsync() bittikten hemen sonra
        // süreç öldürülebilir ve son mesaj kaybolur. Zaten arka plan
        // iş parçacığındayız, commit() güvenli.
        prefs?.edit()
            ?.putString(KEY, json.encodeToString(messages.map { it.toDto() }))
            ?.commit()
    }

    private fun StoredMessage.toDto() = MessageDto(
        id, sender, body, receivedAt,
        verdict.action.raw, verdict.subAction.raw, verdict.risk, verdict.senderKind.raw,
        verdict.reasons.map { ReasonDto(it.code, it.title, it.detail, it.weight) },
    )

    private fun MessageDto.toModel() = StoredMessage(
        id, sender, body, receivedAt,
        Verdict(
            action = FilterAction.entries.firstOrNull { it.raw == action } ?: FilterAction.NONE,
            subAction = FilterSubAction.entries.firstOrNull { it.raw == subAction } ?: FilterSubAction.NONE,
            risk = risk,
            reasons = reasons.map { Reason(it.code, it.title, it.detail, it.weight) },
            senderKind = SenderKind.entries.firstOrNull { it.raw == senderKind } ?: SenderKind.UNKNOWN,
        ),
    )

    private companion object {
        const val KEY = "messages"
        /**
         * Cihazda tutulan mesaj sayısı üst sınırı. Kasıtlı olarak düşük:
         * ne kadar az veri saklarsak sızıntı yüzeyi o kadar küçük.
         * Ayarlar ekranında bu sayı kullanıcıya açıkça söyleniyor.
         */
        const val MAX_MESSAGES = 200
    }
}


// ── Bağış kuyruğu ───────────────────────────────────────────────────

@Serializable
private data class DonationDto(val text: String, val category: String, val at: Long)

/**
 * Gönüllü spam bağışları. Kullanıcı düğmeye bastığında metin GERÇEKTEN
 * saklanır — "gönderdim" hissi verip hiçbir şey yapmamak, ürünün gizlilik
 * iddiasından daha kötü bir güven ihlali olurdu.
 *
 * Gönderim uç noktası henüz yok; kuyruk cihazda bekler. Uç nokta
 * eklendiğinde `drain()` çağrılır ve yalnızca metin gönderilir —
 * gönderen numarası, cihaz kimliği veya zaman damgası değil.
 */
class DonationQueue(context: Context) {

    private val prefs = encryptedPrefs(context, "bekci.donations.enc")

    @Synchronized
    fun enqueue(text: String, category: String, at: Long) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        val current = pending().toMutableList()
        current += DonationDto(cleaned, category, at)
        prefs?.edit()?.putString(KEY, json.encodeToString(current.takeLast(MAX)))?.commit()
    }

    @Synchronized
    fun count(): Int = pending().size

    @Synchronized
    fun clear() {
        prefs?.edit()?.remove(KEY)?.commit()
    }

    private fun pending(): List<DonationDto> {
        val raw = prefs?.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DonationDto>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "donations"
        const val MAX = 100
    }
}
