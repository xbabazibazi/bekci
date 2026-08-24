package tr.bekci.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tr.bekci.core.Classifier
import tr.bekci.data.MessageRepository
import tr.bekci.data.Prefs
import tr.bekci.data.RuleStore

/**
 * Bekçi varsayılan SMS uygulaması DEĞİLKEN gelen mesajları sınıflandırır.
 *
 * Ne yapamayız: `SMS_RECEIVED` dinleyen bir uygulama mesajı gelen kutusundan
 * silemez veya taşıyamaz. Bu kipte ürün mesajı **etiketler ve uyarır**;
 * ayıklamak için varsayılan uygulama olmak gerekir ([SmsRole]).
 *
 * Sınıflandırma tamamen cihaz içinde, ağ erişimi olmadan yapılır.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // ÇİFT İŞLEME KORUMASI. `SMS_RECEIVED`, varsayılan uygulamaya da
        // gönderilir — yani Bekçi varsayılanken hem bu alıcı hem
        // `SmsDeliverReceiver` tetiklenir. Guard olmasa her mesaj iki kez
        // kaydedilir ve dolandırıcılık bildirimi iki kez düşerdi.
        if (SmsRole.isDefault(context)) return

        // Çok parçalı (concatenated) SMS'ler ayrı PDU'lar hâlinde gelir ve
        // ayrı ayrı sınıflandırılırsa spam gövdesi bölünüp kaçabilir.
        // getMessagesFromIntent platform tipi döndürür: bozuk bir PDU'da
        // eleman null olabilir ve BroadcastReceiver çöker.
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return

        val sender = parts.first().displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val timestamp = parts.first().timestampMillis

        // BroadcastReceiver'ın ana iş parçacığı bloke edilmemeli.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val verdict = Classifier(RuleStore(context).load()).classify(sender, body)

                MessageRepository(context).insert(
                    sender = sender, body = body,
                    receivedAt = timestamp, verdict = verdict,
                )

                // Bu kipte NORMAL mesaj bildirimi ATILMAZ: varsayılan
                // uygulama (Google Messages vb.) zaten kendi bildirimini
                // atıyor, biz de atarsak kullanıcı her mesaj için iki
                // bildirim görür. Yalnızca dolandırıcılık uyarısı veriyoruz
                // — o, diğer uygulamanın veremeyeceği katkı.
                if (verdict.isFraud && Prefs(context).fraudNotifications()) {
                    notifyFraud(context, sender, verdict.reasons.firstOrNull()?.title)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
