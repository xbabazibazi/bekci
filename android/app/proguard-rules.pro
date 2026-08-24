# kotlinx.serialization — R8 üretilen serializer'ları atmasın,
# yoksa release derlemesinde çalışma zamanında SerializationException alınır.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class tr.bekci.** {
    *** Companion;
}
-keepclasseswithmembers class tr.bekci.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class tr.bekci.**$$serializer { *; }

# BroadcastReceiver manifest üzerinden bulunuyor
-keep class tr.bekci.sms.SmsReceiver { *; }

# androidx.security:security-crypto → Google Tink → yalnızca DERLEME
# ZAMANI var olan anotasyonlar. Bunlar APK'ya hiç girmez, çalışma
# zamanında da aranmaz; R8 referansları çözemeyip release derlemesini
# tamamen durduruyordu (`minifyReleaseWithR8` başarısız).
# Anotasyonları susturmak güvenli — Tink'in kendi sınıfları korunuyor.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# Tink, anahtar yöneticilerini yansıma (reflection) ile kaydeder;
# adları karıştırılırsa şifreli depo çalışma zamanında açılamaz ve
# "Şifreli depo kurulamadı — mesaj saklanmıyor" durumuna düşülür.
# APK'yı biraz büyütür ama sessiz veri kaybına yeğdir.
-keep class com.google.crypto.tink.** { *; }
-keep class * extends com.google.crypto.tink.KeyTypeManager { *; }

# Tink'in `KeysDownloader`'ı anahtar setini AĞDAN çeker ve bunun için
# google-http-client + joda-time ister. Bekçi'nin manifestinde INTERNET
# izni YOK (yalnızca RECEIVE_SMS ve POST_NOTIFICATIONS), yani bu kod yolu
# hiçbir koşulda çalışamaz — bağımlılıkları da APK'da bulunmuyor.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
