import Foundation

/// Bekçi'nin sınıflandırma motoru.
///
/// Tamamen deterministik, tamamen cihaz içi, sıfır ağ erişimi. Filtre
/// uzantısının bellek bütçesi çok dar olduğu için burada model yüklenmez —
/// kural motoru saf metin işlemedir ve tipik bir mesajda mikrosaniyeler sürer.
///
/// Davranışı `shared/golden.json` ile kilitlenmiştir; Kotlin uygulaması ve
/// Python referansı aynı testi çalıştırır. Buradaki bir ağırlığı değiştirirsen
/// üç tarafı birlikte güncellemen gerekir, aksi halde testler kırılır.
public struct Classifier: Sendable {

    public let rules: UserRules

    public init(rules: UserRules = .default) {
        self.rules = rules
    }

    // MARK: - Genel giriş

    public func classify(sender: String?,
                         body: String?,
                         receiverCountry: String? = "TR") -> Verdict {

        let raw = body ?? ""
        let folded = TurkishText.fold(raw)
        let kind = Self.senderKind(sender)
        let senderKey = sender.map(UserRules.key) ?? ""

        var reasons: [Reason] = []
        func add(_ code: String, _ title: String, _ detail: String, _ weight: Int) {
            reasons.append(Reason(code, title, detail, weight))
        }

        // ── 1. Kullanıcı kuralları her şeyi ezer ─────────────────────────
        let group: Lex.Group? = (kind == .alpha) ? Lex.trustedHeaders[senderKey] : nil

        if !senderKey.isEmpty, rules.allowSenders.contains(senderKey) {
            let (_, sub) = Self.category(folded, group: Lex.trustedHeaders[senderKey])
            return Verdict(action: .allow,
                           subAction: Self.declared(sub),
                           risk: 0,
                           reasons: [Reason("userAllow", "Sizin güvenli listenizde",
                                            "Bu göndereni her zaman güven olarak işaretlediniz.", -100)],
                           senderKind: kind)
        }

        if !senderKey.isEmpty, rules.blockSenders.contains(senderKey) {
            return Verdict(action: .junk, risk: 100,
                           reasons: [Reason("userBlock", "Sizin engel listenizde",
                                            "Bu göndereni engellemiştiniz.", 100)],
                           senderKind: kind)
        }

        for keyword in rules.blockKeywords.sorted() where !keyword.isEmpty {
            if folded.contains(keyword) {
                return Verdict(action: .junk, risk: 100,
                               reasons: [Reason("userKeyword", "Engellediğiniz kelime",
                                                "Mesajda “\(keyword)” geçiyor.", 100)],
                               senderKind: kind)
            }
        }

        let urls = Self.hosts(in: raw)
        let hasIPURL = Self.ipURL.firstMatch(raw)
        let hasURL = !urls.isEmpty || hasIPURL
        let trusted = group != nil
        let hasBCode = Self.bCode.firstMatch(raw)

        // ── 2. OTP koruması ──────────────────────────────────────────────
        // Yanlış pozitifin en pahalı olduğu yer burasıdır: banka doğrulama
        // kodunu çöpe atarsak kullanıcı uygulamayı siler. Bağlantı taşımayan
        // ve kod istemeyen bir doğrulama mesajı hiçbir koşulda çöp olamaz.
        if Self.otp.firstMatch(folded), !hasURL, !Self.codeHarvest.firstMatch(folded) {
            add("otp", "Doğrulama kodu",
                "Bağlantı içermeyen tek kullanımlık kod mesajı.", -60)
            let financeHit = !TurkishText.matches(folded, Lex.finance).isEmpty
            let sub: FilterSubAction = (financeHit || group == .bank) ? .transactionalFinance : .none
            return Verdict(action: .transaction, subAction: Self.declared(sub),
                           risk: 0, reasons: reasons, senderKind: kind)
        }

        // Kesin karar: numarası "değişen" bir yakın gibi davranıp acil para/
        // havale isteyen mesaj — codeHarvest ile aynı ruhta, duyarlılık
        // eşiklerini beklemeden. İki bağımsız ifade grubu birlikte arandığı
        // için düşük yanlış-pozitif, zarar geri alınamaz olduğu için doğrudan uyarı.
        if !TurkishText.matches(folded, Lex.contactChange).isEmpty,
           !TurkishText.matches(folded, Lex.moneyRequest).isEmpty {
            return Verdict(action: .junk, risk: 92,
                           reasons: [Reason("impostorContact", "Tanıdık taklidi",
                                            "Numarası değişen bir yakınınız gibi davranıp acilen para istiyor. "
                                            + "Göndermeden önce mutlaka o kişiyi bildiğiniz eski numarasından arayarak doğrulayın.",
                                            92)],
                           senderKind: kind)
        }

        // ── 3. Risk sinyalleri ───────────────────────────────────────────
        var risk = 0

        let shortHits = urls.filter { Lex.shorteners.contains($0) }.sorted()
        if let first = shortHits.first {
            risk += 30
            add("shortener", "Kısaltılmış bağlantı",
                "\(first) adresi gerçek hedefi gizliyor. Kurumlar kısaltıcı kullanmaz.", 30)
        }

        if hasIPURL {
            risk += 35
            add("ipUrl", "Sayısal adres",
                "Bağlantı alan adı yerine doğrudan IP adresine gidiyor.", 35)
        }

        if urls.contains(where: { $0.hasPrefix("xn--") || $0.contains(".xn--") }) {
            risk += 35
            add("punycode", "Sahte alfabe",
                "Bağlantıda tanıdık harflere benzeyen farklı karakterler kullanılmış.", 35)
        }

        let risky = urls.filter { Lex.riskyTLDs.contains(Self.tld($0)) }.sorted()
        if let first = risky.first {
            risk += 18
            add("riskyTld", "Şüpheli alan adı uzantısı",
                "“.\(Self.tld(first))” uzantısı dolandırıcılıkta sık kullanılıyor.", 18)
        }

        if hasURL, [.trMobile, .trGeo, .international, .unknown].contains(kind) {
            risk += 22
            add("urlFromNumber", "Sıradan numaradan bağlantı",
                "Kurumsal gönderim yerine kişisel görünümlü bir numaradan bağlantı geldi.", 22)
        }

        if kind == .international, hasURL {
            risk += 20
            add("intlUrl", "Yurt dışı kaynaklı",
                "Yurt dışı numarasından bağlantı içeren mesaj.", 20)
        }

        if kind == .trNonGeo, hasURL {
            risk += 14
            add("nonGeoUrl", "0850 hattından bağlantı",
                "Hizmet numarasından gelen bağlantılı toplu gönderim.", 14)
        }

        // Bonuslar (birden fazla ifade) Reason.weight'e DE yazılır: aksi hâlde
        // gerekçelerin toplamı risk skorunu tutmuyor ve topReasons() aciliyet/
        // bahis sinyalini hak ettiğinden düşük sıralıyordu.
        let gambling = TurkishText.matches(folded, Lex.gambling)
        if let first = gambling.first {
            let weight = 38 + (Set(gambling).count >= 2 ? 12 : 0)
            risk += weight
            add("gambling", "Bahis/kumar içeriği",
                "Mesajda “\(first)” gibi yasa dışı bahis ifadeleri var.", weight)
        }

        let prize = TurkishText.matches(folded, Lex.prize)
        if let first = prize.first {
            risk += 26
            add("prize", "Ödül vaadi",
                "“\(first)” gibi kazanç vaatleri dolandırıcılığın en yaygın kancasıdır.", 26)
        }

        let institutions = TurkishText.matches(folded, Lex.institutions)
        if let first = institutions.first, !trusted {
            risk += 30
            add("impersonation", "Resmî kurum taklidi",
                "“\(first)” adına yazılmış ama gönderen kayıtlı bir kurumsal başlık değil.", 30)
        }

        // Vishing: "bu numarayı arayın" bağlantı GEREKTİRMEZ. `!trusted`
        // koşulu, doğrulanmış bir kurumdan gelen "444 0 333'ü arayın" gibi
        // meşru yönlendirmeleri etkilemez.
        if !TurkishText.matches(folded, Lex.callback).isEmpty, !trusted {
            risk += 35
            add("callbackPhishing", "Sizi aramanızı istiyor",
                "Doğrulama için bir telefon numarasını aramanızı istiyor — bankalar bunu SMS ile istemez, "
                + "şüpheniz varsa kartınızın arkasındaki resmî numarayı siz arayın.", 35)
        }

        // Kurumsal bir işlem anlatıyor (kargo, banka, vergi) ama gönderen
        // doğrulanmış bir başlık değil ve mesaj bağlantı taşıyor.
        if hasURL, !trusted, kind != .shortcode {
            let businessHits = TurkishText.matches(folded, Lex.orders)
                + TurkishText.matches(folded, Lex.finance)
                + TurkishText.matches(folded, Lex.publicServices)
            if businessHits.count >= 2 {
                risk += 22
                add("orgClaimFromStranger", "Kurumsal içerik, tanınmayan gönderen",
                    "Mesaj bir kurum işlemi anlatıyor ama kayıtlı kurumsal başlıktan gelmiyor.", 22)
            }
        }

        let urgency = TurkishText.matches(folded, Lex.urgency)
        if let first = urgency.first {
            let weight = 14 + (Set(urgency).count >= 2 ? 10 : 0)
            risk += weight
            add("urgency", "Aciliyet baskısı",
                "“\(first)” gibi ifadeler düşünmenizi engellemek için kullanılıyor.", weight)
        }

        if !TurkishText.matches(folded, Lex.redirectChannel).isEmpty,
           !TurkishText.matches(folded, Lex.easyMoney).isEmpty {
            // 58: link taşımayan "Telegram'dan yazın" varyantı bile (noUrl -10
            // sonrası net 48) sıkı modda junk_at'e (48) tam otursun diye — ama
            // careful/balanced'te TEK BAŞINA hâlâ junk_at'e ulaşmaz. "telegram"
            // tek kelime olarak meşru işletme mesajında da geçebileceği için
            // varsayılan modda kesin karar vermiyoruz.
            risk += 58
            add("thirdPartyRedirect", "Denetimsiz kanala yönlendiriyor",
                "Kazanç vaadiyle Telegram/WhatsApp gibi bir kanala yönlendiriyor — "
                + "meşru bir iş teklifi sizi SMS filtresinin göremeyeceği bir yere taşımaya çalışmaz.", 58)
        }

        // ASCII kaçınma: Türkçe bir metin ama içinde hiç Türkçe karakter yok.
        // Uzunluk KOD NOKTASI olarak ölçülür: `raw.count` grafem kümesi sayar,
        // Kotlin'in `length`'i UTF-16 birimi sayar. Emoji içeren bir mesajda
        // (spam'de çok yaygın) bu iki sayı ikiye katlanacak kadar ayrışır ve
        // 40 eşiği iki platformda farklı yerde tetiklenirdi.
        if raw.unicodeScalars.count > 40,
           !raw.contains(where: { TurkishText.turkishCharacters.contains($0) }) {
            if TurkishText.matches(folded, Lex.turkishMarkers).count >= 2 {
                risk += 16
                add("asciiEvasion", "Türkçe karakter yok",
                    "Filtrelerden kaçmak için mesaj kasıtlı olarak Türkçe harfsiz yazılmış.", 16)
            }
        }

        // Harf sayımı da KOD NOKTASI üzerinden: `raw.filter { $0.isLetter }
        // .count` grafem kümesi sayar, Kotlin'in `length`'i UTF-16 birimi.
        // BMP dışı harfler (matematiksel kalın "𝐊𝐀𝐙𝐀𝐍𝐃𝐈𝐍𝐈𝐙" — spam'de
        // filtre kaçınmak için kullanılıyor) iki tarafta farklı sayılıyordu.
        let letters = raw.unicodeScalars.filter { Character($0).isLetter }
        if letters.count > 40 {
            let upper = letters.filter { Character($0).isUppercase }.count
            if Double(upper) / Double(letters.count) > 0.75 {
                risk += 8
                add("shouting", "Tamamı büyük harf",
                    "Dikkat çekmek için mesajın tamamı büyük yazılmış.", 8)
            }
        }

        // Kesin karar: meşru hiçbir gönderen doğrulama kodunuzu geri istemez.
        if Self.codeHarvest.firstMatch(folded) {
            return Verdict(action: .junk, risk: 95,
                           reasons: [Reason("codeHarvest", "Doğrulama kodunuzu istiyor",
                                            "Hiçbir banka, kurum veya şirket size gönderilen kodu geri istemez. " +
                                            "Bu mesaj hesabınızı ele geçirmeye çalışıyor.", 95)],
                           senderKind: kind)
        }

        if kind == .alpha, !hasBCode, hasURL, !trusted {
            risk += 12
            add("noBCode", "B kodu yok",
                "Yasal toplu gönderimlerde bulunması gereken operatör kodu eksik.", 12)
        }

        // ── 4. Güven azaltıcılar ─────────────────────────────────────────
        if trusted, hasBCode {
            risk -= 45
            add("verifiedHeader", "Doğrulanmış gönderen",
                "Tanınan kurumsal başlık ve geçerli operatör B kodu.", -45)
        } else if trusted {
            // -18, -45 değil: Türkiye'de alfanumerik başlık TAKLİT EDİLEBİLİR
            // ve BTK yasal toplu gönderimde B kodunu zorunlu kılıyor. B kodu
            // olmayan bir "MIGROS" başlığı zayıf kanıttır; 78'lik çöp eşiğinin
            // karşısına -30 koymak, bit.ly + ödül vaadi + aciliyet taşıyan bir
            // mesajı varsayılan modda "işlem mesajı" saydırıyordu.
            // Meşru gönderene zarar vermez: URL/taklit sinyallerinin çoğu
            // zaten `!trusted` koşuluna bağlı.
            risk -= 18
            add("knownHeader", "Tanınan gönderen",
                "Bu başlık bilinen kurumlar listesinde ama operatör B kodu yok.", -18)
        }

        if !hasURL {
            risk -= 10
            add("noUrl", "Bağlantı yok", "Mesajda tıklanabilir bir adres bulunmuyor.", -10)
        }

        if kind == .shortcode, !hasURL {
            risk -= 12
            add("shortCode", "Kısa numara",
                "Operatör onaylı kısa numaradan gönderilmiş.", -12)
        }

        risk = max(0, min(100, risk))

        // ── 5. Karar ─────────────────────────────────────────────────────
        let (junkAt, grayAt) = rules.sensitivity.thresholds
        let (catAction, catSub) = Self.category(folded, group: group)

        if risk >= junkAt {
            return Verdict(action: .junk, risk: risk,
                           reasons: Self.topReasons(reasons), senderKind: kind)
        }

        if risk >= grayAt {
            // Gri bant: çöp demeye yetecek kadar emin değiliz.
            // Pazarlama sinyali varsa kampanyaya at, yoksa hiç dokunma.
            if catAction == .promotion {
                return Verdict(action: .promotion, subAction: Self.declared(catSub),
                               risk: risk, reasons: Self.topReasons(reasons), senderKind: kind)
            }
            return Verdict(action: .none, risk: risk,
                           reasons: Self.topReasons(reasons), senderKind: kind)
        }

        if catAction != .none {
            return Verdict(action: catAction, subAction: Self.declared(catSub),
                           risk: risk, reasons: Self.topReasons(reasons), senderKind: kind)
        }

        // Kategoriye oturtamadık ve riskli de değil → sistemi bozma.
        return Verdict(action: .none, risk: risk,
                       reasons: Self.topReasons(reasons), senderKind: kind)
    }

    // MARK: - Yardımcılar

    /// Beyan etmediğimiz alt-aksiyonu döndürmeyiz; iOS zaten atardı.
    static func declared(_ sub: FilterSubAction) -> FilterSubAction {
        declaredSubActions.contains(sub) ? sub : .none
    }

    /// Kullanıcıya gösterilecek gerekçeler: önce ağırlıklı riskler.
    /// Sıralama **kararlı** olmalı — aynı ağırlıktaki iki gerekçenin sırası
    /// platformlar arasında değişirse golden test kırılır.
    static func topReasons(_ reasons: [Reason]) -> [Reason] {
        let positives = reasons.enumerated().filter { $0.element.weight > 0 }
        if !positives.isEmpty {
            return positives
                .sorted { ($0.element.weight, -$0.offset) > ($1.element.weight, -$1.offset) }
                .prefix(5).map(\.element)
        }
        return reasons.enumerated()
            .sorted { ($0.element.weight, $0.offset) < ($1.element.weight, $1.offset) }
            .prefix(3).map(\.element)
    }

    static func category(_ folded: String, group: Lex.Group?) -> (FilterAction, FilterSubAction) {
        // Sıra sabittir; eşitlikte ilk sıradaki kazanır.
        let scored: [(FilterAction, FilterSubAction, Int)] = [
            (.transaction, .transactionalFinance, TurkishText.matches(folded, Lex.finance).count),
            (.transaction, .transactionalOrders,  TurkishText.matches(folded, Lex.orders).count),
            (.transaction, .transactionalCarrier, TurkishText.matches(folded, Lex.carrier).count),
            (.transaction, .none, TurkishText.matches(folded, Lex.health).count
                                + TurkishText.matches(folded, Lex.publicServices).count),
            (.promotion, .promotionalCoupons, TurkishText.matches(folded, Lex.promoCoupons).count * 2),
            (.promotion, .promotionalOffers,  TurkishText.matches(folded, Lex.promoOffers).count),
        ]
        guard let best = scored.max(by: { $0.2 < $1.2 }) else { return (.none, .none) }

        if best.2 >= 2 { return (best.0, best.1) }

        // İçerik kararsız. Gönderen doğrulanmış bir kurumsal başlıksa,
        // başlığın kendisi tek başına yeterli bir kategori kanıtıdır.
        if let group {
            if best.2 == 1, group == .retail { return (best.0, best.1) }
            if let prior = Lex.groupPrior[group] { return prior }
            if best.2 == 1 { return (best.0, best.1) }
        }
        return (.none, .none)
    }

    // MARK: - Gönderen tipi

    /// Yalnızca ASCII rakam. `Character.isNumber` ICU tarafında "²" / "½" gibi
    /// Unicode sayılarını da doğru sayar; Kotlin'in `isDigit()`'i ve referansın
    /// `[0-9]`'u saymaz. Şebekeden gelen numara her zaman ASCII'dir.
    static func isDigit(_ c: Character) -> Bool { c.isASCII && c.isNumber }

    public static func senderKind(_ sender: String?) -> SenderKind {
        guard let sender, !sender.isEmpty else { return .unknown }
        let s = sender.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "")
        if s.contains(where: { $0.isLetter }) { return .alpha }

        let digits = s.filter { isDigit($0) }
        guard !digits.isEmpty else { return .unknown }

        if s.hasPrefix("+"), !digits.hasPrefix("90") { return .international }
        if !s.hasPrefix("+"), !digits.hasPrefix("0"), (3...6).contains(digits.count) {
            return .shortcode
        }

        var local = digits.hasPrefix("90") ? String(digits.dropFirst(2)) : digits
        while local.hasPrefix("0") { local.removeFirst() }
        guard let head = local.first else { return .unknown }

        if head == "5" { return .trMobile }
        for prefix in ["850", "444", "800", "900"] where local.hasPrefix(prefix) {
            return .trNonGeo
        }
        if ["2", "3", "4"].contains(String(head)) { return .trGeo }
        return .unknown
    }

    // MARK: - Bağlantı çıkarımı

    static func tld(_ host: String) -> String {
        host.split(separator: ".").last.map(String.init) ?? ""
    }

    /// Metindeki alan adlarını çıkarır. `https://` şart değildir —
    /// Türkçe spam'de çıplak alan adı ("bet-xy7.co") çok yaygındır.
    static func hosts(in text: String) -> [String] {
        var out: [String] = []
        Self.url.forEachMatch(text) { groups in
            guard let host = groups.first??.lowercased() else { return }
            // "18.450" gibi sayısal ifadeleri alan adı sanma.
            if host.allSatisfy({ isDigit($0) || $0 == "." }) { return }
            let t = tld(host)
            if t.count < 2 || t.allSatisfy({ isDigit($0) }) { return }
            out.append(host)
        }
        return out
    }

    // MARK: - Derlenmiş desenler
    //
    // NSRegularExpression pahalıdır; uzantı her mesajda yeniden yaratmasın
    // diye statik olarak bir kez derlenir.

    // Desenlerde \s \d \w \b KULLANILMAZ. Bunlar lehçeye göre değişir:
    // ICU (NSRegularExpression) \s'e NBSP'yi, \d'ye Unicode rakamları,
    // \b'ye Türkçe harfleri dahil eder; Java/Kotlin regex etmez. Aynı mesaj
    // iki platformda farklı sınıflanırdı. Her yerde açık sınıf kullanıyoruz.
    //
    // Boşluk sınıfı `[ \t\r\n]`dir — SATIR SONU DAHİL. Bir dönem `[ \t]`
    // yazıldığı için "Dogrulama kodunuz:\n729104" biçimindeki (operatörlerin
    // sıkça ürettiği) çok satırlı gerçek OTP mesajları OTP korumasına
    // girmiyordu; dengeli/sıkı duyarlılıkta böyle bir banka kodu çöpe
    // gidiyordu. Nöbetçi vaka: golden.json > otp-satir-sonu.
    // NBSP ailesi fold() içinde normal boşluğa çevrildiği için bu küme kapalıdır.

    static let url = Rx(
        #"(?:https?://|www\.|(?<![\p{L}\p{Nd}_@.]))((?:[a-z0-9\u00a1-\uffff](?:[a-z0-9\u00a1-\uffff-]{0,61}[a-z0-9\u00a1-\uffff])?\.)+[a-z\u00a1-\uffff]{2,24})(?:/[^ \t\r\n]*)?"#,
        options: [.caseInsensitive])

    static let ipURL = Rx(#"https?://[0-9]{1,3}(?:\.[0-9]{1,3}){3}"#, options: [.caseInsensitive])

    /// Operatör B kodu — BTK'nın alfanumerik başlıklı SMS'ler için zorunlu
    /// kıldığı 4 haneli izlenebilirlik kodu (B001, B012 …). Ham metinde,
    /// büyük/küçük harf duyarlı aranır. Komşuluk kontrolü Unicode harfleri
    /// de kapsar; yoksa "ğB045" iki platformda farklı davranırdı.
    static let bCode = Rx(#"(?<![\p{L}\p{Nd}_])B[0-9]{3}(?![\p{L}\p{Nd}_])"#)

    static let otp = Rx(
        #"(dogrulama|onay|guvenlik|islem|giris|aktivasyon|tek kullanimlik)[ \t\r\n]*(kodunuz|kodu|sifreniz|sifre|parolasi)?[ \t\r\n]*[:\-]?[ \t\r\n]*[0-9]{4,8}"#)

    static let codeHarvest = Rx(
        #"(kodu|sifreyi|parolayi|dogrulama kodunu)[ \t\r\n]+(bize|bana|asagidaki|whatsapp|paylas|gonder|ilet)"#)
}

// MARK: - Regex sarmalayıcı

/// `NSRegularExpression` üzerine ince bir katman. Derleme hatası olursa
/// çökmek yerine sessizce hiç eşleşmeyen bir desene döner — bir filtre
/// uzantısının çökmesi, mesajın hiç filtrelenmemesinden çok daha kötüdür.
struct Rx: @unchecked Sendable {
    private let regex: NSRegularExpression?

    init(_ pattern: String, options: NSRegularExpression.Options = []) {
        self.regex = try? NSRegularExpression(pattern: pattern, options: options)
    }

    func firstMatch(_ s: String) -> Bool {
        guard let regex else { return false }
        return regex.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil
    }

    func forEachMatch(_ s: String, _ body: ([String?]) -> Void) {
        guard let regex else { return }
        let ns = s as NSString
        regex.enumerateMatches(in: s, range: NSRange(location: 0, length: ns.length)) { m, _, _ in
            guard let m else { return }
            var groups: [String?] = []
            for i in 1..<m.numberOfRanges {
                let r = m.range(at: i)
                groups.append(r.location == NSNotFound ? nil : ns.substring(with: r))
            }
            body(groups)
        }
    }
}
