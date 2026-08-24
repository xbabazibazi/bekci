import SwiftUI

@main
// AppState @MainActor izolasyonlu; Swift 6 dil modunda senkron bir
// nonisolated bağlamdan kurulamaz. App'i de ana aktöre bağlıyoruz.
@MainActor
struct BekciApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(state)
        }
    }
}
