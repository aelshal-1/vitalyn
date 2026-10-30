import SwiftUI
import SwiftData
import SceneKit
import VitalynCore

/// "My body": a 3D body shaped from your numbers (start, now, goal), the journey slider and progress photos.
struct BodyView: View {
    @Environment(HealthService.self) private var health
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @Query(sort: \ProgressPhoto.date) private var photos: [ProgressPhoto]

    @AppStorage("avatar-skin") private var skin = 2
    @AppStorage("avatar-long-hair") private var longHair = -1 // -1 = by sex

    @State private var journey: Double = 0.5
    @State private var comparing = false
    @State private var bodies: AvatarBodies?
    @State private var showCapture = false
    @State private var showStyle = false
    @State private var animation: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                stage
                modePicker
                measurementBar
                journeyCard
                Button { showCapture = true } label: {
                    BiLabel(ar: "صور تقدّم جديدة", en: "New progress photos", systemImage: "camera", size: 14)
                }
                .buttonStyle(PrimaryButtonStyle(color: VColor.night))
                comparison
                BiText(ar: "الجسم ده متشكّل من طولك ووزنك ونسبة الدهون (من InBody لو موجود) وسنّك. كل ما تسجّل قياس جديد بيتغيّر معاك.", en: "This body is shaped from your height, weight, body fat (from InBody when available) and age. It changes with every new measurement.", size: 12, weight: 400, color: VColor.inkSecondary)
            }
            .padding(20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle("جسمي · My body")
        .sheet(isPresented: $showCapture) { AvatarCaptureView() }
        .sheet(isPresented: $showStyle) { AvatarStyleSheet(skin: $skin, longHair: longHairBinding).presentationDetents([.height(320)]) }
        .task(id: statsKey) { await rebuild() }
    }

    // MARK: Body stats

    private var profile: UserProfile? { profiles.first }

    private var style: AvatarStyle {
        AvatarStyle(skin: skin, longHair: longHair == -1 ? profile?.sex == .female : longHair == 1)
    }

    private var longHairBinding: Binding<Bool> {
        Binding(get: { style.longHair }, set: { longHair = $0 ? 1 : 0 })
    }

    private var keyframes: (start: BodyStats, now: BodyStats, goal: BodyStats)? {
        guard let profile else { return nil }
        let first = scans.first
        let latest = scans.last
        let startWeight = first?.weightKg ?? profile.startWeightKg
        let nowWeight = AppData.currentWeight(profile: profile, scans: scans, health: health)
        let lean = latest?.effectiveLeanMassKg
        func stats(_ weight: Double, fat: Double?) -> BodyStats {
            BodyStats(sex: profile.sex, ageYears: profile.ageYears, heightCm: profile.heightCm, weightKg: weight, bodyFatPercent: fat)
        }
        // With a scan, assume lean mass is kept and weight changes are fat.
        let nowFat = lean.map { max(5, (nowWeight - $0) / nowWeight * 100) }
        let goalFat = lean.map { max(8, (profile.goalWeightKg - $0) / profile.goalWeightKg * 100) }
        return (stats(startWeight, fat: first?.effectiveBodyFatPercent), stats(nowWeight, fat: nowFat), stats(profile.goalWeightKg, fat: goalFat))
    }

    private var statsKey: String {
        guard let k = keyframes else { return "none" }
        return [k.start, k.now, k.goal].map { "\($0.sex)\($0.ageYears)-\($0.heightCm)-\($0.weightKg)-\($0.bodyFatPercent ?? -1)" }.joined(separator: "|")
    }

    private func rebuild() async {
        guard let k = keyframes else { bodies = nil; return }
        bodies = await AvatarBodies.make(start: k.start, now: k.now, goal: k.goal)
    }

    // MARK: Stage

    private var stage: some View {
        ZStack {
            VColor.ground
            if let bodies {
                BodyAvatarView(meshes: comparing ? [bodies.start, bodies.goal] : [bodies.at(journey)], style: style)
            } else if profile == nil {
                BiText(ar: "كمّل ملفك الأول", en: "Finish your profile first", size: 15, alignment: .center)
            } else {
                ProgressView()
            }
        }
        .frame(height: 480)
        .overlay(alignment: .topTrailing) {
            Button { showStyle = true } label: {
                Image(systemName: "paintpalette")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(VColor.ink)
                    .frame(width: 44, height: 44)
                    .background(VColor.surface, in: Circle())
                    .shadow(color: .black.opacity(0.08), radius: 6, y: 2)
            }
            .padding(12)
            .accessibilityLabel("شكل الأفاتار · Avatar style")
        }
        .overlay(alignment: .bottom) {
            VStack(spacing: 4) {
                if comparing {
                    HStack {
                        Text("البداية · Start").frame(maxWidth: .infinity)
                        Text("الهدف · Goal").frame(maxWidth: .infinity)
                    }
                    .font(VFont.readex(13, weight: 600))
                    .foregroundStyle(VColor.ink)
                    .environment(\.layoutDirection, .leftToRight)
                }
                Label("لفّ الموديل بصباعك · Drag to rotate", systemImage: "rotate.3d")
                    .font(VFont.readex(11))
                    .foregroundStyle(VColor.muted)
            }
            .padding(.bottom, 10)
        }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).stroke(VColor.line))
    }

    private var modePicker: some View {
        HStack(spacing: 8) {
            BiChip(ar: "البداية", en: "Start", selected: !comparing && journey == 0) { show(0) }
            BiChip(ar: "دلوقتي", en: "Now", selected: !comparing && journey == 0.5) { show(0.5) }
            BiChip(ar: "الهدف", en: "Goal", selected: !comparing && journey == 1) { show(1) }
            BiChip(ar: "قارن", en: "Compare", selected: comparing, dark: true) { comparing.toggle() }
        }
    }

    /// Morphs smoothly to a point on the journey.
    private func show(_ target: Double) {
        comparing = false
        animation?.cancel()
        let from = journey
        animation = Task { @MainActor in
            let steps = 18
            for step in 1...steps {
                try? await Task.sleep(for: .milliseconds(22))
                if Task.isCancelled { return }
                let x = Double(step) / Double(steps)
                journey = from + (target - from) * (x * x * (3 - 2 * x))
            }
            journey = target
        }
    }

    private var measurementBar: some View {
        let values = journeyValues
        return HStack(spacing: 8) {
            pill(ar: "الوزن", en: "Weight", value: "\(VFormat.decimal(values.weight)) kg", highlight: false)
            pill(ar: "الدهون", en: "Body fat", value: values.fat.map { "\(VFormat.decimal($0))%" } ?? "—", highlight: true)
            pill(ar: "العضلات", en: "Muscle", value: values.muscle.map { "\(VFormat.decimal($0)) kg" } ?? "—", highlight: false)
        }
    }

    private func pill(ar: String, en: String, value: String, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(ar) · \(en)").font(VFont.readex(10)).foregroundStyle(VColor.muted)
            Text(value).font(VFont.readex(15, weight: 600)).foregroundStyle(highlight ? VColor.primary : VColor.ink)
                .environment(\.layoutDirection, .leftToRight)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(VColor.line))
    }

    // MARK: Journey

    private var journeyValues: (weight: Double, fat: Double?, muscle: Double?) {
        let first = scans.first
        let latest = scans.last
        let profile = profiles.first
        let startWeight = first?.weightKg ?? profile?.startWeightKg ?? 0
        let nowWeight = AppData.currentWeight(profile: profile ?? UserProfile(), scans: scans, health: health)
        let goalWeight = profile?.goalWeightKg ?? nowWeight
        let weight = ProgressMath.journey(first: startWeight, now: nowWeight, goal: goalWeight, t: journey)

        var fat: Double?
        if let firstFat = first?.effectiveBodyFatPercent, let nowFat = latest?.effectiveBodyFatPercent, let lean = latest?.effectiveLeanMassKg {
            // At the goal, assume lean mass is kept and the difference is fat.
            let goalFat = max(8, (goalWeight - lean) / goalWeight * 100)
            fat = ProgressMath.journey(first: firstFat, now: nowFat, goal: goalFat, t: journey)
        }
        var muscle: Double?
        if let firstMuscle = first?.skeletalMuscleKg, let nowMuscle = latest?.skeletalMuscleKg {
            muscle = ProgressMath.journey(first: firstMuscle, now: nowMuscle, goal: nowMuscle, t: journey)
        }
        return (weight, fat, muscle)
    }

    private var journeyCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                BiText(ar: "رحلتك: البداية ← دلوقتي ← الهدف", en: "Your journey: start → now → goal", size: 14, weight: 500)
                Slider(value: Binding(get: { journey }, set: { animation?.cancel(); comparing = false; journey = $0 }), in: 0...1)
                    .tint(VColor.primary)
            }
        }
    }

    // MARK: Photos

    private var comparison: some View {
        let fronts = photos.filter { $0.kindRaw == PhotoKind.bodyFront.rawValue }
        return Group {
            if fronts.count > 1, let first = fronts.first, let last = fronts.last {
                Card {
                    VStack(alignment: .leading, spacing: 10) {
                        SectionTitle(ar: "قبل وبعد", en: "Before & after")
                        HStack(spacing: 10) {
                            photoColumn(first, ar: "البداية", en: "Start")
                            photoColumn(last, ar: "دلوقتي", en: "Now")
                        }
                    }
                }
            }
        }
    }

    private func photoColumn(_ photo: ProgressPhoto, ar: String, en: String) -> some View {
        VStack(spacing: 6) {
            if let data = photo.image, let image = ImageTools.thumbnail(data, maxDimension: 600) {
                Image(uiImage: image).resizable().scaledToFill()
                    .frame(height: 220).frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            Text("\(ar) · \(en) · \(photo.date.formatted(date: .abbreviated, time: .omitted))").font(VFont.readex(11)).foregroundStyle(VColor.muted)
        }
    }
}

/// Skin tone and hair for the generated body.
struct AvatarStyleSheet: View {
    @Binding var skin: Int
    @Binding var longHair: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            SectionTitle(ar: "شكل الأفاتار", en: "Avatar style")
            BiCaption(ar: "لون البشرة", en: "Skin tone")
            HStack(spacing: 12) {
                ForEach(AvatarStyle.skinTones.indices, id: \.self) { i in
                    Button { skin = i } label: {
                        Circle()
                            .fill(Color(hex: AvatarStyle.skinTones[i]))
                            .frame(width: 42, height: 42)
                            .overlay(Circle().stroke(skin == i ? VColor.primary : VColor.line, lineWidth: skin == i ? 3 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Skin tone \(i + 1)")
                    .accessibilityAddTraits(skin == i ? .isSelected : [])
                }
            }
            BiCaption(ar: "الشعر", en: "Hair")
            HStack(spacing: 8) {
                BiChip(ar: "قصير", en: "Short", selected: !longHair) { longHair = false }
                BiChip(ar: "طويل", en: "Long", selected: longHair) { longHair = true }
            }
            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VColor.ground)
    }
}

/// Guided capture: 3 selfies and 2 body photos.
struct AvatarCaptureView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var index = 0
    @State private var showCamera = false
    @State private var checking = false
    @State private var problem: String?

    private let steps: [(PhotoKind, String, String, String)] = [
        (.faceFront, "بص للكاميرا على طول", "Look straight at the camera", "face.smiling"),
        (.faceRight, "لف وشك شوية لليمين", "Turn your head a little right", "arrow.turn.up.right"),
        (.faceLeft, "لف وشك شوية للشمال", "Turn your head a little left", "arrow.turn.up.left"),
        (.bodyFront, "اقف مفرود ودراعاتك جنبك — جسمك كله في الصورة", "Stand straight, arms by your side — full body in frame", "figure.stand"),
        (.bodySide, "لف جنب", "Turn to the side", "figure.walk")
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if index < steps.count {
                    let step = steps[index]
                    HStack(spacing: 6) {
                        ForEach(0..<steps.count, id: \.self) { i in
                            Capsule().fill(i < index ? VColor.mint : (i == index ? VColor.nightText : Color(hex: 0x1F322B))).frame(height: 4)
                        }
                    }
                    Spacer()
                    Image(systemName: step.3).font(.system(size: 70)).foregroundStyle(VColor.mint)
                    BiText(ar: step.1, en: step.2, size: 20, color: VColor.nightText, enColor: VColor.nightMuted, alignment: .center)
                    Text("\(index + 1) / \(steps.count)").font(VFont.readex(13)).foregroundStyle(VColor.nightMuted)
                    Spacer()
                    Button { showCamera = true } label: {
                        BiLabel(ar: "صوّر", en: "Capture", systemImage: "camera")
                    }
                    .buttonStyle(PrimaryButtonStyle(color: VColor.primary))
                } else {
                    Spacer()
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 70)).foregroundStyle(VColor.mint)
                    BiText(ar: "الصور اتحفظت", en: "Photos saved", size: 22, color: VColor.nightText, enColor: VColor.nightMuted, alignment: .center)
                    BiText(ar: "هتشوف المقارنة قبل وبعد في شاشة جسمي.", en: "See the before & after on the My body screen.", size: 14, weight: 400, color: VColor.nightMuted, enColor: VColor.nightMuted, alignment: .center)
                    Spacer()
                    Button { dismiss() } label: { BiLabel(ar: "تمام", en: "Done") }
                        .buttonStyle(PrimaryButtonStyle())
                }
                if checking {
                    ProgressView().tint(VColor.mint)
                }
                if let problem {
                    Text(problem)
                        .font(VFont.readex(13, weight: 500))
                        .foregroundStyle(Color(hex: 0xFFB4A0))
                        .multilineTextAlignment(.center)
                        .padding(12)
                        .frame(maxWidth: .infinity)
                        .background(Color(hex: 0x2A1A14), in: RoundedRectangle(cornerRadius: 14))
                }
                BiText(ar: "لبس ضيق وإضاءة كويسة بيدّوا نتيجة أدق. الصور بتفضل على أجهزتك.", en: "Fitted clothes and good light give better results. Photos stay on your devices.", size: 11, weight: 400, color: VColor.nightMuted, enColor: VColor.nightMuted, alignment: .center)
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(hex: 0x0B1512))
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() }.foregroundStyle(VColor.nightText) } }
            .sheet(isPresented: $showCamera) {
                CameraPicker(device: .front) { image in save(image) }.ignoresSafeArea()
            }
        }
    }

    private func save(_ image: UIImage) {
        guard index < steps.count else { return }
        let kind = steps[index].0
        checking = true
        problem = nil
        Task {
            let result = await PhotoCheck.check(image, for: kind)
            checking = false
            if case let .problem(ar, en) = result {
                problem = "\(ar)\n\(en)"
                return
            }
            store(image, kind: kind)
        }
    }

    private func store(_ image: UIImage, kind: PhotoKind) {
        let photo = ProgressPhoto()
        photo.kindRaw = kind.rawValue
        photo.image = ImageTools.jpeg(image, maxDimension: 1600, quality: 0.8)
        context.insert(photo)
        try? context.save()
        index += 1
    }
}
