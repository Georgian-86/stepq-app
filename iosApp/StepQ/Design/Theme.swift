import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// StepQ brand colours, matching the Android app (ui/theme/Color.kt) and the Figma variables.
enum Palette {
    static let leaf = Color(hex: 0x22C55E)
    static let deep = Color(hex: 0x0A7F3F)
    static let mint = Color(hex: 0xEEF6F0)
    static let line = Color(hex: 0xDCE7DF)
    static let leafSoft = Color(hex: 0xDDF7E6)
    static let page = Color(hex: 0xF6F9F6)

    static let mango = Color(hex: 0xFFB627)
    static let tomato = Color(hex: 0xF0533A)
    static let ink = Color(hex: 0x0E2A1A)
    static let muted = Color(hex: 0x55695D)
    static let mangoSoft = Color(hex: 0xFFF3D6)
    static let tomatoSoft = Color(hex: 0xFDE6E1)
    static let tomatoText = Color(hex: 0xB8321C)

    // StepQ Pro, "Kadam Gold".
    static let gold1 = Color(hex: 0xFFE29A)
    static let gold2 = Color(hex: 0xF5B83D)
    static let gold3 = Color(hex: 0xC98A1B)
    static let goldText = Color(hex: 0x8A5A00)
    static let night1 = Color(hex: 0x17452C)
    static let night2 = Color(hex: 0x0E2A1A)
    static let onNightMuted = Color(hex: 0xCFE3D6)
    static let champagne = Color(hex: 0xFFF8E6)

    static let gold = LinearGradient(colors: [gold1, gold2, gold3], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let night = LinearGradient(colors: [night1, night2], startPoint: .top, endPoint: .bottom)
}

/// Fredoka for headings and numbers, Nunito for text, both bundled (SIL Open Font License).
/// Every style scales with Dynamic Type.
enum Typo {
    static func display(_ size: CGFloat = 34) -> Font { .custom("FredokaLight-SemiBold", size: size, relativeTo: .largeTitle) }
    static func title(_ size: CGFloat = 24) -> Font { .custom("FredokaLight-SemiBold", size: size, relativeTo: .title) }
    static func heading(_ size: CGFloat = 18) -> Font { .custom("FredokaLight-Medium", size: size, relativeTo: .headline) }
    static let body = Font.custom("NunitoExtraLight-Regular", size: 16, relativeTo: .body)
    static let bodyStrong = Font.custom("NunitoExtraLight-Bold", size: 16, relativeTo: .body)
    static let small = Font.custom("NunitoExtraLight-Regular", size: 13, relativeTo: .footnote)
    static let smallStrong = Font.custom("NunitoExtraLight-ExtraBold", size: 13, relativeTo: .footnote)
    static let button = Font.custom("NunitoExtraLight-ExtraBold", size: 17, relativeTo: .body)
}

struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = Palette.deep
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Typo.button)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(color.opacity(enabled ? 1 : 0.4), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Typo.button)
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(Capsule().strokeBorder(Palette.line, lineWidth: 1.5).background(Capsule().fill(.white)))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct GoldButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Typo.button)
            .foregroundStyle(Palette.night2)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(Palette.gold.opacity(enabled ? 1 : 0.5), in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

extension View {
    /// A white rounded card on the page background.
    func card(padding: CGFloat = 16) -> some View {
        self.padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Palette.line))
    }

    func pageBackground() -> some View {
        self.background(Palette.page.ignoresSafeArea())
    }
}
