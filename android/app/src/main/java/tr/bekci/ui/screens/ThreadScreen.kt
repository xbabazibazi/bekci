package tr.bekci.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tr.bekci.core.FilterAction
import tr.bekci.data.Conversation
import tr.bekci.data.ThreadMessage
import tr.bekci.ui.components.PrimaryButton
import tr.bekci.ui.components.ReasonsCard
import tr.bekci.ui.components.SecondaryButton
import tr.bekci.ui.theme.Bekci
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tek bir konuşma. Bekçi varsayılan mesaj uygulamasıyken kullanıcının
 * mesajlarını gerçekten okuduğu ekran budur.
 *
 * "Her zaman güven" / "Spam bildir" eylemleri BİLEREK burada — önceden
 * yalnızca eski `MessageDetailScreen`de vardı (Bekçi'nin kendi kaydettiği
 * mesajlar için) ama gerçek kullanım artık tamamen bu ekrandan geçiyor;
 * kullanıcı bir konuşmayı buradan işaretleyemiyordu.
 */
@Composable
fun ThreadScreen(
    conversation: Conversation?,
    messages: List<ThreadMessage>,
    isPro: Boolean,
    onSend: (String) -> Boolean,
    onBlock: () -> Unit,
    onTrust: () -> Unit,
    onUpgrade: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Sohbet, EN SON mesajı gösterecek şekilde AÇILMALI — bir mesajlaşma
    // ekranının en üstten başlaması kullanıcıyı en eski mesaja gömer.
    // `initialFirstVisibleItemIndex` ilk kareyi doğru konumda çizer;
    // `LaunchedEffect` içindeki `scrollToItem` ölçüm tamamlandıktan sonra
    // konumu kesinleştirir (çok satırlı son mesajlarda ilk tahmin sapabilir).
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (messages.size - 1).coerceAtLeast(0),
    )
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Bekci.colors.paper).imePadding()) {
        Text(
            conversation?.address.orEmpty(),
            style = MaterialTheme.typography.titleMedium, color = Bekci.colors.text,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SecondaryButton("Her zaman güven", Modifier.weight(1f), onClick = onTrust)
            SecondaryButton("Spam bildir", Modifier.weight(1f), Bekci.colors.signal, onClick = onBlock)
        }
        // Bu göndereni neden spam saydığımız burada — "spam bildir" demeden
        // önce kullanıcının kanıtı görebilmesi gerekiyor. Baş gerekçe
        // herkeste ücretsiz; tam döküm Pro'da.
        if (conversation != null && conversation.verdict.action == FilterAction.JUNK) {
            ReasonsCard(conversation.verdict, isPro, onUpgrade)
        }
        HorizontalDivider(color = Bekci.colors.line)

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 14.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { message -> Bubble(message) }
        }

        error?.let {
            Text(it, fontSize = 11.5.sp, color = Bekci.colors.signal,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it; error = null },
                placeholder = { Text("Mesaj") },
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                title = "Gönder",
                modifier = Modifier.weight(0.5f),
                enabled = draft.isNotBlank(),
            ) {
                if (onSend(draft.trim())) {
                    draft = ""
                } else {
                    // Sessiz başarısızlık en kötüsü: kullanıcı mesajın
                    // gittiğini sanır.
                    error = "Gönderilemedi. SIM kartı ve SMS iznini kontrol edin."
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ThreadMessage) {
    val alignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.outgoing) Bekci.colors.guard else Bekci.colors.card)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Text(
                message.body, fontSize = 13.5.sp, lineHeight = 19.sp,
                color = if (message.outgoing) androidx.compose.ui.graphics.Color.White
                else Bekci.colors.text,
            )
            Text(
                SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(message.at)),
                fontSize = 9.5.sp, fontWeight = FontWeight.Medium,
                color = if (message.outgoing)
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f)
                else Bekci.colors.text3,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
