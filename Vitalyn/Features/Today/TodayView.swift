import SwiftUI
import SwiftData
import VitalynCore

struct TodayView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]
    @Query private var todayMeals: [MealEntry]
    @Query private var todayWorkouts: [WorkoutEntry]

    @State private var input: MealInput?
    @State private var tip: DailyTip?
    @State private var loadingTip = false
    @State private var tipError: String?
    @State private var water = WaterStore.glasses()
    var openProfile: () -> Void = {}
    var openPlan: () -> Void = {}

    init(openProfile: @escaping () -> Void = {}, openPlan: @escaping () -> Void = {}) {
        self.openProfile = openProfile
        self.openPlan = openPlan
        let start = Calendar.current.startOfDay(for: Date())
        _todayMeals = Query(filter: #Predicate<MealEntry> { $0.date >= start }, sort: \.date)
        _todayWorkouts = Query(filter: #Predicate<WorkoutEntry> { $0.date >= start })
    }

    private var profile: UserProfile? { profiles.first }

    private var targets: MacroTargets {
        guard let profile else { return SampleData.targets }
        return AppData.targets(profile: profile, scans: scans, health: health)
    }

    private var eaten: MacroTargets {
        Portions.total(todayMeals.flatMap { $0.sortedItems.map(\.analyzed) })
    }

    private var workoutMinutes: Int {
        let manual = todayWorkouts.filter { !$0.savedToHealth }.reduce(0) { $0 + $1.minutes }
        let fromHealth = health.workouts.filter { Calendar.current.isDateInToday($0.start) }.reduce(0) { $0 + $1.minutes }
        return Int(manual + fromHealth)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                caloriesCard
                activityRow
                coachCard
                TodayPlanCard(openPlan: openPlan)
                mealsSection
                Button { input = .photo } label: {
                    BiLabel(ar: "صوّر وجبة", en: "Snap a meal", systemImage: "camera", size: 16)
                }
                .buttonStyle(PrimaryButtonStyle())
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .refreshable {
            await health.refresh()
            water = WaterStore.glasses()
            AppData.publishSnapshot(context, health: health)
        }
        .sheet(item: $input) { input in MealCaptureView(input: input) }
        .onAppear {
            water = WaterStore.glasses()
            loadCachedTip()
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                BiCaption(ar: Date().formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "ar"))),
                          en: Date().formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).locale(Locale(identifier: "en"))))
                BiText(ar: greeting.ar + (profile?.name.isEmpty == false ? "، \(profile!.name)" : ""), en: greeting.en, size: 26, enColor: VColor.inkSecondary, enSize: 15)
            }
            Spacer()
            Button(action: openProfile) {
                Image(systemName: "person")
                    .font(.system(size: 18))
                    .foregroundStyle(VColor.ink)
                    .frame(width: 44, height: 44)
                    .background(VColor.surface, in: Circle())
                    .overlay(Circle().stroke(VColor.line))
            }
            .accessibilityLabel("الملف الشخصي — Profile")
        }
    }

    private var greeting: (ar: String, en: String) {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<12: ("صباح الخير", "Good morning")
        case 12..<18: ("مساء الخير", "Good afternoon")
        default: ("مساء النور", "Good evening")
        }
    }

    // MARK: Calories

    private var caloriesCard: some View {
        let left = max(0, targets.calories - eaten.calories)
        let progress = targets.calories > 0 ? Double(eaten.calories) / Double(targets.calories) : 0
        let over = eaten.calories > targets.calories
        return Card {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    BiText(ar: "سعرات النهارده", en: "Calories today")
                    Spacer()
                    BiCaption(ar: over ? "عديت الهدف" : "ماشي صح", en: over ? "Over target" : "On track", color: over ? VColor.energy : VColor.primaryDark)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(over ? Color(hex: 0xFDE4DC) : VColor.primarySoft, in: Capsule())
                }
                HStack(spacing: 20) {
                    ZStack {
                        ProgressRing(progress: progress, color: over ? VColor.energy : VColor.primary)
                        VStack(spacing: 2) {
                            Text(VFormat.number(left))
                                .font(VFont.readex(38, weight: 600, relativeTo: .largeTitle))
                                .foregroundStyle(VColor.ink)
                            BiText(ar: "سعرة باقية", en: "kcal left", size: 12, weight: 400, color: VColor.inkSecondary, enSize: 11, alignment: .center)
                        }
                    }
                    .frame(width: 160, height: 160)

                    VStack(alignment: .leading, spacing: 12) {
                        stat(ar: "الهدف", en: "Target", value: targets.calories, color: VColor.ink)
                        stat(ar: "أكلت", en: "Eaten", value: eaten.calories, color: VColor.ink)
                        stat(ar: "نشاط", en: "Active", value: health.activeKcalToday, color: VColor.energy)
                    }
                }
                Divider().overlay(VColor.track)
                VStack(spacing: 14) {
                    MacroBar(ar: "بروتين", en: "Protein", value: eaten.proteinGrams, goal: targets.proteinGrams, color: VColor.protein)
                    MacroBar(ar: "كارب", en: "Carbs", value: eaten.carbsGrams, goal: targets.carbsGrams, color: VColor.carbs)
                    MacroBar(ar: "دهون", en: "Fat", value: eaten.fatGrams, goal: targets.fatGrams, color: VColor.fat)
                }
            }
        }
    }

    private func stat(ar: String, en: String, value: Int, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            BiCaption(ar: ar, en: en)
            Text(VFormat.number(value)).font(VFont.readex(18, weight: 600)).foregroundStyle(color)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Activity

    private var activityRow: some View {
        HStack(spacing: 10) {
            NavigationLink { ActivityView() } label: {
                tile(symbol: "figure.walk", tint: VColor.primary, value: VFormat.number(health.stepsToday), ar: "خطوة", en: "Steps")
            }
            .buttonStyle(.plain)
            NavigationLink { ActivityView() } label: {
                tile(symbol: "flame", tint: VColor.energy, value: "\(workoutMinutes)", ar: "دقيقة تمرين", en: "Workout min")
            }
            .buttonStyle(.plain)
            Button {
                water = WaterStore.add(1)
                Task { await health.saveWater(glasses: 1, date: Date()) }
                AppData.publishSnapshot(context, health: health)
            } label: {
                tile(symbol: "drop.fill", tint: VColor.water, value: "\(water)/\(profile?.waterGoal ?? 8)", ar: "مية · دوس +1", en: "Water · tap +1")
            }
            .buttonStyle(.plain)
        }
    }

    private func tile(symbol: String, tint: Color, value: String, ar: String, en: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: symbol).font(.system(size: 20)).foregroundStyle(tint)
            Text(value).font(VFont.readex(20, weight: 600)).foregroundStyle(VColor.ink)
                .environment(\.layoutDirection, .leftToRight)
            BiText(ar: ar, en: en, size: 12, weight: 400, color: VColor.inkSecondary, enSize: 11)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    // MARK: Coach tip

    private var fallbackTip: DailyTip {
        let protein = max(0, targets.proteinGrams - eaten.proteinGrams)
        let kcal = max(0, targets.calories - eaten.calories)
        if eaten.calories == 0 {
            return DailyTip(tipAr: "ابدأ يومك بفطار فيه بروتين: 2 بيض + جبنة قريش + نص رغيف بلدي (حوالي 400 سعرة).", tipEn: "Start with a protein breakfast: 2 eggs, cottage cheese and half a baladi loaf (about 400 kcal).")
        }
        return DailyTip(tipAr: "فاضلك \(protein) جم بروتين و\(kcal) سعرة. وجبة زي 200 جم فراخ مشوية + سلطة بتقفل أغلب البروتين.", tipEn: "\(protein) g protein and \(kcal) kcal left. Something like 200 g grilled chicken with salad closes most of the protein gap.")
    }

    private var coachCard: some View {
        let shown = tip ?? fallbackTip
        return Card(background: VColor.night, padding: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Label {
                    BiCaption(ar: "مدرب Vitalyn", en: "Vitalyn Coach", size: 13, color: VColor.mint)
                } icon: {
                    Image(systemName: "sparkles").foregroundStyle(VColor.mint)
                }
                Text(shown.tipAr).font(VFont.readex(15)).foregroundStyle(VColor.nightText).lineSpacing(4)
                Text(shown.tipEn).font(VFont.readex(13)).foregroundStyle(VColor.nightMuted).lineSpacing(3)
                if let tipError { Text(tipError).font(VFont.readex(11)).foregroundStyle(Color(hex: 0xFFB4A0)) }
                HStack(spacing: 10) {
                    Button { input = .text } label: {
                        BiText(ar: "سجّل أكلة", en: "Log a meal", size: 14, color: VColor.night, enColor: VColor.night, enSize: 10, alignment: .center)
                            .padding(.horizontal, 18)
                            .frame(minHeight: 48)
                            .background(VColor.mint, in: Capsule())
                    }
                    Button(action: fetchTip) {
                        Group {
                            if loadingTip {
                                ProgressView().tint(VColor.nightText)
                            } else {
                                BiText(ar: "اقترح عليا", en: "Suggest for me", size: 14, weight: 400, color: VColor.nightText, enColor: VColor.nightMuted, enSize: 10, alignment: .center)
                            }
                        }
                        .padding(.horizontal, 18)
                        .frame(minHeight: 48)
                        .overlay(Capsule().stroke(VColor.nightLine))
                    }
                    .disabled(loadingTip)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var tipKey: String { "tip-\(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970)" }

    private func loadCachedTip() {
        guard tip == nil, let data = UserDefaults.standard.data(forKey: tipKey) else { return }
        tip = try? JSONDecoder().decode(DailyTip.self, from: data)
    }

    private func fetchTip() {
        guard let userContext = AppData.userContext(context, health: health) else { return }
        loadingTip = true
        tipError = nil
        Task {
            do {
                let result = try await ai.run(DailyTip.self, AITasks.dailyTip(context: userContext))
                tip = result
                UserDefaults.standard.set(try? JSONEncoder().encode(result), forKey: tipKey)
            } catch {
                tipError = error.localizedDescription
            }
            loadingTip = false
        }
    }

    // MARK: Meals

    private var mealsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            BiText(ar: "وجبات النهارده", en: "Today’s meals")
            if todayMeals.isEmpty {
                BiText(ar: "لسه مسجلتش أكل النهارده.", en: "No meals logged today yet.", size: 13, weight: 400, color: VColor.inkSecondary)
            }
            ForEach(todayMeals) { meal in
                MealRow(meal: meal)
            }
        }
    }
}
