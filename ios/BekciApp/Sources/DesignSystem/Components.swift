import SwiftUI
import BekciCore

// MARK: - Bölüm başlığı

struct SectionLabel: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text.uppercased())
            .font(Brand.overline)
            .tracking(1.3)
            .foregroundStyle(Color.bkText3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 9)
    }
}

// MARK: - Koruma kartı (Bugün ekranının kalbi)
//
// Burada BİLEREK sayaç yok. iOS'ta filtre uzantısı paylaşılan konteynere
// yazamaz (Apple kısıtı) ve kullanıcının Mesajlar'daki hareketini bildiren
// bir API de yok — yani uygulama "bu hafta N mesaj ayıkladım" DİYEMEZ.
// Bir dönem burada duran sayaç gerçek trafiği değil ilk açılış örneklerini
// sayıyordu; ödeme ekranı da aynı karta dayandığı için uydurma bir rakamla
// ikna ediyordu. Android'de aynı kart sayaç GÖSTERİR, çünkü orada
// SmsReceiver mesajı gerçekten görüyor — asimetri bilinçlidir, "eksik"
// diye iOS'a geri eklenmemeli.
//
// Buradaki üç değer uygulamanın gerçekten sahip olduğu verilerdir:
// kurulum durumu, duyarlılık ve kullanıcının kendi kuralları.
struct GuardCard: View {
    let isActive: Bool
    let sensitivity: String
    let trustedCount: Int
    let blockedCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text((isActive ? "BEKÇİ NÖBETTE" : "KURULUM BEKLİYOR")
                 + " · " + sensitivity.uppercased())
                .font(Brand.overline).tracking(1.3)
                .foregroundStyle(.white.opacity(0.72))

            Text(isActive
                 ? "Tanımadığınız numaralardan gelen mesajlar, siz görmeden değerlendiriliyor."
                 : "Filtreyi Ayarlar'dan açmadan Bekçi hiçbir mesajı göremez.")
                .font(.system(size: 15.5, weight: .semibold))
                .lineSpacing(3)
                .foregroundStyle(.white)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)

            Divider().overlay(.white.opacity(0.18)).padding(.vertical, 16)

            HStack(spacing: 24) {
                stat("\(trustedCount)", "güvenli gönderen")
                stat("\(blockedCount)", "engel kuralı")
            }
        }
        .padding(EdgeInsets(top: 22, leading: 20, bottom: 20, trailing: 20))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [Brand.guard_, Color(hex: 0x0A4A37)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: Brand.radiusLarge, style: .continuous))
        .padding(.horizontal, 20)
    }

    private func stat(_ value: String, _ caption: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value).font(.system(size: 20, weight: .bold)).monospacedDigit()
            Text(caption).font(.system(size: 11, weight: .medium)).opacity(0.8)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Örnek veri uyarısı
//
// İlk açılışta gösterilen örnek mesajlar, kullanıcı verisi gibi
// sunulmamalı: hem dürüstlük hem App Store incelemesi meselesi.
struct ShowcaseNotice: View {
    var body: some View {
        Card {
            HStack(alignment: .top, spacing: 11) {
                Image(systemName: "info.circle")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.bkText3)
                    .padding(.top, 1)
                Text("Aşağıdakiler ürünü tanıtan örnek mesajlardır. iOS filtrelenen mesajları uygulamaya bildirmez; buraya yalnızca “Spam bağışla” ile kendi eklediğiniz mesajlar gelir.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Color.bkText3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(15)
        }
    }
}

// MARK: - Kategori rozeti

struct CategoryBadge: View {
    let verdict: Verdict

    var body: some View {
        Text(label.uppercased())
            .font(.system(size: 10, weight: .bold))
            .tracking(0.6)
            .foregroundStyle(tint)
            .padding(.horizontal, 7).padding(.vertical, 3)
            .background(soft, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    private var label: String {
        if verdict.isFraud { return "Dolandırıcılık" }
        switch (verdict.action, verdict.subAction) {
        case (.junk, _):                    return "Çöp"
        case (_, .transactionalFinance):    return "Finans"
        case (_, .transactionalOrders):     return "Kargo"
        case (_, .transactionalCarrier):    return "Operatör"
        case (_, .promotionalOffers):       return "Kampanya"
        case (_, .promotionalCoupons):      return "Kupon"
        case (.transaction, _):             return "Bilgilendirme"
        case (.promotion, _):               return "Kampanya"
        case (.allow, _):                   return "Güvenli"
        case (.none, _):                    return "Sınıflandırılmadı"
        }
    }

    private var tint: Color {
        switch verdict.action {
        case .junk:      return .bkSignal
        case .promotion: return .bkAmber
        case .none:      return .bkText3
        default:         return .bkGuard
        }
    }

    private var soft: Color {
        switch verdict.action {
        case .junk:      return .bkSignalSoft
        case .promotion: return .bkAmberSoft
        case .none:      return .bkLine
        default:         return .bkGuardSoft
        }
    }
}

// MARK: - Mesaj satırı

struct MessageRow: View {
    let message: StoredMessage

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Avatar(text: message.initials, verdict: message.verdict)

            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(message.sender)
                        .font(Brand.label)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    Text(message.timeLabel)
                        .font(.system(size: 11.5, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(Color.bkText3)
                }
                Text(message.body)
                    .font(.system(size: 13))
                    .foregroundStyle(message.verdict.isFraud ? Color.bkSignal : Color.bkText2)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                CategoryBadge(verdict: message.verdict).padding(.top, 4)
            }
        }
        .padding(.vertical, 13)
        .padding(.horizontal, 20)
        .contentShape(Rectangle())
    }
}

struct Avatar: View {
    let text: String
    let verdict: Verdict

    var body: some View {
        Text(verdict.isFraud ? "!" : text)
            .font(.system(size: 12.5, weight: .bold))
            .foregroundStyle(tint)
            .frame(width: 38, height: 38)
            .background(soft, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }

    private var tint: Color {
        switch verdict.action {
        case .junk: return .bkSignal
        case .promotion: return .bkAmber
        case .none: return .bkText3
        default: return .bkGuard
        }
    }
    private var soft: Color {
        switch verdict.action {
        case .junk: return .bkSignalSoft
        case .promotion: return .bkAmberSoft
        case .none: return .bkLine
        default: return .bkGuardSoft
        }
    }
}

// MARK: - Kart ve satır kabukları

struct Card<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .background(Color.bkCard)
            .clipShape(RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous)
                    .strokeBorder(Color.bkLine, lineWidth: 1)
            )
            .padding(.horizontal, 20)
    }
}

struct SettingRow: View {
    let icon: String
    var tint: Color = .bkText2
    let title: String
    var subtitle: String? = nil
    var value: String? = nil
    var showsChevron = true

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 30, height: 30)
                .background(Color.bkLine.opacity(0.6),
                            in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(Brand.label).foregroundStyle(Color.bkText)
                if let subtitle {
                    Text(subtitle).font(.system(size: 12))
                        .foregroundStyle(Color.bkText3)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let value {
                Text(value).font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.bkText3)
            }
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.bkText3)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .contentShape(Rectangle())
    }
}

// MARK: - Butonlar

struct PrimaryButton: View {
    let title: String
    var icon: String? = nil
    var tint: Color = .bkGuard
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if let icon { Image(systemName: icon).font(.system(size: 16, weight: .semibold)) }
                Text(title).font(.system(size: 15.5, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .foregroundStyle(.white)
            .background(tint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.pressable)
    }
}

struct SecondaryButton: View {
    let title: String
    var tint: Color = .bkText
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title).font(.system(size: 14.5, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .foregroundStyle(tint)
                .background(Color.bkCard, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color.bkLine, lineWidth: 1))
        }
        .buttonStyle(.pressable)
    }
}

struct PressableButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PressableButtonStyle {
    static var pressable: PressableButtonStyle { PressableButtonStyle() }
}
