import SwiftUI
import StoreKit

/// Türkiye pazarında abonelik dönüşümü %1 bandında kalıyor; bu yüzden
/// ömür boyu tek seferlik satın alma öne çıkarılıyor. Sıralama bilinçli.
struct PaywallView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var selected: Plan = .lifetime
    @State private var isRestoring = false

    enum Plan: String, CaseIterable, Identifiable {
        case lifetime, yearly, monthly
        var id: String { rawValue }

        var title: String {
            switch self {
            case .lifetime: return "Ömür boyu"
            case .yearly:   return "Yıllık"
            case .monthly:  return "Aylık"
            }
        }
        var price: String {
            switch self {
            case .lifetime: return "₺1.199"
            case .yearly:   return "₺499"
            case .monthly:  return "₺69,99"
            }
        }
        var detail: String {
            switch self {
            case .lifetime: return "Tek seferlik ödeme, abonelik yok"
            case .yearly:   return "Ayda ₺41,58 · istediğiniz zaman iptal"
            case .monthly:  return "Denemek için"
            }
        }
        /// App Store Connect'te tanımlanacak ürün kimlikleri.
        var productID: String {
            switch self {
            case .lifetime: return "tr.bekci.pro.lifetime"
            case .yearly:   return "tr.bekci.pro.yearly"
            case .monthly:  return "tr.bekci.pro.monthly"
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Burada bilinçli olarak istatistik YOK. Eskiden ödeme
                    // ekranı "bu hafta N mesaj ayıkladım" kartıyla ikna
                    // ediyordu; iOS'ta o sayı gerçek trafiği değil ilk açılış
                    // örneklerini sayıyordu. Uydurma rakamla satış yapılmaz.
                    Text("Bekçi Pro")
                        .font(.system(size: 24, weight: .bold)).kerning(-0.9)
                        .padding(.bottom, 8)

                    // NOT (Android tarafıyla eşleşmesi gereken metin): dolandırıcılık
                    // tespiti ve baş gerekçe HER ZAMAN ücretsizdir, paywall'un
                    // arkasına konulamaz. Buradaki 5 madde Android'de gerçek
                    // kısıtlamalarla uygulandı (bkz. AppViewModel.kt,
                    // ReportScreen.kt) — Swift tarafında henüz KARŞILIĞI YOK.
                    // Bu metni değiştirmeden önce Swift gating mantığı
                    // (kelime limiti, rapor ekranı, dışa/içe aktarma)
                    // yazılmalı; aksi hâlde bu ekran de aynı "tutmayan vaat"
                    // sorununu iOS'ta yeniden yaratır.
                    Text("Dolandırıcılık tespiti ve spam ayırma her zaman ücretsizdir. Pro, güç kullanıcıları için ek katman ekler: sınırsız engellenen kelime, motorun bulduğu tüm sinyaller ve risk skoru, haftalık/aylık rapor, kuralları yedekleme/paylaşma ve öncelikli destek.")
                        .font(.system(size: 13.5)).lineSpacing(2)
                        .foregroundStyle(Color.bkText2)
                        .padding(.bottom, 16)

                    VStack(spacing: 9) {
                        ForEach(Plan.allCases) { plan in
                            planRow(plan)
                        }
                    }

                    Card {
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: "lock")
                                .font(.system(size: 15)).foregroundStyle(Color.bkGuard)
                                .padding(.top, 1)
                            Text("Bekçi'nin sunucusu yok. Ödeme App Store üzerinden yapılır; biz kart bilgisi görmeyiz.")
                                .font(.system(size: 11.5))
                                .foregroundStyle(Color.bkText3)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(15)
                    }
                    .padding(.horizontal, -20)
                    .padding(.top, 16)
                }
                .padding(.horizontal, 20).padding(.top, 8)
            }

            VStack(spacing: 8) {
                PrimaryButton(title: "\(selected.title) al · \(selected.price)") {
                    Task { await purchase(selected) }
                }
                HStack(spacing: 18) {
                    Button("Satın alımı geri yükle") { Task { await restore() } }
                        .disabled(isRestoring)
                    Button("Denemeye devam et") { dismiss() }
                }
                .font(.system(size: 13.5, weight: .semibold))
                .foregroundStyle(Color.bkText3)
                .padding(.vertical, 6)
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
            .background(Color.bkPaper)
            .overlay(Divider(), alignment: .top)
        }
        .background(Color.bkPaper)
        .navigationTitle("Bekçi Pro")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func planRow(_ plan: Plan) -> some View {
        Button { selected = plan } label: {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(plan.title).font(.system(size: 15, weight: .semibold))
                    if plan == .lifetime {
                        Text("EN ÇOK TERCİH EDİLEN")
                            .font(.system(size: 9.5, weight: .bold)).tracking(0.9)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(Color.bkGuard, in: RoundedRectangle(cornerRadius: 5))
                    }
                    Spacer()
                    Text(plan.price)
                        .font(.system(size: 17, weight: .bold)).monospacedDigit()
                }
                Text(plan.detail).font(.system(size: 12)).foregroundStyle(Color.bkText2)
            }
            .padding(.horizontal, 16).padding(.vertical, 15)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(selected == plan ? Color.bkGuardSoft : Color.bkCard,
                        in: RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous)
                .strokeBorder(selected == plan ? Color.bkGuard : Color.bkLine, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
    }

    /// Cihaz değiştiren veya uygulamayı silip yeniden kuran kullanıcı
    /// Pro'yu kaybetmemeli. `isPro` yalnızca UserDefaults'ta tutuluyor;
    /// tek doğru kaynak App Store'daki hak sahipliğidir.
    @MainActor
    private func restore() async {
        isRestoring = true
        defer { isRestoring = false }
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result,
               Plan.allCases.contains(where: { $0.productID == transaction.productID }) {
                state.isPro = true
                dismiss()
                return
            }
        }
    }

    @MainActor
    private func purchase(_ plan: Plan) async {
        do {
            let products = try await Product.products(for: [plan.productID])
            guard let product = products.first else { return }
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                guard case .verified(let transaction) = verification else { break }
                // finish() çağrılmazsa işlem her açılışta Transaction.updates
                // üzerinden yeniden teslim edilir ve kuyruk hiç boşalmaz.
                await transaction.finish()
                state.isPro = true
                dismiss()
            case .userCancelled, .pending:
                break
            @unknown default:
                break
            }
        } catch {
            // Satın alma başarısız olduğunda kullanıcıyı engellemiyoruz;
            // deneme sürümü çalışmaya devam eder.
        }
    }
}
