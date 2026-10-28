import SwiftUI
import SwiftData
import Charts
import VitalynCore

struct ActivityView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai
    @Environment(\.openURL) private var openURL
    @Query(sort: \WorkoutEntry.date, order: .reverse) private var manual: [WorkoutEntry]
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]

    @State private var month = false
    @State private var showLog = false
    @State private var suggestions: [WorkoutSuggestion] = []
    @State private var loadingSuggestions = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                stepsCard
                statsGrid
                workoutsSection
                suggestionsSection
            }
            .padding(20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle("النشاط · Activity")
        .refreshable { await health.refresh() }
        .sheet(isPresented: $showLog) { LogWorkoutSheet() }
    }

    private var stepsGoal: Int { profiles.first?.stepsGoal ?? 8000 }

    private var stepsCard: some View {
        let data = month ? health.monthSteps : health.weekSteps
        let average = data.isEmpty ? 0 : Int(data.map(\.value).reduce(0, +) / Double(data.count))
        return Card {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        BiCaption(ar: month ? "متوسط الشهر" : "متوسط الأسبوع", en: month ? "Monthly average" : "Weekly average")
                        Text("\(VFormat.number(average)) خطوة · steps").font(VFont.readex(22, weight: 600))
                    }
                    Spacer()
                    HStack(spacing: 4) {
                        BiChip(ar: "أسبوع", en: "Week", selected: !month, dark: true) { month = false }
                        BiChip(ar: "شهر", en: "Month", selected: month, dark: true) { month = true }
                    }
                }
                if data.isEmpty {
                    BiText(ar: "اربط Apple Health عشان تشوف خطواتك.", en: "Connect Apple Health to see your steps.", size: 13, weight: 400, color: VColor.inkSecondary)
                } else {
                    Chart {
                        ForEach(data) { day in
                            BarMark(x: .value("Day", day.date, unit: .day), y: .value("Steps", day.value))
                                .foregroundStyle(day.value >= Double(stepsGoal) ? VColor.primary : Color(hex: 0x9AC9B2))
                                .cornerRadius(6)
                        }
                        RuleMark(y: .value("Goal", stepsGoal))
                            .foregroundStyle(VColor.primary.opacity(0.6))
                            .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                    }
                    .frame(height: 170)
                    .environment(\.layoutDirection, .leftToRight)
                }
            }
        }
    }

    private var statsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            stat("سعرات نشاط", "Active kcal", "\(health.activeKcalToday)", VColor.energy)
            stat("مسافة", "Distance", "\(VFormat.decimal(health.distanceKmToday)) km", VColor.ink)
            stat("نبض الراحة", "Resting HR", health.restingHeartRate.map { "\($0) bpm" } ?? "—", VColor.ink)
            stat("النوم", "Sleep", health.sleepHoursLastNight.map { "\(VFormat.decimal($0)) h" } ?? "—", VColor.ink)
        }
    }

    private func stat(_ ar: String, _ en: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            BiCaption(ar: ar, en: en)
            Text(value).font(VFont.readex(20, weight: 600)).foregroundStyle(color)
                .environment(\.layoutDirection, .leftToRight)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20))
    }

    private var workoutsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                SectionTitle(ar: "التمارين", en: "Workouts")
                Button { showLog = true } label: {
                    BiLabel(ar: "سجّل تمرين", en: "Log workout", systemImage: "plus", size: 13)
                }
                .buttonStyle(SecondaryButtonStyle())
            }
            let rows = workoutRows
            if rows.isEmpty {
                BiText(ar: "مفيش تمارين في آخر أسبوعين. سجّل واحد أو قول للمدرب عملت إيه.", en: "No workouts in the last two weeks. Log one or tell the coach.", size: 13, weight: 400, color: VColor.inkSecondary)
            }
            VStack(spacing: 0) {
                ForEach(rows) { row in
                    HStack(spacing: 12) {
                        Image(systemName: row.kind.symbol)
                            .foregroundStyle(VColor.primary)
                            .frame(width: 40, height: 40)
                            .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 12))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(row.nameAr) · \(row.nameEn)").font(VFont.readex(14, weight: 500))
                            Text("\(row.date.formatted(.dateTime.weekday().day().month())) · \(Int(row.minutes)) min · \(Int(row.kcal)) kcal")
                                .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                        }
                        Spacer()
                        Text(row.source).font(VFont.readex(10)).foregroundStyle(VColor.inkSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(VColor.ground, in: Capsule())
                    }
                    .padding(12)
                    Divider()
                }
            }
            .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20))
        }
    }

    private struct WorkoutRow: Identifiable {
        let id: String
        let nameAr: String
        let nameEn: String
        let kind: WorkoutKind
        let date: Date
        let minutes: Double
        let kcal: Double
        let source: String
    }

    /// Health workouts plus manual ones that were not saved to Health (to avoid duplicates).
    private var workoutRows: [WorkoutRow] {
        let fromHealth = health.workouts.map {
            WorkoutRow(id: $0.id.uuidString, nameAr: $0.kind.ar, nameEn: $0.kind.en, kind: $0.kind, date: $0.start, minutes: $0.minutes, kcal: $0.kcal, source: "Health")
        }
        let cutoff = Calendar.current.date(byAdding: .day, value: -14, to: Date()) ?? Date()
        let fromApp = manual.filter { !$0.savedToHealth && $0.date >= cutoff }.map {
            WorkoutRow(id: "\($0.persistentModelID)", nameAr: $0.nameAr, nameEn: $0.nameEn, kind: $0.kind, date: $0.date, minutes: $0.minutes, kcal: $0.kcal, source: "يدوي · Manual")
        }
        return (fromHealth + fromApp).sorted { $0.date > $1.date }
    }

    private var suggestionsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                SectionTitle(ar: "مقترح ليك", en: "Recommended for you")
                Button(action: loadSuggestions) {
                    BiLabel(ar: suggestions.isEmpty ? "اقترح" : "اقترح غيرهم", en: suggestions.isEmpty ? "Suggest" : "New ideas", systemImage: "sparkles", size: 13)
                }
                .buttonStyle(SecondaryButtonStyle())
                .disabled(loadingSuggestions)
            }
            if loadingSuggestions { WorkingView(ar: "بختار تمارين مناسبة ليك…", en: "Picking workouts that fit you…") }
            if let error { ErrorBanner(message: error) }
            ForEach(suggestions, id: \.youtubeQuery) { item in
                Button {
                    openURL(VideoSuggestion(titleAr: item.titleAr, titleEn: item.titleEn, query: item.youtubeQuery).youtubeURL)
                } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "play.rectangle.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(Color(hex: 0xE02B2B))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.titleAr).font(VFont.readex(14, weight: 600))
                            Text(item.titleEn).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                            Text("\(Int(item.minutes)) min · \(item.intensity)").font(VFont.readex(11)).foregroundStyle(VColor.muted)
                            Text(item.whyAr).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                            Text(item.whyEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20))
                }
                .buttonStyle(.plain)
                .foregroundStyle(VColor.ink)
            }
        }
    }

    private func loadSuggestions() {
        guard let userContext = AppData.userContext(context, health: health) else { return }
        loadingSuggestions = true
        error = nil
        Task {
            do {
                suggestions = try await ai.run(WorkoutSuggestions.self, AITasks.workoutSuggestions(context: userContext, request: "")).items
            } catch {
                self.error = error.localizedDescription
            }
            loadingSuggestions = false
        }
    }
}

/// Manual workout entry; calories from MET values and current weight.
struct LogWorkoutSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health

    @State private var kind: WorkoutKind = .walking
    @State private var minutes: Double = 30
    @State private var date = Date()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Card {
                        VStack(alignment: .leading, spacing: 12) {
                            BiCaption(ar: "نوع التمرين", en: "Workout type")
                            FlowLayout(spacing: 8) {
                                ForEach(WorkoutKind.allCases, id: \.self) { item in
                                    BiChip(ar: item.ar, en: item.en, selected: kind == item) { kind = item }
                                }
                            }
                            Stepper(value: $minutes, in: 5...300, step: 5) {
                                BiText(ar: "المدة: \(Int(minutes)) دقيقة", en: "Duration: \(Int(minutes)) min", size: 14, weight: 500)
                            }
                            DatePicker(selection: $date) { BiCaption(ar: "بدأ", en: "Started") }
                        }
                    }
                    Card(background: VColor.night) {
                        HStack {
                            BiText(ar: "سعرات تقديرية", en: "Estimated kcal", size: 14, color: VColor.nightText, enColor: VColor.nightMuted)
                            Spacer()
                            Text("\(Int(estimatedKcal))").font(VFont.readex(28, weight: 700)).foregroundStyle(VColor.mint)
                        }
                    }
                    Button(action: save) { BiLabel(ar: "سجّل", en: "Log workout", systemImage: "checkmark") }
                        .buttonStyle(PrimaryButtonStyle())
                }
                .padding(20)
            }
            .background(VColor.ground)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() } } }
        }
    }

    private var weightKg: Double {
        health.weights.last?.value ?? AppData.profile(context)?.startWeightKg ?? 80
    }

    /// kcal ≈ (MET − 1) × kg × hours (active energy, excluding resting).
    private var estimatedKcal: Double {
        let met: Double = switch kind {
        case .walking: 3.8
        case .running: 9.0
        case .cycling: 7.0
        case .swimming: 7.0
        case .football: 7.0
        case .strength: 5.0
        case .hiit: 8.0
        case .yoga: 2.8
        case .other: 5.0
        }
        return ((met - 1) * weightKg * minutes / 60).rounded()
    }

    private func save() {
        let entry = WorkoutEntry()
        entry.kind = kind
        entry.nameAr = kind.ar
        entry.nameEn = kind.en
        entry.minutes = minutes
        entry.kcal = estimatedKcal
        entry.date = date
        context.insert(entry)
        try? context.save()
        Task {
            entry.savedToHealth = await health.saveWorkout(kind: kind, start: date, minutes: minutes, kcal: estimatedKcal)
            try? context.save()
            AppData.publishSnapshot(context, health: health)
        }
        dismiss()
    }
}
