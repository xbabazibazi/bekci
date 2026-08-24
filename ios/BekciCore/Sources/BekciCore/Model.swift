import Foundation

// MARK: - Karar tipleri
//
// Bu tipler Apple'ın ILMessageFilterAction / ILMessageFilterSubAction
// enum'larıyla birebir eşleşir. BekciCore'un IdentityLookup'a bağımlılığı
// yoktur — böylece hem uygulamada, hem uzantıda, hem de testte çalışır.
// Dönüşüm yalnızca BekciFilter hedefinde yapılır.

public enum FilterAction: String, Codable, Sendable {
    /// Karar veremedim. Sistem mesajı bildiği gibi göstersin.
    /// Emin olmadığımız her durumda dönülmesi gereken değer budur —
    /// `allow` "bu mesaj temiz" iddiasıdır, `none` "karışmıyorum" demektir.
    case none
    case allow
    case junk
    case promotion
    case transaction
}

public enum FilterSubAction: String, Codable, Sendable {
    case none
    case transactionalFinance
    case transactionalOrders
    case transactionalCarrier
    case promotionalOffers
    case promotionalCoupons
}

/// iOS aynı anda en fazla **5** alt-aksiyon beyanına izin veriyor.
/// Beyan etmediğimiz bir alt-aksiyonu döndürürsek sistem onu sessizce atar
/// ve yalnızca ana aksiyonu uygular. Listeyi tek yerde tutuyoruz ki
/// `MessageFilterExtension.handle(capabilitiesRequest:)` ile motorun
/// çıktısı asla ayrışmasın.
public let declaredSubActions: Set<FilterSubAction> = [
    .transactionalFinance, .transactionalOrders, .transactionalCarrier,
    .promotionalOffers, .promotionalCoupons,
]

public enum Sensitivity: String, Codable, Sendable, CaseIterable {
    case careful   // Temkinli — varsayılan. Emin olmadığında dokunmaz.
    case balanced
    case strict

    /// (çöp eşiği, gri bant alt sınırı)
    var thresholds: (junk: Int, gray: Int) {
        switch self {
        case .careful:  return (78, 55)
        case .balanced: return (62, 42)
        case .strict:   return (48, 32)
        }
    }

    public var title: String {
        switch self {
        case .careful:  return "Temkinli"
        case .balanced: return "Dengeli"
        case .strict:   return "Sıkı"
        }
    }

    public var subtitle: String {
        switch self {
        case .careful:  return "Emin olmadığında dokunmaz. Önerilen."
        case .balanced: return "Şüphelileri kampanya olarak işaretler."
        case .strict:   return "Daha çok yakalar, yanlış pozitif riski artar."
        }
    }
}

public enum SenderKind: String, Codable, Sendable {
    case alpha            // Alfanumerik başlık: GARANTI, BONUS-VIP
    case shortcode        // 3–6 haneli kısa numara
    case trMobile         // +90 5xx
    case trNonGeo         // 0850 / 444 / 800 / 900
    case trGeo            // Coğrafi sabit hat
    case international
    case unknown

    public var label: String {
        switch self {
        case .alpha:         return "Alfanumerik başlık"
        case .shortcode:     return "Kısa numara"
        case .trMobile:      return "Cep numarası"
        case .trNonGeo:      return "Hizmet numarası"
        case .trGeo:         return "Sabit hat"
        case .international: return "Yurt dışı numarası"
        case .unknown:       return "Bilinmiyor"
        }
    }
}

/// Kullanıcıya "neden" sorusunu cevaplayan tek bir kanıt.
/// Dolandırıcılık ekranındaki liste doğrudan bunlardan üretilir.
public struct Reason: Codable, Sendable, Hashable, Identifiable {
    public let code: String
    public let title: String
    public let detail: String
    public let weight: Int

    public var id: String { code }

    public init(_ code: String, _ title: String, _ detail: String, _ weight: Int) {
        self.code = code
        self.title = title
        self.detail = detail
        self.weight = weight
    }
}

public struct Verdict: Codable, Sendable, Hashable {
    public let action: FilterAction
    public let subAction: FilterSubAction
    /// 0–100. 78+ (temkinli modda) çöp sayılır.
    public let risk: Int
    public let reasons: [Reason]
    public let senderKind: SenderKind

    public init(action: FilterAction,
                subAction: FilterSubAction = .none,
                risk: Int,
                reasons: [Reason] = [],
                senderKind: SenderKind = .unknown) {
        self.action = action
        self.subAction = subAction
        self.risk = risk
        self.reasons = reasons
        self.senderKind = senderKind
    }

    public var isFraud: Bool { action == .junk && risk >= 70 }
}

/// Kullanıcının kendi kuralları. Model kararlarını **her zaman** ezer.
///
/// Uygulama bunu App Group konteynerine yazar, uzantı yalnızca okur.
/// Apple, filtre uzantısının paylaşılan konteynere **yazmasına** izin
/// vermiyor — bu yüzden akış tek yönlüdür: uygulama → uzantı.
public struct UserRules: Codable, Sendable, Equatable {
    public var allowSenders: Set<String>
    public var blockSenders: Set<String>
    public var blockKeywords: Set<String>
    public var sensitivity: Sensitivity

    public init(allowSenders: Set<String> = [],
                blockSenders: Set<String> = [],
                blockKeywords: Set<String> = [],
                sensitivity: Sensitivity = .careful) {
        self.allowSenders = allowSenders
        self.blockSenders = blockSenders
        self.blockKeywords = blockKeywords
        self.sensitivity = sensitivity
    }

    public static let `default` = UserRules()

    /// Gönderen anahtarlarını normalize eder — kural eşleşmesi bu forma dayanır.
    public static func key(_ sender: String) -> String {
        TurkishText.fold(sender)
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
    }
}
