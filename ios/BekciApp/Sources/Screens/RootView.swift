import SwiftUI
import BekciCore

struct RootView: View {
    @EnvironmentObject private var state: AppState

    var body: some View {
        Group {
            if state.hasCompletedSetup {
                MainTabs()
            } else {
                OnboardingView()
            }
        }
        .tint(.bkGuard)
    }
}

struct MainTabs: View {
    var body: some View {
        TabView {
            NavigationStack { TodayView() }
                .tabItem { Label("Bugün", systemImage: "shield") }
            NavigationStack { InboxView() }
                .tabItem { Label("Kutu", systemImage: "tray") }
            NavigationStack { RulesView() }
                .tabItem { Label("Kurallar", systemImage: "slider.horizontal.3") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Ayarlar", systemImage: "gearshape") }
        }
    }
}
