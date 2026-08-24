import SwiftUI
import BekciCore

struct SettingsView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Gizlilik iddiası en üstte: ürünün ana satış argümanı
                // burada kanıtlanıyor, dipnota gömülmüyor.
                Card {
                    HStack(spacing: 12) {
                        Image(systemName: "cpu")
                            .font(.system(size: 20))
                            .foregroundStyle(Color.bkGuard)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Çevrimdışı çalışıyor")
                                .font(Brand.label).foregroundStyle(Color.bkGuard)
                            // Ölçülen bir sayı değil, mimari bir gerçek:
                            // uygulama hiçbir ağ API'si kullanmıyor.
                            Text("Bekçi ağ erişimi kullanmaz")
                                .font(.system(size: 11.5)).foregroundStyle(Color.bkText3)
                        }
                        Spacer()
                    }
                    .padding(15)
                }
                .padding(.top, 4)

                SectionLabel("Filtre")
                Card {
                    HStack(spacing: 13) {
                        icon("shield", .bkGuard)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Bekçi etkin").font(Brand.label)
                            Text("Mesajlar › Önemsiz filtresi")
                                .font(.system(size: 12)).foregroundStyle(Color.bkText3)
                        }
                        Spacer()
                        NavigationLink(value: Route.setup) {
                            Text("Kontrol et").font(.system(size: 13, weight: .semibold))
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 13)

                    Divider().padding(.leading, 59)
                    NavigationLink { RulesView() } label: {
                        SettingRow(icon: "slider.horizontal.3", title: "Duyarlılık",
                                   value: state.rules.sensitivity.title)
                    }.buttonStyle(.plain)

                    Divider().padding(.leading, 59)
                    Toggle(isOn: $state.fraudNotifications) {
                        HStack(spacing: 13) {
                            icon("bell", .bkText2)
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Dolandırıcılık bildirimi").font(Brand.label)
                                Text("Yüksek riskte anında uyar")
                                    .font(.system(size: 12)).foregroundStyle(Color.bkText3)
                            }
                        }
                    }
                    .tint(.bkGuard)
                    .padding(.horizontal, 16).padding(.vertical, 9)
                }

                SectionLabel("Gizlilik")
                Card {
                    HStack(spacing: 13) {
                        icon("cpu", .bkText2)
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Cihaz içi işleme").font(Brand.label)
                            Text("Kapatılamaz — mimarinin parçası")
                                .font(.system(size: 12)).foregroundStyle(Color.bkText3)
                        }
                        Spacer()
                        Image(systemName: "lock.fill")
                            .font(.system(size: 13)).foregroundStyle(Color.bkGuard)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 13)

                    Divider().padding(.leading, 59)
                    Button {
                        state.clearStoredMessages()
                    } label: {
                        SettingRow(icon: "trash", tint: .bkSignal,
                                   title: "Saklanan mesajları sil",
                                   subtitle: "Cihazda \(state.messages.count) mesaj tutuluyor",
                                   showsChevron: false)
                    }.buttonStyle(.plain)

                    Divider().padding(.leading, 59)
                    // Uygulama İÇİ metin: ağ erişimi olmayan bir üründe
                    // gizlilik metnini tarayıcıya göndermek tutarsız olurdu
                    // (ve `bekci.app/kvkk` yayında değildi — kullanıcı boş
                    // sayfaya düşüyordu).
                    NavigationLink { PrivacyView() } label: {
                        SettingRow(icon: "eye", title: "Aydınlatma metni ve KVKK")
                    }.buttonStyle(.plain)
                }

                SectionLabel("Bekçi")
                Card {
                    NavigationLink(value: Route.paywall) {
                        SettingRow(icon: "sparkles", tint: .bkAmber, title: "Bekçi Pro",
                                   subtitle: state.isPro ? "Etkin · teşekkürler" : "Deneme sürüyor")
                    }.buttonStyle(.plain)
                    Divider().padding(.leading, 59)
                    NavigationLink(value: Route.donate) {
                        SettingRow(icon: "heart", tint: .bkGuard, title: "Spam bağışla")
                    }.buttonStyle(.plain)
                    Divider().padding(.leading, 59)
                    NavigationLink(value: Route.setup) {
                        SettingRow(icon: "questionmark.circle", title: "Kurulumu tekrar göster")
                    }.buttonStyle(.plain)
                }

                Text("Bekçi \(Bundle.main.shortVersion)")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Color.bkText3)
                    .padding(.vertical, 22)
            }
        }
        .background(Color.bkPaper)
        .navigationTitle("Ayarlar")
        .navigationDestination(for: Route.self) { route in
            switch route {
            case .donate:  DonateView()
            case .paywall: PaywallView()
            case .setup:   SetupView()
            }
        }
    }

    private func icon(_ name: String, _ tint: Color) -> some View {
        Image(systemName: name)
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(tint)
            .frame(width: 30, height: 30)
            .background(Color.bkLine.opacity(0.6),
                        in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }
}

extension Bundle {
    var shortVersion: String {
        object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.1.0"
    }
}
