import SwiftUI

/// Aydınlatma metni — uygulama İÇİNDE, çevrimdışı.
///
/// Önceden Ayarlar'dan `https://bekci.app/kvkk` adresine bir bağlantı
/// vardı; o alan adı yayında değil ve kullanıcı boş sayfaya düşüyordu.
/// Ayrıca ağ erişimi olmayan bir üründe gizlilik metnini ağdan çekmek
/// kendi iddiasıyla çelişirdi.
///
/// **Bu metin bilgilendirmedir, hukuki görüş değildir.** Uygulama
/// yayınlanmadan önce bir hukukçu tarafından gözden geçirilmeli;
/// özellikle bağış akışı devreye alındığında (metin sunucuya gitmeye
/// başladığında) VERBİS ve açık rıza yükümlülükleri yeniden
/// değerlendirilmelidir.
struct PrivacyView: View {

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Mesajlarınız telefonunuzdan çıkmıyor.")
                    .font(.system(size: 21, weight: .bold))
                    .kerning(-0.7)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.bottom, 10)

                Text("Aşağıdakiler Bekçi'nin ne yaptığının ve ne yapmadığının tam listesidir.")
                    .font(.system(size: 13.5))
                    .foregroundStyle(Color.bkText2)
                    .fixedSize(horizontal: false, vertical: true)

                section("Toplanan veri", items: [
                    ("Hiçbir kişisel veri toplanmıyor",
                     "Bekçi'nin sunucusu yok. Hesap açmanız istenmez, telefon numaranız veya cihaz kimliğiniz hiçbir yere gönderilmez."),
                    ("Sınıflandırma cihazda yapılır",
                     "Gelen mesaj, telefonunuzun içindeki kural motorundan geçer. Mesajın kendisi cihazdan çıkmaz."),
                ])

                section("Cihazda saklananlar", items: [
                    ("Kurallarınız",
                     "Güvenli/engelli gönderen listeleriniz ve engellediğiniz kelimeler telefonunuzda tutulur."),
                    ("Uygulama içi mesaj listesi",
                     "iOS, filtrelenen mesajları uygulamaya bildirmez. Bu yüzden listede yalnızca sizin “Spam bağışla” ile eklediğiniz metinler bulunur."),
                    ("Silme hakkınız",
                     "Ayarlar › Saklanan mesajları sil ile tek dokunuşta hepsini kaldırabilirsiniz."),
                ])

                section("Bağış akışı", items: [
                    ("Yalnızca siz isterseniz",
                     "“Spam bağışla” ekranında gönderdiğiniz metin, ayrı ve açık onayınızla paylaşılır. Onay vermezseniz hiçbir şey gönderilmez."),
                    ("Yalnızca mesaj metni",
                     "Gönderen numarası, adınız veya cihaz kimliğiniz eklenmez. Metni göndermeden önce içindeki kişisel bilgileri silmeniz önerilir."),
                    ("Amaç",
                     "Bağışlanan metinler yalnızca filtrenin Türkçe dolandırıcılık kalıplarını daha iyi tanıması için kullanılır."),
                ])

                section("Yapılmayanlar", items: [
                    ("Reklam ve izleme yok",
                     "Bekçi reklam göstermez, üçüncü taraf izleyici (SDK) içermez."),
                    ("Veri satışı yok",
                     "Hiçbir veri satılmaz veya üçüncü taraflarla paylaşılmaz."),
                    ("Konum yok",
                     "Bekçi konum bilginize erişmez."),
                ])

                Text("Sorularınız için: kvkk@bekci.app")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.bkText3)
                    .padding(.top, 26)
                    .padding(.horizontal, 20)

                Text("Bu metin bilgilendirme amaçlıdır ve son hâlini almadan önce hukuki incelemeden geçecektir.")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.bkText3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 8)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 30)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
        .background(Color.bkPaper)
        .navigationTitle("Aydınlatma metni")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func section(_ title: String, items: [(String, String)]) -> some View {
        SectionLabel(title).padding(.horizontal, -20)
        Card {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.0).font(.system(size: 14, weight: .semibold))
                    Text(item.1)
                        .font(.system(size: 12.5))
                        .foregroundStyle(Color.bkText2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 15).padding(.vertical, 13)
                if index < items.count - 1 { Divider().padding(.leading, 15) }
            }
        }
        .padding(.horizontal, -20)
    }
}
