import SwiftUI
import UIKit
import CoreText

/// Colors from the Vitalyn design canvas.
enum VColor {
    static let ground = Color(hex: 0xF3F5F1)
    static let surface = Color.white
    static let ink = Color(hex: 0x0F1F1A)
    static let inkSecondary = Color(hex: 0x4A5A54)
    static let muted = Color(hex: 0x5F6D67)
    static let line = Color(hex: 0xE2E7E2)
    static let track = Color(hex: 0xEEF1EC)

    static let primary = Color(hex: 0x0E7A55)
    static let primaryDark = Color(hex: 0x0A5C40)
    static let primarySoft = Color(hex: 0xDDF1E7)
    static let mint = Color(hex: 0x7FE0B0)
    static let energy = Color(hex: 0xB83F1C)
    static let water = Color(hex: 0x3E63E0)

    static let protein = Color(hex: 0x0E7A55)
    static let carbs = Color(hex: 0xF2A93B)
    static let fat = Color(hex: 0x5B7CFA)

    static let night = Color(hex: 0x0F1F1A)
    static let nightText = Color(hex: 0xEAF3EE)
    static let nightMuted = Color(hex: 0xB9CCC3)
    static let nightLine = Color(hex: 0x3A4B45)

    // Widget palette (bolder than the app).
    static let lime = Color(hex: 0xC6F432)
    static let amber = Color(hex: 0xFFB020)
    static let sky = Color(hex: 0x4DA3FF)
    static let coral = Color(hex: 0xFF5A36)
    static let electric = Color(hex: 0x2F5BFF)
}

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

/// Readex Pro is a variable font, so weights are set through its `wght` axis.
enum VFont {
    private static let weightAxis = 0x7767_6874 // "wght"

    static func readex(_ size: CGFloat, weight: CGFloat = 400, relativeTo style: UIFont.TextStyle = .body) -> Font {
        let descriptor = UIFontDescriptor(fontAttributes: [.family: "Readex Pro"])
            .addingAttributes([
                UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [weightAxis: weight]
            ])
        let base = UIFont(descriptor: descriptor, size: size)
        let scaled = UIFontMetrics(forTextStyle: style).scaledFont(for: base)
        return Font(scaled as CTFont)
    }
}

/// Every label in Vitalyn shows Arabic first and English under it.
struct BiText: View {
    let ar: String
    let en: String
    var size: CGFloat = 16
    var weight: CGFloat = 600
    var color: Color = VColor.ink
    var enColor: Color = VColor.muted
    var enSize: CGFloat? = nil
    var alignment: HorizontalAlignment = .leading

    var body: some View {
        VStack(alignment: alignment, spacing: 1) {
            Text(ar)
                .font(VFont.readex(size, weight: weight))
                .foregroundStyle(color)
            Text(en)
                .font(VFont.readex(enSize ?? max(9, (size * 0.75).rounded()), weight: 400))
                .foregroundStyle(enColor)
                .environment(\.locale, Locale(identifier: "en"))
        }
        .multilineTextAlignment(alignment == .center ? .center : .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Inline "عربي · English" label for small captions.
struct BiCaption: View {
    let ar: String
    let en: String
    var size: CGFloat = 12
    var color: Color = VColor.muted

    var body: some View {
        Text("\(ar) · \(en)")
            .font(VFont.readex(size))
            .foregroundStyle(color)
    }
}

struct Card<Content: View>: View {
    var background: Color = VColor.surface
    var padding: CGFloat = 18
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

/// Number formatting stays in Western digits (1,240) inside the Arabic layout.
enum VFormat {
    static func number(_ value: Int) -> String {
        value.formatted(.number.locale(Locale(identifier: "en_US")))
    }

    static func decimal(_ value: Double, digits: Int = 1) -> String {
        value.formatted(.number.precision(.fractionLength(digits)).locale(Locale(identifier: "en_US")))
    }
}
