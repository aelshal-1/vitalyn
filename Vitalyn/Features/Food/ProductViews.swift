import SwiftUI
import SwiftData
import VisionKit
import VitalynCore

enum ProductCategory: String, CaseIterable {
    case all, dairy, grains, protein, snacks, drinks, other

    var ar: String {
        switch self {
        case .all: "الكل"
        case .dairy: "ألبان"
        case .grains: "حبوب وعيش"
        case .protein: "بروتين"
        case .snacks: "سناكس"
        case .drinks: "مشروبات"
        case .other: "تاني"
        }
    }

    var en: String {
        switch self {
        case .all: "All"
        case .dairy: "Dairy"
        case .grains: "Grains"
        case .protein: "Protein"
        case .snacks: "Snacks"
        case .drinks: "Drinks"
        case .other: "Other"
        }
    }
}

struct ProductsSection: View {
    @Query(sort: \FoodProduct.createdAt, order: .reverse) private var products: [FoodProduct]
    @State private var search = ""
    @State private var category: ProductCategory = .all
    @State private var showLabelCapture = false
    @State private var showScanner = false
    @State private var editing: FoodProduct?
    @State private var logging: FoodProduct?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Button { showScanner = true } label: {
                    BiLabel(ar: "امسح باركود", en: "Scan barcode", systemImage: "barcode.viewfinder", size: 14)
                }
                .buttonStyle(PrimaryButtonStyle(color: VColor.night))
                Button { showLabelCapture = true } label: {
                    BiLabel(ar: "صوّر الليبل", en: "Snap label", systemImage: "camera", size: 14)
                }
                .buttonStyle(PrimaryButtonStyle())
            }

            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(VColor.muted)
                TextField("ابحث باسم المنتج أو البراند · Search", text: $search)
                    .font(VFont.readex(14))
            }
            .padding(12)
            .background(VColor.surface, in: RoundedRectangle(cornerRadius: 14))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ProductCategory.allCases, id: \.self) { item in
                        BiChip(ar: item.ar, en: item.en, selected: category == item, dark: true) { category = item }
                    }
                }
            }

            if filtered.isEmpty {
                BiText(ar: "لسه مفيش منتجات. صوّر الليبل وجدول القيم الغذائية لأي منتج بتستخدمه.", en: "No products yet. Snap the label and nutrition table of anything you use.", size: 13, weight: 400, color: VColor.inkSecondary)
                    .padding(.vertical, 8)
            }

            VStack(spacing: 0) {
                ForEach(filtered) { product in
                    ProductRow(product: product)
                        .contentShape(Rectangle())
                        .onTapGesture { logging = product }
                        .contextMenu {
                            Button("سجّل أكلة · Log") { logging = product }
                            Button("عدّل · Edit") { editing = product }
                        }
                    Divider()
                }
            }
            .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20))
        }
        .sheet(isPresented: $showLabelCapture) { ProductCaptureView(barcode: nil) }
        .sheet(isPresented: $showScanner) { BarcodeFlowView() }
        .sheet(item: $editing) { product in ProductEditView(product: product) }
        .sheet(item: $logging) { product in MealCaptureView(input: .product(product)) }
    }

    private var filtered: [FoodProduct] {
        products.filter { product in
            (category == .all || product.categoryRaw == category.rawValue) &&
            (search.isEmpty || "\(product.nameAr) \(product.nameEn) \(product.brand) \(product.barcode)".localizedCaseInsensitiveContains(search))
        }
    }
}

struct ProductRow: View {
    let product: FoodProduct

    var body: some View {
        HStack(spacing: 12) {
            Group {
                if let image = ImageTools.thumbnail(product.photo, maxDimension: 120) {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    Image(systemName: "shippingbox").foregroundStyle(VColor.primary)
                }
            }
            .frame(width: 44, height: 44)
            .background(VColor.primarySoft)
            .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 1) {
                Text(product.nameAr).font(VFont.readex(14, weight: 500))
                Text(product.nameEn + (product.brand.isEmpty ? "" : " · \(product.brand)"))
                    .font(VFont.readex(11)).foregroundStyle(VColor.inkSecondary)
                Text("100 g: \(Int(product.kcalPer100)) kcal · P \(VFormat.decimal(product.proteinPer100)) g")
                    .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                    .environment(\.layoutDirection, .leftToRight)
            }
            Spacer()
            Image(systemName: product.barcode.isEmpty ? "doc.text" : "barcode")
                .foregroundStyle(VColor.muted)
        }
        .padding(12)
    }
}

/// Snap the front and the nutrition table; Claude reads them into a product.
struct ProductCaptureView: View {
    let barcode: String?
    var prefilled: ProductExtraction?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai

    @State private var photos: [UIImage] = []
    @State private var extraction: ProductExtraction?
    @State private var working = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let extraction {
                        ProductForm(extraction: extraction, photo: photos.first) { result, category in
                            save(result, category: category)
                        }
                    } else {
                        BiText(ar: "صوّر واجهة المنتج وجدول القيم الغذائية", en: "Snap the front and the nutrition table", size: 18)
                        BiText(ar: "صورة أو اتنين أو تلاتة — Claude هيقرا الاسم والقيم ويحفظهم.", en: "One to three photos — Claude reads the name and values and saves them.", size: 13, weight: 400, color: VColor.inkSecondary)
                        if !photos.isEmpty {
                            ScrollView(.horizontal) {
                                HStack {
                                    ForEach(photos.indices, id: \.self) { index in
                                        Image(uiImage: photos[index]).resizable().scaledToFill()
                                            .frame(width: 110, height: 140)
                                            .clipShape(RoundedRectangle(cornerRadius: 14))
                                    }
                                }
                            }
                        }
                        if photos.count < 3 {
                            ImageSourceButtons(cameraAr: photos.isEmpty ? "صوّر الواجهة" : "صوّر الجدول", cameraEn: photos.isEmpty ? "Front" : "Table") { image in
                                photos.append(image)
                            }
                        }
                        if working {
                            WorkingView(ar: "بقرا الليبل…", en: "Reading the label…")
                        } else if !photos.isEmpty {
                            Button(action: analyze) {
                                BiLabel(ar: "اقرا الليبل", en: "Read label", systemImage: "sparkles")
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
                ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() } }
            }
            .onAppear {
                if extraction == nil, let prefilled { extraction = prefilled }
            }
        }
    }

    private func analyze() {
        let images = photos.compactMap { ImageTools.jpeg($0, maxDimension: 1800, quality: 0.85) }
        working = true
        error = nil
        Task {
            do {
                var result = try await ai.run(ProductExtraction.self, AITasks.productFromLabel(images: images))
                if let barcode, !barcode.isEmpty { result.barcode = barcode }
                extraction = result
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    private func save(_ result: ProductExtraction, category: ProductCategory) {
        let product = FoodProduct()
        product.apply(result)
        product.categoryRaw = category.rawValue
        product.sourceRaw = prefilled != nil ? "openfoodfacts" : "label"
        if let first = photos.first { product.photo = ImageTools.jpeg(first, maxDimension: 600, quality: 0.7) }
        context.insert(product)
        try? context.save()
        dismiss()
    }
}

/// Editable product values.
struct ProductForm: View {
    @State var extraction: ProductExtraction
    var photo: UIImage?
    let onSave: (ProductExtraction, ProductCategory) -> Void
    @State private var category: ProductCategory = .other

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let photo {
                Image(uiImage: photo).resizable().scaledToFit().frame(maxHeight: 180).clipShape(RoundedRectangle(cornerRadius: 16))
            }
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    textField("الاسم بالعربي", "Arabic name", $extraction.nameAr)
                    textField("الاسم بالإنجليزي", "English name", $extraction.nameEn)
                    textField("البراند", "Brand", Binding(get: { extraction.brand ?? "" }, set: { extraction.brand = $0.isEmpty ? nil : $0 }))
                    textField("الباركود", "Barcode", Binding(get: { extraction.barcode ?? "" }, set: { extraction.barcode = $0.isEmpty ? nil : $0 }))
                    NumberField(ar: "حجم العبوة/الحصة", en: "Serving size", value: Binding(get: { extraction.servingSizeG ?? 0 }, set: { extraction.servingSizeG = $0 == 0 ? nil : $0 }), unit: "g", digits: 0)
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    SectionTitle(ar: "القيم لكل 100 جم", en: "Per 100 g")
                    NumberField(ar: "سعرات", en: "Calories", value: $extraction.per100g.kcal, unit: "kcal", digits: 0)
                    NumberField(ar: "بروتين", en: "Protein", value: $extraction.per100g.proteinG, unit: "g")
                    NumberField(ar: "كارب", en: "Carbs", value: $extraction.per100g.carbsG, unit: "g")
                    NumberField(ar: "دهون", en: "Fat", value: $extraction.per100g.fatG, unit: "g")
                    NumberField(ar: "صوديوم", en: "Sodium", value: Binding(get: { extraction.per100g.sodiumMg ?? 0 }, set: { extraction.per100g.sodiumMg = $0 == 0 ? nil : $0 }), unit: "mg", digits: 0)
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 8) {
                    BiCaption(ar: "التصنيف", en: "Category")
                    FlowLayout(spacing: 8) {
                        ForEach(ProductCategory.allCases.filter { $0 != .all }, id: \.self) { item in
                            BiChip(ar: item.ar, en: item.en, selected: category == item) { category = item }
                        }
                    }
                }
            }
            Button {
                onSave(extraction, category)
            } label: {
                BiLabel(ar: "احفظ في منتجاتي", en: "Save to my products", systemImage: "checkmark")
            }
            .buttonStyle(PrimaryButtonStyle())
        }
    }

    private func textField(_ ar: String, _ en: String, _ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            BiCaption(ar: ar, en: en)
            TextField("\(ar) · \(en)", text: text)
                .font(VFont.readex(15))
                .padding(10)
                .background(VColor.ground, in: RoundedRectangle(cornerRadius: 10))
        }
    }
}

struct ProductEditView: View {
    @Bindable var product: FoodProduct
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    var body: some View {
        NavigationStack {
            ScrollView {
                ProductForm(
                    extraction: ProductExtraction(
                        nameAr: product.nameAr, nameEn: product.nameEn, brand: product.brand, barcode: product.barcode,
                        servingSizeG: product.servingSizeG, servingDescAr: product.servingDescAr, servingDescEn: product.servingDescEn,
                        per100g: product.per100g
                    ),
                    photo: product.photo.flatMap(UIImage.init(data:))
                ) { result, category in
                    product.apply(result)
                    product.categoryRaw = category.rawValue
                    try? context.save()
                    dismiss()
                }
                .padding(20)
            }
            .background(VColor.ground)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() } }
                ToolbarItem(placement: .destructiveAction) {
                    Button("امسح · Delete", role: .destructive) {
                        context.delete(product)
                        try? context.save()
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Barcode

/// Scan → found in my products (log it) → found online (review & save) → not found (snap the label).
struct BarcodeFlowView: View {
    @Environment(\.dismiss) private var dismiss
    @Query private var products: [FoodProduct]

    @State private var code: String?
    @State private var looking = false
    @State private var online: ProductExtraction?
    @State private var notFound = false
    @State private var local: FoodProduct?

    var body: some View {
        NavigationStack {
            Group {
                if let local {
                    MealCaptureView(input: .product(local))
                } else if let online {
                    ProductCaptureView(barcode: code, prefilled: online)
                } else if notFound {
                    ProductCaptureView(barcode: code)
                } else if looking {
                    WorkingView(ar: "بدوّر على المنتج…", en: "Looking up the product…").padding(20)
                } else if BarcodeScanner.isSupported {
                    BarcodeScanner { value in found(value) }
                        .ignoresSafeArea()
                        .overlay(alignment: .bottom) {
                            BiText(ar: "وجّه الكاميرا على الباركود", en: "Point the camera at the barcode", size: 15, color: .white, enColor: .white, alignment: .center)
                                .padding(14)
                                .background(.black.opacity(0.6), in: Capsule())
                                .padding(.bottom, 40)
                        }
                } else {
                    ManualBarcodeEntry { value in found(value) }
                }
            }
            .toolbar {
                if local == nil && online == nil && !notFound {
                    ToolbarItem(placement: .cancellationAction) { Button("إلغاء · Cancel") { dismiss() } }
                }
            }
        }
    }

    private func found(_ value: String) {
        guard code == nil else { return }
        code = value
        if let match = products.first(where: { $0.barcode == value }) {
            local = match
            return
        }
        looking = true
        Task {
            let result = try? await OpenFoodFacts.lookup(barcode: value)
            looking = false
            if let result { online = result } else { notFound = true }
        }
    }
}

struct ManualBarcodeEntry: View {
    let onCode: (String) -> Void
    @State private var text = ""

    var body: some View {
        VStack(spacing: 16) {
            BiText(ar: "الكاميرا مش متاحة — اكتب الباركود", en: "Camera unavailable — type the barcode", size: 16, alignment: .center)
            TextField("6221234567890", text: $text)
                .keyboardType(.numberPad)
                .font(.system(.title3, design: .monospaced))
                .multilineTextAlignment(.center)
                .padding(12)
                .background(VColor.surface, in: RoundedRectangle(cornerRadius: 14))
            Button { onCode(text) } label: { BiLabel(ar: "دوّر", en: "Look up") }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(text.count < 6)
        }
        .padding(20)
    }
}

struct BarcodeScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void

    static var isSupported: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.ean13, .ean8, .upce, .code128, .qr])],
            qualityLevel: .balanced,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        try? scanner.startScanning()
        return scanner
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        if !controller.isScanning { try? controller.startScanning() }
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onCode: (String) -> Void
        private var delivered = false

        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for item in addedItems {
                if case let .barcode(barcode) = item, let value = barcode.payloadStringValue, !value.isEmpty {
                    delivered = true
                    dataScanner.stopScanning()
                    onCode(value)
                    return
                }
            }
        }
    }
}
