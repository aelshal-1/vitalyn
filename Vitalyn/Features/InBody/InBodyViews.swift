import SwiftUI
import SwiftData
import Charts
import UniformTypeIdentifiers
import VitalynCore

/// List of scans with the weight trend and an "add scan" entry point.
struct InBodyListView: View {
    @Query(sort: \InBodyScan.date, order: .reverse) private var scans: [InBodyScan]
    @State private var showCapture = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Button { showCapture = true } label: {
                    BiLabel(ar: "ضيف InBody جديد", en: "Add a new InBody", systemImage: "doc.viewfinder")
                }
                .buttonStyle(PrimaryButtonStyle())

                if scans.count > 1 {
                    Card {
                        VStack(alignment: .leading, spacing: 10) {
                            SectionTitle(ar: "الوزن عبر القياسات", en: "Weight across scans")
                            Chart(scans.reversed()) { scan in
                                LineMark(x: .value("Date", scan.date), y: .value("kg", scan.weightKg))
                                    .foregroundStyle(VColor.primary)
                                PointMark(x: .value("Date", scan.date), y: .value("kg", scan.weightKg))
                                    .foregroundStyle(VColor.primary)
                            }
                            .chartYScale(domain: .automatic(includesZero: false))
                            .frame(height: 160)
                            .environment(\.layoutDirection, .leftToRight)
                        }
                    }
                }

                if scans.isEmpty {
                    BiText(ar: "لسه مفيش قياسات. صوّر ورقة الـ InBody أو ارفع الـ PDF.", en: "No scans yet. Snap your InBody sheet or upload the PDF.", size: 14, weight: 400, color: VColor.inkSecondary)
                }

                ForEach(Array(scans.enumerated()), id: \.element.persistentModelID) { index, scan in
                    NavigationLink {
                        InBodyDetailView(scan: scan, previous: index + 1 < scans.count ? scans[index + 1] : nil)
                    } label: {
                        ScanRow(scan: scan, previous: index + 1 < scans.count ? scans[index + 1] : nil)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(20)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle("InBody")
        .sheet(isPresented: $showCapture) {
            InBodyCaptureView()
        }
    }
}

private struct ScanRow: View {
    let scan: InBodyScan
    let previous: InBodyScan?

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.text.magnifyingglass")
                .foregroundStyle(VColor.primary)
                .frame(width: 44, height: 44)
                .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text(scan.date.formatted(date: .abbreviated, time: .omitted))
                    .font(VFont.readex(14, weight: 600))
                Text("\(VFormat.decimal(scan.weightKg)) kg · \(scan.effectiveBodyFatPercent.map { VFormat.decimal($0) + "%" } ?? "—")")
                    .font(VFont.readex(12))
                    .foregroundStyle(VColor.inkSecondary)
                    .environment(\.layoutDirection, .leftToRight)
            }
            Spacer()
            if let previous {
                let delta = scan.weightKg - previous.weightKg
                Text((delta > 0 ? "+" : "") + VFormat.decimal(delta) + " kg")
                    .font(VFont.readex(13, weight: 600))
                    .foregroundStyle(delta <= 0 ? VColor.primaryDark : VColor.energy)
                    .environment(\.layoutDirection, .leftToRight)
            }
        }
        .padding(12)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

struct InBodyDetailView: View {
    let scan: InBodyScan
    let previous: InBodyScan?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                MetricsGrid(metrics: InBodyMetric.list(for: scan, previous: previous))
                SegmentalCard(scan: scan)
                if !scan.summaryAr.isEmpty {
                    Card(background: VColor.night, padding: 20) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(scan.summaryAr).font(VFont.readex(14)).foregroundStyle(VColor.nightText)
                            Text(scan.summaryEn).font(VFont.readex(12)).foregroundStyle(VColor.nightMuted)
                        }
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
        .navigationTitle(scan.date.formatted(date: .abbreviated, time: .omitted))
    }
}

struct InBodyMetric: Identifiable {
    var id: String { en }
    let ar: String
    let en: String
    let value: String
    let unit: String
    let delta: String?
    let deltaGood: Bool
    let lowConfidence: Bool

    static func list(for scan: InBodyScan, previous: InBodyScan?, lowConfidence: Set<String> = []) -> [InBodyMetric] {
        func item(_ ar: String, _ en: String, _ key: String, _ value: Double?, _ old: Double?, unit: String, lowerIsBetter: Bool) -> InBodyMetric? {
            guard let value else { return nil }
            var deltaText: String?
            var good = true
            if let old {
                let delta = value - old
                deltaText = (delta > 0 ? "▲ " : "▼ ") + VFormat.decimal(abs(delta))
                good = lowerIsBetter ? delta <= 0 : delta >= 0
            }
            return InBodyMetric(ar: ar, en: en, value: VFormat.decimal(value), unit: unit, delta: deltaText, deltaGood: good, lowConfidence: lowConfidence.contains(key))
        }
        return [
            item("الوزن", "Weight", "weightKg", scan.weightKg, previous?.weightKg, unit: "kg", lowerIsBetter: true),
            item("الكتلة العضلية", "Skeletal muscle", "skeletalMuscleKg", scan.skeletalMuscleKg, previous?.skeletalMuscleKg, unit: "kg", lowerIsBetter: false),
            item("نسبة الدهون", "Body fat %", "bodyFatPercent", scan.effectiveBodyFatPercent, previous?.effectiveBodyFatPercent, unit: "%", lowerIsBetter: true),
            item("كتلة الدهون", "Fat mass", "bodyFatKg", scan.bodyFatKg, previous?.bodyFatKg, unit: "kg", lowerIsBetter: true),
            item("دهون حشوية", "Visceral fat", "visceralFatLevel", scan.visceralFatLevel, previous?.visceralFatLevel, unit: "level", lowerIsBetter: true),
            item("معدل الحرق", "BMR", "bmrKcal", scan.bmrKcal, previous?.bmrKcal, unit: "kcal", lowerIsBetter: false),
            item("مية الجسم", "Body water", "totalBodyWaterL", scan.totalBodyWaterL, previous?.totalBodyWaterL, unit: "L", lowerIsBetter: false),
            item("InBody Score", "InBody score", "inbodyScore", scan.inbodyScore, previous?.inbodyScore, unit: "/100", lowerIsBetter: false)
        ].compactMap { $0 }
    }
}

struct MetricsGrid: View {
    let metrics: [InBodyMetric]

    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(metrics) { metric in
                VStack(alignment: .leading, spacing: 4) {
                    BiText(ar: metric.ar, en: metric.en, size: 12, weight: 400, color: VColor.inkSecondary, enSize: 11)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(metric.value).font(VFont.readex(22, weight: 600))
                        Text(metric.unit).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                    }
                    .environment(\.layoutDirection, .leftToRight)
                    if let delta = metric.delta {
                        Text(delta).font(VFont.readex(12, weight: 500))
                            .foregroundStyle(metric.deltaGood ? VColor.primaryDark : VColor.energy)
                    }
                    if metric.lowConfidence {
                        BiCaption(ar: "ثقة متوسطة — اتأكد", en: "Check this", size: 10, color: Color(hex: 0x8A5A00))
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Color(hex: 0xFFF3D6), in: Capsule())
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(metric.lowConfidence ? Color(hex: 0xF2C14E) : .clear, lineWidth: 1.5))
            }
        }
    }
}

struct SegmentalCard: View {
    let scan: InBodyScan

    var body: some View {
        let rows: [(String, String, Double?)] = [
            ("ذراع يمين", "Right arm", scan.rightArmKg),
            ("ذراع شمال", "Left arm", scan.leftArmKg),
            ("الجذع", "Trunk", scan.trunkKg),
            ("رجل يمين", "Right leg", scan.rightLegKg),
            ("رجل شمال", "Left leg", scan.leftLegKg)
        ]
        let maxValue = rows.compactMap(\.2).max() ?? 1
        if rows.contains(where: { $0.2 != nil }) {
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    SectionTitle(ar: "العضلات حسب المنطقة", en: "Segmental lean mass")
                    ForEach(rows, id: \.1) { row in
                        if let value = row.2 {
                            HStack(spacing: 10) {
                                BiText(ar: row.0, en: row.1, size: 13, weight: 400, enSize: 10)
                                    .frame(width: 100, alignment: .leading)
                                GeometryReader { proxy in
                                    ZStack(alignment: .leading) {
                                        Capsule().fill(VColor.track)
                                        Capsule().fill(VColor.primary).frame(width: proxy.size.width * value / maxValue)
                                    }
                                }
                                .frame(height: 8)
                                Text("\(VFormat.decimal(value)) kg").font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary)
                                    .frame(width: 60, alignment: .trailing)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Capture and review

struct InBodyCaptureView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai
    @Environment(HealthService.self) private var health

    @State private var fileData: Data?
    @State private var isPDF = false
    @State private var previewImage: UIImage?
    @State private var working = false
    @State private var error: String?
    @State private var extraction: InBodyExtraction?
    @State private var showFileImporter = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let extraction {
                        InBodyReviewView(extraction: extraction, onSave: save)
                    } else {
                        BiText(ar: "صوّر ورقة الـ InBody أو ارفع الـ PDF", en: "Snap your InBody sheet or upload the PDF", size: 18)
                        BiText(ar: "Claude هيقرا كل الأرقام وانت تراجعها قبل الحفظ.", en: "Claude reads every value; you review before saving.", size: 13, weight: 400, color: VColor.inkSecondary)
                        if let previewImage {
                            Image(uiImage: previewImage)
                                .resizable()
                                .scaledToFit()
                                .frame(maxHeight: 280)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        } else if isPDF {
                            Label("PDF", systemImage: "doc.richtext").font(VFont.readex(16, weight: 600))
                        }
                        ImageSourceButtons { image in
                            previewImage = image
                            fileData = ImageTools.jpeg(image, maxDimension: 2400, quality: 0.85)
                            isPDF = false
                        }
                        Button { showFileImporter = true } label: {
                            BiLabel(ar: "ارفع PDF", en: "Upload PDF", systemImage: "doc.badge.arrow.up")
                        }
                        .buttonStyle(SecondaryButtonStyle())

                        if working {
                            WorkingView(ar: "بقرا الأرقام…", en: "Reading the values…")
                        } else if fileData != nil {
                            Button(action: analyze) {
                                BiLabel(ar: "حلّل", en: "Analyze", systemImage: "sparkles")
                            }
                            .buttonStyle(PrimaryButtonStyle(color: VColor.night))
                        }
                    }
                    if let error { ErrorBanner(message: error) }
                }
                .padding(20)
            }
            .background(VColor.ground)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("إلغاء · Cancel") { dismiss() }
                }
            }
            .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.pdf, .image]) { result in
                guard case let .success(url) = result else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                guard let data = try? Data(contentsOf: url) else { return }
                if UTType(filenameExtension: url.pathExtension)?.conforms(to: .pdf) == true {
                    fileData = data
                    isPDF = true
                    previewImage = nil
                } else if let image = UIImage(data: data) {
                    previewImage = image
                    fileData = ImageTools.jpeg(image, maxDimension: 2400, quality: 0.85)
                    isPDF = false
                }
            }
        }
    }

    private func analyze() {
        guard let fileData else { return }
        working = true
        error = nil
        let content: ClaudeContent = isPDF ? .pdf(fileData) : .image(fileData, mediaType: "image/jpeg")
        let request = AITasks.inBody(file: content, context: AppData.userContext(context, health: health))
        Task {
            do {
                extraction = try await ai.run(InBodyExtraction.self, request)
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    private func save(_ draft: InBodyExtraction, date: Date) {
        let scan = InBodyScan()
        scan.date = date
        scan.weightKg = draft.weightKg
        scan.skeletalMuscleKg = draft.skeletalMuscleKg
        scan.bodyFatKg = draft.bodyFatKg
        scan.bodyFatPercent = draft.bodyFatPercent
        scan.leanMassKg = draft.leanMassKg
        scan.visceralFatLevel = draft.visceralFatLevel
        scan.bmrKcal = draft.bmrKcal
        scan.totalBodyWaterL = draft.totalBodyWaterL
        scan.inbodyScore = draft.inbodyScore
        scan.rightArmKg = draft.segmentalLean.rightArmKg
        scan.leftArmKg = draft.segmentalLean.leftArmKg
        scan.trunkKg = draft.segmentalLean.trunkKg
        scan.rightLegKg = draft.segmentalLean.rightLegKg
        scan.leftLegKg = draft.segmentalLean.leftLegKg
        scan.summaryAr = draft.summaryAr
        scan.summaryEn = draft.summaryEn
        scan.sourceFile = fileData
        scan.sourceIsPDF = isPDF
        context.insert(scan)
        if let profile = AppData.profile(context), let height = draft.heightCm, height > 100 {
            profile.heightCm = height
        }
        try? context.save()
        Task {
            await health.saveBody(weightKg: scan.weightKg, bodyFatPercent: scan.effectiveBodyFatPercent, leanMassKg: scan.effectiveLeanMassKg, date: date)
        }
        AppData.publishSnapshot(context, health: health)
        dismiss()
    }
}

/// Editable review of what Claude read from the sheet.
struct InBodyReviewView: View {
    @State var extraction: InBodyExtraction
    @State private var date = Date()
    let onSave: (InBodyExtraction, Date) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(VColor.primary)
                BiText(ar: "راجع الأرقام وصلّح أي حاجة قبل الحفظ", en: "Review the values and fix anything before saving", size: 14, weight: 500, color: VColor.primaryDark)
            }
            Card {
                VStack(spacing: 14) {
                    DatePicker(selection: $date, displayedComponents: .date) {
                        BiText(ar: "تاريخ القياس", en: "Scan date", size: 14, weight: 500)
                    }
                    NumberField(ar: "الوزن", en: "Weight", value: $extraction.weightKg, unit: "kg")
                    optional("الكتلة العضلية", "Skeletal muscle", \.skeletalMuscleKg, "kg", key: "skeletalMuscleKg")
                    optional("كتلة الدهون", "Fat mass", \.bodyFatKg, "kg", key: "bodyFatKg")
                    optional("نسبة الدهون", "Body fat", \.bodyFatPercent, "%", key: "bodyFatPercent")
                    optional("دهون حشوية", "Visceral fat", \.visceralFatLevel, "", key: "visceralFatLevel")
                    optional("معدل الحرق BMR", "BMR", \.bmrKcal, "kcal", key: "bmrKcal")
                    optional("مية الجسم", "Body water", \.totalBodyWaterL, "L", key: "totalBodyWaterL")
                }
            }
            Card(background: VColor.night, padding: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(extraction.summaryAr).font(VFont.readex(14)).foregroundStyle(VColor.nightText)
                    Text(extraction.summaryEn).font(VFont.readex(12)).foregroundStyle(VColor.nightMuted)
                }
            }
            Button {
                onSave(extraction, date)
            } label: {
                BiLabel(ar: "احفظ وحدّث أهدافي", en: "Save & update my targets", systemImage: "checkmark")
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        .onAppear {
            if let text = extraction.scanDate, let parsed = try? Date(text, strategy: .iso8601.year().month().day()) {
                date = parsed
            }
        }
    }

    private func optional(_ ar: String, _ en: String, _ keyPath: WritableKeyPath<InBodyExtraction, Double?>, _ unit: String, key: String) -> some View {
        let binding = Binding<Double>(
            get: { extraction[keyPath: keyPath] ?? 0 },
            set: { extraction[keyPath: keyPath] = $0 == 0 ? nil : $0 }
        )
        return NumberField(ar: ar, en: en, value: binding, unit: unit)
            .padding(6)
            .background(extraction.lowConfidenceFields.contains(key) ? Color(hex: 0xFFF3D6) : .clear, in: RoundedRectangle(cornerRadius: 10))
    }
}
