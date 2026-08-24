import Foundation
import Combine
import BekciCore

/// Uygulama tarafındaki mesaj kaydı.
///
/// **Önemli:** Bu kayıtlar filtre uzantısından gelmez — Apple uzantının
/// paylaşılan konteynere yazmasına izin vermiyor ve kullanıcının Mesajlar'daki
/// hareketlerini bize bildiren bir API yok. Buradaki liste, kullanıcının
/// "spam bağışla" akışıyla eklediği ve uygulama içinde denediği mesajlardır.
/// Ürün bunu kullanıcıya açıkça söyler; "tüm mesajlarınızı görüyoruz" gibi
/// bir yanılsama yaratmaz.
struct StoredMessage: Identifiable, Codable, Hashable {
    let id: UUID
    let sender: String
    let body: String
    let receivedAt: Date
    let verdict: Verdict

    init(id: UUID = UUID(), sender: String, body: String,
         receivedAt: Date = .now, verdict: Verdict) {
        self.id = id
        self.sender = sender
        self.body = body
        self.receivedAt = receivedAt
        self.verdict = verdict
    }

    var initials: String {
        let letters = sender.filter { $0.isLetter }
        guard !letters.isEmpty else { return "#" }
        return String(letters.prefix(2)).uppercased()
    }

    var timeLabel: String {
        let cal = Calendar.current
        if cal.isDateInToday(receivedAt) {
            return receivedAt.formatted(date: .omitted, time: .shortened)
        }
        if cal.isDateInYesterday(receivedAt) { return "Dün" }
        return receivedAt.formatted(.dateTime.day().month(.abbreviated))
    }
}

enum InboxFilter: String, CaseIterable, Identifiable {
    case all, finance, orders, carrier, promo, junk
    var id: String { rawValue }

    var title: String {
        switch self {
        case .all:     return "Tümü"
        case .finance: return "Finans"
        case .orders:  return "Kargo"
        case .carrier: return "Operatör"
        case .promo:   return "Kampanya"
        case .junk:    return "Çöp"
        }
    }

    func matches(_ v: Verdict) -> Bool {
        switch self {
        case .all:     return true
        case .finance: return v.subAction == .transactionalFinance
        case .orders:  return v.subAction == .transactionalOrders
        case .carrier: return v.subAction == .transactionalCarrier
        case .promo:   return v.action == .promotion
        case .junk:    return v.action == .junk
        }
    }
}

@MainActor
final class AppState: ObservableObject {

    @Published private(set) var messages: [StoredMessage] = []

    /// `messages` ilk açılış örneklerinden mi ibaret?
    ///
    /// iOS'ta uzantı paylaşılan konteynere yazamadığı için uygulama gerçek
    /// trafiği HİÇ göremez; bu liste yalnızca kullanıcının kendi eklediği
    /// mesajlarla dolabilir. Örnekler kullanıcı verisi gibi sunulmamalı —
    /// arayüz bu bayrağa bakıp açıkça "örnek" diyor.
    @Published private(set) var isShowcase: Bool = false

    @Published var rules: UserRules { didSet { persistRules() } }
    @Published var hasCompletedSetup: Bool {
        didSet { defaults.set(hasCompletedSetup, forKey: Keys.setupDone) }
    }
    @Published var isPro: Bool {
        didSet { defaults.set(isPro, forKey: Keys.isPro) }
    }
    @Published var fraudNotifications = true

    private let defaults: UserDefaults
    private let store: RuleStore

    private enum Keys {
        static let setupDone = "bekci.setupDone"
        static let isPro = "bekci.isPro"
        static let messages = "bekci.messages"
        static let showcase = "bekci.messagesAreShowcase"
    }

    init(defaults: UserDefaults = .standard, store: RuleStore = .shared) {
        self.defaults = defaults
        self.store = store
        self.rules = store.load()
        self.hasCompletedSetup = defaults.bool(forKey: Keys.setupDone)
        self.isPro = defaults.bool(forKey: Keys.isPro)
        // Örnekler bir kez kaydedilirse (kural değişince reclassifyAll()
        // yazıyor) sonraki açılışta "gerçek veri" sanılmamalı; bayrak da
        // mesajlarla birlikte saklanıyor.
        if let saved = Self.loadMessages(defaults) {
            self.messages = saved
            self.isShowcase = defaults.bool(forKey: Keys.showcase)
        } else {
            self.messages = Self.demoMessages(rules: store.load())
            self.isShowcase = true
        }
    }

    var classifier: Classifier { Classifier(rules: rules) }

    // MARK: - Sayaçlar
    //
    // "Bu hafta N mesaj ayıklandı" / "N dolandırıcılık engellendi" sayaçları
    // iOS'tan KALDIRILDI. Uzantı paylaşılan konteynere yazamıyor, Apple da
    // kullanıcının Junk hareketini bildiren bir API vermiyor — yani bu
    // sayılar gerçek trafiği değil, yalnızca ilk açılış örneklerini
    // sayıyordu ve ödeme ekranı da onlara dayanıyordu.
    // Android'de aynı sayaçlar DURUYOR ve gerçektir (SmsReceiver mesajı
    // görüyor); asimetri bilinçlidir, iOS'a "eksik" diye geri eklenmemeli.

    func categoryCounts() -> [(InboxFilter, Int)] {
        InboxFilter.allCases.dropFirst().map { filter in
            (filter, messages.filter { filter.matches($0.verdict) }.count)
        }
    }

    func messages(for filter: InboxFilter) -> [StoredMessage] {
        messages.filter { filter.matches($0.verdict) }
            .sorted { $0.receivedAt > $1.receivedAt }
    }

    var needsAttention: [StoredMessage] {
        messages.filter(\.verdict.isFraud)
            .sorted { $0.receivedAt > $1.receivedAt }
            .prefix(3).map { $0 }
    }

    // MARK: - Eylemler

    func alwaysTrust(_ sender: String) {
        rules.allowSenders.insert(UserRules.key(sender))
        rules.blockSenders.remove(UserRules.key(sender))
        reclassifyAll()
    }

    func alwaysBlock(_ sender: String) {
        rules.blockSenders.insert(UserRules.key(sender))
        rules.allowSenders.remove(UserRules.key(sender))
        reclassifyAll()
    }

    func removeRule(sender key: String) {
        rules.allowSenders.remove(key)
        rules.blockSenders.remove(key)
        reclassifyAll()
    }

    func addBlockedKeyword(_ keyword: String) {
        let normalized = TurkishText.fold(keyword).trimmingCharacters(in: .whitespaces)
        guard !normalized.isEmpty else { return }
        rules.blockKeywords.insert(normalized)
        reclassifyAll()
    }

    /// Kullanıcı "yanlış işaretlendi" dedi — göndereni kalıcı olarak güven
    /// listesine alır ve saklanan mesajları yeniden değerlendirir.
    ///
    /// Bir sayaç TUTULMUYOR: iOS'ta kullanıcının Mesajlar uygulamasındaki
    /// gerçek düzeltmelerini gören bir API yok, dolayısıyla "0 yanlış
    /// işaretleme" gibi bir rakam ürünü doğrulanamaz bir iddiaya sokardı.
    func reportFalsePositive(_ message: StoredMessage) {
        alwaysTrust(message.sender)
    }

    /// Kurallar değiştiğinde saklanan tüm mesajlar yeniden değerlendirilir;
    /// aksi halde kullanıcı bir kural ekleyip hiçbir şeyin değişmediğini görür.
    private func reclassifyAll() {
        let c = classifier
        messages = messages.map {
            StoredMessage(id: $0.id, sender: $0.sender, body: $0.body,
                          receivedAt: $0.receivedAt,
                          verdict: c.classify(sender: $0.sender, body: $0.body))
        }
        persistMessages()
    }

    /// Kullanıcının saklanan mesajları silme hakkı. Gizlilik iddiası eden
    /// bir ürünün bu düğmeyi sunmaması tutarsızlık olurdu.
    func clearStoredMessages() {
        messages = []
        isShowcase = false
        defaults.removeObject(forKey: Keys.messages)
        defaults.set(false, forKey: Keys.showcase)
    }

    // MARK: - Kalıcılık

    private func persistRules() {
        // Uzantı bu dosyayı okuyacak. Yazma yalnızca uygulamadan yapılabilir.
        _ = store.save(rules)
    }

    private func persistMessages() {
        guard let data = try? JSONEncoder().encode(messages) else { return }
        defaults.set(data, forKey: Keys.messages)
        defaults.set(isShowcase, forKey: Keys.showcase)
    }

    private static func loadMessages(_ defaults: UserDefaults) -> [StoredMessage]? {
        guard let data = defaults.data(forKey: Keys.messages),
              let decoded = try? JSONDecoder().decode([StoredMessage].self, from: data),
              !decoded.isEmpty else { return nil }
        return decoded
    }

    // MARK: - İlk açılış örnekleri
    //
    // Boş bir gelen kutusu, ürünün ne yaptığını anlatamaz. İlk açılışta
    // gerçek kalıpları gösteren örneklerle başlıyoruz; kullanıcı bunları
    // tek dokunuşla temizleyebilir.

    private static func demoMessages(rules: UserRules) -> [StoredMessage] {
        let classifier = Classifier(rules: rules)
        let samples: [(String, String, TimeInterval)] = [
            ("GARANTI", "Sayin musterimiz, 15/08/2026 14:32 tarihinde **4358 nolu kartinizdan MIGROS'ta 1.249,90 TL harcama yapilmistir. Bilgi: 444 0 333 B012", -3_600),
            ("YURTICIKARGO", "4728301992 takip numarali gonderiniz Kadikoy subemize ulasmistir. Kurye dagitima cikacaktir. B031", -8_000),
            ("+90 850 304 11 22", "ICRA DAIRESI: Adiniza kayitli 18.450 TL borc icin haciz islemi baslatilmistir. 24 saat icinde odeme yapilmazsa mal varliginiza el konulacaktir. Detay: t.co/xkz2f", -12_000),
            ("TURKCELL", "Paketinizin 2,4 GB internet hakki kaldi. Kalan kullanim ve tarife detaylari icin 5555'i arayabilirsiniz. B001", -20_000),
            ("MEDIAMARKT", "Sepette %40'a varan indirim basladi! Firsatlari kacirma, hemen incele. Kampanya son gun. B045", -26_000),
            ("BONUS-VIP", "MERHABA! 1000TL DENEME BONUSU HESABINIZDA TANIMLANDI. CEVRIMSIZ CEKIM. GUNCEL GIRIS ADRESI: bet-xy7.co", -34_000),
            ("E-DEVLET", "e-Devlet giris dogrulama kodunuz: 725104", -96_000),
        ]
        return samples.map { sender, body, offset in
            StoredMessage(sender: sender, body: body,
                          receivedAt: .now.addingTimeInterval(offset),
                          verdict: classifier.classify(sender: sender, body: body))
        }
    }
}
