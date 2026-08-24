package tr.bekci.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Bekçi'nin varsayılan SMS uygulaması olup olmadığını sorar ve rolü ister.
 *
 * Ürün iki kipte de çalışır:
 *
 * - **Varsayılan DEĞİLKEN** — `SmsReceiver` (`SMS_RECEIVED`) dinler. Mesajı
 *   sınıflandırıp uyarır ama gelen kutusundan ayıklayamaz; sistem mesajı
 *   kendi kaydeder.
 * - **Varsayılanken** — `SmsDeliverReceiver` (`SMS_DELIVER`) dinler.
 *   Mesajı sağlayıcıya yazmak artık Bekçi'nin sorumluluğudur ve spam ana
 *   listeden ayrılabilir.
 *
 * İki kipin ikisi de desteklenmeli: kullanıcı rolü vermeyi reddedebilir ya
 * da sonradan geri alabilir, o durumda uygulama işlevsiz kalmamalı.
 */
object SmsRole {

    /** Bekçi şu anda varsayılan SMS uygulaması mı? */
    fun isDefault(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /**
     * Rolü istemek için başlatılacak intent.
     *
     * API 29+ `RoleManager` kullanır (sistem diyaloğu). Daha eskilerde
     * `ACTION_CHANGE_DEFAULT` ile aynı sonuca varılır. `null` dönerse rol
     * bu cihazda istenemiyordur (ör. telefon donanımı yok) — çağıran
     * tarafın bunu kullanıcıya söylemesi gerekir, sessizce yutmamalı.
     */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            return manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        @Suppress("DEPRECATION")
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(
            Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName,
        )
    }
}
