package tr.bekci

import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import tr.bekci.ui.components.PrimaryButton
import tr.bekci.ui.theme.Bekci
import tr.bekci.ui.theme.BekciTheme

/**
 * `sms:` / `smsto:` niyetlerini karşılayan yazma ekranı.
 *
 * Varsayılan SMS uygulaması olabilmek için ZORUNLU dört bileşenden biri:
 * başka bir uygulama ("bu numaraya mesaj gönder") bu ekranı açar.
 *
 * Bilinçli olarak minimal — tam konuşma ekranı ve geçmiş henüz yok
 * (bkz. README "Yapılmamış olanlar"). Buradaki amaç, rolü alabilmek ve
 * gelen niyeti sessizce düşürmemek: kullanıcı "mesaj gönder" deyip hiçbir
 * şey açılmamasından, sade ama çalışan bir ekran görmeyi yeğler.
 */
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // sms:+905321184409 → schemeSpecificPart = "+905321184409"
        val number = intent?.data?.schemeSpecificPart?.trim().orEmpty()
        val prefill = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        setContent {
            BekciTheme {
                ComposeScreen(initialNumber = number, initialText = prefill) { finish() }
            }
        }
    }
}

@Composable
private fun ComposeScreen(
    initialNumber: String,
    initialText: String,
    onDone: () -> Unit,
) {
    var number by remember { mutableStateOf(initialNumber) }
    var text by remember { mutableStateOf(initialText) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Bekci.colors.paper,
    ) { inner ->
        Column(
            Modifier.fillMaxSize().background(Bekci.colors.paper)
                .padding(inner).padding(20.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Yeni mesaj", style = MaterialTheme.typography.headlineMedium,
                color = Bekci.colors.text)

            OutlinedTextField(
                value = number, onValueChange = { number = it },
                label = { Text("Numara") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Mesaj") },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            PrimaryButton(
                title = "Gönder",
                enabled = number.isNotBlank() && text.isNotBlank(),
            ) {
                val sent = runCatching {
                    @Suppress("DEPRECATION")
                    val manager = SmsManager.getDefault()
                    // Uzun metin bölünmeden gönderilirse sessizce kırpılır.
                    manager.sendMultipartTextMessage(
                        number.trim(), null, manager.divideMessage(text), null, null,
                    )
                }.isSuccess

                if (sent) {
                    onDone()
                } else {
                    // Sessizce başarısız olmak, kullanıcının mesajı gittiğini
                    // sanmasına yol açar — bu, gönderilmemesinden daha kötü.
                    scope.launch {
                        snackbar.showSnackbar("Mesaj gönderilemedi. SIM kartı ve izinleri kontrol edin.")
                    }
                }
            }
        }
    }
}
