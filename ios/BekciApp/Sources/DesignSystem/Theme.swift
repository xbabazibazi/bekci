import SwiftUI

/// Bekçi tasarım dili — iOS ve Android'de birebir aynı.
/// Android karşılığı: `android/app/src/main/java/tr/bekci/ui/theme/Theme.kt`
enum Brand {

    // MARK: Renk
    //
    // Altı renk, gradyan yok. Sıcak kağıt zemin bu kategorideki
    // mavi/mor tekdüzelikten ayrışmak için bilinçli bir seçim.

    static let ink     = Color(hex: 0x12161C)   // metin / koyu yüzey
    static let paper   = Color(hex: 0xF6F4EF)   // sıcak kağıt zemin
    static let guard_  = Color(hex: 0x0F6B4F)   // Bekçi yeşili — güvenli / birincil
    static let signal  = Color(hex: 0xB93A2B)   // kiremit kırmızısı — dolandırıcılık
    static let amber   = Color(hex: 0xA06A00)   // kehribar — kampanya / dikkat
    static let muted   = Color(hex: 0x857F74)   // ikincil metin

    // Koyu tema karşılıkları
    static let inkDark     = Color(hex: 0x0D1116)
    static let cardDark    = Color(hex: 0x161C24)
    static let guardDark   = Color(hex: 0x3FA37C)
    static let signalDark  = Color(hex: 0xE2705E)
    static let amberDark   = Color(hex: 0xD6A03C)

    // MARK: Yarıçap / boşluk
    static let radiusLarge: CGFloat = 22
    static let radiusMedium: CGFloat = 16
    static let radiusSmall: CGFloat = 11

    // MARK: Tipografi
    // Sistem fontu (SF Pro) kullanılıyor; karakter ağırlık ve
    // harf aralığından geliyor, ayrı bir font dosyası yüklenmiyor.

    static func display(_ size: CGFloat = 29) -> Font {
        .system(size: size, weight: .bold, design: .default)
    }
    static let title = Font.system(size: 27, weight: .bold)
    static let heading = Font.system(size: 18, weight: .semibold)
    static let bodyText = Font.system(size: 15, weight: .regular)
    static let label = Font.system(size: 14.5, weight: .semibold)
    static let caption = Font.system(size: 12.5, weight: .medium)
    static let overline = Font.system(size: 10, weight: .bold)
}

/// Ortam duyarlı renkler — koyu temada otomatik karşılığa geçer.
extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >>  8) & 0xFF) / 255,
            blue:  Double( hex        & 0xFF) / 255,
            opacity: 1
        )
    }

    static func adaptive(_ light: Color, _ dark: Color) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark
            ? UIColor(dark) : UIColor(light) })
    }

    static let bkPaper   = adaptive(Brand.paper, Brand.inkDark)
    static let bkCard    = adaptive(.white, Brand.cardDark)
    static let bkGuard   = adaptive(Brand.guard_, Brand.guardDark)
    static let bkSignal  = adaptive(Brand.signal, Brand.signalDark)
    static let bkAmber   = adaptive(Brand.amber, Brand.amberDark)
    static let bkText    = adaptive(Brand.ink, Color(hex: 0xEFEDE7))
    static let bkText2   = adaptive(Color(hex: 0x5D584F), Color(hex: 0xA8A399))
    static let bkText3   = adaptive(Color(hex: 0x8B8578), Color(hex: 0x77726A))
    static let bkLine    = adaptive(Brand.ink.opacity(0.10), Color.white.opacity(0.10))

    static let bkGuardSoft  = adaptive(Color(hex: 0xE4EFE9), Color(hex: 0x14301F))
    static let bkSignalSoft = adaptive(Color(hex: 0xF8E7E4), Color(hex: 0x331A16))
    static let bkAmberSoft  = adaptive(Color(hex: 0xF8EFDD), Color(hex: 0x302511))
}
