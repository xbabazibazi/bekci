import SwiftUI
import BekciCore

/// iOS'ta filtre uzantısı paylaşılan konteynere yazamadığı ve kullanıcının
/// Mesajlar'daki düzeltmelerini bize bildiren bir API olmadığı için,
/// modelin tek gerçek veri kaynağı bu ekrandır. Rıza ayrı ve açıktır.
struct DonateView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var text = ""
    @State private var category = "Bahis"
    @State private var consented = true
    @State private var didSend = false

    private let categories = ["Bahis", "Sahte kurum", "Sahte kargo", "Kripto", "Diğer"]

    /// Kullanıcı yazarken motorun mesajı ŞU AN nasıl gördüğünü gösterir —
    /// ekranın kendi vaadiyle ("kaçırdığımız bir mesaj") doğrudan bağlantılı:
    /// eğer motor bunu zaten çöp/dolandırıcılık görüyorsa bağışa gerek yok,
    /// görmüyorsa kullanıcı NEDEN bağışladığını somut olarak anlıyor.
    ///
    /// Bilinçli olarak `sender: nil` ve varsayılan `Classifier()` kullanılır
    /// (kullanıcının kendi kural/duyarlılık ayarları DEĞİL): burada gösterilen
    /// motorun genel davranışıdır, gönderen toplamak formun "numaranız
    /// gönderilmez" rızasıyla çelişirdi. Hiçbir yere kaydedilmez.
    private var preview: Verdict? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 8 else { return nil }
        return Classifier().classify(sender: nil, body: trimmed)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Kaçırdığımız bir\nmesaj mı var?")
                        .font(.system(size: 25, weight: .bold)).kerning(-0.9)
                        .padding(.bottom, 10)

                    Text("Yakalayamadığımız spam'i buraya yapıştırın. Türkçe spam veri seti çok küçük — sizin katkınız modeli doğrudan iyileştirir.")
                        .font(.system(size: 13.5)).lineSpacing(2)
                        .foregroundStyle(Color.bkText2)

                    TextEditor(text: $text)
                        .font(.system(size: 13.5))
                        .frame(minHeight: 112)
                        .scrollContentBackground(.hidden)
                        .padding(10)
                        .background(Color.bkCard,
                                    in: RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous)
                            .strokeBorder(Color.bkLine, lineWidth: 1))
                        .overlay(alignment: .topLeading) {
                            if text.isEmpty {
                                Text("Mesajı buraya yapıştırın…")
                                    .font(.system(size: 13.5))
                                    .foregroundStyle(Color.bkText3)
                                    .padding(.horizontal, 15).padding(.vertical, 18)
                                    .allowsHitTesting(false)
                            }
                        }
                        .padding(.top, 18)

                    if let preview {
                        HStack(spacing: 8) {
                            Text("Bekçi şu an bunu görüyor:")
                                .font(.system(size: 11.5))
                                .foregroundStyle(Color.bkText3)
                            CategoryBadge(verdict: preview)
                            Spacer(minLength: 0)
                            Text("risk \(preview.risk)")
                                .font(.system(size: 11.5, weight: .semibold))
                                .monospacedDigit()
                                .foregroundStyle(Color.bkText3)
                        }
                        .padding(.top, 10)
                    }

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 7) {
                            ForEach(categories, id: \.self) { item in
                                Button { category = item } label: {
                                    Text(item)
                                        .font(.system(size: 12.5, weight: .semibold))
                                        .padding(.horizontal, 13).padding(.vertical, 7)
                                        .foregroundStyle(category == item ? Color.bkPaper : Color.bkText2)
                                        .background(category == item ? Color.bkText : Color.bkCard, in: Capsule())
                                        .overlay(Capsule().strokeBorder(
                                            category == item ? .clear : Color.bkLine, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.top, 12)

                    Button { consented.toggle() } label: {
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: consented ? "checkmark" : "")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 20, height: 20)
                                .background(consented ? Color.bkGuard : .clear,
                                            in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                                .overlay(RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .strokeBorder(consented ? .clear : Color.bkLine, lineWidth: 1.5))
                                .padding(.top, 1)
                            Text("Bu mesajın **yalnızca metnini** Bekçi ile paylaşmayı kabul ediyorum. Telefon numaram, adım veya cihaz kimliğim gönderilmez. Mesajı göndermeden önce içindeki kişisel bilgileri silmem gerektiğini biliyorum.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.bkText2)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(14)
                        .background(Color.bkCard,
                                    in: RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Brand.radiusMedium, style: .continuous)
                            .strokeBorder(Color.bkLine, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 16)

                    Text("Bağışlanan metinler yalnızca model eğitimi için kullanılır ve üçüncü taraflarla paylaşılmaz. İstediğiniz zaman geri çekebilirsiniz.")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Color.bkText3)
                        .padding(.top, 12)
                }
                .padding(.horizontal, 20).padding(.top, 8)
            }

            PrimaryButton(title: didSend ? "Teşekkürler" : "Bağışla ve gönder",
                          icon: didSend ? "checkmark" : "heart") {
                send()
            }
            .disabled(!consented || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(consented && !text.isEmpty ? 1 : 0.5)
            .padding(.horizontal, 20).padding(.vertical, 14)
            .background(Color.bkPaper)
            .overlay(Divider(), alignment: .top)
        }
        .background(Color.bkPaper)
        .navigationTitle("Spam bağışla")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func send() {
        // Bağış kuyruğu: metin, kullanıcı Wi-Fi'ye bağlandığında ve yalnızca
        // rıza verilmişse gönderilir. Gönderen numarası ve cihaz kimliği
        // hiçbir koşulda eklenmez.
        DonationQueue.shared.enqueue(text: text, category: category)
        didSend = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { dismiss() }
    }
}

/// Bağış kuyruğu. Gönderim uç noktası eklenene kadar cihazda **kalıcı**
/// olarak biriktirir — belleğe yazıp uygulama kapanınca kaybetmek,
/// kullanıcıya "gönderdim" hissi verip hiçbir şey yapmamak olurdu.
///
/// Uç nokta eklendiğinde yalnızca metin gönderilir: gönderen numarası,
/// cihaz kimliği veya konum değil.
final class DonationQueue {

    static let shared = DonationQueue()

    struct Entry: Codable, Hashable {
        let text: String
        let category: String
        let at: Date
    }

    private let key = "bekci.donations"
    private let defaults = UserDefaults.standard
    private let maximum = 100

    private(set) lazy var pending: [Entry] = load()

    func enqueue(text: String, category: String) {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        pending = (pending + [Entry(text: cleaned, category: category, at: .now)])
            .suffix(maximum)
        persist()
    }

    func clear() {
        pending = []
        defaults.removeObject(forKey: key)
    }

    private func load() -> [Entry] {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([Entry].self, from: data) else { return [] }
        return decoded
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(pending) else { return }
        defaults.set(data, forKey: key)
    }
}
