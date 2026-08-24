import SwiftUI
import BekciCore

struct TodayView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0, pinnedViews: []) {
                GuardCard(isActive: state.hasCompletedSetup,
                          sensitivity: state.rules.sensitivity.title,
                          trustedCount: state.rules.allowSenders.count,
                          blockedCount: state.rules.blockSenders.count
                                      + state.rules.blockKeywords.count)
                    .padding(.top, 4)

                if state.isShowcase {
                    ShowcaseNotice().padding(.top, 16)
                }

                if !state.needsAttention.isEmpty {
                    SectionLabel("Dikkat isteyen")
                    Card {
                        ForEach(Array(state.needsAttention.enumerated()), id: \.element.id) { index, message in
                            NavigationLink(value: message) { MessageRow(message: message) }
                                .buttonStyle(.plain)
                            if index < state.needsAttention.count - 1 { Divider().padding(.leading, 70) }
                        }
                    }
                }

                SectionLabel("Kategori dağılımı")
                Card {
                    let counts = state.categoryCounts()
                    ForEach(Array(counts.enumerated()), id: \.element.0.id) { index, entry in
                        NavigationLink(value: entry.0) {
                            SettingRow(icon: icon(for: entry.0), tint: tint(for: entry.0),
                                       title: entry.0.title, value: "\(entry.1)")
                        }
                        .buttonStyle(.plain)
                        if index < counts.count - 1 { Divider().padding(.leading, 59) }
                    }
                }

                SectionLabel("Bekçi'yi güçlendir")
                Card {
                    NavigationLink(value: Route.donate) {
                        SettingRow(icon: "heart", tint: .bkGuard, title: "Spam bağışla",
                                   subtitle: "Yakalayamadığımız bir mesajı gönder, model gelişsin")
                    }.buttonStyle(.plain)
                    Divider().padding(.leading, 59)
                    NavigationLink(value: Route.paywall) {
                        SettingRow(icon: "sparkles", tint: .bkAmber, title: "Bekçi Pro",
                                   subtitle: state.isPro ? "Etkin" : "Deneme sürüyor")
                    }.buttonStyle(.plain)
                }
                .padding(.bottom, 24)
            }
        }
        .background(Color.bkPaper)
        .navigationTitle("Bugün")
        .navigationDestination(for: StoredMessage.self) { MessageDetailView(message: $0) }
        .navigationDestination(for: InboxFilter.self) { InboxView(initialFilter: $0) }
        .navigationDestination(for: Route.self) { route in
            switch route {
            case .donate:  DonateView()
            case .paywall: PaywallView()
            case .setup:   SetupView()
            }
        }
    }

    private func icon(for filter: InboxFilter) -> String {
        switch filter {
        case .finance: return "building.columns"
        case .orders:  return "shippingbox"
        case .carrier: return "antenna.radiowaves.left.and.right"
        case .promo:   return "tag"
        case .junk:    return "exclamationmark.triangle"
        case .all:     return "tray"
        }
    }

    private func tint(for filter: InboxFilter) -> Color {
        switch filter {
        case .promo: return .bkAmber
        case .junk:  return .bkSignal
        default:     return .bkGuard
        }
    }
}

enum Route: Hashable { case donate, paywall, setup }
