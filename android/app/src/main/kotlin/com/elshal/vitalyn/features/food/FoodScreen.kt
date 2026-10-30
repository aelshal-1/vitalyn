package com.elshal.vitalyn.features.food

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.Portions
import com.elshal.vitalyn.data.MealEntry
import com.elshal.vitalyn.data.shortTime
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.SecondaryButton
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Food tab: food log, product library and weekly plan. The section is remembered in `app.foodTab`. */
@Composable
fun FoodScreen() {
    val app = LocalApp.current
    Box(
        Modifier
            .fillMaxSize()
            .background(VColor.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BiText("الأكل", "Food", size = 26f, enSize = 15f)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FoodTab.entries.forEach { item ->
                    BiChip(item.ar, item.en, selected = app.foodTab == item, dark = true) { app.foodTab = item }
                }
            }
            when (app.foodTab) {
                FoodTab.LOG -> FoodLogSection()
                FoodTab.PRODUCTS -> ProductsSection()
                FoodTab.PLAN -> PlanSection()
            }
        }
    }
}

@Composable
fun FoodLogSection() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    var input by remember { mutableStateOf<MealInput?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(onClick = { input = MealInput.Photo }, modifier = Modifier.weight(1f)) {
                BiLabel("صوّر وجبة", "Snap a meal", icon = Icons.Rounded.PhotoCamera, size = 14f)
            }
            PrimaryButton(onClick = { input = MealInput.Text }, modifier = Modifier.weight(1f), color = VColor.night) {
                BiLabel("اكتب أو قول", "Type or say", icon = Icons.Rounded.Mic, size = 14f)
            }
        }
        SecondaryButton(onClick = { showScanner = true }, modifier = Modifier.fillMaxWidth()) {
            BiLabel("امسح باركود منتج", "Scan a product barcode", icon = Icons.Rounded.QrCodeScanner, size = 14f)
        }

        val today = LocalDate.now()
        (0 until 7).map { today.minusDays(it.toLong()) }.forEach { day ->
            val dayMeals = state.mealsOn(day)
            if (dayMeals.isNotEmpty()) {
                DayMealsCard(day = day, meals = dayMeals) { meal ->
                    app.store.update { s -> s.copy(meals = s.meals.filter { it.id != meal.id }) }
                    app.store.deleteBlob(meal.photo)
                    app.publishSnapshot()
                }
            }
        }
        if (state.meals.isEmpty()) {
            BiText(
                "لسه مسجلتش أكل. صوّر أول وجبة!", "Nothing logged yet. Snap your first meal!",
                size = 14f, weight = 400, color = VColor.inkSecondary,
            )
        }
    }

    input?.let { current -> MealCaptureSheet(current) { input = null } }
    if (showScanner) BarcodeFlowSheet { showScanner = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayMealsCard(day: LocalDate, meals: List<MealEntry>, onDelete: (MealEntry) -> Unit) {
    val total = Portions.total(meals.flatMap { meal -> meal.sortedItems.map { it.analyzed } })
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BiText(dayTitleAr(day), dayTitleEn(day), size = 15f)
            Spacer(Modifier.weight(1f))
            Ltr {
                Text(
                    "${VFormat.number(total.calories)} kcal · P ${total.proteinGrams} g",
                    style = VFont.style(12f, 500, VColor.inkSecondary),
                )
            }
        }
        meals.sortedBy { it.date }.forEach { meal ->
            key(meal.id) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    MealRow(
                        meal = meal,
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("امسح · Delete", style = VFont.style(14f, 500, VColor.energy)) },
                            onClick = {
                                menu = false
                                onDelete(meal)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun dayTitleAr(day: LocalDate): String {
    val today = LocalDate.now()
    if (day == today) return "النهارده"
    if (day == today.minusDays(1)) return "امبارح"
    return DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("ar")).format(day)
}

private fun dayTitleEn(day: LocalDate): String {
    val today = LocalDate.now()
    if (day == today) return "Today"
    if (day == today.minusDays(1)) return "Yesterday"
    return DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH).format(day)
}

@Composable
fun MealRow(meal: MealEntry, modifier: Modifier = Modifier) {
    val total = meal.totals
    val photo = rememberFoodBlobBitmap(meal.photo, 160)
    val titleAr = meal.titleAr.ifEmpty { meal.sortedItems.joinToString(" + ") { it.nameAr } }
    val titleEn = meal.titleEn.ifEmpty { meal.sortedItems.joinToString(", ") { it.nameEn } }
    Row(
        modifier
            .fillMaxWidth()
            .background(VColor.surface, RoundedCornerShape(20.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${meal.slot.ar} — ${meal.slot.en}, ${total.calories} kcal, $titleAr"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(VColor.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Restaurant, contentDescription = null, tint = VColor.ink)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(meal.slot.ar, style = VFont.style(14f, 600, VColor.ink))
                Text("${meal.slot.en} · ${meal.date.shortTime()}", style = VFont.style(11f, 400, VColor.muted))
                Spacer(Modifier.weight(1f))
                Ltr { Text("${total.calories} kcal", style = VFont.style(14f, 600, VColor.ink)) }
            }
            Text(titleAr, style = VFont.style(12f, 400, VColor.inkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(titleEn, style = VFont.style(11f, 400, VColor.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Loads a stored photo (by blob name) off the main thread, downsampled to `maxDimension` px. */
@Composable
internal fun rememberFoodBlobBitmap(name: String?, maxDimension: Int): Bitmap? {
    val app = LocalApp.current
    val bitmap by produceState<Bitmap?>(initialValue = null, name, maxDimension) {
        value = if (name == null) null else withContext(Dispatchers.IO) { app.store.blobBitmap(name, maxDimension) }
    }
    return bitmap
}
