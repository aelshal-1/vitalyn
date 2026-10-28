import SwiftUI
import SwiftData
import VitalynCore

/// How a meal is being entered.
enum MealInput: Identifiable {
    case photo
    case text
    case product(FoodProduct)
    case prefilled(MealLog, slot: MealSlot?)

    var id: String {
        switch self {
        case .photo: "photo"
        case .text: "text"
        case let .product(product): "product-\(product.uid)"
        case let .prefilled(log, _): "prefilled-\(log.titleEn)"
        }
    }
}

/// Editable meal before saving.
struct MealDraft {
    var titleAr: String = ""
    var titleEn: String = ""
    var slot: MealSlot = MealDraft.slotForNow()
    var items: [AnalyzedFood] = []
    var questions: [ClarifyingQuestion] = []
    var notesAr: String = ""
    var notesEn: String = ""
    var photo: Data?
    var source: String = "photo"

    static func slotForNow(_ date: Date = Date()) -> MealSlot {
        switch Calendar.current.component(.hour, from: date) {
        case 4..<11: .breakfast
        case 11..<13: .snack
        case 13..<17: .lunch
        case 17..<19: .snack
        default: .dinner
        }
    }

    var totals: MacroTargets { Portions.total(items) }
}

struct MealCaptureView: View {
    let input: MealInput

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai
    @Environment(HealthService.self) private var health
    @Query(sort: \FoodProduct.createdAt, order: .reverse) private var products: [FoodProduct]

    @State private var speech = SpeechService()
    @State private var photo: UIImage?
    @State private var note = ""
    @State private var text = ""
    @State private var draft: MealDraft?
    @State private var working = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let draft {
                        MealReviewView(draft: draft, library: library, onSave: save)
                    } else {
                        switch input {
                        case .photo: photoInput
                        case .text: textInput
                        case .product, .prefilled: EmptyView()
                        }
                    }
                    if working { WorkingView(ar: "بحلل الأكل…", en: "Analyzing your food…") }
                    if let error { ErrorBanner(message: error) }
                }
                .padding(20)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(VColor.ground)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("إلغاء · Cancel") { dismiss() }
                }
            }
            .onAppear(perform: prepare)
            .onDisappear { speech.stop() }
        }
    }

    private var library: [LibraryProduct] { products.map(\.library) }

    private var photoInput: some View {
        VStack(alignment: .leading, spacing: 16) {
            BiText(ar: "صوّر الوجبة", en: "Snap your meal", size: 20)
            if let photo {
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 260)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
            }
            ImageSourceButtons { image in photo = image }
            if photo != nil {
                TextField("ملاحظة (اختياري) · Note (optional) — e.g. half portion", text: $note, axis: .vertical)
                    .font(VFont.readex(14))
                    .padding(12)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 14))
                Button(action: analyzePhoto) {
                    BiLabel(ar: "حلّل الوجبة", en: "Analyze meal", systemImage: "sparkles")
                }
                .buttonStyle(PrimaryButtonStyle(color: VColor.night))
                .disabled(working)
            }
        }
    }

    private var textInput: some View {
        VStack(alignment: .leading, spacing: 16) {
            BiText(ar: "اكتب أو قول أكلت إيه", en: "Type or say what you ate", size: 20)
            HStack(alignment: .bottom, spacing: 10) {
                TextField("مثلاً: 2 بيض وعيش بلدي وجبنة قريش · e.g. 2 eggs, baladi bread, cottage cheese", text: $text, axis: .vertical)
                    .lineLimit(3...6)
                    .font(VFont.readex(15))
                    .padding(12)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 14))
                DictationButton(speech: speech, text: $text)
            }
            if let message = speech.errorMessage { ErrorBanner(message: message) }
            Button(action: analyzeText) {
                BiLabel(ar: "احسب", en: "Calculate", systemImage: "sparkles")
            }
            .buttonStyle(PrimaryButtonStyle(color: VColor.night))
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || working)
        }
    }

    private func prepare() {
        guard draft == nil else { return }
        switch input {
        case let .product(product):
            let grams = product.servingSizeG ?? 100
            draft = MealDraft(
                titleAr: product.nameAr,
                titleEn: product.nameEn,
                items: [Portions.food(nameAr: product.nameAr, nameEn: product.nameEn, grams: grams, per100g: product.per100g, productId: product.uid)],
                source: "product"
            )
        case let .prefilled(log, slot):
            draft = MealDraft(titleAr: log.titleAr, titleEn: log.titleEn, slot: slot ?? MealDraft.slotForNow(), items: log.items, source: "plan")
        default:
            break
        }
    }

    private func analyzePhoto() {
        guard let photo, let jpeg = ImageTools.jpeg(photo) else { return }
        run(AITasks.mealFromPhoto(image: jpeg, note: note, library: library, context: AppData.userContext(context, health: health)), photo: jpeg, source: "photo")
    }

    private func analyzeText() {
        speech.stop()
        run(AITasks.mealFromText(text, library: library, context: AppData.userContext(context, health: health)), photo: nil, source: "text")
    }

    private func run(_ request: AIRequest, photo: Data?, source: String) {
        working = true
        error = nil
        Task {
            do {
                let analysis = try await ai.run(MealAnalysis.self, request)
                draft = MealDraft(
                    titleAr: analysis.titleAr,
                    titleEn: analysis.titleEn,
                    items: analysis.items.map(resolveLibrary),
                    questions: analysis.questions,
                    notesAr: analysis.notesAr,
                    notesEn: analysis.notesEn,
                    photo: photo,
                    source: source
                )
            } catch {
                self.error = error.localizedDescription
            }
            working = false
        }
    }

    /// When Claude matched a saved product, use the product's exact nutrition for the estimated weight.
    private func resolveLibrary(_ item: AnalyzedFood) -> AnalyzedFood {
        guard let id = item.libraryProductId, let product = library.first(where: { $0.id == id }) else { return item }
        return Portions.food(nameAr: product.nameAr, nameEn: product.nameEn, grams: item.grams, per100g: product.per100g, productId: id)
    }

    private func save(_ draft: MealDraft, date: Date) {
        let meal = MealEntry()
        meal.date = date
        meal.slot = draft.slot
        meal.titleAr = draft.titleAr
        meal.titleEn = draft.titleEn
        meal.sourceRaw = draft.source
        meal.photo = draft.photo
        context.insert(meal)
        for (index, food) in draft.items.enumerated() {
            let item = MealItem(food, order: index)
            item.meal = meal
            if let id = food.libraryProductId {
                item.product = products.first { $0.uid == id }
            }
            context.insert(item)
        }
        try? context.save()
        let totals = draft.totals
        Task {
            meal.savedToHealth = await health.saveNutrition(totals: totals, date: date)
            try? context.save()
        }
        AppData.publishSnapshot(context, health: health)
        dismiss()
    }
}

/// Review and edit the analyzed foods, answer Claude's questions, then save.
struct MealReviewView: View {
    @State var draft: MealDraft
    let library: [LibraryProduct]
    let onSave: (MealDraft, Date) -> Void
    @State private var date = Date()
    @State private var showAddProduct = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let photo = draft.photo, let image = UIImage(data: photo) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(height: 200)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
            }

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    BiText(ar: draft.titleAr, en: draft.titleEn, size: 18)
                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text(VFormat.number(draft.totals.calories)).font(VFont.readex(32, weight: 600))
                        Text("kcal").font(VFont.readex(14)).foregroundStyle(VColor.inkSecondary)
                    }
                    .environment(\.layoutDirection, .leftToRight)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    macroPill("بروتين · P", draft.totals.proteinGrams, VColor.primarySoft, VColor.primaryDark)
                    macroPill("كارب · C", draft.totals.carbsGrams, Color(hex: 0xFDEBD0), Color(hex: 0x7A4B00))
                    macroPill("دهون · F", draft.totals.fatGrams, Color(hex: 0xE3EAFE), Color(hex: 0x2A43A8))
                }
            }

            Card(padding: 12) {
                VStack(spacing: 0) {
                    ForEach(draft.items.indices, id: \.self) { index in
                        itemRow(index)
                        if index < draft.items.count - 1 { Divider() }
                    }
                    Button { showAddProduct = true } label: {
                        Label { BiCaption(ar: "ضيف من منتجاتي", en: "Add from my products", size: 13, color: VColor.primaryDark) } icon: { Image(systemName: "plus") }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .disabled(library.isEmpty)
                }
            }

            ForEach(draft.questions.indices, id: \.self) { index in
                questionCard(index)
            }

            if !draft.notesAr.isEmpty {
                BiText(ar: draft.notesAr, en: draft.notesEn, size: 13, weight: 400, color: VColor.inkSecondary)
            }

            Card {
                VStack(alignment: .leading, spacing: 12) {
                    BiCaption(ar: "الوجبة", en: "Meal")
                    HStack(spacing: 8) {
                        ForEach(MealSlot.allCases, id: \.self) { slot in
                            BiChip(ar: slot.ar, en: slot.en, selected: draft.slot == slot) { draft.slot = slot }
                        }
                    }
                    DatePicker(selection: $date) {
                        BiCaption(ar: "الوقت", en: "Time")
                    }
                }
            }

            BiText(ar: "الكميات تقديرية — عدّلها لو تعرف الوزن بالظبط.", en: "Portions are estimates — adjust if you know the weight.", size: 12, weight: 400, color: VColor.muted)

            Button {
                onSave(draft, date)
            } label: {
                BiLabel(ar: "احفظ الوجبة", en: "Save meal", systemImage: "checkmark")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(draft.items.isEmpty)
        }
        .sheet(isPresented: $showAddProduct) {
            ProductPickerSheet(library: library) { product in
                let grams = product.servingSizeG ?? 100
                draft.items.append(Portions.food(nameAr: product.nameAr, nameEn: product.nameEn, grams: grams, per100g: product.per100g, productId: product.id))
            }
        }
    }

    private func macroPill(_ label: String, _ value: Int, _ background: Color, _ foreground: Color) -> some View {
        Text("\(label) \(value)g")
            .font(VFont.readex(11, weight: 500))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(background, in: Capsule())
    }

    private func itemRow(_ index: Int) -> some View {
        let item = draft.items[index]
        return HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(item.nameAr).font(VFont.readex(14, weight: 500))
                    if item.libraryProductId != nil {
                        Image(systemName: "checkmark.seal.fill").font(.system(size: 11)).foregroundStyle(VColor.primary)
                    }
                    if item.confidence == .low {
                        Image(systemName: "questionmark.circle").font(.system(size: 11)).foregroundStyle(Color(hex: 0x8A5A00))
                    }
                }
                Text(item.nameEn).font(VFont.readex(11)).foregroundStyle(VColor.inkSecondary)
                Text("\(Int(item.kcal)) kcal · P \(VFormat.decimal(item.proteinG)) · C \(VFormat.decimal(item.carbsG)) · F \(VFormat.decimal(item.fatG))")
                    .font(VFont.readex(11)).foregroundStyle(VColor.muted)
                    .environment(\.layoutDirection, .leftToRight)
            }
            Spacer(minLength: 4)
            HStack(spacing: 0) {
                Button { change(index, by: -10) } label: { Image(systemName: "minus").frame(width: 34, height: 36) }
                    .accessibilityLabel("قلّل — Less")
                Text("\(Int(item.grams)) g").font(VFont.readex(13, weight: 600)).frame(minWidth: 52)
                    .environment(\.layoutDirection, .leftToRight)
                Button { change(index, by: 10) } label: { Image(systemName: "plus").frame(width: 34, height: 36) }
                    .accessibilityLabel("زوّد — More")
            }
            .foregroundStyle(VColor.ink)
            .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
            Button(role: .destructive) {
                draft.items.remove(at: index)
            } label: {
                Image(systemName: "trash").font(.system(size: 13)).foregroundStyle(VColor.energy).frame(width: 30, height: 36)
            }
            .accessibilityLabel("امسح — Delete")
        }
        .buttonStyle(.plain)
        .padding(.vertical, 8)
    }

    private func change(_ index: Int, by delta: Double) {
        let item = draft.items[index]
        draft.items[index] = Portions.rescale(item, to: max(5, item.grams + delta))
    }

    private func questionCard(_ index: Int) -> some View {
        let question = draft.questions[index]
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "sparkles").foregroundStyle(Color(hex: 0x8A5A00))
                BiText(ar: question.questionAr, en: question.questionEn, size: 14, weight: 500, enColor: Color(hex: 0x5F4600))
            }
            ForEach(question.options.indices, id: \.self) { optionIndex in
                let option = question.options[optionIndex]
                Button {
                    answer(questionIndex: index, option: option)
                } label: {
                    BiText(ar: option.labelAr, en: option.labelEn, size: 13, weight: 500)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .padding(.horizontal, 12)
                        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: 0xE8D6A8)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(Color(hex: 0xFFF8E6), in: RoundedRectangle(cornerRadius: 20))
    }

    private func answer(questionIndex: Int, option: QuestionOption) {
        let question = draft.questions[questionIndex]
        if let itemIndex = question.itemIndex, draft.items.indices.contains(itemIndex) {
            draft.items[itemIndex] = Portions.apply(option, to: draft.items[itemIndex], library: library)
        } else if let id = option.libraryProductId, let product = library.first(where: { $0.id == id }) {
            draft.items.append(Portions.food(nameAr: product.nameAr, nameEn: product.nameEn, grams: option.grams ?? product.servingSizeG ?? 100, per100g: product.per100g, productId: id))
        }
        draft.questions.remove(at: questionIndex)
    }
}

struct ProductPickerSheet: View {
    let library: [LibraryProduct]
    let onPick: (LibraryProduct) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List(filtered, id: \.id) { product in
                Button {
                    onPick(product)
                    dismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(product.nameAr).font(VFont.readex(14, weight: 500))
                        Text(product.nameEn + (product.brand.isEmpty ? "" : " · \(product.brand)")).font(VFont.readex(12)).foregroundStyle(VColor.muted)
                    }
                }
            }
            .searchable(text: $search, prompt: "ابحث · Search")
            .navigationTitle("منتجاتي · My products")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var filtered: [LibraryProduct] {
        guard !search.isEmpty else { return library }
        return library.filter { "\($0.nameAr) \($0.nameEn) \($0.brand)".localizedCaseInsensitiveContains(search) }
    }
}
