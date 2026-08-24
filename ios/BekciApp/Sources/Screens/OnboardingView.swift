import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject private var state: AppState
    @State private var showSetup = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        BekciMark(size: 52).padding(.bottom, 20)

                        Text("Mesaj kutunuz\nkendini toplasın.")
                            .font(Brand.display())
                            .kerning(-1.1)
                            .padding(.bottom, 10)

                        Text("Bekçi gelen mesajları Finans, Kargo, Operatör ve Kampanya olarak ayırır — dolandırıcılık girişimlerini işaretler.")
                            .font(.system(size: 14.5))
                            .lineSpacing(2)
                            .foregroundStyle(Color.bkText2)

                        VStack(spacing: 0) {
                            feature("cpu", .bkGuard, .bkGuardSoft,
                                    "Her şey telefonunuzda",
                                    "Mesajlarınız cihazdan çıkmaz. Sunucumuz yok, hesap yok, ağ isteği yok.")
                            Divider()
                            feature("exclamationmark.triangle", .bkSignal, .bkSignalSoft,
                                    "Dolandırıcılığı adıyla söyler",
                                    "Sahte icra, kargo ve banka mesajlarını neden şüphelendiğini açıklayarak gösterir.")
                            Divider()
                            feature("tag", .bkAmber, .bkAmberSoft,
                                    "Türkçe için eğitildi",
                                    "B kodu, kısa numara ve alfanumerik başlık gibi Türkiye'ye özgü sinyalleri okur.")
                        }
                        .padding(.top, 22)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 26)
                }

                VStack(spacing: 8) {
                    PrimaryButton(title: "Kuruluma başla", icon: "arrow.right") { showSetup = true }
                    Text("Kurulum Ayarlar üzerinden 3 adım sürer · Ücretsiz denemede kart istenmez")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Color.bkText3)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 20).padding(.vertical, 14)
                .background(Color.bkPaper)
                .overlay(Divider(), alignment: .top)
            }
            .background(Color.bkPaper)
            .navigationDestination(isPresented: $showSetup) { SetupView() }
        }
    }

    private func feature(_ icon: String, _ tint: Color, _ soft: Color,
                         _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 13) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(tint)
                .frame(width: 36, height: 36)
                .background(soft, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14.5, weight: .semibold))
                Text(detail).font(.system(size: 12.5))
                    .foregroundStyle(Color.bkText2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 14)
    }
}

/// Bekçi logosu — fener/kalkan melezi. Vektör, hiçbir varlık dosyası gerekmez.
struct BekciMark: View {
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            ShieldOutline().fill(Brand.guard_)
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: size * 0.06)
                    .strokeBorder(Brand.paper, lineWidth: size * 0.048)
                    .frame(width: size * 0.27, height: size * 0.34)
                    .overlay(Circle().fill(Brand.paper).frame(width: size * 0.085))
            }
            .offset(y: size * 0.02)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

private struct ShieldOutline: Shape {
    func path(in rect: CGRect) -> Path {
        let w = rect.width, h = rect.height
        var p = Path()
        p.move(to: CGPoint(x: w * 0.5, y: h * 0.09))
        p.addLine(to: CGPoint(x: w * 0.16, y: h * 0.21))
        p.addLine(to: CGPoint(x: w * 0.16, y: h * 0.49))
        p.addCurve(to: CGPoint(x: w * 0.5, y: h * 0.92),
                   control1: CGPoint(x: w * 0.16, y: h * 0.72),
                   control2: CGPoint(x: w * 0.30, y: h * 0.86))
        p.addCurve(to: CGPoint(x: w * 0.84, y: h * 0.49),
                   control1: CGPoint(x: w * 0.70, y: h * 0.86),
                   control2: CGPoint(x: w * 0.84, y: h * 0.72))
        p.addLine(to: CGPoint(x: w * 0.84, y: h * 0.21))
        p.closeSubpath()
        return p
    }
}
