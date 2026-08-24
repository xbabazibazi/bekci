import SwiftUI

/// Ürünün en büyük kayıp noktası. Kullanıcı buradan çıkarsa uygulama
/// yüklü ama işlevsiz kalır — bu yüzden adımlar kısa, yol tarifi birebir
/// ve butona basınca doğrudan doğru ayar sayfası açılıyor.
struct SetupView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Bekçi'yi devreye alın")
                        .font(.system(size: 25, weight: .bold))
                        .kerning(-0.9)
                        .padding(.bottom, 10)

                    Text("iOS, mesaj filtresini sistem ayarlarından seçmenizi ister. Aynı anda yalnızca bir filtre çalışabilir.")
                        .font(.system(size: 13.5))
                        .lineSpacing(2)
                        .foregroundStyle(Color.bkText2)

                    VStack(alignment: .leading, spacing: 0) {
                        step(1, done: true, title: "Bekçi'yi yükleyin",
                             detail: "Tamamlandı.", path: nil)
                        step(2, done: false, title: "Ayarlar'ı açın",
                             detail: "Aşağıdaki yolu izleyin. Butona basınca doğrudan oraya gideceksiniz.",
                             path: "Ayarlar › Uygulamalar › Mesajlar › Bilinmeyen ve Önemsiz")
                        step(3, done: false, title: "“Bekçi”yi seçin",
                             detail: "“SMS Filtreleme” altında listelenen uygulamalardan Bekçi'yi işaretleyin.",
                             path: nil)
                    }
                    .padding(.top, 14)

                    Card {
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: "lock")
                                .font(.system(size: 15)).foregroundStyle(Color.bkGuard)
                                .padding(.top, 1)
                            Text("Bu izin yalnızca cihaz içinde kullanılır. Bekçi'nin sunucusu yoktur; mesajlarınız hiçbir yere gönderilmez.")
                                .font(.system(size: 11.5))
                                .foregroundStyle(Color.bkText3)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(15)
                    }
                    .padding(.horizontal, -20)
                    .padding(.top, 8)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
            }

            VStack(spacing: 8) {
                PrimaryButton(title: "Ayarlar'ı aç", icon: "arrow.up.right") {
                    openSettings()
                    state.hasCompletedSetup = true
                }
                Button("Sonra hatırlat") { state.hasCompletedSetup = true }
                    .font(.system(size: 13.5, weight: .semibold))
                    .foregroundStyle(Color.bkText3)
                    .padding(.vertical, 6)
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
            .background(Color.bkPaper)
            .overlay(Divider(), alignment: .top)
        }
        .background(Color.bkPaper)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// iOS, üçüncü taraf uygulamaların doğrudan "Bilinmeyen ve Önemsiz"
    /// sayfasını açmasına izin vermiyor — gidebileceğimiz en yakın yer
    /// uygulamanın kendi ayar sayfası. Kullanıcıyı oradan yönlendiriyoruz.
    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func step(_ number: Int, done: Bool, title: String,
                      detail: String, path: String?) -> some View {
        HStack(alignment: .top, spacing: 14) {
            Group {
                if done {
                    Image(systemName: "checkmark").font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                } else {
                    Text("\(number)").font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(Color.bkPaper)
                }
            }
            .frame(width: 27, height: 27)
            .background(done ? Color.bkGuard : Color.bkText, in: Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 14.5, weight: .semibold))
                Text(detail).font(.system(size: 12.5))
                    .foregroundStyle(Color.bkText2)
                    .fixedSize(horizontal: false, vertical: true)
                if let path {
                    Text(path)
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundStyle(Color.bkText)
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(Color.bkLine.opacity(0.6),
                                    in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                        .padding(.top, 5)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 15)
    }
}
