package com.elshal.vitalyn.features.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.GroceryCategory
import com.elshal.vitalyn.core.GroceryLine
import com.elshal.vitalyn.core.GroceryList
import com.elshal.vitalyn.core.PlanDay
import com.elshal.vitalyn.core.WeekPlan
import com.elshal.vitalyn.data.MealPlanRecord
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.CircleIconButton
import com.elshal.vitalyn.ui.FullSheet
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.SheetScaffold
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.shareText
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The week's shopping, added up from every meal and grouped by supermarket section. Ticks are saved with the plan. */
@Composable
internal fun GroceriesSheet(recordId: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val state by app.store.state.collectAsState()
    val record = state.plan(recordId)
    val lines = record?.plan?.let { GroceryList.build(it) } ?: emptyList()
    val checked = record?.checkedGroceries?.toSet() ?: emptySet()
    val left = lines.count { it.id !in checked }

    fun toggle(line: GroceryLine, isChecked: Boolean) {
        app.store.update { s ->
            s.updatePlan(recordId) { r ->
                if (isChecked) r.copy(checkedGroceries = r.checkedGroceries.filter { it != line.id })
                else r.copy(checkedGroceries = r.checkedGroceries + line.id)
            }
        }
    }

    FullSheet(onDismiss = onDismiss) {
        SheetScaffold(
            ar = "المشتريات",
            en = "Groceries",
            onClose = onDismiss,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (checked.isNotEmpty()) {
                        Text(
                            "شيل العلامات · Uncheck all",
                            style = VFont.style(12f, 400, VColor.primary),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) {
                                    app.store.update { s -> s.updatePlan(recordId) { it.copy(checkedGroceries = emptyList()) } }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        )
                    }
                    CircleIconButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = "شارك — Share",
                        onClick = { shareText(context, PlanShareText.groceries(lines, checked)) },
                        background = VColor.surface,
                        tint = VColor.primary,
                        size = 40.dp,
                    )
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(VColor.surface, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                BiText(
                    ar = if (left == 0) "اشتريت كل حاجة" else "فاضل $left من ${lines.size} حاجة",
                    en = if (left == 0) "Everything's bought" else "$left of ${lines.size} items left",
                    size = 14f,
                    weight = 500,
                )
            }
            GroceryCategory.entries.forEach { category ->
                val items = lines.filter { it.category == category }
                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${category.ar} · ${category.en}",
                            style = VFont.style(12f, 600, VColor.muted),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(VColor.surface, RoundedCornerShape(16.dp)),
                        ) {
                            items.forEachIndexed { index, line ->
                                if (index > 0) {
                                    Spacer(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 48.dp)
                                            .height(1.dp)
                                            .background(VColor.line),
                                    )
                                }
                                GroceryRow(line, checked = line.id in checked) { toggle(line, line.id in checked) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroceryRow(line: GroceryLine, checked: Boolean, onToggle: () -> Unit) {
    val color = if (checked) VColor.muted else VColor.ink
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) VColor.primary else VColor.muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                line.nameAr,
                style = VFont.style(14f, 400, color).copy(textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None),
            )
            Text(line.nameEn, style = VFont.style(11f, 400, VColor.muted))
        }
        Spacer(Modifier.width(8.dp))
        Ltr { Text(line.quantity, style = VFont.style(13f, 500, color)) }
    }
}

/** The plan as plain text for sharing. *Stars* make headings bold in WhatsApp. */
object PlanShareText {
    private val english = Locale.ENGLISH
    private val arabic: Locale = Locale.forLanguageTag("ar")

    /** "الجمعة، 30 أكتوبر" with Western digits (iOS `ar@numbers=latn`). */
    private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE، d MMMM", arabic)
    private val weekdayEnglish = DateTimeFormatter.ofPattern("EEEE", english)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", english)

    fun dayTitle(record: MealPlanRecord, index: Int): String = record.date(index).format(dayTitleFormat)

    private fun dayHeading(record: MealPlanRecord, index: Int): String {
        val englishDay = record.date(index).format(weekdayEnglish)
        return "*${dayTitle(record, index)} · $englishDay*"
    }

    /** "Oct 30 – Nov 5". */
    fun range(record: MealPlanRecord): String {
        val first = record.date(0).format(shortDate)
        val last = record.date(6).format(shortDate)
        return "$first – $last"
    }

    /** Every day's meals, plus the cooking plan. */
    fun week(record: MealPlanRecord, plan: WeekPlan): String {
        val text = mutableListOf("*جدول أكل الأسبوع · Weekly meal plan*", range(record))
        for (day in plan.days.sortedBy { it.dayIndex }) {
            text += ""
            text += dayHeading(record, day.dayIndex)
            for (meal in day.meals) {
                text += "• ${meal.slot.ar}: ${meal.nameAr}"
                text += "   ${meal.slot.en}: ${meal.nameEn} · ${meal.kcal.toInt()} kcal"
            }
        }
        if (plan.prepAr.isNotEmpty()) {
            text += listOf("", "*خطة الطبخ · Cooking plan*", plan.prepAr, plan.prepEn)
        }
        text += listOf("", "— Vitalyn")
        return text.joinToString("\n")
    }

    /** One day with each meal's ingredients and method. */
    fun day(record: MealPlanRecord, day: PlanDay): String {
        val text = mutableListOf(dayHeading(record, day.dayIndex))
        for (meal in day.meals) {
            text += ""
            text += "*${meal.slot.ar} · ${meal.slot.en}* — ${meal.nameAr}"
            text += "${meal.nameEn} · ${meal.kcal.toInt()} kcal"
            if (meal.ingredients.isNotEmpty()) {
                text += "المكونات · Ingredients:"
                text += meal.ingredients.map { "• ${it.nameAr} / ${it.nameEn} — ${it.amountText}" }
            }
            if (meal.recipeAr.isNotEmpty()) text += "الطريقة: ${meal.recipeAr}"
            if (meal.recipeEn.isNotEmpty()) text += "Method: ${meal.recipeEn}"
        }
        text += listOf("", "— Vitalyn")
        return text.joinToString("\n")
    }

    /** What's still to buy (or everything, once it's all ticked), by section. */
    fun groceries(lines: List<GroceryLine>, checked: Set<String>): String {
        val toBuy = lines.filter { it.id !in checked }
        val shown = toBuy.ifEmpty { lines }
        val text = mutableListOf("*قائمة المشتريات · Grocery list*")
        for (category in GroceryCategory.entries) {
            val items = shown.filter { it.category == category }
            if (items.isEmpty()) continue
            text += "\n*${category.ar} · ${category.en}*"
            text += items.map { "• ${it.nameAr} / ${it.nameEn} — ${it.quantity}" }
        }
        return text.joinToString("\n")
    }
}
