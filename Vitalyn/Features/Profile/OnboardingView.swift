import SwiftUI
import SwiftData
import VitalynCore

/// First-launch setup: about you → goal → health → connections.
struct OnboardingView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai

    @State private var profile = UserProfile()
    @State private var step = 0
    @State private var apiKey = ""
    @State private var prepared = false
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
                        BasicsSection(profile: profile)
                    case 1:
                        GoalSection(profile: profile)
                            .onAppear {
                                if profile.goalWeightKg == 0 {
                                    profile.goalWeightKg = NutritionCalculator.idealWeight(heightCm: profile.heightCm, ageYears: profile.ageYears, sex: profile.sex, currentWeightKg: profile.startWeightKg).goalKg
                                }
                            }
                    case 2:
                        HealthSection(profile: profile)
                    default:
                        connections
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
            let hints = health.profileHints()
            if let sex = hints.sex { profile.sex = sex }
            if let year = hints.birthYear { profile.birthYear = year }
        }
    }

    /// What the current step still needs before Next is enabled.
    private var missing: [(ar: String, en: String)] {
        var items: [(ar: String, en: String)] = []
        switch step {
        case 0:
            if profile.name.trimmingCharacters(in: .whitespaces).isEmpty { items.append(("اكتب اسمك", "Enter your name")) }
            if !(1930...2012).contains(profile.birthYear) { items.append(("اختار سنة ميلادك", "Choose your birth year")) }
            if !(120...230).contains(profile.heightCm) { items.append(("اكتب طولك (120–230 سم)", "Enter your height (120–230 cm)")) }
            if !(30...300).contains(profile.startWeightKg) { items.append(("اكتب وزنك (30–300 كجم)", "Enter your weight (30–300 kg)")) }
        case 1:
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
                        Task { await health.requestAuthorization() }
                    } label: {
                        BiLabel(ar: health.requested ? "متصل" : "اربط Apple Health", en: health.requested ? "Connected" : "Connect Apple Health", systemImage: health.requested ? "checkmark.circle.fill" : "heart.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(color: health.requested ? VColor.primaryDark : Color(hex: 0xC2264B)))
                    .disabled(!health.isAvailable)
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
            TargetsPreview(profile: profile)
        }
    }

    private func finish() {
        if !apiKey.trimmingCharacters(in: .whitespaces).isEmpty { ai.setKey(apiKey) }
        context.insert(profile)
        try? context.save()
        if health.requested {
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
