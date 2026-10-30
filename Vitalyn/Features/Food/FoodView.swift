import SwiftUI
import SwiftData
import VitalynCore

enum FoodTab: String, CaseIterable {
    case log, products, plan

    static let storageKey = "food-tab"

    var ar: String {
        switch self {
        case .log: "سجل الأكل"
        case .products: "منتجاتي"
        case .plan: "الجدول"
        }
    }

    var en: String {
        switch self {
        case .log: "Food log"
        case .products: "Products"
        case .plan: "Weekly plan"
        }
    }
}

struct FoodView: View {
    /// Stored so other screens (Today's plan card) can open a specific tab.
    @AppStorage(FoodTab.storageKey) private var tab: FoodTab = .log

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BiText(ar: "الأكل", en: "Food", size: 26, enSize: 15)
                    HStack(spacing: 6) {
                        ForEach(FoodTab.allCases, id: \.self) { item in
                            BiChip(ar: item.ar, en: item.en, selected: tab == item, dark: true) { tab = item }
                        }
                    }
                    switch tab {
                    case .log: FoodLogSection()
                    case .products: ProductsSection()
                    case .plan: PlanSection()
                    }
                }
                .padding(20)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .background(VColor.ground)
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

struct FoodLogSection: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Query(sort: \MealEntry.date, order: .reverse) private var meals: [MealEntry]
    @State private var input: MealInput?
    @State private var showScanner = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Button { input = .photo } label: { BiLabel(ar: "صوّر وجبة", en: "Snap a meal", systemImage: "camera", size: 14) }
                    .buttonStyle(PrimaryButtonStyle())
                Button { input = .text } label: { BiLabel(ar: "اكتب أو قول", en: "Type or say", systemImage: "mic", size: 14) }
                    .buttonStyle(PrimaryButtonStyle(color: VColor.night))
            }
            Button { showScanner = true } label: {
                BiLabel(ar: "امسح باركود منتج", en: "Scan a product barcode", systemImage: "barcode.viewfinder", size: 14)
            }
            .buttonStyle(SecondaryButtonStyle())

            ForEach(days, id: \.self) { day in
                let dayMeals = meals.filter { Calendar.current.isDate($0.date, inSameDayAs: day) }
                if !dayMeals.isEmpty {
                    DayMealsCard(day: day, meals: dayMeals) { meal in
                        context.delete(meal)
                        try? context.save()
                        AppData.publishSnapshot(context, health: health)
                    }
                }
            }
            if meals.isEmpty {
                BiText(ar: "لسه مسجلتش أكل. صوّر أول وجبة!", en: "Nothing logged yet. Snap your first meal!", size: 14, weight: 400, color: VColor.inkSecondary)
            }
        }
        .sheet(item: $input) { input in MealCaptureView(input: input) }
        .sheet(isPresented: $showScanner) { BarcodeFlowView() }
    }

    private var days: [Date] {
        let today = Calendar.current.startOfDay(for: Date())
        return (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: -$0, to: today) }
    }
}

struct DayMealsCard: View {
    let day: Date
    let meals: [MealEntry]
    let onDelete: (MealEntry) -> Void

    var body: some View {
        let total = Portions.total(meals.flatMap { $0.sortedItems.map(\.analyzed) })
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                BiText(ar: dayTitleAr, en: dayTitleEn, size: 15)
                Spacer()
                Text("\(VFormat.number(total.calories)) kcal · P \(total.proteinGrams) g")
                    .font(VFont.readex(12, weight: 500))
                    .foregroundStyle(VColor.inkSecondary)
                    .environment(\.layoutDirection, .leftToRight)
            }
            ForEach(meals.sorted { $0.date < $1.date }) { meal in
                MealRow(meal: meal)
                    .contextMenu {
                        Button("امسح · Delete", role: .destructive) { onDelete(meal) }
                    }
            }
        }
    }

    private var dayTitleAr: String {
        if Calendar.current.isDateInToday(day) { return "النهارده" }
        if Calendar.current.isDateInYesterday(day) { return "امبارح" }
        return day.formatted(.dateTime.weekday(.wide).day().month().locale(Locale(identifier: "ar")))
    }

    private var dayTitleEn: String {
        if Calendar.current.isDateInToday(day) { return "Today" }
        if Calendar.current.isDateInYesterday(day) { return "Yesterday" }
        return day.formatted(.dateTime.weekday(.abbreviated).day().month().locale(Locale(identifier: "en")))
    }
}

struct MealRow: View {
    let meal: MealEntry

    var body: some View {
        let total = meal.totals
        HStack(spacing: 12) {
            Group {
                if let image = ImageTools.thumbnail(meal.photo, maxDimension: 160) {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    Image(systemName: "fork.knife").foregroundStyle(VColor.ink)
                }
            }
            .frame(width: 52, height: 52)
            .background(VColor.primarySoft)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(meal.slot.ar).font(VFont.readex(14, weight: 600))
                    Text("\(meal.slot.en) · \(meal.date.formatted(date: .omitted, time: .shortened))")
                        .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                    Spacer()
                    Text("\(total.calories) kcal").font(VFont.readex(14, weight: 600))
                }
                Text(meal.titleAr.isEmpty ? meal.sortedItems.map(\.nameAr).joined(separator: " + ") : meal.titleAr)
                    .font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary).lineLimit(1)
                Text(meal.titleEn.isEmpty ? meal.sortedItems.map(\.nameEn).joined(separator: ", ") : meal.titleEn)
                    .font(VFont.readex(11)).foregroundStyle(VColor.muted).lineLimit(1)
            }
        }
        .padding(12)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}
