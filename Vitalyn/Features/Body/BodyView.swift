import SwiftUI
import SwiftData
import SceneKit
import UniformTypeIdentifiers
import VitalynCore

/// "My body": the 3D avatar (once created/imported), measurements, journey slider and progress photos.
struct BodyView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @Query(sort: \AvatarAsset.date, order: .reverse) private var avatars: [AvatarAsset]
    @Query(sort: \ProgressPhoto.date) private var photos: [ProgressPhoto]

    @State private var journey: Double = 0.5
    @State private var showCapture = false
    @State private var showImporter = false
    @State private var importError: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                stage
                journeyCard
                HStack(spacing: 10) {
                    Button { showCapture = true } label: {
                        BiLabel(ar: "صور جديدة", en: "New photos", systemImage: "camera", size: 14)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    Button { showImporter = true } label: {
                        BiLabel(ar: "استورد أفاتار", en: "Import avatar", systemImage: "cube", size: 14)
                    }
                    .buttonStyle(PrimaryButtonStyle(color: VColor.night))
                }
                if let importError { ErrorBanner(message: importError) }
                comparison
                BiText(ar: "الأفاتار الواقعي بوشّك بيتعمل من صورك (3 سيلفي + صورتين لجسمك) وبيتظبط بأرقام InBody. تقدر تستورد ملف .usdz من خدمة أفاتار لحد ما نربطها جوه التطبيق.", en: "The realistic avatar is built from your photos (3 selfies + 2 body photos) and shaped by your InBody numbers. You can import a .usdz from an avatar service until it’s built into the app.", size: 12, weight: 400, color: VColor.inkSecondary)
            }
            .padding(20)
            .frame(maxWidth: 720)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle("جسمي · My body")
        .sheet(isPresented: $showCapture) { AvatarCaptureView() }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.usdz, UTType(filenameExtension: "reality") ?? .usdz]) { result in
            importAvatar(result)
        }
    }

    // MARK: Stage

    private var stage: some View {
        ZStack(alignment: .bottom) {
            Color(hex: 0x12201B)
            if let avatar = avatars.first, let url = avatarURL(avatar) {
                AvatarSceneView(url: url)
            } else if let front = latestPhoto(.bodyFront), let image = UIImage(data: front) {
                Image(uiImage: image).resizable().scaledToFit().padding(.vertical, 20)
            } else {
                VStack(spacing: 10) {
                    Image(systemName: "cube.transparent").font(.system(size: 44)).foregroundStyle(VColor.mint)
                    BiText(ar: "الأفاتار بتاعك", en: "Your 3D avatar", size: 15, color: VColor.nightText, enColor: VColor.nightMuted, alignment: .center)
                    BiText(ar: "ابدأ بصور جديدة", en: "Start with new photos", size: 12, weight: 400, color: VColor.nightMuted, enColor: VColor.nightMuted, alignment: .center)
                }
                .frame(maxHeight: .infinity)
            }
            measurementBar
        }
        .frame(height: 460)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    private var measurementBar: some View {
        let values = journeyValues
        return HStack(spacing: 8) {
            pill(ar: "الوزن", en: "Weight", value: "\(VFormat.decimal(values.weight)) kg", highlight: false)
            pill(ar: "الدهون", en: "Body fat", value: values.fat.map { "\(VFormat.decimal($0))%" } ?? "—", highlight: true)
            pill(ar: "العضلات", en: "Muscle", value: values.muscle.map { "\(VFormat.decimal($0)) kg" } ?? "—", highlight: false)
        }
        .padding(12)
    }

    private func pill(ar: String, en: String, value: String, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(ar) · \(en)").font(VFont.readex(10)).foregroundStyle(VColor.nightMuted)
            Text(value).font(VFont.readex(15, weight: 600)).foregroundStyle(highlight ? VColor.mint : VColor.nightText)
                .environment(\.layoutDirection, .leftToRight)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0x1C2F29), in: RoundedRectangle(cornerRadius: 12))
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
                BiText(ar: "رحلتك: أول قياس ← دلوقتي ← الهدف", en: "Your journey: first scan → now → goal", size: 14, weight: 500)
                Slider(value: $journey, in: 0...1)
                    .tint(VColor.primary)
                HStack {
                    Button("أول قياس · First") { withAnimation { journey = 0 } }
                    Spacer()
                    Button("دلوقتي · Now") { withAnimation { journey = 0.5 } }
                    Spacer()
                    Button("الهدف · Goal") { withAnimation { journey = 1 } }
                }
                .font(VFont.readex(12, weight: 600))
            }
        }
    }

    // MARK: Photos

    private func latestPhoto(_ kind: PhotoKind) -> Data? {
        photos.last { $0.kindRaw == kind.rawValue }?.image
    }

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

    // MARK: Avatar files

    private func avatarURL(_ avatar: AvatarAsset) -> URL? {
        guard let data = avatar.file else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(avatar.fileName)
        if !FileManager.default.fileExists(atPath: url.path) {
            try? data.write(to: url)
        }
        return url
    }

    private func importAvatar(_ result: Result<URL, Error>) {
        importError = nil
        guard case let .success(url) = result else { return }
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            importError = "مقدرتش أقرا الملف · Couldn't read the file"
            return
        }
        let asset = AvatarAsset()
        asset.fileName = "avatar-\(UUID().uuidString).\(url.pathExtension.isEmpty ? "usdz" : url.pathExtension)"
        asset.file = data
        context.insert(asset)
        try? context.save()
    }
}

/// Rotatable, zoomable 3D view of the avatar file.
struct AvatarSceneView: View {
    let url: URL

    var body: some View {
        if let scene = try? SCNScene(url: url) {
            SceneView(scene: scene, options: [.allowsCameraControl, .autoenablesDefaultLighting])
                .background(Color(hex: 0x12201B))
                .accessibilityLabel("الأفاتار 3D — 3D avatar")
        } else {
            BiText(ar: "الملف ده مش مدعوم", en: "This file isn't supported", size: 14, color: VColor.nightText, enColor: VColor.nightMuted, alignment: .center)
        }
    }
}

/// Guided capture: 3 selfies and 2 body photos.
struct AvatarCaptureView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @State private var index = 0
    @State private var showCamera = false

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
                BiText(ar: "لبس ضيق وإضاءة كويسة بيدّوا نتيجة أدق. الصور بتفضل على أجهزتك.", en: "Fitted clothes and good light give better results. Photos stay on your devices.", size: 11, weight: 400, color: VColor.nightMuted, enColor: VColor.nightMuted, alignment: .center)
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(hex: 0x0B1512))
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() }.foregroundStyle(VColor.nightText) } }
            .sheet(isPresented: $showCamera) {
                CameraPicker { image in save(image) }.ignoresSafeArea()
            }
        }
    }

    private func save(_ image: UIImage) {
        guard index < steps.count else { return }
        let photo = ProgressPhoto()
        photo.kindRaw = steps[index].0.rawValue
        photo.image = ImageTools.jpeg(image, maxDimension: 1600, quality: 0.8)
        context.insert(photo)
        try? context.save()
        index += 1
    }
}
