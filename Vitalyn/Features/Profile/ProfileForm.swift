import SwiftUI
import VitalynCore

/// Profile sections shared by onboarding and the profile screen.
struct BasicsSection: View {
    @Bindable var profile: UserProfile

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionTitle(ar: "بياناتك", en: "About you")
                VStack(alignment: .leading, spacing: 6) {
                    BiCaption(ar: "الاسم", en: "Name")
                    TextField("الاسم · Name", text: $profile.name)
                        .font(VFont.readex(16))
                        .padding(12)
                        .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
                }
                HStack(spacing: 8) {
                    BiChip(ar: "راجل", en: "Male", selected: profile.sex == .male) { profile.sex = .male }
                    BiChip(ar: "ست", en: "Female", selected: profile.sex == .female) { profile.sex = .female }
                    Spacer()
                }
                HStack {
                    BiText(ar: "سنة الميلاد", en: "Birth year", size: 14, weight: 500)
                    Spacer()
                    Menu {
                        Picker("سنة الميلاد · Birth year", selection: $profile.birthYear) {
                            ForEach(Array((1930...2012).reversed()), id: \.self) { year in
                                Text(String(year)).tag(year)
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text(profile.birthYear >= 1930 ? String(profile.birthYear) : "اختار · Choose")
                                .font(VFont.readex(16, weight: 600))
                            Image(systemName: "chevron.up.chevron.down").font(.system(size: 12))
                        }
                        .foregroundStyle(VColor.ink)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 40)
                        .background(VColor.ground, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .accessibilityLabel("سنة الميلاد — Birth year")
                }
                NumberField(ar: "الطول", en: "Height", value: $profile.heightCm, unit: "cm", digits: 0)
                NumberField(ar: "الوزن دلوقتي", en: "Current weight", value: $profile.startWeightKg, unit: "kg")
            }
        }
    }
}

struct GoalSection: View {
    @Bindable var profile: UserProfile
    var currentWeightKg: Double? = nil
    var bodyFatPercent: Double? = nil
    private let paces: [Double] = [0.25, 0.5, 0.75, 1.0]

    private var ideal: IdealWeight? {
        let weight = currentWeightKg ?? profile.startWeightKg
        guard profile.heightCm >= 120, weight >= 30, profile.birthYear >= 1930 else { return nil }
        return NutritionCalculator.idealWeight(heightCm: profile.heightCm, ageYears: profile.ageYears, sex: profile.sex, currentWeightKg: weight, bodyFatPercent: bodyFatPercent)
    }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionTitle(ar: "هدفك", en: "Your goal")
                if let ideal {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline) {
                            BiText(ar: "وزنك المثالي", en: "Your ideal weight", size: 14, weight: 500, color: VColor.primaryDark)
                            Spacer()
                            Text("\(VFormat.decimal(ideal.goalKg)) kg")
                                .font(VFont.readex(22, weight: 700))
                                .foregroundStyle(VColor.primaryDark)
                                .environment(\.layoutDirection, .leftToRight)
                        }
                        Text("المدى الصحي لطولك \(VFormat.decimal(ideal.healthyLowKg))–\(VFormat.decimal(ideal.healthyHighKg)) كجم · Healthy range for your height \(VFormat.decimal(ideal.healthyLowKg))–\(VFormat.decimal(ideal.healthyHighKg)) kg")
                            .font(VFont.readex(11))
                            .foregroundStyle(VColor.inkSecondary)
                        if ideal.alreadyThere {
                            BiText(ar: "وزنك دلوقتي في المدى الصحي — ممكن تركز على الدهون والعضلات.", en: "You're already in the healthy range — focus on fat and muscle.", size: 12, weight: 400, color: VColor.inkSecondary)
                        }
                        if profile.goalWeightKg != ideal.goalKg {
                            Button {
                                profile.goalWeightKg = ideal.goalKg
                            } label: {
                                BiLabel(ar: "استخدمه كهدف", en: "Use as my goal", systemImage: "target", size: 13)
                            }
                            .buttonStyle(SecondaryButtonStyle())
                        }
                    }
                    .padding(14)
                    .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 16))
                }
                NumberField(ar: "الوزن المستهدف", en: "Goal weight", value: $profile.goalWeightKg, unit: "kg")
                VStack(alignment: .leading, spacing: 8) {
                    BiCaption(ar: "معدل النزول في الأسبوع", en: "Weekly pace")
                    HStack(spacing: 8) {
                        ForEach(paces, id: \.self) { pace in
                            BiChip(ar: "\(VFormat.decimal(pace, digits: 2)) كجم", en: "\(VFormat.decimal(pace, digits: 2)) kg", selected: profile.weeklyLossKg == pace) {
                                profile.weeklyLossKg = pace
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 8) {
                    BiCaption(ar: "نشاطك اليومي (من غير التمرين)", en: "Daily activity (outside workouts)")
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(ActivityLevel.allCases, id: \.self) { level in
                                BiChip(ar: level.ar, en: level.en, selected: profile.activity == level) { profile.activity = level }
                            }
                        }
                    }
                }
                HStack(spacing: 16) {
                    Stepper(value: $profile.stepsGoal, in: 2000...25000, step: 500) {
                        BiText(ar: "هدف الخطوات \(VFormat.number(profile.stepsGoal))", en: "Steps goal", size: 13, weight: 500)
                    }
                }
                Stepper(value: $profile.waterGoal, in: 4...16) {
                    BiText(ar: "هدف المية \(profile.waterGoal) كوباية", en: "Water goal (glasses)", size: 13, weight: 500)
                }
            }
        }
    }
}

struct HealthSection: View {
    @Bindable var profile: UserProfile

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 14) {
                SectionTitle(ar: "الأمراض المزمنة", en: "Chronic conditions")
                BiText(ar: "اختار اللي عندك — Claude هياخدها في اعتباره في كل اقتراح.", en: "Pick yours — Claude considers them in every suggestion.", size: 12, weight: 400, color: VColor.inkSecondary)
                FlowLayout(spacing: 8) {
                    ForEach(ChronicCondition.allCases) { condition in
                        let selected = profile.conditions.contains(condition)
                        BiChip(ar: condition.ar, en: condition.en, selected: selected) {
                            if selected {
                                profile.conditions.removeAll { $0 == condition }
                            } else {
                                profile.conditions.append(condition)
                            }
                        }
                    }
                }
                field(ar: "أمراض تانية", en: "Other conditions", text: $profile.otherConditions)
                field(ar: "الأدوية", en: "Medications", text: $profile.medications)
                field(ar: "حساسية من أكل", en: "Food allergies", text: $profile.allergies)
                PreferencesEditor(preferences: $profile.preferences)
            }
        }
    }

    private func field(ar: String, en: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            BiCaption(ar: ar, en: en)
            TextField("\(ar) · \(en)", text: text, axis: .vertical)
                .font(VFont.readex(14))
                .padding(12)
                .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
        }
    }
}

struct PreferencesEditor: View {
    @Binding var preferences: [String]
    @State private var draft = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            BiCaption(ar: "ذوقك في الأكل", en: "Food preferences")
            FlowLayout(spacing: 8) {
                ForEach(preferences, id: \.self) { item in
                    Button {
                        preferences.removeAll { $0 == item }
                    } label: {
                        HStack(spacing: 6) {
                            Text(item).font(VFont.readex(13))
                            Image(systemName: "xmark").font(.system(size: 10, weight: .bold))
                        }
                        .padding(.horizontal, 12)
                        .frame(minHeight: 36)
                        .foregroundStyle(VColor.primaryDark)
                        .background(VColor.primarySoft, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            HStack {
                TextField("مثلاً: بحب الفراخ · e.g. I like chicken", text: $draft)
                    .font(VFont.readex(14))
                    .onSubmit(add)
                Button(action: add) { Image(systemName: "plus.circle.fill").font(.system(size: 24)) }
                    .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityLabel("ضيف — Add")
            }
            .padding(12)
            .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
        }
    }

    private func add() {
        let value = draft.trimmingCharacters(in: .whitespaces)
        guard !value.isEmpty, !preferences.contains(value) else { return }
        preferences.append(value)
        draft = ""
    }
}

extension ActivityLevel {
    var ar: String {
        switch self {
        case .sedentary: "قاعد أغلب اليوم"
        case .light: "حركة خفيفة"
        case .moderate: "حركة متوسطة"
        case .active: "نشيط"
        case .veryActive: "نشيط جداً"
        }
    }

    var en: String {
        switch self {
        case .sedentary: "Sedentary"
        case .light: "Light"
        case .moderate: "Moderate"
        case .active: "Active"
        case .veryActive: "Very active"
        }
    }
}

/// Wrapping layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: width, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rtl = layoutDirectionIsRTL(subviews)
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > bounds.width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            let originX = rtl ? bounds.maxX - x - size.width : bounds.minX + x
            subview.place(at: CGPoint(x: originX, y: bounds.minY + y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }

    private func layoutDirectionIsRTL(_ subviews: Subviews) -> Bool {
        // Custom layouts are mirrored automatically for RTL; place left-to-right.
        false
    }
}
