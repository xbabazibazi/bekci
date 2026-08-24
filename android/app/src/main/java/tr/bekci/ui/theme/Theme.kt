package tr.bekci.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bekçi tasarım dili — iOS'takiyle birebir aynı.
 * Swift karşılığı: `ios/BekciApp/Sources/DesignSystem/Theme.kt`
 *
 * Material 3'ün dinamik renklendirmesi (Material You) **bilinçli olarak
 * kapalı**: ürünün vaadi "aynı Bekçi, her telefonda" ve duvar kâğıdından
 * türeyen bir renk paleti dolandırıcılık kırmızısını da değiştirirdi.
 */

// Altı marka rengi — gradyan yok.
private val Ink = Color(0xFF12161C)
private val Paper = Color(0xFFF6F4EF)
private val Guard = Color(0xFF0F6B4F)
private val Signal = Color(0xFFB93A2B)
private val Amber = Color(0xFFA06A00)
private val Muted = Color(0xFF857F74)

private val InkDark = Color(0xFF0D1116)
private val CardDark = Color(0xFF161C24)
private val GuardDark = Color(0xFF3FA37C)
private val SignalDark = Color(0xFFE2705E)
private val AmberDark = Color(0xFFD6A03C)

data class BekciColors(
    val paper: Color, val card: Color, val line: Color,
    val text: Color, val text2: Color, val text3: Color,
    val guard: Color, val guardSoft: Color,
    val signal: Color, val signalSoft: Color,
    val amber: Color, val amberSoft: Color,
    /**
     * Satır ikonlarının arkasındaki nötr daire/kapsül rengi. Önceden
     * `line.copy(alpha = 0.6f)` kullanılıyordu — ama `Color.copy(alpha=)`
     * mevcut alfayı ÇARPMAZ, TAMAMEN DEĞİŞTİRİR. `line` aydınlık temada
     * neredeyse siyah bir renk üzerine %10 alfa taşıyor; `.copy(alpha=0.6f)`
     * bu %10'u atıp %60'a zorluyor ve ikon arkası koyu gri/siyah çıkıyordu.
     * Koyu temada `line` beyaz olduğu için aynı hata görünmüyordu. Artık
     * her iki tema için ayrı ayrı doğru tasarlanmış, sabit bir renk.
     */
    val chip: Color,
)

private val LightColors = BekciColors(
    paper = Paper, card = Color.White, line = Ink.copy(alpha = 0.10f),
    text = Ink, text2 = Color(0xFF5D584F), text3 = Color(0xFF8B8578),
    guard = Guard, guardSoft = Color(0xFFE4EFE9),
    signal = Signal, signalSoft = Color(0xFFF8E7E4),
    amber = Amber, amberSoft = Color(0xFFF8EFDD),
    chip = Color(0xFFECE9E2),
)

private val DarkColors = BekciColors(
    paper = InkDark, card = CardDark, line = Color.White.copy(alpha = 0.10f),
    text = Color(0xFFEFEDE7), text2 = Color(0xFFA8A399), text3 = Color(0xFF77726A),
    guard = GuardDark, guardSoft = Color(0xFF14301F),
    signal = SignalDark, signalSoft = Color(0xFF331A16),
    amber = AmberDark, amberSoft = Color(0xFF302511),
    chip = Color(0xFF232B36),
)

val LocalBekciColors = staticCompositionLocalOf { LightColors }

object Bekci {
    val colors: BekciColors
        @Composable get() = LocalBekciColors.current

    val radiusLarge = 22.dp
    val radiusMedium = 16.dp
    val radiusSmall = 11.dp

    val shapeLarge = RoundedCornerShape(22.dp)
    val shapeMedium = RoundedCornerShape(16.dp)
}

private val BekciTypography = Typography(
    displayLarge = TextStyle(fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.1).sp),
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
)

@Composable
fun BekciTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.guard, onPrimary = Color.White,
            background = colors.paper, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            error = colors.signal,
        )
    } else {
        lightColorScheme(
            primary = colors.guard, onPrimary = Color.White,
            background = colors.paper, onBackground = colors.text,
            surface = colors.card, onSurface = colors.text,
            error = colors.signal,
        )
    }

    CompositionLocalProvider(LocalBekciColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = BekciTypography, content = content)
    }
}
