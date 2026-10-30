import SwiftUI
import SwiftData
import VitalynCore

/// When a new weekly plan begins.
enum PlanStart: String, CaseIterable, Identifiable {
    case today, tomorrow, saturday

    var id: String { rawValue }

    var ar: String {
        switch self {
        case .today: "النهارده"
        case .tomorrow: "بكرة"
        case .saturday: "السبت الجاي"
        }
    }

    var en: String {
        switch self {
        case .today: "Today"
        case .tomorrow: "Tomorrow"
        case .saturday: "Next Saturday"
        }
    }

    func date(from now: Date = Date()) -> Date {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: now)
        switch self {
        case .today: return today
        case .tomorrow: return calendar.date(byAdding: .day, value: 1, to: today) ?? today
        case .saturday: return calendar.nextDate(after: today, matching: DateComponents(weekday: 7), matchingPolicy: .nextTime) ?? today
        }
    }
}

struct PlanSection: View {
    private enum Mode { case day, week }

    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai
    @Environment(HealthService.self) private var health
    @Query(sort: \MealPlanRecord.createdAt, order: .reverse) private var plans: [MealPlanRecord]

    @State private var preset: PlanPreset = .egyptianHome
    @State private var mealsPerDay: MealsPerDay = .threeMealsTwoSnacks
    @State private var start: PlanStart = .today
    @State private var request = ""
    @State private var showSetup = false
    @State private var mode: Mode = .week
    @State private var dayIndex = 0
    @State private var working = false
    @State private var daysDone: Int?
    @State private var swapping: String?
    @State private var error: String?
    @State private var showGroceries = false
    @State private var logging: MealInput?
    @State private var speech = SpeechService()

    private var record: MealPlanRecord? { plans.first }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if record == nil || showSetup { setupCard }
            if working {
                if let daysDone {
                    WorkingView(ar: "بكتب المقادير والوصفات… \(daysDone) من 7 أيام", en: "Writing portions and recipes… \(daysDone) of 7 days")
                } else {
                    WorkingView(ar: "بختار أكلات الأسبوع…", en: "Choosing this week’s dishes…")
                }
            }
            if let error { ErrorBanner(message: error) }

            if !working, let record, let plan = record.plan {
                header(record)
                Button { showGroceries = true } label: {
                    BiLabel(ar: "قائمة المشتريات", en: groceriesSubtitle(record, plan), systemImage: "cart")
                }
                .buttonStyle(PrimaryButtonStyle())

                HStack(spacing: 6) {
                    BiChip(ar: "الأسبوع كله", en: "Whole week", selected: mode == .week, dark: true) { mode = .week }
                    BiChip(ar: "يوم بيوم", en: "Day by day", selected: mode == .day, dark: true) { mode = .day }
                    Spacer()
                }

                switch mode {
                case .week: weekOverview(record, plan)
                case .day: dayDetail(record, plan)
                }

                if !plan.prepAr.isEmpty {
                    Card {
                        VStack(alignment: .leading, spacing: 8) {
                            Label {
                                BiCaption(ar: "خطة الطبخ", en: "Cooking plan", size: 13, color: VColor.primaryDark)
                            } icon: {
                                Image(systemName: "frying.pan").foregroundStyle(VColor.primary)
                            }
                            BiText(ar: plan.prepAr, en: plan.prepEn, size: 13, weight: 400, color: VColor.inkSecondary)
                        }
                    }
                }
                if !plan.notesAr.isEmpty {
                    BiText(ar: plan.notesAr, en: plan.notesEn, size: 13, weight: 400, color: VColor.inkSecondary)
                }
            }
        }
        .sheet(item: $logging) { input in MealCaptureView(input: input) }
        .sheet(isPresented: $showGroceries) {
            if let record { GroceriesView(record: record) }
        }
        .onAppear {
            if let record { dayIndex = record.dayIndex(for: Date()) ?? 0 }
        }
        .onChange(of: preset) { _, newValue in
            if newValue == .intermittentFasting { mealsPerDay = .twoMealsOneSnack }
        }
    }

    // MARK: Setup

    private var setupCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 14) {
                SectionTitle(ar: "جدول أكل الأسبوع", en: "This week’s meal plan")
                BiText(ar: "7 أيام أكل على قد احتياجك، وقائمة مشتريات واحدة متجمعة للأسبوع كله.", en: "Seven days of meals sized to your targets, with one combined grocery list for the week.", size: 13, weight: 400, color: VColor.inkSecondary)

                BiCaption(ar: "نوع الأكل", en: "Food style")
                FlowLayout(spacing: 8) {
                    ForEach(PlanPreset.allCases) { item in
                        BiChip(ar: item.ar, en: item.en, selected: preset == item, dark: true) { preset = item }
                    }
                }
                if preset != .ramadan {
                    BiCaption(ar: "الوجبات في اليوم", en: "Meals a day")
                    FlowLayout(spacing: 8) {
                        ForEach(MealsPerDay.allCases) { item in
                            BiChip(ar: item.ar, en: item.en, selected: mealsPerDay == item, dark: true) { mealsPerDay = item }
                        }
                    }
                }
                BiCaption(ar: "يبدأ من", en: "Starts")
                FlowLayout(spacing: 8) {
                    ForEach(PlanStart.allCases) { item in
                        BiChip(ar: item.ar, en: item.en, selected: start == item, dark: true) { start = item }
                    }
                }
                HStack(alignment: .bottom, spacing: 10) {
                    TextField("طلب إضافي (اختياري) · Extra request — e.g. fish on Friday, cook twice a week", text: $request, axis: .vertical)
                        .lineLimit(1...4)
                        .font(VFont.readex(14))
                        .padding(12)
                        .background(VColor.ground, in: RoundedRectangle(cornerRadius: 14))
                    DictationButton(speech: speech, text: $request)
                }
                Button(action: generate) {
                    BiLabel(ar: record == nil ? "اعمل جدول الأسبوع" : "اعمل جدول جديد", en: record == nil ? "Create this week’s plan" : "Create a new plan", systemImage: "sparkles")
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(working)
                if record != nil {
                    Button { withAnimation { showSetup = false } } label: {
                        BiLabel(ar: "إلغاء", en: "Cancel", size: 14)
                    }
                    .buttonStyle(SecondaryButtonStyle())
                    .disabled(working)
                }
            }
        }
    }

    private func generate() {
        guard let userContext = AppData.userContext(context, health: health) else { return }
        speech.stop()
        working = true
        daysDone = nil
        error = nil
        let startDate = start.date()
        let preset = preset
        let mealsPerDay = mealsPerDay
        let request = request
        Task {
            do {
                let plan = try await WeekPlanner.build(ai: ai, context: userContext, preset: preset, mealsPerDay: mealsPerDay, start: startDate, request: request) { done in
                    daysDone = done
                }
                let record = MealPlanRecord()
                record.startDate = startDate
                record.presetRaw = preset.rawValue
                record.mealsPerDayRaw = mealsPerDay.rawValue
                record.plan = plan
                context.insert(record)
                try? context.save()
                dayIndex = record.dayIndex(for: Date()) ?? 0
                mode = .week
                showSetup = false
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    // MARK: Plan

    private func header(_ record: MealPlanRecord) -> some View {
        let english = Locale(identifier: "en")
        let first = record.startDate.formatted(.dateTime.day().month(.abbreviated).locale(english))
        let last = record.date(ofDay: 6).formatted(.dateTime.day().month(.abbreviated).locale(english))
        return HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                BiText(ar: "جدول الأسبوع", en: "Weekly plan", size: 18)
                Text("\(first) – \(last)").font(VFont.readex(12)).foregroundStyle(VColor.muted)
                    .environment(\.layoutDirection, .leftToRight)
            }
            Spacer()
            if !showSetup {
                Button { withAnimation { showSetup = true } } label: {
                    Label("جدول جديد · New plan", systemImage: "arrow.clockwise")
                        .font(VFont.readex(12, weight: 600))
                }
            }
        }
    }

    private func groceriesSubtitle(_ record: MealPlanRecord, _ plan: WeekPlan) -> String {
        let lines = GroceryList.build(plan)
        let left = lines.filter { !record.checkedGroceries.contains($0.id) }.count
        return left == 0 ? "Grocery list · all bought" : "Grocery list · \(left) of \(lines.count) to buy"
    }

    private func weekOverview(_ record: MealPlanRecord, _ plan: WeekPlan) -> some View {
        VStack(spacing: 10) {
            ForEach(plan.days.sorted { $0.dayIndex < $1.dayIndex }, id: \.dayIndex) { day in
                let date = record.date(ofDay: day.dayIndex)
                let totals = day.totals
                let isToday = Calendar.current.isDateInToday(date)
                Button {
                    dayIndex = day.dayIndex
                    mode = .day
                } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(date.formatted(.dateTime.weekday(.wide).day().locale(Locale(identifier: "ar")))).font(VFont.readex(15, weight: 600))
                            Text(date.formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "en")))).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                            if isToday {
                                Text("النهارده · Today").font(VFont.readex(10, weight: 600)).foregroundStyle(VColor.primaryDark)
                                    .padding(.horizontal, 8).padding(.vertical, 2)
                                    .background(VColor.primarySoft, in: Capsule())
                            }
                            Spacer()
                            Text("\(VFormat.number(totals.calories)) kcal · P \(totals.proteinGrams) g")
                                .font(VFont.readex(11, weight: 500)).foregroundStyle(VColor.inkSecondary)
                                .environment(\.layoutDirection, .leftToRight)
                        }
                        ForEach(day.meals.indices, id: \.self) { index in
                            let meal = day.meals[index]
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(meal.slot.ar).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                                    .frame(width: 40, alignment: .leading)
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(meal.nameAr).font(VFont.readex(13))
                                    Text(meal.nameEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                                }
                                Spacer()
                                Text("\(Int(meal.kcal))").font(VFont.readex(11)).foregroundStyle(VColor.muted)
                            }
                        }
                    }
                    .foregroundStyle(VColor.ink)
                    .multilineTextAlignment(.leading)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(isToday ? VColor.primary : Color.clear, lineWidth: 1.5))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func dayDetail(_ record: MealPlanRecord, _ plan: WeekPlan) -> some View {
        dayPicker(record)
        if let day = plan.days.first(where: { $0.dayIndex == dayIndex }) ?? plan.days.first {
            let totals = day.totals
            HStack {
                Text("\(VFormat.number(totals.calories)) kcal")
                Spacer()
                Text("P \(totals.proteinGrams) g").foregroundStyle(VColor.primaryDark)
                Spacer()
                Text("C \(totals.carbsGrams) g").foregroundStyle(VColor.inkSecondary)
                Spacer()
                Text("F \(totals.fatGrams) g").foregroundStyle(VColor.inkSecondary)
            }
            .font(VFont.readex(13, weight: 500))
            .padding(12)
            .background(VColor.surface, in: RoundedRectangle(cornerRadius: 16))
            .environment(\.layoutDirection, .leftToRight)

            ForEach(day.meals.indices, id: \.self) { index in
                let key = "\(day.dayIndex)-\(index)"
                PlannedMealCard(meal: day.meals[index], swapping: swapping == key) {
                    swap(record: record, day: day, mealIndex: index)
                } onLog: {
                    let meal = day.meals[index]
                    logging = .prefilled(meal.mealLog, slot: meal.slot)
                }
            }
        }
    }

    private func dayPicker(_ record: MealPlanRecord) -> some View {
        HStack(spacing: 5) {
            ForEach(0..<7, id: \.self) { index in
                let date = record.date(ofDay: index)
                let selected = index == dayIndex
                Button { dayIndex = index } label: {
                    VStack(spacing: 1) {
                        Text(date.formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "ar")))).font(VFont.readex(10))
                        Text(date.formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "en")))).font(VFont.readex(9)).opacity(0.85)
                        Text(date.formatted(.dateTime.day())).font(VFont.readex(16, weight: 600))
                    }
                    .frame(maxWidth: .infinity, minHeight: 66)
                    .foregroundStyle(selected ? .white : VColor.ink)
                    .background(selected ? VColor.primary : VColor.surface, in: RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Calendar.current.isDateInToday(date) && !selected ? VColor.primary : Color.clear))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func swap(record: MealPlanRecord, day: PlanDay, mealIndex: Int) {
        guard let userContext = AppData.userContext(context, health: health), let plan = record.plan else { return }
        swapping = "\(day.dayIndex)-\(mealIndex)"
        error = nil
        let request = AITasks.swapMeal(context: userContext, meal: day.meals[mealIndex], ingredients: plan.ingredientNames, request: "")
        Task {
            do {
                let replacement = try await ai.run(PlannedMeal.self, request)
                if var plan = record.plan, let dayPosition = plan.days.firstIndex(where: { $0.dayIndex == day.dayIndex }),
                   plan.days[dayPosition].meals.indices.contains(mealIndex) {
                    plan.days[dayPosition].meals[mealIndex] = replacement
                    record.plan = plan
                    try? context.save()
                }
            } catch {
                self.error = error.localizedDescription
            }
            swapping = nil
        }
    }
}

extension PlannedMeal {
    /// The planned meal as a single item, for logging it as eaten.
    var mealLog: MealLog {
        MealLog(titleAr: nameAr, titleEn: nameEn, items: [
            AnalyzedFood(nameAr: nameAr, nameEn: nameEn, grams: 0, kcal: kcal, proteinG: proteinG, carbsG: carbsG, fatG: fatG, confidence: .medium)
        ])
    }
}

struct PlannedMealCard: View {
    let meal: PlannedMeal
    let swapping: Bool
    let onSwap: () -> Void
    let onLog: () -> Void
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(meal.slot.ar) · \(meal.slot.en) · \(Int(meal.kcal)) kcal · P \(Int(meal.proteinG)) g")
                        .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                    Text(meal.nameAr).font(VFont.readex(14, weight: 500))
                    Text(meal.nameEn).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                }
                Spacer()
                if swapping {
                    ProgressView()
                } else {
                    Button(action: onSwap) {
                        VStack(spacing: 1) {
                            Image(systemName: "arrow.left.arrow.right")
                            Text("بدّل").font(VFont.readex(11))
                            Text("Swap").font(VFont.readex(9)).foregroundStyle(VColor.muted)
                        }
                        .frame(width: 52, height: 52)
                        .background(VColor.ground, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                }
            }
            if expanded {
                if !meal.ingredients.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(meal.ingredients.indices, id: \.self) { index in
                            let ingredient = meal.ingredients[index]
                            HStack {
                                Text("\(ingredient.nameAr) · \(ingredient.nameEn)").font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                                Spacer()
                                Text(ingredient.amountText).font(VFont.readex(12, weight: 600)).foregroundStyle(VColor.ink)
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                        }
                    }
                    .padding(10)
                    .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
                }
                Text(meal.recipeAr).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                Text(meal.recipeEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
            }
            HStack {
                Button(expanded ? "اخفي · Hide" : "المكونات والطريقة · Ingredients & method") { withAnimation { expanded.toggle() } }
                Spacer()
                Button("سجّلها · Log it", action: onLog)
            }
            .font(VFont.readex(12, weight: 600))
        }
        .padding(14)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

/// The week's shopping, added up from every meal and grouped by supermarket section. Ticks are saved with the plan.
struct GroceriesView: View {
    let record: MealPlanRecord
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    var body: some View {
        let lines = record.plan.map(GroceryList.build) ?? []
        let checked = Set(record.checkedGroceries)
        let left = lines.filter { !checked.contains($0.id) }.count
        NavigationStack {
            List {
                Section {
                    BiText(ar: left == 0 ? "اشتريت كل حاجة" : "فاضل \(left) من \(lines.count) حاجة", en: left == 0 ? "Everything's bought" : "\(left) of \(lines.count) items left", size: 14, weight: 500)
                }
                ForEach(GroceryCategory.allCases, id: \.self) { category in
                    let items = lines.filter { $0.category == category }
                    if !items.isEmpty {
                        Section {
                            ForEach(items) { line in
                                row(line, checked: checked.contains(line.id))
                            }
                        } header: {
                            Text("\(category.ar) · \(category.en)").font(VFont.readex(12, weight: 600))
                        }
                    }
                }
            }
            .navigationTitle("المشتريات · Groceries")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("تم · Done") { dismiss() } }
                ToolbarItemGroup(placement: .topBarLeading) {
                    ShareLink(item: shareText(lines, checked: checked))
                    if !checked.isEmpty {
                        Button("شيل العلامات · Uncheck all") {
                            record.checkedGroceries = []
                            try? context.save()
                        }
                        .font(VFont.readex(12))
                    }
                }
            }
        }
    }

    private func row(_ line: GroceryLine, checked: Bool) -> some View {
        Button {
            if checked {
                record.checkedGroceries.removeAll { $0 == line.id }
            } else {
                record.checkedGroceries.append(line.id)
            }
            try? context.save()
        } label: {
            HStack {
                Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(checked ? VColor.primary : VColor.muted)
                VStack(alignment: .leading) {
                    Text(line.nameAr).font(VFont.readex(14)).strikethrough(checked)
                    Text(line.nameEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                }
                Spacer()
                Text(line.quantity).font(VFont.readex(13, weight: 500))
                    .environment(\.layoutDirection, .leftToRight)
            }
            .foregroundStyle(checked ? VColor.muted : VColor.ink)
        }
    }

    /// What's still to buy (or everything, once it's all ticked), by section.
    private func shareText(_ lines: [GroceryLine], checked: Set<String>) -> String {
        let toBuy = lines.filter { !checked.contains($0.id) }
        let shown = toBuy.isEmpty ? lines : toBuy
        var text = ["قائمة المشتريات · Grocery list"]
        for category in GroceryCategory.allCases {
            let items = shown.filter { $0.category == category }
            guard !items.isEmpty else { continue }
            text.append("\n\(category.ar) · \(category.en)")
            text += items.map { "• \($0.nameAr) / \($0.nameEn) — \($0.quantity)" }
        }
        return text.joined(separator: "\n")
    }
}

/// Today's meals from the weekly plan on the Today screen, or a way into planning when there are none.
struct TodayPlanCard: View {
    @Query(sort: \MealPlanRecord.createdAt, order: .reverse) private var plans: [MealPlanRecord]
    let openPlan: () -> Void
    @State private var logging: MealInput?

    var body: some View {
        Group {
            if let record = plans.first, let index = record.dayIndex(for: Date()),
               let day = record.plan?.days.first(where: { $0.dayIndex == index }), !day.meals.isEmpty {
                Card {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            BiText(ar: "أكل النهارده من الجدول", en: "Today’s planned meals", size: 15)
                            Spacer()
                            Button(action: openPlan) {
                                Text("الأسبوع · Week").font(VFont.readex(12, weight: 600))
                            }
                        }
                        ForEach(day.meals.indices, id: \.self) { index in
                            let meal = day.meals[index]
                            HStack(spacing: 10) {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text("\(meal.slot.ar) · \(meal.slot.en) · \(Int(meal.kcal)) kcal")
                                        .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                                    Text(meal.nameAr).font(VFont.readex(13, weight: 500))
                                    Text(meal.nameEn).font(VFont.readex(11)).foregroundStyle(VColor.inkSecondary)
                                }
                                Spacer()
                                Button { logging = .prefilled(meal.mealLog, slot: meal.slot) } label: {
                                    Image(systemName: "plus.circle.fill").font(.system(size: 24)).foregroundStyle(VColor.primary)
                                }
                                .accessibilityLabel("سجّلها — Log it")
                            }
                        }
                    }
                }
            } else {
                Button(action: openPlan) {
                    HStack(spacing: 12) {
                        Image(systemName: "calendar").font(.system(size: 20)).foregroundStyle(VColor.primary)
                        BiText(ar: upcoming ? "جدول الأكل بيبدأ قريب" : "خطط أكل الأسبوع وقائمة المشتريات", en: upcoming ? "Your meal plan starts soon" : "Plan this week’s meals and grocery list", size: 14, weight: 500)
                        Spacer()
                        Image(systemName: "chevron.forward").foregroundStyle(VColor.muted)
                    }
                    .padding(14)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .sheet(item: $logging) { input in MealCaptureView(input: input) }
    }

    private var upcoming: Bool {
        guard let record = plans.first else { return false }
        return record.startDate > Date()
    }
}
