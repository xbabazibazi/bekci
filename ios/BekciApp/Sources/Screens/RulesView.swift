import SwiftUI
import BekciCore

struct RulesView: View {
    @EnvironmentObject private var state: AppState
    @State private var newKeyword = ""
    @State private var isAddingKeyword = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("Model kararlarını sizin kurallarınız ezer. Buraya eklediğiniz bir gönderen veya kelime, risk skorundan bağımsız olarak uygulanır.")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.bkText2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20).padding(.top, 4)

                ruleSection(title: "Her zaman güven",
                            keys: state.rules.allowSenders.sorted(),
                            icon: "checkmark", tint: .bkGuard, soft: .bkGuardSoft)

                ruleSection(title: "Her zaman engelle",
                            keys: state.rules.blockSenders.sorted(),
                            icon: "xmark", tint: .bkSignal, soft: .bkSignalSoft)

                SectionLabel("Engellenen kelimeler · \(state.rules.blockKeywords.count)")
                Card {
                    ForEach(state.rules.blockKeywords.sorted(), id: \.self) { keyword in
                        ruleRow(keyword, subtitle: "Anahtar kelime",
                                icon: "xmark", tint: .bkSignal, soft: .bkSignalSoft) {
                            state.rules.blockKeywords.remove(keyword)
                        }
                        Divider().padding(.leading, 57)
                    }
                    Button { isAddingKeyword = true } label: {
                        SettingRow(icon: "plus", tint: .bkGuard,
                                   title: "Kelime ekle", showsChevron: false)
                    }
                    .buttonStyle(.plain)
                }

                SectionLabel("Duyarlılık")
                Card {
                    ForEach(Array(Sensitivity.allCases.enumerated()), id: \.element) { index, level in
                        Button { state.rules.sensitivity = level } label: {
                            HStack(spacing: 13) {
                                Image(systemName: icon(for: level))
                                    .font(.system(size: 15))
                                    .foregroundStyle(state.rules.sensitivity == level ? Color.bkGuard : Color.bkText3)
                                    .frame(width: 30, height: 30)
                                    .background(Color.bkLine.opacity(0.6),
                                                in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(level.title).font(Brand.label)
                                    Text(level.subtitle).font(.system(size: 12))
                                        .foregroundStyle(Color.bkText3)
                                }
                                Spacer(minLength: 8)
                                if state.rules.sensitivity == level {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(Color.bkGuard)
                                }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 13)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if index < Sensitivity.allCases.count - 1 { Divider().padding(.leading, 59) }
                    }
                }
                .padding(.bottom, 26)
            }
        }
        .background(Color.bkPaper)
        .navigationTitle("Kurallar")
        .alert("Engellenecek kelime", isPresented: $isAddingKeyword) {
            TextField("örn. bahis", text: $newKeyword)
            Button("Ekle") {
                state.addBlockedKeyword(newKeyword)
                newKeyword = ""
            }
            Button("Vazgeç", role: .cancel) { newKeyword = "" }
        } message: {
            Text("Bu kelimeyi içeren her mesaj çöpe gider. Türkçe karakter farkı dikkate alınmaz.")
        }
    }

    @ViewBuilder
    private func ruleSection(title: String, keys: [String],
                             icon: String, tint: Color, soft: Color) -> some View {
        SectionLabel("\(title) · \(keys.count)")
        Card {
            if keys.isEmpty {
                Text("Henüz kural yok. Bir mesajı açıp “Her zaman güven” veya “Spam bildir” diyerek ekleyebilirsiniz.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.bkText3)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(15)
            } else {
                ForEach(Array(keys.enumerated()), id: \.element) { index, key in
                    ruleRow(key, subtitle: "Gönderen",
                            icon: icon, tint: tint, soft: soft) {
                        state.removeRule(sender: key)
                    }
                    if index < keys.count - 1 { Divider().padding(.leading, 57) }
                }
            }
        }
    }

    private func ruleRow(_ title: String, subtitle: String,
                         icon: String, tint: Color, soft: Color,
                         onRemove: @escaping () -> Void) -> some View {
        HStack(spacing: 11) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(tint)
                .frame(width: 30, height: 30)
                .background(soft, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 14, weight: .semibold))
                Text(subtitle).font(.system(size: 11.5)).foregroundStyle(Color.bkText3)
            }
            Spacer(minLength: 8)
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.bkText3)
                    .frame(width: 26, height: 26)
                    .background(Color.bkLine.opacity(0.6), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(title) kuralını kaldır")
        }
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    private func icon(for level: Sensitivity) -> String {
        switch level {
        case .careful:  return "shield"
        case .balanced: return "slider.horizontal.3"
        case .strict:   return "exclamationmark.triangle"
        }
    }
}
