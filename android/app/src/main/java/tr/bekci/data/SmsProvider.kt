package tr.bekci.data

import android.content.Context
import android.provider.Telephony
import tr.bekci.core.Classifier
import tr.bekci.core.FilterAction
import tr.bekci.core.Verdict

/**
 * Telefonun GERÇEK mesaj geçmişi — sistem SMS sağlayıcısından okunur.
 *
 * Bekçi'nin kendi şifreli deposundan ([MessageRepository]) farkı önemli:
 * o depo yalnızca Bekçi kurulduktan SONRA gelen mesajları ve onların
 * sınıflandırma gerekçelerini tutar. Kullanıcının gelen kutusu ise
 * sağlayıcıdadır ve yıllar öncesine gider. Varsayılan mesaj uygulaması
 * olarak gösterilmesi gereken budur.
 *
 * Sınıflandırma okuma anında yapılır, saklanmaz: motor mesaj başına
 * ~59 µs sürüyor, 200 mesaj ~12 ms. Bu, kuralları değiştiren kullanıcının
 * sonucu ANINDA görmesini de sağlıyor — önbelleğe alınsaydı eski kararlar
 * ekranda kalırdı.
 */
data class Conversation(
    val threadId: Long,
    val address: String,
    val lastBody: String,
    val lastAt: Long,
    val unread: Boolean,
    val verdict: Verdict,
) {
    val initials: String
        get() = address.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "#" }
}

class SmsProvider(private val context: Context) {

    /**
     * Konuşmaları en yeniden eskiye döndürür (her thread'in son mesajı).
     *
     * İzin yoksa ya da sağlayıcı okunamıyorsa boş liste döner. Arayüz bu
     * durumu "hiç mesaj yok" gibi göstermemeli — READ_SMS izni verilmemiş
     * olabilir; boş listede kullanıcıya izin/rol durumu hatırlatılıyor.
     */
    fun conversations(limit: Int = 200): List<Conversation> {
        val rules = RuleStore(context).load()
        val classifier = Classifier(rules)
        val seen = HashSet<Long>()
        val out = ArrayList<Conversation>()

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                    Telephony.Sms.TYPE,
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val thread = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val read = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
                val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext() && out.size < limit) {
                    val threadId = cursor.getLong(thread)
                    // Sorgu tarihe göre sıralı olduğu için bir thread'i ilk
                    // gördüğümüzde elimizdeki satır o thread'in SON mesajıdır.
                    if (!seen.add(threadId)) continue

                    val from = cursor.getString(address).orEmpty().ifBlank { "Bilinmeyen" }
                    val text = cursor.getString(body).orEmpty()

                    // GİDEN mesajlar sınıflandırılmaz: kendi yazdığınız
                    // metnin "dolandırıcılık" damgası yemesi saçma olurdu.
                    val incoming = cursor.getInt(type) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    val verdict = if (incoming) classifier.classify(from, text) else NEUTRAL

                    out += Conversation(
                        threadId = threadId,
                        address = from,
                        lastBody = text,
                        lastAt = cursor.getLong(date),
                        unread = incoming && cursor.getInt(read) == 0,
                        verdict = verdict,
                    )
                }
            }
        }

        return out
    }

    /**
     * Belirtilen tarihten bu yana gelen TÜM kutu mesajlarının sınıflandırması
     * (Pro rapor ekranı için). Yalnızca gönderen + zaman + karar taşınır,
     * mesaj GÖVDESİ hiç okunmaz/döndürülmez — rapor bir trend grafiğidir,
     * içerik göstermez ve "az veri sakla" ilkesine uyar.
     */
    fun messagesSince(sinceMillis: Long, limit: Int = 4000): List<ReportEntry> {
        val rules = RuleStore(context).load()
        val classifier = Classifier(rules)
        val out = ArrayList<ReportEntry>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(sinceMillis.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && out.size < limit) {
                    val from = cursor.getString(address).orEmpty().ifBlank { "Bilinmeyen" }
                    val text = cursor.getString(body).orEmpty()
                    out += ReportEntry(cursor.getLong(date), from, classifier.classify(from, text))
                }
            }
        }
        return out
    }

    /**
     * Bir konuşmanın tüm mesajları, eskiden yeniye.
     * Konuşma ekranı için; gelen/giden ayrımı `outgoing` ile taşınır.
     */
    fun messages(threadId: Long, limit: Int = 500): List<ThreadMessage> {
        val out = ArrayList<ThreadMessage>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val type = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (cursor.moveToNext() && out.size < limit) {
                    out += ThreadMessage(
                        id = cursor.getLong(id),
                        body = cursor.getString(body).orEmpty(),
                        at = cursor.getLong(date),
                        outgoing = cursor.getInt(type) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        }
        return out
    }

    /**
     * SMS gönderir ve GÖNDERİLENLER kutusuna yazar.
     *
     * Varsayılan mesaj uygulaması olduğumuzda sistem gönderilen mesajı
     * kendisi kaydetmez; yazmazsak mesaj şebekeye gider ama konuşmada hiç
     * görünmez ve kullanıcı gönderip göndermediğinden emin olamaz.
     */
    fun send(address: String, text: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val manager = android.telephony.SmsManager.getDefault()
        // Uzun metin bölünmeden gönderilirse sessizce kırpılır.
        manager.sendMultipartTextMessage(address, null, manager.divideMessage(text), null, null)

        context.contentResolver.insert(
            Telephony.Sms.Sent.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, text)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            },
        )
        true
    }.getOrDefault(false)

    /**
     * Okunmamış gelen mesaj sayısı — uygulama simgesindeki rozet için.
     *
     * Bildirim adedi değil MESAJ adedi sayılır: aynı kişiden üç mesaj
     * geldiğinde tek bildirim görünür ama rozette 3 yazmalı.
     */
    fun unreadCount(): Int = runCatching {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            null,
        )?.use { it.count } ?: 0
    }.getOrDefault(0)

    /**
     * TÜM gelen kutusunu okundu işaretler — yalnızca Bekçi varsayılan SMS
     * uygulaması olurken, `Prefs.inboxBaselined()` bayrağı arkasında BİR
     * KEZ çağrılır (bkz. `AppViewModel.init`).
     *
     * Neden gerekli: önceki mesajlaşma uygulaması eski mesajları "okundu"
     * işaretlememiş olabilir. Bu taban çizgisi çekilmezse bildirim rozeti,
     * Bekçi'yi kurar kurmaz yıllar öncesine ait yüzlerce "okunmamış"
     * mesajı sayar — tek bir yeni mesaj geldiğinde bile rozette 99+
     * görünür (gerçekte yaşanan buydu).
     */
    fun markAllRead() {
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
                "${Telephony.Sms.READ} = 0",
                null,
            )
        }
    }

    /** Konuşmayı okundu işaretler. Yalnızca varsayılan uygulama yazabilir. */
    fun markRead(threadId: Long) {
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                android.content.ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }
    }
}

data class ThreadMessage(
    val id: Long,
    val body: String,
    val at: Long,
    val outgoing: Boolean,
)

/** Rapor ekranı için tek satır — gövde metni bilinçli olarak taşınmaz. */
data class ReportEntry(val at: Long, val sender: String, val verdict: Verdict)

/** Giden mesajlar için nötr karar — sınıflandırıcı hiç çalıştırılmaz. */
private val NEUTRAL = Verdict(action = FilterAction.NONE, risk = 0)
