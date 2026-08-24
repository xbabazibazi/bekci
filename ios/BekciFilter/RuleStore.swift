import Foundation
import BekciCore

/// Kullanıcı kurallarının App Group konteyneri üzerinden taşınması.
///
/// Yön tek: **uygulama yazar, uzantı okur.** Apple, filtre uzantısının
/// paylaşılan konteynere yazmasına izin vermiyor, bu yüzden uzantı içinde
/// kalıcı durum tutmaya çalışmak boşuna.
///
/// `RuleStore` hem uygulama hem uzantı hedefine eklenir.
public final class RuleStore {

    public static let shared = RuleStore()

    /// Xcode'da her iki hedefin de "App Groups" yeteneğinde bu kimlik olmalı.
    public static let appGroup = "group.tr.bekci.shared"

    private let fileName = "rules.json"
    private let queue = DispatchQueue(label: "tr.bekci.rulestore")

    private var cached: (rules: UserRules, stamp: Date)?

    private var url: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: Self.appGroup)?
            .appendingPathComponent(fileName)
    }

    // MARK: - Okuma (uygulama + uzantı)

    /// Diskteki kuralları döndürür. Dosya değişmediyse önbellekten okur —
    /// uzantı süreci arka arkaya gelen mesajlarda tekrar tekrar disk açmasın.
    public func load() -> UserRules {
        queue.sync {
            guard let url else { return .default }

            let stamp = (try? FileManager.default.attributesOfItem(atPath: url.path)[.modificationDate]) as? Date

            if let cached, let stamp, cached.stamp == stamp {
                return cached.rules
            }

            guard let data = try? Data(contentsOf: url),
                  let rules = try? JSONDecoder().decode(UserRules.self, from: data) else {
                return .default
            }

            if let stamp { cached = (rules, stamp) }
            return rules
        }
    }

    // MARK: - Yazma (yalnızca uygulama)

    /// Uzantıdan çağrılırsa sessizce başarısız olur — Apple izin vermiyor.
    @discardableResult
    public func save(_ rules: UserRules) -> Bool {
        queue.sync {
            guard let url, let data = try? JSONEncoder().encode(rules) else { return false }
            do {
                // .completeFileProtection değil: uzantı cihaz kilitliyken de
                // çağrılabilir ve kuralları okuyamazsa filtre çalışmaz.
                // İlk açılıştan sonra erişilebilir olan seviye doğru denge.
                try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                cached = nil
                return true
            } catch {
                return false
            }
        }
    }
}
