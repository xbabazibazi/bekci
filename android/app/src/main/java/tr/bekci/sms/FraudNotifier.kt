package tr.bekci.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tr.bekci.MainActivity
import tr.bekci.R

/**
 * Bildirime dokununca açılacak intent.
 *
 * `threadId` verilirse doğrudan o konuşma açılır. Bu olmadan bildirim
 * kullanıcıyı yalnızca uygulamanın açılış ekranına atıyordu ve mesajı
 * elle bulması gerekiyordu — bildirimin işe yaramasının tek yolu
 * dokunulan mesaja gitmesidir.
 */
private fun acilisIntent(context: Context, threadId: Long): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    if (threadId > 0) intent.putExtra(EXTRA_THREAD_ID, threadId)
    return PendingIntent.getActivity(
        // requestCode thread başına ayrı: aynı PendingIntent farklı
        // konuşmalar için yeniden kullanılırsa hepsi ilk konuşmayı açar.
        context, threadId.toInt(), intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** MainActivity bu ekstrayı okuyup ilgili konuşmaya gider. */
const val EXTRA_THREAD_ID = "tr.bekci.extra.THREAD_ID"

/**
 * Dolandırıcılık bildirimi — iki alıcı da (varsayılanken `SmsDeliverReceiver`,
 * değilken `SmsReceiver`) buradan çağırır. Tek yerde durması, bildirimin
 * gizlilik davranışının (içerik göstermeme) iki kipte ayrışmamasını sağlar.
 */
internal fun notifyFraud(
    context: Context,
    sender: String,
    topReason: String?,
    threadId: Long = 0L,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    ensureChannel(context)

    val tapIntent = acilisIntent(context, threadId)

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("Dolandırıcılık girişimi engellendi")
        // Mesaj İÇERİĞİ bildirime konmaz: bildirim gölgesi kilit ekranında
        // görünür ve içerik orada sızabilir. Yalnızca gönderen ve gerekçe.
        .setContentText(topReason?.let { "$sender · $it" } ?: sender)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .setAutoCancel(true)
        .setContentIntent(tapIntent)
        // Kilit ekranında gönderen numarası bile görünmesin; oradaki sürüm
        // yalnızca "bir şey engellendi" der.
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Dolandırıcılık girişimi engellendi")
                .build()
        )
        .build()

    NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
}

/**
 * NORMAL (spam olmayan) mesaj bildirimi.
 *
 * Varsayılan mesaj uygulaması olmanın en temel sorumluluğu: mesaj gelince
 * kullanıcıya söylemek. Bu eklenene kadar Bekçi yalnızca dolandırıcılık
 * için bildirim atıyordu — yani normal mesajlar SESSİZCE geliyordu ve
 * kullanıcı için uygulama önceki mesajlaşma uygulamasından geri
 * gidiyordu.
 *
 * Spam bildirim ATMAZ; onlar akşam özetinde toplanır ([SpamDigest]).
 * Ürünün bütün değeri burada: gerçek mesaj hemen, çöp sessizce.
 *
 * @param unreadCount uygulama simgesindeki rozet sayısı için.
 * @param threadId bildirime dokununca doğrudan bu konuşma açılır.
 */
internal fun notifyMessage(
    context: Context,
    sender: String,
    body: String,
    unreadCount: Int,
    threadId: Long = 0L,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    ensureMessageChannel(context)

    val tapIntent = acilisIntent(context, threadId)

    // Rozet sayısı GÜVENLİ BİR TAVANLA sınırlanıyor. `unreadCount` telefonun
    // sistem SMS deposundaki TÜM zamanların okunmamış sayısıdır — Bekçi
    // varsayılan uygulama olarak ilk kez devreye girdiğinde, önceki
    // uygulamanın hiç "okundu" işaretlemediği yıllar öncesine ait yüzlerce
    // eski mesaj olabilir. Bazı launcher'lar (özellikle bazı Xiaomi/MIUI ve
    // ColorOS sürümleri) ayrıca büyük bir sayıyı doğru render etmek yerine
    // kendi iç taşma değerlerini (genelde "9999") gösteriyor. Her iki
    // durumda da 99 üstü bir rakamın kullanıcıya bir anlamı yok — tavan,
    // gerçek nedeni ne olursa olsun anlamsız rakamı önlüyor.
    val rozet = unreadCount.coerceIn(0, 99)

    val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(sender)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setContentIntent(tapIntent)
        // Rozet sayısı: launcher'lar bunu okur. Çoğu launcher yalnızca
        // NOKTA gösterir; Samsung/OneUI gibi bazıları sayıyı da gösterir.
        // Sayı, okunmamış mesaj adedidir — bildirim adedi değil.
        .setNumber(rozet)
        // Kilit ekranında mesaj İÇERİĞİ görünmesin; orada yalnızca
        // "yeni mesaj var" denir. Telefon açıkken tam metin görünür.
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setPublicVersion(
            NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Yeni mesaj")
                .setNumber(rozet)
                .build()
        )
        .build()

    NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
}

private fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_fraud_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_fraud_desc)
            // Simge rozeti bu bayrağa bağlı; kapalıysa launcher hiçbir şey
            // göstermez.
            setShowBadge(true)
        }
    )
}

private fun ensureMessageChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(MESSAGE_CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "Gelen mesajlar",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Spam olmayan yeni SMS'ler için bildirim."
            setShowBadge(true)
        }
    )
}

private const val CHANNEL_ID = "bekci.fraud"
private const val MESSAGE_CHANNEL_ID = "bekci.message"
