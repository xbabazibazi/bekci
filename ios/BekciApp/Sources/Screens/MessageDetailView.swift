import SwiftUI
import BekciCore

/// Tek bir ekran iki hâli birden taşır: güvenli mesaj ve dolandırıcılık uyarısı.
/// Ayrı ekranlar yapmak, aynı bilgiyi iki yerde tutmak demekti.
struct MessageDetailView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss

    let message: StoredMessage

    private var verdict: Verdict { message.verdict }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if verdict.isFraud { fraudBanner } else { safeHeader }

                Text(message.body)
                    .font(.system(size: 14.5))
                    .lineSpacing(3)
                    .padding(15)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(verdict.isFraud ? Color.bkSignalSoft : Color.bkCard)
                    .clipShape(BubbleShape())
                    .overlay(BubbleShape().strokeBorder(Color.bkLine, lineWidth: 1))
                    .padding(.horizontal, 20)
                    .padding(.top, 16)
                    .textSelection(.enabled)

                HStack {
                    Text(message.sender)
                    Spacer()
                    Text(message.receivedAt.formatted(date: .abbreviated, time: .shortened))
                }
                .font(.system(size: 11.5, weight: .medium))
                .foregroundStyle(Color.bkText3)
                .padding(.horizontal, 22).padding(.top, 8)

                if verdict.isFraud { reasonList } else { evidenceTable }

                if verdict.isFraud { advice }

                actions.padding(.top, 20).padding(.bottom, 30)
            }
        }
        .background(Color.bkPaper)
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: Başlıklar

    private var safeHeader: some View {
        HStack(spacing: 13) {
            Avatar(text: message.initials, verdict: verdict)
                .scaleEffect(1.2).frame(width: 46, height: 46)
            VStack(alignment: .leading, spacing: 4) {
                Text(message.sender).font(.system(size: 18, weight: .bold))
                CategoryBadge(verdict: verdict)
            }
            Spacer()
        }
        .padding(.horizontal, 20).padding(.top, 8)
    }

    private var fraudBanner: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 5) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 11))
                Text("YÜKSEK RİSK").font(Brand.overline).tracking(1.3)
            }
            .foregroundStyle(.white.opacity(0.8))

            Text("Bu bir dolandırıcılık girişimi")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.white)

            Text(headline)
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.93))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(EdgeInsets(top: 16, leading: 18, bottom: 16, trailing: 18))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.bkSignal)
        .clipShape(RoundedRectangle(cornerRadius: Brand.radiusLarge, style: .continuous))
        .padding(.horizontal, 20).padding(.top, 4)
    }

    /// Başlıktaki tek cümle, en ağır gerekçeye göre değişir — genel bir
    /// "bu spam" uyarısı kullanıcıyı ikna etmiyor, somut olan ediyor.
    private var headline: String {
        switch verdict.reasons.first?.code {
        case "codeHarvest":
            return "Doğrulama kodunuzu istiyor. Hiçbir kurum bunu yapmaz — kodu kimseyle paylaşmayın."
        case "impersonation":
            return "Resmî bir kurum size SMS ile bağlantı göndermez. Bağlantıya dokunmayın."
        case "gambling":
            return "Yasa dışı bahis reklamı. Türkiye'de bu siteler suç konusudur."
        case "prize":
            return "Kazandığınız bir ödül yok. Bu kalıp, bilgilerinizi almak için kullanılıyor."
        default:
            return "Bu mesajdaki bağlantıya dokunmayın ve içindeki numarayı aramayın."
        }
    }

    // MARK: Gerekçeler

    private var reasonList: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionLabel("Neden şüpheli — \(verdict.reasons.count) işaret")
                .padding(.horizontal, -20)
            Card {
                ForEach(Array(verdict.reasons.enumerated()), id: \.element.id) { index, reason in
                    HStack(alignment: .top, spacing: 11) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(Color.bkSignal)
                            .frame(width: 20, height: 20)
                            .background(Color.bkSignalSoft, in: Circle())
                            .padding(.top, 1)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(reason.title).font(.system(size: 13.5, weight: .semibold))
                            Text(reason.detail).font(.system(size: 12))
                                .foregroundStyle(Color.bkText2)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 15).padding(.vertical, 12)
                    if index < verdict.reasons.count - 1 { Divider().padding(.leading, 46) }
                }
            }
            .padding(.horizontal, -20)
        }
        .padding(.horizontal, 20)
    }

    private var evidenceTable: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionLabel("Bekçi ne gördü").padding(.horizontal, -20)
            Card {
                row("Gönderen tipi", verdict.senderKind.label)
                Divider().padding(.horizontal, 15)
                row("Risk skoru", "\(verdict.risk) / 100")
                Divider().padding(.horizontal, 15)
                row("Karar", "\(verdict.action.rawValue)" +
                    (verdict.subAction == .none ? "" : " · \(verdict.subAction.rawValue)"))
                if let top = verdict.reasons.first {
                    Divider().padding(.horizontal, 15)
                    row("Ana gerekçe", top.title)
                }
            }
            .padding(.horizontal, -20)
        }
        .padding(.horizontal, 20)
    }

    private func row(_ key: String, _ value: String) -> some View {
        HStack {
            Text(key).font(.system(size: 12)).foregroundStyle(Color.bkText3)
            Spacer()
            Text(value).font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.bkText2)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 15).padding(.vertical, 10)
    }

    private var advice: some View {
        Card {
            HStack(alignment: .top, spacing: 11) {
                Image(systemName: "eye")
                    .font(.system(size: 15)).foregroundStyle(Color.bkGuard)
                    .padding(.top, 1)
                Text("Gerçek bir borcunuz olup olmadığını **e-Devlet › UYAP** üzerinden kendiniz kontrol edin. Mesajdaki hiçbir numarayı aramayın.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Color.bkText3)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(15)
        }
        .padding(.top, 14)
    }

    // MARK: Eylemler

    private var actions: some View {
        VStack(spacing: 9) {
            if verdict.isFraud {
                PrimaryButton(title: "Bu göndereni engelle",
                              icon: "shield.slash", tint: .bkSignal) {
                    state.alwaysBlock(message.sender)
                    dismiss()
                }
                Button("Yanlış işaretlendi, bu güvenli") {
                    state.reportFalsePositive(message)
                    dismiss()
                }
                .font(.system(size: 13.5, weight: .semibold))
                .foregroundStyle(Color.bkText3)
                .padding(.vertical, 6)
            } else {
                HStack(spacing: 9) {
                    SecondaryButton(title: "Her zaman güven") {
                        state.alwaysTrust(message.sender)
                        dismiss()
                    }
                    SecondaryButton(title: "Spam bildir", tint: .bkSignal) {
                        state.alwaysBlock(message.sender)
                        dismiss()
                    }
                }
            }
        }
        .padding(.horizontal, 20)
    }
}

private struct BubbleShape: InsettableShape {
    var inset: CGFloat = 0

    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect.insetBy(dx: inset, dy: inset),
             cornerSize: CGSize(width: 18, height: 18))
    }

    func inset(by amount: CGFloat) -> some InsettableShape {
        var copy = self
        copy.inset += amount
        return copy
    }
}
