package tr.bekci.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tr.bekci.core.Classifier
import tr.bekci.core.FilterAction
import tr.bekci.data.MessageRepository
import tr.bekci.data.Prefs
import tr.bekci.data.RuleStore
import tr.bekci.data.SmsProvider

/**
 * Bekçi VARSAYILAN SMS uygulamasıyken gelen mesajları karşılar.
 *
 * `SMS_RECEIVED` ile farkı kritik: `SMS_DELIVER` yalnızca varsayılan
 * uygulamaya gönderilir ve **sistem artık mesajı kendisi kaydetmez**.
 * Sağlayıcıya yazmak bu sınıfın sorumluluğudur; yazmazsak mesaj
 * telefondan tamamen kaybolur.
 *
 * ## Spam'e ne oluyor
 *
 * Spam mesaj **yine de sağlayıcıya yazılır**, silinmez. Sebebi ürünün
 * kendi ilkesi: yanlış pozitif en pahalı hatadır ve bir mesajı sessizce
 * yok etmek geri alınamaz. Ayrım Bekçi'nin ARAYÜZÜNDE yapılır — spam
 * ana konuşma listesinde görünmez, ayrı bölümde durur. Kullanıcı yanlış
 * işaretlendiğini söylerse mesaj kaybolmadığı için geri getirilebilir.
 *
 * Ayrıca sağlayıcıya yazmak, kullanıcının ileride başka bir mesajlaşma
 * uygulamasına geçebilmesi için de şart: geçmiş orada durur.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        // Çok parçalı SMS ayrı PDU'lar hâlinde gelir; ayrı sınıflandırılırsa
        // spam gövdesi bölünüp kaçabilir. Parçalar birleştiriliyor.
        // getMessagesFromIntent platform tipi döndürür ve bozuk bir PDU'da
        // eleman null olabilir — filtrelenmezse alıcı çöker ve mesaj kaybolur.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return

        val sender = parts.first().displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val sentAt = parts.first().timestampMillis

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val verdict = Classifier(RuleStore(context).load()).classify(sender, body)

                // ÖNCE sağlayıcıya yaz. Sınıflandırma sonucu ne olursa olsun
                // ve sonraki adımlar patlasa bile mesaj telefonda kalsın.
                // Dönen thread kimliği bildirime konuyor: kullanıcı bildirime
                // dokununca doğrudan o konuşma açılsın.
                val threadId = writeToInbox(context, sender, body, sentAt)

                // Sonra Bekçi'nin kendi kaydı: karar ve gerekçeler burada
                // tutuluyor, sağlayıcıda böyle bir alan yok.
                MessageRepository(context).insert(
                    sender = sender, body = body,
                    receivedAt = sentAt, verdict = verdict,
                )

                // Bildirim politikası — ürünün asıl vaadi burada:
                //  · yüksek riskli dolandırıcılık → HEMEN uyar (kullanıcı
                //    numarayı arayıp para göndermeden önce görmeli)
                //  · diğer spam → SESSİZ, akşam özetinde toplanır
                //  · normal mesaj → normal mesaj bildirimi + rozet
                val junk = verdict.action == FilterAction.JUNK
                when {
                    verdict.isFraud && Prefs(context).fraudNotifications() ->
                        notifyFraud(context, sender, verdict.reasons.firstOrNull()?.title, threadId)
                    !junk ->
                        notifyMessage(
                            context, sender, body,
                            SmsProvider(context).unreadCount(), threadId,
                        )
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Mesajı sistem gelen kutusuna yazar.
     *
     * `Telephony.Sms.Inbox.CONTENT_URI` yalnızca varsayılan SMS
     * uygulamasının yazabildiği bir alandır; Bekçi varsayılan değilken
     * bu çağrı `SecurityException` atar. Alıcı zaten yalnızca varsayılanken
     * tetiklenir ama rol geçişi sırasındaki yarış durumuna karşı yine de
     * korumalı: bir istisna yüzünden `goAsync()` bloğu çökerse bildirim
     * de kaybolurdu.
     */
    private fun writeToInbox(
        context: Context,
        sender: String,
        body: String,
        sentAt: Long,
    ): Long = runCatching {
        val uri = context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, sentAt)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            },
        ) ?: return@runCatching 0L

        // Thread kimliğini sağlayıcı atar (aynı numaradan gelenler aynı
        // thread'e düşer); eklediğimiz satırı geri okuyup öğreniyoruz.
        context.contentResolver.query(
            uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)
}
