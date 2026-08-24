package tr.bekci.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

/**
 * "Mesajla yanıtla" servisi — gelen aramayı reddederken hazır bir mesaj
 * göndermek için sistem tarafından çağrılır. Varsayılan SMS uygulaması
 * olabilmek için ZORUNLU dört bileşenden biri.
 *
 * Sistem, `smsto:` şemalı bir intent ile başlatır ve mesaj metnini
 * `Intent.EXTRA_TEXT` içinde verir.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.trim()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (!number.isNullOrEmpty() && !text.isNullOrBlank()) {
            // Gönderim başarısız olursa (SIM yok, izin geri alınmış) servis
            // çökmemeli: arama reddi akışının ortasında bir çökme, kullanıcıya
            // sistem düzeyinde hata olarak görünürdü.
            runCatching {
                @Suppress("DEPRECATION")
                val manager = SmsManager.getDefault()
                // Uzun metin tek parçaya sığmayabilir; bölünmeden gönderilirse
                // sessizce kırpılır.
                val parts = manager.divideMessage(text)
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            }
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }
}
