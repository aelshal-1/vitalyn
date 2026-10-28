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
                Stepper(value: $profile.birthYear, in: 1940...2012) {
                    HStack {
                        BiText(ar: "سنة الميلاد", en: "Birth year", size: 14, weight: 500)
                        Spacer()
                        Text(String(profile.birthYear)).font(VFont.readex(16, weight: 600))
                    }
                }
                NumberField(ar: "الطول", en: "Height", value: $profile.heightCm, unit: "cm", digits: 0)
                NumberField(ar: "الوزن دلوقتي", en: "Current weight", value: $profile.startWeightKg, unit: "kg")
            }
        }
    }
}

struct GoalSection: View {
    @Bindable var profile: UserProfile
    private let paces: [Double] = [0.25, 0.5, 0.75, 1.0]

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                SectionTitle(ar: "هدفك", en: "Your goal")
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
