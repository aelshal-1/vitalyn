import SwiftUI
import SwiftData
import VitalynCore

enum ShareCardStyle: String, CaseIterable {
    case kilos, chart, streak

    var ar: String {
        switch self {
        case .kilos: "الكيلوهات"
        case .chart: "المنحنى"
        case .streak: "الالتزام"
        }
    }

    var en: String {
        switch self {
        case .kilos: "Kilos"
        case .chart: "Chart"
        case .streak: "Streak"
        }
    }
}

struct AchievementsView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @Query private var meals: [MealEntry]

    @State private var style: ShareCardStyle = .kilos

    var body: some View {
        let data = cardData
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 8) {
                    ForEach(ShareCardStyle.allCases, id: \.self) { item in
                        BiChip(ar: item.ar, en: item.en, selected: style == item, dark: true) { style = item }
                    }
                }
                ShareCard(style: style, data: data)
                    .frame(width: 340, height: 340)
                    .frame(maxWidth: .infinity)

                if let image = renderCard(data) {
                    ShareLink(item: Image(uiImage: image), preview: SharePreview("Vitalyn", image: Image(uiImage: image))) {
                        BiLabel(ar: "شارك مع صحابك", en: "Share with friends", systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }

                SectionTitle(ar: "الشارات", en: "Badges")
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 14) {
                    ForEach(badges) { badge in
                        VStack(spacing: 6) {
                            Image(systemName: badge.symbol)
                                .font(.system(size: 24))
                                .foregroundStyle(badge.achieved ? VColor.primary : Color(hex: 0x9AA8A2))
                                .frame(width: 60, height: 60)
                                .background(badge.achieved ? VColor.primarySoft : Color(hex: 0xE6EBE6), in: Circle())
                            BiText(ar: badge.ar, en: badge.en, size: 11, weight: 500, color: badge.achieved ? VColor.ink : VColor.muted, enSize: 10, alignment: .center)
                        }
                        .accessibilityLabel("\(badge.ar) — \(badge.en)\(badge.achieved ? "" : " (لسه · not yet)")")
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle("الإنجازات · Achievements")
    }

    private var streak: Int { ProgressMath.streak(loggedDays: Set(meals.map { Calendar.current.startOfDay(for: $0.date) })) }

    private var cardData: ShareCardData {
        let profile = profiles.first
        let start = scans.first?.weightKg ?? health.weights.first?.value ?? profile?.startWeightKg ?? 0
        let current = AppData.currentWeight(profile: profile ?? UserProfile(), scans: scans, health: health)
        var fatChange: Double?
        var muscleChange: Double?
        if let first = scans.first, let last = scans.last, scans.count > 1 {
            if let a = first.bodyFatKg, let b = last.bodyFatKg { fatChange = b - a }
            if let a = first.skeletalMuscleKg, let b = last.skeletalMuscleKg { muscleChange = b - a }
        }
        var series = scans.map(\.weightKg)
        if series.count < 2 { series = health.weights.map(\.value) }
        return ShareCardData(start: start, current: current, fatChange: fatChange, muscleChange: muscleChange, streak: streak, series: series, since: scans.first?.date ?? profile?.createdAt ?? Date())
    }

    private var badges: [Badge] {
        let profile = profiles.first
        return Achievements.badges(for: AchievementInput(
            scanCount: scans.count,
            startWeightKg: scans.first?.weightKg ?? profile?.startWeightKg,
            currentWeightKg: AppData.currentWeight(profile: profile ?? UserProfile(), scans: scans, health: health),
            streakDays: streak,
            bestStepsDay: health.bestStepsDay,
            loggedMealCount: meals.count,
            goalWeightKg: profile?.goalWeightKg
        ))
    }

    @MainActor
    private func renderCard(_ data: ShareCardData) -> UIImage? {
        let renderer = ImageRenderer(content: ShareCard(style: style, data: data).frame(width: 340, height: 340).environment(\.layoutDirection, .rightToLeft))
        renderer.scale = 3
        return renderer.uiImage
    }
}

struct ShareCardData {
    let start: Double
    let current: Double
    let fatChange: Double?
    let muscleChange: Double?
    let streak: Int
    let series: [Double]
    let since: Date

    var lost: Double { start - current }
}

struct ShareCard: View {
    let style: ShareCardStyle
    let data: ShareCardData

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Vitalyn").font(VFont.readex(15, weight: 700))
                Spacer()
                Text(Date().formatted(.dateTime.month(.wide).year().locale(Locale(identifier: "ar"))) + " · " + Date().formatted(.dateTime.month(.abbreviated).year().locale(Locale(identifier: "en"))))
                    .font(VFont.readex(11)).foregroundStyle(VColor.nightMuted)
            }
            Spacer(minLength: 0)
            switch style {
            case .kilos:
                Text("نزلت من \(data.since.formatted(date: .abbreviated, time: .omitted)) · Lost since").font(VFont.readex(13)).foregroundStyle(VColor.nightMuted)
                Text("\(data.lost >= 0 ? "−" : "+")\(VFormat.decimal(abs(data.lost))) kg")
                    .font(VFont.readex(64, weight: 700))
                    .foregroundStyle(VColor.mint)
                    .environment(\.layoutDirection, .leftToRight)
                if let fat = data.fatChange {
                    Text("دهون \(VFormat.decimal(fat)) · عضلات \(data.muscleChange.map { ($0 >= 0 ? "+" : "") + VFormat.decimal($0) } ?? "—")")
                        .font(VFont.readex(13)).foregroundStyle(VColor.nightText)
                    Text("Fat \(VFormat.decimal(fat)) kg · Muscle \(data.muscleChange.map { ($0 >= 0 ? "+" : "") + VFormat.decimal($0) } ?? "—") kg")
                        .font(VFont.readex(12)).foregroundStyle(VColor.nightMuted)
                }
            case .chart:
                Text("الوزن · Weight \(VFormat.decimal(data.start)) → \(VFormat.decimal(data.current))").font(VFont.readex(13)).foregroundStyle(VColor.nightMuted)
                Sparkline(values: data.series.isEmpty ? [data.start, data.current] : data.series)
                    .stroke(VColor.mint, style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
                    .frame(height: 150)
                    .environment(\.layoutDirection, .leftToRight)
            case .streak:
                Text("ملتزم بالأكل · On-plan streak").font(VFont.readex(13)).foregroundStyle(VColor.nightMuted)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text("\(data.streak)").font(VFont.readex(72, weight: 700)).foregroundStyle(Color(hex: 0xF7C46C))
                    Text("يوم · days").font(VFont.readex(18)).foregroundStyle(VColor.nightText)
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 5), count: 7), spacing: 5) {
                    ForEach(0..<min(28, max(7, data.streak)), id: \.self) { index in
                        RoundedRectangle(cornerRadius: 3).fill(index < data.streak ? Color(hex: 0xF7C46C) : Color(hex: 0x2A3C36)).frame(height: 12)
                    }
                }
            }
            Spacer(minLength: 0)
            Text("اتعمل بـ Vitalyn · Made with Vitalyn").font(VFont.readex(10)).foregroundStyle(Color(hex: 0x8FA79C))
        }
        .foregroundStyle(VColor.nightText)
        .padding(22)
        .background(VColor.night, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
}

struct Sparkline: Shape {
    let values: [Double]

    func path(in rect: CGRect) -> Path {
        var path = Path()
        guard values.count > 1, let low = values.min(), let high = values.max() else { return path }
        let span = max(0.1, high - low)
        for (index, value) in values.enumerated() {
            let x = rect.minX + rect.width * CGFloat(index) / CGFloat(values.count - 1)
            let y = rect.minY + rect.height * CGFloat((high - value) / span)
            if index == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        return path
    }
}
