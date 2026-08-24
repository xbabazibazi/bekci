import XCTest
@testable import BekciCore

/// `shared/golden.json` üç uygulamanın (Swift, Kotlin, Python referansı)
/// ortak sözleşmesidir. Bu test kırılıyorsa ya bir kuralı bilerek değiştirdin
/// — o zaman fixture'ı yeniden üret ve Kotlin tarafını da güncelle — ya da
/// platformlar arasında istemeden bir ayrışma oluştu.
final class GoldenTests: XCTestCase {

    struct Case: Decodable {
        let id: String
        let sender: String
        let body: String
        let expectAction: String
        let expectSubAction: String
        let expectRisk: Int
        let expectSenderKind: String
        let expectTopReason: String?
    }

    struct Golden: Decodable {
        let version: Int
        let cases: [Case]
    }

    private func loadGolden() throws -> Golden {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "golden", withExtension: "json"),
            "golden.json test bundle'ında bulunamadı"
        )
        return try JSONDecoder().decode(Golden.self, from: Data(contentsOf: url))
    }

    func testGoldenCorpus() throws {
        let golden = try loadGolden()
        XCTAssertGreaterThan(golden.cases.count, 20, "Altın küme fazla küçülmüş")

        let classifier = Classifier()
        var failures: [String] = []

        for c in golden.cases {
            let v = classifier.classify(sender: c.sender, body: c.body)

            if v.action.rawValue != c.expectAction {
                failures.append("\(c.id): aksiyon \(v.action.rawValue) ≠ \(c.expectAction)")
            }
            if v.subAction.rawValue != c.expectSubAction {
                failures.append("\(c.id): alt-aksiyon \(v.subAction.rawValue) ≠ \(c.expectSubAction)")
            }
            if v.risk != c.expectRisk {
                failures.append("\(c.id): risk \(v.risk) ≠ \(c.expectRisk)")
            }
            if v.senderKind.rawValue != c.expectSenderKind {
                failures.append("\(c.id): gönderen tipi \(v.senderKind.rawValue) ≠ \(c.expectSenderKind)")
            }
            if let expected = c.expectTopReason, v.reasons.first?.code != expected {
                failures.append("\(c.id): baş gerekçe \(v.reasons.first?.code ?? "—") ≠ \(expected)")
            }
        }

        XCTAssertTrue(failures.isEmpty, "\n" + failures.joined(separator: "\n"))
    }

    /// Beyan etmediğimiz bir alt-aksiyon asla dışarı sızmamalı.
    /// Sızarsa iOS onu sessizce atar ve mesaj kategorisiz kalır.
    func testOnlyDeclaredSubActionsEscape() throws {
        let golden = try loadGolden()
        let classifier = Classifier()
        for c in golden.cases {
            let v = classifier.classify(sender: c.sender, body: c.body)
            if v.subAction != .none {
                XCTAssertTrue(declaredSubActions.contains(v.subAction),
                              "\(c.id): beyan edilmemiş alt-aksiyon \(v.subAction.rawValue)")
            }
        }
    }

    /// Ürünün en pahalı hatası: doğrulama kodunu çöpe atmak.
    func testOTPNeverJunk() {
        let classifier = Classifier(rules: UserRules(sensitivity: .strict))
        let otpMessages = [
            ("AKBANK", "Dogrulama kodunuz: 481925. Bu kodu kimseyle paylasmayin."),
            ("E-DEVLET", "e-Devlet giris dogrulama kodunuz: 725104"),
            ("+90 850 111 22 33", "Islem kodunuz 90210. ACIL! HEMEN GIRIN, SON UYARI!"),
            ("WHATSAPP", "Guvenlik kodunuz: 442113"),
            // Çok satırlı: kod ayrı satırda. Desenler `[ \t]` kullandığı sürece
            // bu mesaj OTP korumasına GİRMİYOR ve (bankasi + aciliyet + ASCII
            // kaçınma + büyük harf = risk 68) dengeli/sıkı modda çöpe gidiyordu.
            ("+90 212 909 77 12",
             "SAYIN MUSTERIMIZ, XYZ BANKASI ISLEM GUVENLIK KODUNUZ\n729104\n"
             + "BU KODU KIMSEYLE PAYLASMAYIN. ISLEMI HEMEN ONAYLAYIN, SON KEZ."),
        ]
        for (sender, body) in otpMessages {
            let v = classifier.classify(sender: sender, body: body)
            XCTAssertNotEqual(v.action, .junk, "OTP çöpe atıldı: \(body)")
        }
    }

    /// "Tanıdık taklidi" (evlat dolandırıcılığı) her duyarlılıkta çöp
    /// sayılmalı — bu bir eşik hesabı değil, kesin karardır (bkz.
    /// Classifier.swift `impostorContact`). Test, biri yarın bu bloğu risk
    /// bölümüne taşıyıp eşiğe bağımlı hâle getirirse hemen kırılsın diye var.
    func testImpostorContactAlwaysJunk() {
        let messages = [
            ("+90 538 902 11 47",
             "Anne telefonum kirildi bu numaradan yaziyorum, acil param lazim, "
             + "gonderir misin? IBAN'imi atiyorum."),
            ("+90 505 220 11 34",
             "Numaram degisti, eski hattima ulasamiyorum. Borc param var, "
             + "hemen havale yapar misin?"),
        ]
        for sensitivity in Sensitivity.allCases {
            let classifier = Classifier(rules: UserRules(sensitivity: sensitivity))
            for (sender, body) in messages {
                let v = classifier.classify(sender: sender, body: body)
                XCTAssertEqual(v.action, .junk, "\(sensitivity): tanıdık taklidi kaçtı: \(body)")
                XCTAssertEqual(v.reasons.first?.code, "impostorContact")
            }
        }
    }

    /// Kullanıcı kuralları modeli her koşulda ezmeli.
    func testUserRulesOverrideModel() {
        let spam = "1000TL DENEME BONUSU! GUNCEL GIRIS: bet-xy7.co"

        let allowing = Classifier(rules: UserRules(allowSenders: ["bonusvip"]))
        XCTAssertEqual(allowing.classify(sender: "BONUS-VIP", body: spam).action, .allow)

        let blocking = Classifier(rules: UserRules(blockSenders: ["garanti"]))
        let legit = "Kartinizdan 100 TL harcama yapilmistir. B012"
        XCTAssertEqual(blocking.classify(sender: "GARANTI", body: legit).action, .junk)

        let keyword = Classifier(rules: UserRules(blockKeywords: ["kampanya"]))
        XCTAssertEqual(keyword.classify(sender: "MIGROS", body: "Yeni kampanya basladi!").action, .junk)
    }

    /// Duyarlılık arttıkça daha çok mesaj çöpe gitmeli; tersi olmamalı.
    func testSensitivityIsMonotonic() throws {
        let golden = try loadGolden()
        let counts = [Sensitivity.careful, .balanced, .strict].map { sensitivity in
            let c = Classifier(rules: UserRules(sensitivity: sensitivity))
            return golden.cases.filter { c.classify(sender: $0.sender, body: $0.body).action == .junk }.count
        }
        XCTAssertLessThanOrEqual(counts[0], counts[1])
        XCTAssertLessThanOrEqual(counts[1], counts[2])
    }

    func testSenderKindDetection() {
        let cases: [(String, SenderKind)] = [
            ("GARANTI", .alpha), ("BONUS-VIP", .alpha),
            ("5555", .shortcode), ("182", .shortcode),
            ("+90 532 118 44 09", .trMobile), ("05321184409", .trMobile),
            ("+90 850 304 11 22", .trNonGeo), ("08503041122", .trNonGeo),
            ("+90 212 909 77 12", .trGeo),
            ("+1 202 555 0134", .international), ("+44 7700 900123", .international),
            ("", .unknown),
        ]
        for (sender, expected) in cases {
            XCTAssertEqual(Classifier.senderKind(sender), expected, "gönderen: \(sender)")
        }
    }

    func testTurkishFoldingIsConsistent() {
        XCTAssertEqual(TurkishText.fold("İSTANBUL"), "istanbul")
        XCTAssertEqual(TurkishText.fold("Iğdır"), "igdir")
        XCTAssertEqual(TurkishText.fold("ÇEKİLİŞ"), "cekilis")
        XCTAssertEqual(TurkishText.fold("ŞÜPHELİ"), "supheli")
        // 'I' ve 'ı' aynı yere katlanır — sözlük eşleşmesi için kasıtlı.
        XCTAssertEqual(TurkishText.fold("ILIK"), TurkishText.fold("ılık"))
    }

    /// Sözcük sınırı: "bet" kelimesi "bethesda" içinde eşleşmemeli,
    /// ama "bahis" → "bahisler" eşleşmeli (önek araması).
    func testWordBoundaryMatching() {
        XCTAssertTrue(TurkishText.matches("bahisler basladi", ["bahis"]).isEmpty == false)
        XCTAssertTrue(TurkishText.matches("kabahat isledi", ["bahis"]).isEmpty)
        XCTAssertTrue(TurkishText.matches("bet sitesi", ["bet"]).isEmpty == false)
    }

    func testEmptyAndMalformedInput() {
        let c = Classifier()
        XCTAssertEqual(c.classify(sender: nil, body: nil).action, .none)
        XCTAssertEqual(c.classify(sender: "", body: "").action, .none)
        XCTAssertEqual(c.classify(sender: "?????", body: String(repeating: "a", count: 5000)).action, .none)
    }

    /// Uzantının bütçesi dar; 1000 mesaj gözle görülür bir sürede bitmeli.
    func testPerformanceIsExtensionSafe() throws {
        let golden = try loadGolden()
        let classifier = Classifier()
        measure {
            for _ in 0..<50 {
                for c in golden.cases {
                    _ = classifier.classify(sender: c.sender, body: c.body)
                }
            }
        }
    }
}
