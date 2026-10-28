import SwiftUI
import SwiftData
import VitalynCore

struct PlanSection: View {
    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai
    @Environment(HealthService.self) private var health
    @Query(sort: \MealPlanRecord.createdAt, order: .reverse) private var plans: [MealPlanRecord]

    @State private var preset: PlanPreset = .egyptianHome
    @State private var request = ""
    @State private var dayIndex = 0
    @State private var working = false
    @State private var swapping: Int?
    @State private var error: String?
    @State private var showGroceries = false
    @State private var logging: MealInput?
    @State private var speech = SpeechService()

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    BiCaption(ar: "نوع الجدول", en: "Plan style")
                    FlowLayout(spacing: 8) {
                        ForEach(PlanPreset.allCases) { item in
                            BiChip(ar: item.ar, en: item.en, selected: preset == item, dark: true) { preset = item }
                        }
                    }
                    HStack(alignment: .bottom, spacing: 10) {
                        TextField("طلب إضافي (اختياري) · Extra request — e.g. no fish on Friday", text: $request, axis: .vertical)
                            .lineLimit(1...4)
                            .font(VFont.readex(14))
                            .padding(12)
                            .background(VColor.ground, in: RoundedRectangle(cornerRadius: 14))
                        DictationButton(speech: speech, text: $request)
                    }
                    Button(action: generate) {
                        BiLabel(ar: plan == nil ? "اعمل جدول الأسبوع" : "اعمل جدول جديد", en: plan == nil ? "Create this week’s plan" : "Create a new plan", systemImage: "sparkles")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(working)
                }
            }

            if working { WorkingView(ar: "بجهز الجدول… ممكن ياخد دقيقة", en: "Building your plan… this can take a minute") }
            if let error { ErrorBanner(message: error) }

            if let record = plans.first, let plan = record.plan {
                dayPicker(start: record.startDate, plan: plan)
                if let day = plan.days.first(where: { $0.dayIndex == dayIndex }) ?? plan.days.first {
                    let totals = dayTotals(day)
                    HStack {
                        Text("\(VFormat.number(totals.calories)) kcal")
                        Spacer()
                        Text("P \(totals.proteinGrams) g").foregroundStyle(VColor.primaryDark)
                        Spacer()
                        Text("C \(totals.carbsGrams) g").foregroundStyle(VColor.inkSecondary)
                    }
                    .font(VFont.readex(13, weight: 500))
                    .padding(12)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 16))
                    .environment(\.layoutDirection, .leftToRight)

                    ForEach(day.meals.indices, id: \.self) { index in
                        PlannedMealCard(meal: day.meals[index], swapping: swapping == index) {
                            swap(record: record, day: day, mealIndex: index)
                        } onLog: {
                            let meal = day.meals[index]
                            let item = AnalyzedFood(nameAr: meal.nameAr, nameEn: meal.nameEn, grams: 0, kcal: meal.kcal, proteinG: meal.proteinG, carbsG: meal.carbsG, fatG: meal.fatG, confidence: .medium)
                            logging = .prefilled(MealLog(titleAr: meal.nameAr, titleEn: meal.nameEn, items: [item]), slot: meal.slot)
                        }
                    }
                }
                if !plan.notesAr.isEmpty {
                    BiText(ar: plan.notesAr, en: plan.notesEn, size: 13, weight: 400, color: VColor.inkSecondary)
                }
                Button { showGroceries = true } label: {
                    BiLabel(ar: "قائمة المشتريات", en: "Grocery list", systemImage: "cart")
                }
                .buttonStyle(SecondaryButtonStyle())
                .sheet(isPresented: $showGroceries) { GroceriesView(items: plan.groceries) }
            }
        }
        .sheet(item: $logging) { input in MealCaptureView(input: input) }
        .onAppear {
            if let record = plans.first {
                let offset = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: record.startDate), to: Calendar.current.startOfDay(for: Date())).day ?? 0
                dayIndex = min(6, max(0, offset))
            }
        }
    }

    private var plan: WeekPlan? { plans.first?.plan }

    private func dayPicker(start: Date, plan: WeekPlan) -> some View {
        HStack(spacing: 5) {
            ForEach(0..<7, id: \.self) { index in
                let date = Calendar.current.date(byAdding: .day, value: index, to: start) ?? start
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
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func dayTotals(_ day: PlanDay) -> MacroTargets {
        MacroTargets(
            calories: Int(day.meals.reduce(0) { $0 + $1.kcal }),
            proteinGrams: Int(day.meals.reduce(0) { $0 + $1.proteinG }),
            carbsGrams: Int(day.meals.reduce(0) { $0 + $1.carbsG }),
            fatGrams: Int(day.meals.reduce(0) { $0 + $1.fatG })
        )
    }

    private func generate() {
        guard let userContext = AppData.userContext(context, health: health) else { return }
        speech.stop()
        working = true
        error = nil
        let request = AITasks.weekPlan(context: userContext, preset: preset, request: request)
        Task {
            do {
                let plan = try await ai.run(WeekPlan.self, request)
                let record = MealPlanRecord()
                record.startDate = Calendar.current.startOfDay(for: Date())
                record.presetRaw = preset.rawValue
                record.plan = plan
                context.insert(record)
                try? context.save()
                dayIndex = 0
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    private func swap(record: MealPlanRecord, day: PlanDay, mealIndex: Int) {
        guard let userContext = AppData.userContext(context, health: health) else { return }
        swapping = mealIndex
        error = nil
        let request = AITasks.swapMeal(context: userContext, meal: day.meals[mealIndex], request: "")
        Task {
            do {
                let replacement = try await ai.run(PlannedMeal.self, request)
                if var plan = record.plan, let dayPosition = plan.days.firstIndex(where: { $0.dayIndex == day.dayIndex }) {
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
                Text(meal.recipeAr).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                Text(meal.recipeEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
            }
            HStack {
                Button(expanded ? "اخفي الوصفة · Hide recipe" : "الوصفة · Recipe") { withAnimation { expanded.toggle() } }
                Spacer()
                Button("سجّلها · Log it", action: onLog)
            }
            .font(VFont.readex(12, weight: 600))
        }
        .padding(14)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct GroceriesView: View {
    let items: [GroceryItem]
    @Environment(\.dismiss) private var dismiss
    @State private var checked: Set<String> = []

    var body: some View {
        NavigationStack {
            List(items, id: \.nameEn) { item in
                Button {
                    if checked.contains(item.nameEn) { checked.remove(item.nameEn) } else { checked.insert(item.nameEn) }
                } label: {
                    HStack {
                        Image(systemName: checked.contains(item.nameEn) ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(checked.contains(item.nameEn) ? VColor.primary : VColor.muted)
                        VStack(alignment: .leading) {
                            Text(item.nameAr).font(VFont.readex(14))
                            Text(item.nameEn).font(VFont.readex(11)).foregroundStyle(VColor.muted)
                        }
                        Spacer()
                        Text(item.quantity).font(VFont.readex(13, weight: 500))
                    }
                    .foregroundStyle(VColor.ink)
                }
            }
            .navigationTitle("المشتريات · Groceries")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("تم · Done") { dismiss() } }
                ToolbarItem(placement: .topBarLeading) {
                    ShareLink(item: items.map { "• \($0.nameAr) / \($0.nameEn) — \($0.quantity)" }.joined(separator: "\n"))
                }
            }
        }
    }
}
