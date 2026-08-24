package tr.bekci.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * MMS (WAP push) alıcısı.
 *
 * ## Bekçi bilinçli olarak SMS-ODAKLI bir üründür
 *
 * MMS **işlenmiyor**. Tam destek WAP push PDU çözümleme, operatörün APN'i
 * üzerinden indirme ve MMS tablolarına (`Telephony.Mms`, `Mms.Part`,
 * `Mms.Addr`) yazma ister; Android bunun sınıflarını açık SDK'da
 * vermiyor. Yarım bir çözümleme sağlayıcıya bozuk kayıt yazar — hiç
 * yazmamaktan daha kötüdür.
 *
 * Alıcının varlığı ZORUNLU: bildirilmezse Android, Bekçi'yi varsayılan
 * mesaj uygulaması seçenekleri arasında hiç göstermez.
 *
 * ## Neden sessizce düşürmüyoruz
 *
 * Önceki hâlinde bu alıcı boştu ve gelen MMS hiçbir iz bırakmadan yok
 * oluyordu. Sessizce kaybolan bir mesaj, bu ürünün önlemek için var
 * olduğu hata sınıfının ta kendisi. Artık kullanıcıya bir resimli mesaj
 * geldiği ve Bekçi'nin onu gösteremediği SÖYLENİYOR — böylece gönderene
 * "SMS at" diyebilir ya da geçici olarak varsayılanı değiştirebilir.
 *
 * İçeriği gösteremiyoruz ama kaybolduğunu gizlemiyoruz.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Resimli mesaj (MMS) alınamadı")
            .setContentText("Bekçi yalnızca SMS'i destekliyor. Gönderenden SMS olarak yollamasını isteyebilirsiniz.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Size bir resimli mesaj (MMS) gönderildi ama Bekçi MMS'i desteklemiyor ve " +
                        "gösteremiyor. Bu mesaja ihtiyacınız varsa Ayarlar › Varsayılan uygulamalar › " +
                        "SMS üzerinden geçici olarak başka bir mesaj uygulamasına geçip gönderenden " +
                        "tekrar yollamasını isteyin."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(MMS_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Desteklenmeyen mesajlar",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Bekçi'nin gösteremediği resimli mesajlar (MMS) hakkında bilgilendirir."
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "bekci.mms.unsupported"
        // Sabit kimlik: arka arkaya gelen MMS'ler bildirim gölgesini
        // doldurmasın, sonuncusu öncekinin yerine geçsin.
        const val MMS_NOTIFICATION_ID = 9001
    }
}
