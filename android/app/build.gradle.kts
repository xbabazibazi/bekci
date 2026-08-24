import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * İmzalama bilgileri `keystore.properties`ten okunur; dosya sürüm
 * kontrolüne girmez. Yoksa release derlemesi imzasız üretilir (CI ya da
 * anahtarı olmayan bir geliştirici derlemeyi yine de deneyebilsin diye) —
 * sessizce imzasız APK yayınlanmasın diye durum derleme çıktısına yazılır.
 */
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningKey = keystoreProperties.containsKey("storeFile")

android {
    namespace = "tr.bekci"
    compileSdk = 35

    defaultConfig {
        applicationId = "tr.bekci"
        minSdk = 26
        targetSdk = 35
        // SÜRÜM KURALI: her yayınlanan derlemede ikisi de artar.
        // `versionCode` Play'in sıralama için kullandığı tam sayıdır ve
        // ASLA azalamaz/tekrarlanamaz; `versionName` kullanıcının gördüğü
        // metindir ve Ayarlar'da BuildConfig üzerinden okunur.
        versionCode = 9
        versionName = "0.4.2"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v3, imza anahtarının ileride döndürülebilmesini sağlar;
                // v2 tek başına yeterli olsa da rotasyon yolunu kapatırdı.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningKey) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("UYARI: keystore.properties yok — release APK İMZASIZ üretilecek.")
            }
        }
    }

    lint {
        // Bu kontrol, SMS izninin telefon donanımı ima etmesinden yakınıp
        // `required="false"` istiyor ki uygulama ChromeOS'ta da kurulabilsin.
        // Bekçi için bu YANLIŞ olurdu: SMS alamayan bir cihazda uygulama
        // hiçbir işe yaramaz. Manifestte donanımı bilinçli olarak
        // `required="true"` beyan ediyoruz; kontrol o yüzden kapalı.
        disable += "PermissionImpliesUnsupportedChromeOsHardware"
        // Release derlemesi lint hatasında dursun — uyarılar engel değil.
        abortOnError = true
        warningsAsErrors = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        // Sürüm numarası arayüzde ELLE yazılmamalı: bir dönem "0.1.0"
        // gömülüydü ve build.gradle 0.2.0'a çıktığında ekran yalan
        // söylemeye başladı. Artık BuildConfig.VERSION_NAME okunuyor.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Saklanan mesajlar düz metin yazılmamalı — cihaz kilidi çözülmüş
    // olsa bile başka bir uygulamanın yedek üzerinden okumasını zorlaştırır.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
