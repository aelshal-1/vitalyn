import SwiftUI

/// A single progress ring.
struct ProgressRing: View {
    var progress: Double
    var color: Color
    var track: Color = VColor.track
    var lineWidth: CGFloat = 14

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0.001, min(1, progress)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .padding(lineWidth / 2)
    }
}

/// Three nested rings for protein, carbs and fat.
struct MacroRings: View {
    var protein: Double
    var carbs: Double
    var fat: Double
    var lineWidth: CGFloat = 12
    var colors: (Color, Color, Color) = (VColor.lime, VColor.amber, VColor.sky)

    var body: some View {
        ZStack {
            ring(protein, colors.0, inset: 0)
            ring(carbs, colors.1, inset: lineWidth + 2)
            ring(fat, colors.2, inset: (lineWidth + 2) * 2)
        }
        .accessibilityElement()
        .accessibilityLabel("بروتين \(Int(protein * 100))٪، كارب \(Int(carbs * 100))٪، دهون \(Int(fat * 100))٪")
    }

    private func ring(_ value: Double, _ color: Color, inset: CGFloat) -> some View {
        ProgressRing(progress: value, color: color, track: color.opacity(0.22), lineWidth: lineWidth)
            .padding(inset)
    }
}

/// Labelled horizontal macro bar.
struct MacroBar: View {
    let ar: String
    let en: String
    let value: Int
    let goal: Int
    let color: Color

    private var progress: Double {
        goal > 0 ? min(1, Double(value) / Double(goal)) : 0
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(ar).font(VFont.readex(13, weight: 500)).foregroundStyle(VColor.ink)
                Text(en).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                Spacer()
                Text("\(value) / \(goal) g")
                    .font(VFont.readex(13))
                    .foregroundStyle(VColor.inkSecondary)
                    .environment(\.layoutDirection, .leftToRight)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(VColor.track)
                    Capsule().fill(color).frame(width: proxy.size.width * progress)
                }
            }
            .frame(height: 8)
        }
        .accessibilityElement(children: .combine)
    }
}
