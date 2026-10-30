package com.elshal.vitalyn.features.food

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.AppGraph
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AIRequest
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.AnalyzedFood
import com.elshal.vitalyn.core.ClarifyingQuestion
import com.elshal.vitalyn.core.Confidence
import com.elshal.vitalyn.core.LibraryProduct
import com.elshal.vitalyn.core.MacroTargets
import com.elshal.vitalyn.core.MealAnalysis
import com.elshal.vitalyn.core.MealSlot
import com.elshal.vitalyn.core.Portions
import com.elshal.vitalyn.core.QuestionOption
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.MealEntry
import com.elshal.vitalyn.data.MealItem
import com.elshal.vitalyn.data.nowMillis
import com.elshal.vitalyn.data.toLocalDateTime
import com.elshal.vitalyn.data.toMillis
import com.elshal.vitalyn.services.DictationButton
import com.elshal.vitalyn.services.ImageTools
import com.elshal.vitalyn.services.rememberSpeech
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.FullSheet
import com.elshal.vitalyn.ui.ImageSourceButtons
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.SheetScaffold
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.VTextField
import com.elshal.vitalyn.ui.WorkingView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** Editable meal before saving. */
data class MealDraft(
    val titleAr: String = "",
    val titleEn: String = "",
    val slot: MealSlot = MealDraft.slotForNow(),
    val items: List<AnalyzedFood> = emptyList(),
    val questions: List<ClarifyingQuestion> = emptyList(),
    val notesAr: String = "",
    val notesEn: String = "",
    /** JPEG bytes of the meal photo, if any. */
    val photo: ByteArray? = null,
    val source: String = "photo",
) {
    val totals: MacroTargets get() = Portions.total(items)

    companion object {
        fun slotForNow(time: LocalDateTime = LocalDateTime.now()): MealSlot = when (time.hour) {
            in 4 until 11 -> MealSlot.BREAKFAST
            in 11 until 13 -> MealSlot.SNACK
            in 13 until 17 -> MealSlot.LUNCH
            in 17 until 19 -> MealSlot.SNACK
            else -> MealSlot.DINNER
        }
    }
}

/** Product and plan meals start as a ready draft; photo and text meals start empty. */
private fun preparedDraft(input: MealInput): MealDraft? = when (input) {
    is MealInput.Product -> {
        val product = input.product
        val grams = product.servingSizeG ?: 100.0
        MealDraft(
            titleAr = product.nameAr,
            titleEn = product.nameEn,
            items = listOf(Portions.food(product.nameAr, product.nameEn, grams, product.per100g, product.uid)),
            source = "product",
        )
    }
    is MealInput.Prefilled -> MealDraft(
        titleAr = input.log.titleAr,
        titleEn = input.log.titleEn,
        slot = input.slot ?: MealDraft.slotForNow(),
        items = input.log.items,
        source = "plan",
    )
    MealInput.Photo, MealInput.Text -> null
}

/**
 * Full-screen sheet that logs a meal: capture (photo or text) → Claude's analysis → review and edit → save.
 * Product and plan inputs go straight to the review.
 */
@Composable
fun MealCaptureSheet(input: MealInput, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val speech = rememberSpeech()

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var note by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(preparedDraft(input)) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val library = remember(state.products) { state.sortedProducts.map { it.library } }

    /** When Claude matched a saved product, use the product's exact nutrition for the estimated weight. */
    fun resolveLibrary(item: AnalyzedFood): AnalyzedFood {
        val id = item.libraryProductId ?: return item
        val product = library.firstOrNull { it.id == id } ?: return item
        return Portions.food(product.nameAr, product.nameEn, item.grams, product.per100g, id)
    }

    fun analyze(source: String, makeRequest: suspend () -> Pair<AIRequest, ByteArray?>) {
        working = true
        error = null
        scope.launch {
            try {
                val (request, jpeg) = makeRequest()
                val analysis = app.ai.run(MealAnalysis.serializer(), request)
                draft = MealDraft(
                    titleAr = analysis.titleAr,
                    titleEn = analysis.titleEn,
                    items = analysis.items.map(::resolveLibrary),
                    questions = analysis.questions,
                    notesAr = analysis.notesAr,
                    notesEn = analysis.notesEn,
                    photo = jpeg,
                    source = source,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                error = e.userMessage()
            }
            working = false
        }
    }

    fun analyzePhoto() {
        val image = photo ?: return
        val currentNote = note
        analyze("photo") {
            val jpeg = withContext(Dispatchers.Default) { ImageTools.jpeg(image) }
            AITasks.mealFromPhoto(jpeg, currentNote, library, AppData.userContext(app.store.value, app.health)) to jpeg
        }
    }

    fun analyzeText() {
        speech.stop()
        val currentText = text
        analyze("text") {
            AITasks.mealFromText(currentText, library, AppData.userContext(app.store.value, app.health)) to null
        }
    }

    FullSheet(onDismiss) {
        SheetScaffold("سجّل وجبة", "Log a meal", onClose = {
            speech.stop()
            onDismiss()
        }) {
            Column(
                Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val current = draft
                if (current != null) {
                    MealReview(current, library) { result, date ->
                        saveMeal(app, result, date)
                        onDismiss()
                    }
                } else {
                    when (input) {
                        MealInput.Photo -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            BiText("صوّر الوجبة", "Snap your meal", size = 20f)
                            photo?.let { image ->
                                Image(
                                    image.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                )
                            }
                            ImageSourceButtons(onImage = { photo = it })
                            if (photo != null) {
                                VTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    placeholder = "ملاحظة (اختياري) · Note (optional) — e.g. half portion",
                                    singleLine = false,
                                )
                                PrimaryButton(onClick = { analyzePhoto() }, color = VColor.night, enabled = !working) {
                                    BiLabel("حلّل الوجبة", "Analyze meal", icon = Icons.Rounded.AutoAwesome)
                                }
                            }
                        }
                        MealInput.Text -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            BiText("اكتب أو قول أكلت إيه", "Type or say what you ate", size = 20f)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                VTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    placeholder = "مثلاً: 2 بيض وعيش بلدي وجبنة قريش · e.g. 2 eggs, baladi bread, cottage cheese",
                                    modifier = Modifier.weight(1f).heightIn(max = 180.dp),
                                    singleLine = false,
                                    minLines = 3,
                                )
                                DictationButton(speech, onText = { text = it })
                            }
                            speech.errorMessage?.let { ErrorBanner(it) }
                            PrimaryButton(
                                onClick = { analyzeText() },
                                color = VColor.night,
                                enabled = text.isNotBlank() && !working,
                            ) {
                                BiLabel("احسب", "Calculate", icon = Icons.Rounded.AutoAwesome)
                            }
                        }
                        is MealInput.Product, is MealInput.Prefilled -> Unit
                    }
                }
                if (working) WorkingView("بحلل الأكل…", "Analyzing your food…")
                error?.let { ErrorBanner(it) }
            }
        }
    }
}

/** Stores the meal, writes it to Health Connect in the background and refreshes the widgets. */
private fun saveMeal(app: AppGraph, draft: MealDraft, date: Long) {
    val photoName = draft.photo?.let { app.store.saveBlob(it, "jpg") }
    val products = app.store.value
    val meal = MealEntry(
        date = date,
        slotRaw = draft.slot.raw,
        titleAr = draft.titleAr,
        titleEn = draft.titleEn,
        sourceRaw = draft.source,
        photo = photoName,
        items = draft.items.mapIndexed { index, food ->
            MealItem.from(food, index).copy(productId = food.libraryProductId?.takeIf { products.product(it) != null })
        },
    )
    app.store.update { it.upsertMeal(meal) }
    val totals = draft.totals
    app.scope.launch {
        val saved = app.health.saveNutrition(totals, Instant.ofEpochMilli(date))
        app.store.update { s -> s.copy(meals = s.meals.map { if (it.id == meal.id) it.copy(savedToHealth = saved) else it }) }
    }
    app.publishSnapshot()
}

/** Review and edit the analyzed foods, answer Claude's questions, then save. */
@Composable
fun MealReview(initial: MealDraft, library: List<LibraryProduct>, onSave: (MealDraft, Long) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var date by remember { mutableLongStateOf(nowMillis()) }
    var showAddProduct by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val photoBytes = draft.photo
    val photo by produceState<Bitmap?>(initialValue = null, photoBytes) {
        value = photoBytes?.let { bytes -> withContext(Dispatchers.Default) { ImageTools.decode(bytes, 1200) } }
    }

    fun change(index: Int, delta: Double) {
        val item = draft.items[index]
        draft = draft.copy(items = draft.items.toMutableList().also { it[index] = Portions.rescale(item, max(5.0, item.grams + delta)) })
    }

    fun answer(questionIndex: Int, option: QuestionOption) {
        val question = draft.questions[questionIndex]
        var items = draft.items
        val itemIndex = question.itemIndex
        if (itemIndex != null && itemIndex in items.indices) {
            items = items.toMutableList().also { it[itemIndex] = Portions.apply(option, it[itemIndex], library) }
        } else {
            val id = option.libraryProductId
            val product = id?.let { pid -> library.firstOrNull { it.id == pid } }
            if (product != null) {
                items = items + Portions.food(product.nameAr, product.nameEn, option.grams ?: product.servingSizeG ?: 100.0, product.per100g, product.id)
            }
        }
        draft = draft.copy(items = items, questions = draft.questions.filterIndexed { i, _ -> i != questionIndex })
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        photo?.let { image ->
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
        }

        val totals = draft.totals
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BiText(draft.titleAr, draft.titleEn, size = 18f)
                Ltr {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(VFormat.number(totals.calories), style = VFont.style(32f, 600, VColor.ink), modifier = Modifier.alignByBaseline())
                        Text("kcal", style = VFont.style(14f, 400, VColor.inkSecondary), modifier = Modifier.alignByBaseline())
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MacroPill("بروتين · P", totals.proteinGrams, VColor.primarySoft, VColor.primaryDark)
                MacroPill("كارب · C", totals.carbsGrams, Color(0xFFFDEBD0), Color(0xFF7A4B00))
                MacroPill("دهون · F", totals.fatGrams, Color(0xFFE3EAFE), Color(0xFF2A43A8))
            }
        }

        Card(padding = 12.dp) {
            draft.items.forEachIndexed { index, item ->
                ItemRow(
                    item = item,
                    onLess = { change(index, -10.0) },
                    onMore = { change(index, 10.0) },
                    onDelete = { draft = draft.copy(items = draft.items.filterIndexed { i, _ -> i != index }) },
                )
                if (index < draft.items.size - 1) HorizontalDivider(color = VColor.line)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .alpha(if (library.isEmpty()) 0.45f else 1f)
                    .clickable(enabled = library.isNotEmpty(), role = Role.Button) { showAddProduct = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = VColor.primaryDark, modifier = Modifier.size(18.dp))
                BiCaption("ضيف من منتجاتي", "Add from my products", size = 13f, color = VColor.primaryDark)
            }
        }

        draft.questions.forEachIndexed { index, question ->
            QuestionCard(question) { option -> answer(index, option) }
        }

        if (draft.notesAr.isNotEmpty()) {
            BiText(draft.notesAr, draft.notesEn, size = 13f, weight = 400, color = VColor.inkSecondary)
        }

        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BiCaption("الوجبة", "Meal")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealSlot.entries.forEach { slot ->
                        BiChip(slot.ar, slot.en, selected = draft.slot == slot, modifier = Modifier.weight(1f)) {
                            draft = draft.copy(slot = slot)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BiCaption("الوقت", "Time")
                    Spacer(Modifier.weight(1f))
                    Ltr {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val dateTime = date.toLocalDateTime()
                            PickerPill(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(dateTime)) {
                                val now = date.toLocalDateTime()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        date = LocalDateTime.of(LocalDate.of(year, month + 1, day), now.toLocalTime()).toMillis()
                                    },
                                    now.year, now.monthValue - 1, now.dayOfMonth,
                                ).show()
                            }
                            PickerPill(DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(dateTime)) {
                                val now = date.toLocalDateTime()
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        date = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toMillis()
                                    },
                                    now.hour, now.minute, false,
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        BiText(
            "الكميات تقديرية — عدّلها لو تعرف الوزن بالظبط.", "Portions are estimates — adjust if you know the weight.",
            size = 12f, weight = 400, color = VColor.muted,
        )

        PrimaryButton(onClick = { onSave(draft, date) }, enabled = draft.items.isNotEmpty()) {
            BiLabel("احفظ الوجبة", "Save meal", icon = Icons.Rounded.Check)
        }
    }

    if (showAddProduct) {
        ProductPickerSheet(library, onPick = { product ->
            val grams = product.servingSizeG ?: 100.0
            draft = draft.copy(items = draft.items + Portions.food(product.nameAr, product.nameEn, grams, product.per100g, product.id))
        }, onDismiss = { showAddProduct = false })
    }
}

@Composable
private fun PickerPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = VFont.style(14f, 500, VColor.ink),
        modifier = Modifier
            .background(VColor.track, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun MacroPill(label: String, value: Int, background: Color, foreground: Color) {
    Text(
        "$label ${value}g",
        style = VFont.style(11f, 500, foreground),
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ItemRow(item: AnalyzedFood, onLess: () -> Unit, onMore: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.nameAr, style = VFont.style(14f, 500, VColor.ink))
                if (item.libraryProductId != null) {
                    Icon(Icons.Rounded.Verified, contentDescription = null, tint = VColor.primary, modifier = Modifier.size(13.dp))
                }
                if (item.confidence == Confidence.LOW) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = Color(0xFF8A5A00), modifier = Modifier.size(13.dp))
                }
            }
            Text(item.nameEn, style = VFont.style(11f, 400, VColor.inkSecondary))
            Ltr {
                Text(
                    "${item.kcal.toInt()} kcal · P ${VFormat.decimal(item.proteinG)} · C ${VFormat.decimal(item.carbsG)} · F ${VFormat.decimal(item.fatG)}",
                    style = VFont.style(11f, 400, VColor.muted),
                )
            }
        }
        Row(
            Modifier.background(VColor.ground, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(Icons.Rounded.Remove, "قلّل — Less", onLess)
            Ltr {
                Text(
                    "${item.grams.toInt()} g",
                    style = VFont.style(13f, 600, VColor.ink),
                    modifier = Modifier.widthIn(min = 52.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            StepperButton(Icons.Rounded.Add, "زوّد — More", onMore)
        }
        Box(
            Modifier
                .size(width = 30.dp, height = 36.dp)
                .clickable(role = Role.Button, onClick = onDelete)
                .semantics { contentDescription = "امسح — Delete" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = VColor.energy, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 36.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = VColor.ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun QuestionCard(question: ClarifyingQuestion, onAnswer: (QuestionOption) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E6), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF8A5A00))
            BiText(question.questionAr, question.questionEn, size = 14f, weight = 500, enColor = Color(0xFF5F4600))
        }
        question.options.forEach { option ->
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .background(VColor.surface, shape)
                    .border(1.dp, Color(0xFFE8D6A8), shape)
                    .clickable(role = Role.Button) { onAnswer(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BiText(option.labelAr, option.labelEn, size = 13f, weight = 500)
            }
        }
    }
}

/** Searchable list of the user's saved products. */
@Composable
fun ProductPickerSheet(library: List<LibraryProduct>, onPick: (LibraryProduct) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isEmpty()) library else library.filter {
        "${it.nameAr} ${it.nameEn} ${it.brand}".contains(search, ignoreCase = true)
    }
    FullSheet(onDismiss) {
        SheetScaffold("منتجاتي", "My products", onClose = onDismiss) {
            VTextField(value = search, onValueChange = { search = it }, placeholder = "ابحث · Search")
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(VColor.surface, RoundedCornerShape(20.dp)),
            ) {
                filtered.forEachIndexed { index, product ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                onPick(product)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(product.nameAr, style = VFont.style(14f, 500, VColor.ink))
                        Text(
                            product.nameEn + if (product.brand.isEmpty()) "" else " · ${product.brand}",
                            style = VFont.style(12f, 400, VColor.muted),
                        )
                    }
                    if (index < filtered.size - 1) HorizontalDivider(color = VColor.line)
                }
            }
        }
    }
}
