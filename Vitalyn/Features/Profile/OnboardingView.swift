import SwiftUI
import SwiftData
import VitalynCore

/// First-launch setup: Apple Health + Claude key → about you (prefilled from Health) → goal → health.
struct OnboardingView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai

    @State private var profile = UserProfile()
    @State private var step = 0
    @State private var apiKey = ""
    @State private var prepared = false
    @State private var imported: HealthService.HealthProfile?
    @State private var importing = false
    @State private var editingImported = false
    let onFinish: () -> Void

    private let steps = 4

    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: Double(step + 1), total: Double(steps))
                .tint(VColor.primary)
                .padding(.horizontal, 20)
                .padding(.top, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case 0:
                        welcome
                        connections
                    case 1:
                        if let imported, !imported.isEmpty {
                            Label {
                                BiText(ar: "جبنا البيانات دي من Apple Health — راجعها وعدّل أي حاجة.", en: "Filled in from Apple Health — review and edit anything.", size: 12, weight: 500, color: VColor.primaryDark)
                            } icon: {
                                Image(systemName: "heart.fill").foregroundStyle(Color(hex: 0xC2264B))
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 14))
                        }
                        BasicsSection(profile: profile)
                    case 2:
                        GoalSection(profile: profile, bodyFatPercent: imported?.bodyFatPercent)
                            .onAppear {
                                if profile.goalWeightKg == 0 {
                                    profile.goalWeightKg = NutritionCalculator.idealWeight(heightCm: profile.heightCm, ageYears: profile.ageYears, sex: profile.sex, currentWeightKg: profile.startWeightKg).goalKg
                                    if let recommended = NutritionCalculator.paceOptions(weightKg: profile.startWeightKg, heightCm: profile.heightCm, ageYears: profile.ageYears).first(where: \.recommended) {
                                        profile.weeklyLossKg = recommended.kgPerWeek
                                    }
                                }
                            }
                    default:
                        HealthSection(profile: profile)
                        MetabolismSection(profile: profile)
                        TargetsPreview(profile: profile)
                    }
                }
                .padding(20)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }

            if !missing.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(missing, id: \.en) { item in
                        Label {
                            Text("\(item.ar) · \(item.en)").font(VFont.readex(12))
                        } icon: {
                            Image(systemName: "exclamationmark.circle")
                        }
                        .foregroundStyle(VColor.energy)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
            }

            HStack(spacing: 10) {
                if step > 0 {
                    Button { withAnimation { step -= 1 } } label: {
                        BiLabel(ar: "رجوع", en: "Back")
                    }
                    .buttonStyle(SecondaryButtonStyle())
                }
                Button {
                    if step < steps - 1 {
                        if step == 0, !apiKey.trimmingCharacters(in: .whitespaces).isEmpty {
                            ai.setKey(apiKey)
                        }
                        withAnimation { step += 1 }
                    } else {
                        finish()
                    }
                } label: {
                    BiLabel(ar: step < steps - 1 ? "التالي" : "يلا نبدأ", en: step < steps - 1 ? "Next" : "Let’s start")
                }
                .buttonStyle(PrimaryButtonStyle(color: missing.isEmpty ? VColor.primary : VColor.muted))
                .disabled(!missing.isEmpty)
            }
            .padding(20)
        }
        .background(VColor.ground)
        .task {
            // Start empty so nothing is accepted without the user entering it.
            if !prepared {
                prepared = true
                profile.heightCm = 0
                profile.startWeightKg = 0
                profile.goalWeightKg = 0
                profile.birthYear = 0
            }
            if health.requested, imported == nil { await importFromHealth() }
        }
    }

    /// What the current step still needs before Next is enabled.
    private var missing: [(ar: String, en: String)] {
        var items: [(ar: String, en: String)] = []
        switch step {
        case 1:
            if profile.name.trimmingCharacters(in: .whitespaces).isEmpty { items.append(("اكتب اسمك", "Enter your name")) }
            if !(1930...2012).contains(profile.birthYear) { items.append(("اختار سنة ميلادك", "Choose your birth year")) }
            if !(120...230).contains(profile.heightCm) { items.append(("اكتب طولك (120–230 سم)", "Enter your height (120–230 cm)")) }
            if !(30...300).contains(profile.startWeightKg) { items.append(("اكتب وزنك (30–300 كجم)", "Enter your weight (30–300 kg)")) }
        case 2:
            if !(30...300).contains(profile.goalWeightKg) {
                items.append(("اكتب الوزن المستهدف", "Enter your goal weight"))
            } else if profile.goalWeightKg > profile.startWeightKg {
                items.append(("الهدف لازم يكون أقل من وزنك دلوقتي أو قده", "Goal can't be above your current weight"))
            }
        default:
            break
        }
        return items
    }

    private var welcome: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "leaf.circle.fill")
                .font(.system(size: 48))
                .foregroundStyle(VColor.primary)
            BiText(ar: "أهلاً بيك في Vitalyn", en: "Welcome to Vitalyn", size: 26, enSize: 16)
            BiText(ar: "هنعرف عنك شوية حاجات عشان نحسب احتياجك ونتابعك يوم بيوم.", en: "A few questions so we can calculate your needs and coach you day by day.", size: 14, weight: 400, color: VColor.inkSecondary)
        }
    }

    private var connections: some View {
        VStack(alignment: .leading, spacing: 16) {
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    SectionTitle(ar: "Apple Health", en: "Apple Health")
                    BiText(ar: "عشان ناخد خطواتك ونشاطك ووزنك، ونسجل وجباتك وتمارينك.", en: "To read steps, activity and weight, and save your meals and workouts.", size: 13, weight: 400, color: VColor.inkSecondary)
                    Button {
                        Task {
                            await health.requestAuthorization()
                            await importFromHealth()
                        }
                    } label: {
                        BiLabel(ar: health.requested ? "متصل" : "اربط Apple Health", en: health.requested ? "Connected" : "Connect Apple Health", systemImage: health.requested ? "checkmark.circle.fill" : "heart.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(color: health.requested ? VColor.primaryDark : Color(hex: 0xC2264B)))
                    .disabled(!health.isAvailable)
                    if importing { ProgressView() }
                    if let imported {
                        if imported.isEmpty {
                            BiText(ar: "Apple Health مفيهوش بيانات عنك لسه — هتكتبها في الخطوة الجاية.", en: "Apple Health has no profile data yet — you'll enter it next.", size: 12, weight: 400, color: VColor.inkSecondary)
                        } else {
                            importedSection(imported)
                        }
                    }
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    SectionTitle(ar: "مفتاح Claude API", en: "Claude API key")
                    BiText(ar: "ده اللي بيخلّي Vitalyn يحلل الصور ويقترح. هيتخزن في الـ Keychain على جهازك بس. تقدر تضيفه بعدين من الإعدادات.", en: "Powers photo analysis and coaching. Stored only in this device's Keychain. You can add it later in Settings.", size: 13, weight: 400, color: VColor.inkSecondary)
                    SecureField("sk-ant-…", text: $apiKey)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(.system(.body, design: .monospaced))
                        .padding(12)
                        .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
                        .environment(\.layoutDirection, .leftToRight)
                }
            }
        }
    }

    /// What came from Health, shown as the profile's current values so edits show up here too.
    private func importedSection(_ imported: HealthService.HealthProfile) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                BiCaption(ar: "جبنا من Apple Health", en: "Imported from Apple Health", color: VColor.primaryDark)
                Spacer()
                Button {
                    withAnimation { editingImported.toggle() }
                } label: {
                    Label(editingImported ? "تمام · Done" : "عدّل · Edit", systemImage: editingImported ? "checkmark" : "pencil")
                        .font(VFont.readex(12, weight: 600))
                }
            }
            if editingImported {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        BiChip(ar: "راجل", en: "Male", selected: profile.sex == .male) { profile.sex = .male }
                        BiChip(ar: "ست", en: "Female", selected: profile.sex == .female) { profile.sex = .female }
                        Spacer()
                    }
                    BirthYearField(year: $profile.birthYear)
                    NumberField(ar: "الطول", en: "Height", value: $profile.heightCm, unit: "cm", digits: 0)
                    NumberField(ar: "الوزن دلوقتي", en: "Current weight", value: $profile.startWeightKg, unit: "kg")
                    NumberField(ar: "نسبة الدهون", en: "Body fat", value: bodyFatBinding, unit: "%")
                }
                .padding(.top, 8)
            } else {
                if imported.heightCm != nil, profile.heightCm > 0 { importedRow("الطول", "Height", "\(VFormat.number(Int(profile.heightCm.rounded()))) cm") }
                if imported.weightKg != nil, profile.startWeightKg > 0 {
                    importedRow("الوزن", "Weight", "\(VFormat.decimal(profile.startWeightKg)) kg", measured: profile.startWeightKg == roundedImportedWeight ? imported.weightDate : nil)
                }
                if let value = imported.bodyFatPercent { importedRow("نسبة الدهون", "Body fat", "\(VFormat.decimal(value))%", measured: imported.bodyFatDate) }
                if imported.birthYear != nil, profile.birthYear > 0 { importedRow("سنة الميلاد", "Birth year", String(profile.birthYear)) }
                if imported.sex != nil { importedRow("النوع", "Sex", profile.sex == .male ? "راجل · Male" : "ست · Female") }
            }
        }
    }

    /// A row; `measured` flags a reading old enough that it may no longer be right.
    private func importedRow(_ ar: String, _ en: String, _ value: String, measured: Date? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("\(ar) · \(en)").font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                Spacer()
                Text(value).font(VFont.readex(13, weight: 600)).foregroundStyle(VColor.ink)
                    .environment(\.layoutDirection, .leftToRight)
            }
            if let measured, measured < Date().addingTimeInterval(-30 * 86_400) {
                Label {
                    Text("آخر قياس \(Self.ago(measured, locale: "ar@numbers=latn")) · Last measured \(Self.ago(measured, locale: "en"))")
                        .font(VFont.readex(11))
                } icon: {
                    Image(systemName: "clock.badge.exclamationmark")
                }
                .foregroundStyle(VColor.energy)
            }
        }
    }

    private static func ago(_ date: Date, locale: String) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: locale)
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }

    /// Health's weight as it was put into the profile, to tell whether the user changed it.
    private var roundedImportedWeight: Double? {
        imported?.weightKg.map { ($0 * 10).rounded() / 10 }
    }

    /// Body fat lives on the import (it feeds the goal step); 0 clears it.
    private var bodyFatBinding: Binding<Double> {
        Binding(
            get: { imported?.bodyFatPercent ?? 0 },
            set: { value in
                let newValue = value > 0 ? value : nil
                guard newValue != imported?.bodyFatPercent else { return }
                imported?.bodyFatPercent = newValue
                imported?.bodyFatDate = nil
            }
        )
    }

    /// Fills empty profile fields from Health; the user reviews and edits them on the next step.
    private func importFromHealth() async {
        importing = true
        let data = await health.profileFromHealth()
        if let value = data.heightCm, profile.heightCm == 0 { profile.heightCm = value.rounded() }
        if let value = data.weightKg, profile.startWeightKg == 0 { profile.startWeightKg = (value * 10).rounded() / 10 }
        if let value = data.birthYear, profile.birthYear == 0 { profile.birthYear = value }
        if let value = data.sex { profile.sex = value }
        imported = data
        importing = false
    }

    private func finish() {
        if !apiKey.trimmingCharacters(in: .whitespaces).isEmpty { ai.setKey(apiKey) }
        context.insert(profile)
        try? context.save()
        // Only write the weight back when it's new, so an old Health reading isn't re-dated to today.
        if health.requested, profile.startWeightKg != roundedImportedWeight {
            Task { await health.saveBody(weightKg: profile.startWeightKg, bodyFatPercent: nil, leanMassKg: nil, date: Date()) }
        }
        NotificationService.scheduleDefaults()
        AppData.publishSnapshot(context, health: health)
        onFinish()
    }
}

/// Shows the calculated daily targets for a profile.
struct TargetsPreview: View {
    let profile: UserProfile
    var scans: [InBodyScan] = []
    @Environment(HealthService.self) private var health

    var body: some View {
        let targets = AppData.targets(profile: profile, scans: scans, health: health)
        Card(background: VColor.night, padding: 20) {
            VStack(alignment: .leading, spacing: 12) {
                BiText(ar: "احتياجك اليومي", en: "Your daily target", size: 16, color: VColor.nightText, enColor: VColor.nightMuted)
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(VFormat.number(targets.calories))
                        .font(VFont.readex(36, weight: 700))
                        .foregroundStyle(VColor.mint)
                    Text("سعرة · kcal").font(VFont.readex(13)).foregroundStyle(VColor.nightMuted)
                }
                HStack(spacing: 8) {
                    macro(value: targets.proteinGrams, ar: "بروتين", en: "Protein", color: VColor.mint)
                    macro(value: targets.carbsGrams, ar: "كارب", en: "Carbs", color: Color(hex: 0xF7C46C))
                    macro(value: targets.fatGrams, ar: "دهون", en: "Fat", color: Color(hex: 0x9DB1FF))
                }
                BiText(ar: "الأرقام دي محسوبة من وزنك ونشاطك وهدفك، وبتتحدث مع كل InBody.", en: "Calculated from your weight, activity and goal; updated with every InBody scan.", size: 11, weight: 400, color: VColor.nightMuted, enColor: VColor.nightMuted)
            }
        }
    }

    private func macro(value: Int, ar: String, en: String, color: Color) -> some View {
        VStack(spacing: 2) {
            Text("\(value) g").font(VFont.readex(18, weight: 600)).foregroundStyle(color)
            Text(ar).font(VFont.readex(11)).foregroundStyle(VColor.nightText)
            Text(en).font(VFont.readex(10)).foregroundStyle(VColor.nightMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color(hex: 0x1C2F29), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Shows the BMR in use and lets the user enter their own measured value.
struct MetabolismSection: View {
    @Bindable var profile: UserProfile
    var scans: [InBodyScan] = []
    @Environment(HealthService.self) private var health

    var body: some View {
        let calculated = AppData.calculatedBMR(profile: profile, scans: scans, health: health)
        Card {
            VStack(alignment: .leading, spacing: 12) {
                SectionTitle(ar: "معدل الحرق الأساسي (BMR)", en: "Basal metabolic rate (BMR)")
                BiText(
                    ar: "السعرات اللي جسمك بيحرقها وانت مرتاح. لو تعرف رقمك الحقيقي (من InBody أو دكتور) اكتبه هنا وهنحسب عليه.",
                    en: "Calories your body burns at rest. If you know your real number (InBody or a doctor), enter it and we'll use it.",
                    size: 12, weight: 400, color: VColor.inkSecondary
                )
                HStack {
                    Text("\(calculated.sourceAr) · \(calculated.sourceEn)").font(VFont.readex(11)).foregroundStyle(VColor.muted)
                    Spacer()
                    Text("\(VFormat.number(calculated.kcal)) kcal").font(VFont.readex(14, weight: 600)).foregroundStyle(VColor.inkSecondary)
                        .environment(\.layoutDirection, .leftToRight)
                }
                NumberField(ar: "الـ BMR بتاعي", en: "My BMR", value: $profile.manualBMR, unit: "kcal", digits: 0)
                if profile.manualBMR > 0 {
                    HStack {
                        Label {
                            BiCaption(ar: "بنستخدم رقمك", en: "Using your number", size: 11, color: VColor.primaryDark)
                        } icon: {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(VColor.primary)
                        }
                        Spacer()
                        Button("رجّع المحسوب · Use calculated") { profile.manualBMR = 0 }
                            .font(VFont.readex(12, weight: 600))
                    }
                    if profile.manualBMR < 900 || profile.manualBMR > 3500 {
                        BiText(ar: "الرقم ده غريب — اتأكد منه.", en: "That number looks unusual — please double-check.", size: 11, weight: 500, color: VColor.energy)
                    }
                }
            }
        }
    }
}
