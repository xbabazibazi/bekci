package tr.bekci.sms

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tr.bekci.MainActivity
import tr.bekci.data.SmsProvider
import java.util.Calendar

/**
 * Akşam spam özeti.
 *
 * Her dolandırıcılık mesajı için ayrı bildirim atmak, engellemeye
 * çalıştığımız rahatsızlığın aynısını üretirdi. Onun yerine günde bir kez
 * "bugün şu kadar mesaj ayrıldı" denir. Anlık bildirim yalnızca YÜKSEK
 * RİSKLİ dolandırıcılıkta atılır ([notifyFraud]) — o gerçekten acil,
 * kullanıcı numarayı arayıp para göndermeden önce görmeli.
 *
 * Zamanlama `AlarmManager` ile: WorkManager daha modern ama yeni bir
 * bağımlılık getirirdi ve burada tek bir günlük tetik yeterli.
 */
object SpamDigest {

    /** Özet saati — akşam 20:00. */
    private const val HOUR = 20

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Bugünün saati geçtiyse yarına kur.
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        // setInexactRepeating: dakikası dakikasına olması gerekmiyor ve
        // esnek zamanlama sistemin uyandırmaları toplamasına izin verip
        // pil harcamıyor. setExact bu iş için gereksiz agresif olurdu.
        manager.setInexactRepeating(
            AlarmManager.RTC,
            next.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, SpamDigestReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal const val REQUEST_CODE = 4201
    internal const val CHANNEL_ID = "bekci.digest"
    internal const val NOTIFICATION_ID = 4202
}

/** Günlük özeti hesaplayıp bildiren alıcı. */
class SpamDigestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val pending = goAsync()
        try {
            val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            val spam = SmsProvider(context).conversations()
                .filter { it.verdict.action == tr.bekci.core.FilterAction.JUNK && it.lastAt > dayAgo }

            // Sessiz gün = bildirim yok. "Bugün 0 spam" demek, engellemeye
            // çalıştığımız gereksiz bildirimin ta kendisi olurdu.
            if (spam.isEmpty()) return

            ensureChannel(context)

            val ornekler = spam.take(3).joinToString("\n") { "· ${it.address}" }
            val notification = NotificationCompat.Builder(context, SpamDigest.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Bugün ${spam.size} mesaj spam'e ayrıldı")
                .setContentText("Gelen kutunuza girmediler. Kontrol etmek için dokunun.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Bugün ${spam.size} mesaj ana listenizden ayrıldı:\n$ornekler" +
                            if (spam.size > 3) "\n… ve ${spam.size - 3} tane daha" else ""
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, SpamDigest.REQUEST_CODE,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build()

            NotificationManagerCompat.from(context)
                .notify(SpamDigest.NOTIFICATION_ID, notification)
        } finally {
            pending.finish()
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(SpamDigest.CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                SpamDigest.CHANNEL_ID,
                "Günlük spam özeti",
                // DÜŞÜK önem: bu bilgilendirmedir, acil değil. Ses çıkarmaz,
                // ekranı yakmaz — akşam bildirim gölgesinde sessizce durur.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Gün içinde spam'e ayrılan mesajların akşam özeti."
            }
        )
    }
}

/** Cihaz yeniden başlayınca alarmlar silinir; yeniden kurulmalı. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) SpamDigest.schedule(context)
    }
}
