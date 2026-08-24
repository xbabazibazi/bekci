import SwiftUI
import BekciCore

struct InboxView: View {
    @EnvironmentObject private var state: AppState
    @State private var filter: InboxFilter

    init(initialFilter: InboxFilter = .all) {
        _filter = State(initialValue: initialFilter)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 7) {
                    ForEach(InboxFilter.allCases) { option in
                        Chip(title: option.title,
                             count: option == .all ? state.messages.count
                                                   : state.messages(for: option).count,
                             isSelected: option == filter) { filter = option }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 12)
            }

            let items = state.messages(for: filter)

            if items.isEmpty {
                EmptyState(filter: filter)
            } else {
                ScrollView {
                    Card {
                        ForEach(Array(items.enumerated()), id: \.element.id) { index, message in
                            NavigationLink(value: message) { MessageRow(message: message) }
                                .buttonStyle(.plain)
                            if index < items.count - 1 { Divider().padding(.leading, 70) }
                        }
                    }
                    Text("Bekçi mesajlarınızı silmez veya taşımaz. Yalnızca telefonun kendi “Bilinmeyen ve Önemsiz” sekmelerine yönlendirir.")
                        .font(.system(size: 11.5))
                        .foregroundStyle(Color.bkText3)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 34)
                        .padding(.vertical, 16)
                }
            }
        }
        .background(Color.bkPaper)
        .navigationTitle("Kutu")
        .navigationDestination(for: StoredMessage.self) { MessageDetailView(message: $0) }
    }
}

private struct Chip: View {
    let title: String
    let count: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(title)
                Text("\(count)").opacity(0.55).monospacedDigit()
            }
            .font(.system(size: 12.5, weight: .semibold))
            .padding(.horizontal, 13).padding(.vertical, 7)
            .foregroundStyle(isSelected ? Color.bkPaper : Color.bkText2)
            .background(isSelected ? Color.bkText : Color.bkCard,
                        in: Capsule())
            .overlay(Capsule().strokeBorder(isSelected ? .clear : Color.bkLine, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct EmptyState: View {
    let filter: InboxFilter

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            Image(systemName: "checkmark.shield")
                .font(.system(size: 26, weight: .medium))
                .foregroundStyle(Color.bkGuard)
                .frame(width: 58, height: 58)
                .background(Color.bkGuardSoft,
                            in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(.bottom, 15)
            Text(filter == .junk ? "Hiç çöp yok" : "Burada bir şey yok")
                .font(.system(size: 16, weight: .semibold))
                .padding(.bottom, 6)
            Text(filter == .junk
                 ? "Bu kategoriye düşen bir mesaj olmadı."
                 : "Yeni mesajlar geldikçe burada görünecek.")
                .font(.system(size: 13))
                .foregroundStyle(Color.bkText3)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Spacer(); Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}
