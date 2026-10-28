import AppIntents
import SwiftUI
import WidgetKit
import VitalynCore

@main
struct VitalynWidgetBundle: WidgetBundle {
    var body: some Widget {
        FuelWidget()
        StreakWidget()
        StepsWidget()
        WaterWidget()
        JourneyWidget()
    }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: TodaySnapshot
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: .now, snapshot: SampleData.today)
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: .now, snapshot: SnapshotStore.load() ?? SampleData.today))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let entry = SnapshotEntry(date: .now, snapshot: SnapshotStore.load() ?? SampleData.today)
        let refresh = Calendar.current.date(byAdding: .minute, value: 30, to: .now) ?? .now
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }
}

// MARK: - Fuel: calories left + macro rings

struct FuelWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "fuel", provider: SnapshotProvider()) { entry in
            FuelWidgetView(snapshot: entry.snapshot)
                .containerBackground(Color(hex: 0x0A0F0D), for: .widget)
        }
        .configurationDisplayName("سعرات وماكروز · Fuel")
        .description("السعرات الباقية والماكروز · Calories left and macros")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular])
    }
}

struct FuelWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let snapshot: TodaySnapshot

    var body: some View {
        switch family {
        case .accessoryCircular:
            Gauge(value: snapshot.calorieProgress) {
                Text("kcal")
            } currentValueLabel: {
                Text("\(snapshot.caloriesLeft)")
            }
            .gaugeStyle(.accessoryCircularCapacity)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 1) {
                Text("\(snapshot.caloriesLeft) سعرة باقية").font(.headline)
                Text("\(snapshot.caloriesLeft) kcal left").font(.caption)
                Text("P \(snapshot.proteinGrams)/\(snapshot.targets.proteinGrams)g").font(.caption2)
            }
        case .systemSmall:
            ZStack {
                rings
                caloriesLabel(size: 22)
            }
            .environment(\.layoutDirection, .rightToLeft)
        default:
            HStack(spacing: 14) {
                rings.frame(width: 128, height: 128)
                VStack(alignment: .leading, spacing: 6) {
                    caloriesLabel(size: 40)
                    HStack(spacing: 10) {
                        macroLegend(value: snapshot.proteinGrams, letter: "P", ar: "بروتين", color: VColor.lime)
                        macroLegend(value: snapshot.carbsGrams, letter: "C", ar: "كارب", color: VColor.amber)
                        macroLegend(value: snapshot.fatGrams, letter: "F", ar: "دهون", color: VColor.sky)
                    }
                }
                Spacer(minLength: 0)
            }
            .environment(\.layoutDirection, .rightToLeft)
        }
    }

    private var rings: some View {
        MacroRings(protein: snapshot.proteinProgress, carbs: snapshot.carbsProgress, fat: snapshot.fatProgress, lineWidth: 11)
    }

    private func caloriesLabel(size: CGFloat) -> some View {
        VStack(spacing: 2) {
            Text("\(snapshot.caloriesLeft)")
                .font(.system(size: size, weight: .bold, design: .rounded))
                .foregroundStyle(.white)
            Text("سعرة باقية · kcal left")
                .font(.system(size: 10))
                .foregroundStyle(Color(hex: 0xC9D4CF))
        }
    }

    private func macroLegend(value: Int, letter: String, ar: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("\(letter) \(value)").font(.system(size: 12, weight: .semibold, design: .rounded))
            Text(ar).font(.system(size: 10))
        }
        .foregroundStyle(color)
    }
}

// MARK: - Streak

struct StreakWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "streak", provider: SnapshotProvider()) { entry in
            StreakWidgetView(snapshot: entry.snapshot)
                .containerBackground(VColor.coral, for: .widget)
        }
        .configurationDisplayName("التزام · Streak")
        .description("عدد الأيام اللي التزمت فيها · Days on plan in a row")
        .supportedFamilies([.systemSmall])
    }
}

struct StreakWidgetView: View {
    let snapshot: TodaySnapshot

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Image(systemName: "flame.fill")
                .font(.system(size: 90))
                .foregroundStyle(Color(hex: 0xFF7A5C))
                .offset(x: -18, y: 22)
            VStack(alignment: .leading) {
                Text("التزام").font(.system(size: 12, weight: .semibold))
                Text("Streak").font(.system(size: 11))
                Spacer()
                Text("\(snapshot.streakDays)").font(.system(size: 52, weight: .bold, design: .rounded))
                Text("يوم ورا بعض · days").font(.system(size: 11))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .foregroundStyle(Color(hex: 0x2A0C04))
        .environment(\.layoutDirection, .rightToLeft)
    }
}

// MARK: - Steps

struct StepsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "steps", provider: SnapshotProvider()) { entry in
            StepsWidgetView(snapshot: entry.snapshot)
                .containerBackground(VColor.electric, for: .widget)
        }
        .configurationDisplayName("خطوات · Steps")
        .description("خطواتك النهارده من Apple Health · Today’s steps")
        .supportedFamilies([.systemSmall])
    }
}

struct StepsWidgetView: View {
    let snapshot: TodaySnapshot

    var body: some View {
        VStack(alignment: .leading) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("خطوات").font(.system(size: 12, weight: .semibold))
                    Text("Steps").font(.system(size: 11))
                }
                Spacer()
                ProgressRing(progress: snapshot.stepsProgress, color: .white, track: Color(hex: 0x5A7DFF), lineWidth: 6)
                    .frame(width: 44, height: 44)
            }
            Spacer()
            Text(snapshot.steps.formatted(.number.locale(Locale(identifier: "en_US"))))
                .font(.system(size: 34, weight: .bold, design: .rounded))
            Text("الهدف · Goal \(snapshot.stepsGoal.formatted(.number.locale(Locale(identifier: "en_US"))))")
                .font(.system(size: 11))
        }
        .foregroundStyle(.white)
        .environment(\.layoutDirection, .rightToLeft)
    }
}

// MARK: - Water (interactive)

struct WaterWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "water", provider: SnapshotProvider()) { entry in
            WaterWidgetView(snapshot: entry.snapshot)
                .containerBackground(Color(hex: 0xE6F0FF), for: .widget)
        }
        .configurationDisplayName("مية · Water")
        .description("دوس + عشان تضيف كوباية · Tap + to add a glass")
        .supportedFamilies([.systemSmall])
    }
}

struct WaterWidgetView: View {
    let snapshot: TodaySnapshot

    var body: some View {
        let glasses = Calendar.current.isDateInToday(snapshot.date) ? snapshot.waterGlasses : 0
        let fraction = snapshot.waterGoal > 0 ? min(1, Double(glasses) / Double(snapshot.waterGoal)) : 0
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                Rectangle()
                    .fill(Color(hex: 0xBFD6FF))
                    .frame(height: proxy.size.height * fraction)
                    .padding(-20)
                VStack(alignment: .leading) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("مية").font(.system(size: 12, weight: .semibold))
                        Text("Water").font(.system(size: 11))
                    }
                    Spacer()
                    HStack(alignment: .firstTextBaseline, spacing: 2) {
                        Text("\(glasses)").font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("/\(snapshot.waterGoal)").font(.system(size: 16, weight: .medium, design: .rounded))
                    }
                    .environment(\.layoutDirection, .leftToRight)
                    Button(intent: AddWaterIntent()) {
                        Text("+ كوباية · glass")
                            .font(.system(size: 12, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: 34)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(VColor.electric)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .foregroundStyle(Color(hex: 0x0D2A66))
        .environment(\.layoutDirection, .rightToLeft)
    }
}

// MARK: - Journey

struct JourneyWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "journey", provider: SnapshotProvider()) { entry in
            JourneyWidgetView(snapshot: entry.snapshot)
                .containerBackground(Color(hex: 0x0D3B2B), for: .widget)
        }
        .configurationDisplayName("رحلتي · My journey")
        .description("الوزن والتغيير من البداية · Weight and change since you started")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct JourneyWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let snapshot: TodaySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 0) {
                Text("رحلتي").font(.system(size: 12, weight: .semibold))
                Text("My journey").font(.system(size: 11))
            }
            .foregroundStyle(Color(hex: 0x9FF0C6))
            Spacer()
            Text((snapshot.weightChangeKg <= 0 ? "−" : "+") + String(format: "%.1f", abs(snapshot.weightChangeKg)) + " kg")
                .font(.system(size: family == .systemSmall ? 34 : 44, weight: .bold, design: .rounded))
                .foregroundStyle(VColor.lime)
                .environment(\.layoutDirection, .leftToRight)
            Text("دلوقتي · now " + String(format: "%.1f", snapshot.weightKg) + " kg")
                .font(.system(size: 11))
                .foregroundStyle(Color(hex: 0xCFE5DA))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .environment(\.layoutDirection, .rightToLeft)
    }
}
