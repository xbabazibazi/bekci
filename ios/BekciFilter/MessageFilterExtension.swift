import IdentityLookup
import BekciCore
import os

/// Bekçi'nin SMS filtre uzantısı.
///
/// Bilmesi gereken üç kısıt:
///
/// 1. **Sadece rehberde olmayan gönderenler.** iOS, kişilerdeki bir numaradan
///    gelen mesajı ya da herhangi bir iMessage'ı bu uzantıya hiç göstermez.
/// 2. **Ağ yok.** `deferQueryRequestToNetwork` teorik olarak var ama
///    Associated Domains + `apple-app-site-association` zinciri gerektiriyor.
///    Bekçi bilinçli olarak kullanmıyor: mesaj cihazdan çıkmıyor.
/// 3. **Paylaşılan konteynere yazamaz.** Kural akışı tek yönlüdür:
///    uygulama yazar, uzantı okur. Bu yüzden uzantı içinde öğrenme yoktur.
///
/// Uzantı çökerse iOS mesajı filtrelemeden geçirir. Bu yüzden burada
/// `try!`, zorla açma veya dizi indeksleme yoktur — her yol bir cevapla biter.
final class MessageFilterExtension: ILMessageFilterExtension {

    private static let log = Logger(subsystem: "tr.bekci.filter", category: "classify")

    /// Kurallar her sorguda diskten okunmaz; uzantı süreci yaşadığı sürece
    /// önbellekte tutulur ve dosya değişince tazelenir.
    private lazy var classifier: Classifier = Classifier(rules: RuleStore.shared.load())
}

// MARK: - Yetenek beyanı

extension MessageFilterExtension: ILMessageFilterCapabilitiesQueryHandling {

    /// iOS en fazla **5** alt-aksiyon beyanına izin veriyor.
    /// Liste `BekciCore.declaredSubActions` ile aynı olmak zorunda; ayrışırsa
    /// motorun ürettiği alt-aksiyon sistem tarafından sessizce atılır.
    func handle(_ capabilitiesQueryRequest: ILMessageFilterCapabilitiesQueryRequest,
                context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterCapabilitiesQueryResponse) -> Void) {

        let response = ILMessageFilterCapabilitiesQueryResponse()
        response.transactionalSubActions = [
            .transactionalFinance,   // banka, ödeme, fatura
            .transactionalOrders,    // kargo, sipariş
            .transactionalCarrier,   // operatör bildirimleri
        ]
        response.promotionalSubActions = [
            .promotionalOffers,      // indirim, kampanya
            .promotionalCoupons,     // kupon, hediye çeki
        ]
        completion(response)
    }
}

// MARK: - Sorgu işleme

extension MessageFilterExtension: ILMessageFilterQueryHandling {

    func handle(_ queryRequest: ILMessageFilterQueryRequest,
                context: ILMessageFilterExtensionContext,
                completion: @escaping (ILMessageFilterQueryResponse) -> Void) {

        let response = ILMessageFilterQueryResponse()

        let verdict = classifier.classify(
            sender: queryRequest.sender,
            body: queryRequest.messageBody,
            // iOS 11+: alıcı numaranın ülke kodu. Roaming'de yurt içi/yurt dışı
            // ayrımı için tek güvenilir sinyal.
            receiverCountry: queryRequest.receiverISOCountryCode
        )

        response.action = verdict.action.ilAction
        response.subAction = verdict.subAction.ilSubAction

        // Mesaj içeriği ASLA loglanmaz. Yalnızca karar ve risk skoru.
        Self.log.debug("verdict=\(verdict.action.rawValue, privacy: .public) risk=\(verdict.risk, privacy: .public)")

        // Uygulamanın "Bugün" ekranındaki sayaçlar için içeriksiz özet.
        // Uzantı paylaşılan konteynere YAZAMAZ; bu yüzden sayaç uzantıda
        // değil, uygulama açıldığında kendi tarafında güncellenir.
        completion(response)
    }
}

// MARK: - BekciCore ↔ IdentityLookup köprüsü
//
// BekciCore'un IdentityLookup'a bağımlılığı yok: böylece macOS'ta test
// edilebiliyor ve uygulama hedefi de aynı motoru kullanabiliyor.

extension FilterAction {
    var ilAction: ILMessageFilterAction {
        switch self {
        case .none:        return .none
        case .allow:       return .allow
        case .junk:        return .junk
        case .promotion:   return .promotion
        case .transaction: return .transaction
        }
    }
}

extension FilterSubAction {
    var ilSubAction: ILMessageFilterSubAction {
        switch self {
        case .none:                 return .none
        case .transactionalFinance: return .transactionalFinance
        case .transactionalOrders:  return .transactionalOrders
        case .transactionalCarrier: return .transactionalCarrier
        case .promotionalOffers:    return .promotionalOffers
        case .promotionalCoupons:   return .promotionalCoupons
        }
    }
}
