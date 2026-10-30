package com.elshal.vitalyn.data

import com.elshal.vitalyn.core.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

// Stored records (the iOS SwiftData models). Records are immutable: change them with `store.update { … copy(…) }`.
// Dates are epoch milliseconds; photos and files live in the store's blob folder and are referenced by name.

fun newId(): String = UUID.randomUUID().toString()

fun nowMillis(): Long = System.currentTimeMillis()

val zone: ZoneId get() = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

fun LocalDate.startMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()

/** yyyy-MM-dd, as the iOS prompts use (`.iso8601.year().month().day()`). */
fun Long.isoDay(): String = toLocalDate().toString()

/** "8:30 AM" style short time, in English (prompts and English captions). */
fun Long.shortTime(): String = DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(toLocalDateTime())

@Serializable
data class UserProfile(
    val id: String = newId(),
    val name: String = "",
    val birthYear: Int = 1990,
    val sexRaw: String = Sex.MALE.raw,
    val heightCm: Double = 175.0,
    val startWeightKg: Double = 90.0,
    val goalWeightKg: Double = 80.0,
    val weeklyLossKg: Double = 0.5,
    val activityRaw: String = ActivityLevel.LIGHT.raw,
    val conditionsRaw: List<String> = emptyList(),
    val otherConditions: String = "",
    val medications: String = "",
    val allergies: String = "",
    val preferences: List<String> = emptyList(),
    val stepsGoal: Int = 8000,
    val waterGoal: Int = 8,
    val createdAt: Long = nowMillis(),
    /** What Claude understood from conditions, medications and allergies. */
    val healthInsights: List<HealthItemInsight> = emptyList(),
    /** BMR entered by the user (kcal/day); 0 means calculate it (InBody, then formulas). */
    val manualBMR: Double = 0.0,
) {
    val sex: Sex get() = Sex.fromRaw(sexRaw) ?: Sex.MALE
    val activity: ActivityLevel get() = ActivityLevel.fromRaw(activityRaw) ?: ActivityLevel.LIGHT
    val conditions: List<ChronicCondition> get() = conditionsRaw.mapNotNull { ChronicCondition.fromRaw(it) }

    fun withSex(value: Sex) = copy(sexRaw = value.raw)
    fun withActivity(value: ActivityLevel) = copy(activityRaw = value.raw)
    fun withConditions(value: List<ChronicCondition>) = copy(conditionsRaw = value.map { it.raw })

    val ageYears: Int get() = maxOf(10, LocalDate.now().year - birthYear)

    /** Items currently in the profile, by kind. */
    val healthItems: List<Pair<HealthItemKind, String>>
        get() = ProfileList.items(otherConditions).map { HealthItemKind.CONDITION to it } +
            ProfileList.items(medications).map { HealthItemKind.MEDICATION to it } +
            ProfileList.items(allergies).map { HealthItemKind.ALLERGY to it }

    /** Insights for items still in the profile. */
    val currentHealthInsights: List<HealthItemInsight>
        get() {
            val keys = healthItems.map { HealthItemInsight.key(it.first, it.second) }.toSet()
            return healthInsights.filter { it.id in keys }
        }
}

@Serializable
data class InBodyScan(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val weightKg: Double = 0.0,
    val skeletalMuscleKg: Double? = null,
    val bodyFatKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val leanMassKg: Double? = null,
    val visceralFatLevel: Double? = null,
    val bmrKcal: Double? = null,
    val totalBodyWaterL: Double? = null,
    val inbodyScore: Double? = null,
    val rightArmKg: Double? = null,
    val leftArmKg: Double? = null,
    val trunkKg: Double? = null,
    val rightLegKg: Double? = null,
    val leftLegKg: Double? = null,
    val summaryAr: String = "",
    val summaryEn: String = "",
    /** Blob name of the photo or PDF the scan was read from. */
    val sourceFile: String? = null,
    val sourceIsPDF: Boolean = false,
) {
    val effectiveLeanMassKg: Double?
        get() = leanMassKg ?: bodyFatKg?.let { weightKg - it } ?: bodyFatPercent?.let { weightKg * (1 - it / 100) }

    val effectiveBodyFatPercent: Double?
        get() = bodyFatPercent ?: bodyFatKg?.takeIf { weightKg > 0 }?.let { it / weightKg * 100 }

    val promptSummary: String
        get() {
            val parts = mutableListOf("${date.isoDay()}: weight $weightKg kg")
            skeletalMuscleKg?.let { parts += "SMM $it kg" }
            effectiveBodyFatPercent?.let { parts += "body fat ${String.format(Locale.US, "%.1f", it)}%" }
            visceralFatLevel?.let { parts += "visceral fat ${it.toInt()}" }
            bmrKcal?.let { parts += "BMR ${it.toInt()} kcal" }
            return parts.joinToString(", ")
        }
}

@Serializable
data class FoodProduct(
    val id: String = newId(),
    val nameAr: String = "",
    val nameEn: String = "",
    val brand: String = "",
    val barcode: String = "",
    val servingSizeG: Double? = null,
    val servingDescAr: String = "",
    val servingDescEn: String = "",
    val kcalPer100: Double = 0.0,
    val proteinPer100: Double = 0.0,
    val carbsPer100: Double = 0.0,
    val fatPer100: Double = 0.0,
    val fiberPer100: Double? = null,
    val sugarPer100: Double? = null,
    val sodiumMgPer100: Double? = null,
    val categoryRaw: String = "other",
    val sourceRaw: String = "label",
    /** Blob name of the product photo. */
    val photo: String? = null,
    val createdAt: Long = nowMillis(),
) {
    /** The iOS `uid`: the id Claude sees for library matches. */
    val uid: String get() = id

    val per100g: NutrientsPer100
        get() = NutrientsPer100(
            kcal = kcalPer100, proteinG = proteinPer100, carbsG = carbsPer100, fatG = fatPer100,
            fiberG = fiberPer100, sugarG = sugarPer100, sodiumMg = sodiumMgPer100,
        )

    val library: LibraryProduct
        get() = LibraryProduct(id = id, nameAr = nameAr, nameEn = nameEn, brand = brand, servingSizeG = servingSizeG, per100g = per100g)

    fun applying(extraction: ProductExtraction): FoodProduct = copy(
        nameAr = extraction.nameAr,
        nameEn = extraction.nameEn,
        brand = extraction.brand ?: "",
        barcode = extraction.barcode?.takeIf { it.isNotEmpty() } ?: barcode,
        servingSizeG = extraction.servingSizeG,
        servingDescAr = extraction.servingDescAr,
        servingDescEn = extraction.servingDescEn,
        kcalPer100 = extraction.per100g.kcal,
        proteinPer100 = extraction.per100g.proteinG,
        carbsPer100 = extraction.per100g.carbsG,
        fatPer100 = extraction.per100g.fatG,
        fiberPer100 = extraction.per100g.fiberG,
        sugarPer100 = extraction.per100g.sugarG,
        sodiumMgPer100 = extraction.per100g.sodiumMg,
    )
}

@Serializable
data class MealItem(
    val id: String = newId(),
    val order: Int = 0,
    val nameAr: String = "",
    val nameEn: String = "",
    val grams: Double = 0.0,
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    /** The library product this item came from, if any (cleared when the product is deleted). */
    val productId: String? = null,
) {
    val analyzed: AnalyzedFood
        get() = AnalyzedFood(
            nameAr = nameAr, nameEn = nameEn, grams = grams, kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
            confidence = Confidence.HIGH, libraryProductId = productId,
        )

    companion object {
        fun from(food: AnalyzedFood, order: Int) = MealItem(
            order = order, nameAr = food.nameAr, nameEn = food.nameEn, grams = food.grams, kcal = food.kcal,
            proteinG = food.proteinG, carbsG = food.carbsG, fatG = food.fatG, productId = food.libraryProductId,
        )
    }
}

@Serializable
data class MealEntry(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val slotRaw: String = MealSlot.LUNCH.raw,
    val titleAr: String = "",
    val titleEn: String = "",
    val sourceRaw: String = "photo",
    /** Blob name of the meal photo. */
    val photo: String? = null,
    val items: List<MealItem> = emptyList(),
    val savedToHealth: Boolean = false,
) {
    val slot: MealSlot get() = MealSlot.fromRaw(slotRaw) ?: MealSlot.LUNCH

    val sortedItems: List<MealItem> get() = items.sortedBy { it.order }

    val totals: MacroTargets get() = Portions.total(sortedItems.map { it.analyzed })

    val promptLine: String
        get() {
            val total = totals
            val names = sortedItems.joinToString(", ") { "${it.nameEn} ${it.grams.toInt()} g" }
            return "${slot.en} at ${date.shortTime()}: $names — ${total.calories} kcal, P ${total.proteinGrams} g"
        }
}

@Serializable
data class WorkoutEntry(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val nameAr: String = "",
    val nameEn: String = "",
    val kindRaw: String = WorkoutKind.OTHER.raw,
    val minutes: Double = 0.0,
    val kcal: Double = 0.0,
    val savedToHealth: Boolean = false,
) {
    val kind: WorkoutKind get() = WorkoutKind.fromRaw(kindRaw) ?: WorkoutKind.OTHER
}

enum class PlanStatus(val raw: String) {
    /** Claude is still writing it (possibly in the background). */
    BUILDING("building"),
    READY("ready"),
    FAILED("failed");

    companion object {
        fun fromRaw(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class MealPlanRecord(
    val id: String = newId(),
    val createdAt: Long = nowMillis(),
    val startDate: Long = nowMillis(),
    val presetRaw: String = PlanPreset.EGYPTIAN_HOME.raw,
    val mealsPerDayRaw: String = MealsPerDay.THREE_MEALS_TWO_SNACKS.raw,
    /** The user's extra request, kept so a failed plan can be tried again. */
    val requestText: String = "",
    val statusRaw: String = PlanStatus.READY.raw,
    val errorMessage: String = "",
    val plan: WeekPlan? = null,
    /** Grocery-list lines (GroceryLine.id) already bought. */
    val checkedGroceries: List<String> = emptyList(),
) {
    /** The iOS `uid`. */
    val uid: String get() = id

    val status: PlanStatus get() = PlanStatus.fromRaw(statusRaw) ?: PlanStatus.READY
    fun withStatus(value: PlanStatus) = copy(statusRaw = value.raw)

    val preset: PlanPreset get() = PlanPreset.fromRaw(presetRaw) ?: PlanPreset.EGYPTIAN_HOME
    val mealsPerDay: MealsPerDay get() = MealsPerDay.fromRaw(mealsPerDayRaw) ?: MealsPerDay.THREE_MEALS_TWO_SNACKS
    val mealCount: Int get() = 7 * mealsPerDay.slots(preset).size

    /** The plan day for `date`, or null when the date is outside the plan's week. */
    fun dayIndex(date: LocalDate): Int? {
        val offset = ChronoUnit.DAYS.between(startDate.toLocalDate(), date).toInt()
        return if (offset in 0 until 7) offset else null
    }

    fun date(ofDay: Int): LocalDate = startDate.toLocalDate().plusDays(ofDay.toLong())
}

@Serializable
data class CoachMessage(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val roleRaw: String = "user",
    val textAr: String = "",
    val textEn: String = "",
    val actions: List<CoachAction> = emptyList(),
) {
    val isUser: Boolean get() = roleRaw == "user"
}

enum class PhotoKind(val raw: String) {
    FACE_FRONT("faceFront"), FACE_RIGHT("faceRight"), FACE_LEFT("faceLeft"), BODY_FRONT("bodyFront"), BODY_SIDE("bodySide");

    companion object {
        fun fromRaw(raw: String) = entries.firstOrNull { it.raw == raw }
    }
}

@Serializable
data class ProgressPhoto(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val kindRaw: String = PhotoKind.BODY_FRONT.raw,
    /** Blob name of the image. */
    val image: String? = null,
) {
    val kind: PhotoKind get() = PhotoKind.fromRaw(kindRaw) ?: PhotoKind.BODY_FRONT
}

@Serializable
data class AvatarAsset(
    val id: String = newId(),
    val date: Long = nowMillis(),
    val fileName: String = "avatar.bin",
    /** Blob name of the avatar file. */
    val file: String? = null,
)

/** Everything Vitalyn stores, as one immutable value. */
@Serializable
data class StoreState(
    val profiles: List<UserProfile> = emptyList(),
    val scans: List<InBodyScan> = emptyList(),
    val products: List<FoodProduct> = emptyList(),
    val meals: List<MealEntry> = emptyList(),
    val workouts: List<WorkoutEntry> = emptyList(),
    val plans: List<MealPlanRecord> = emptyList(),
    val coachMessages: List<CoachMessage> = emptyList(),
    val progressPhotos: List<ProgressPhoto> = emptyList(),
    val avatars: List<AvatarAsset> = emptyList(),
) {
    /** The single profile (the first created), or null before onboarding. */
    val profile: UserProfile? get() = profiles.minByOrNull { it.createdAt }

    /** InBody scans, oldest first. */
    val sortedScans: List<InBodyScan> get() = scans.sortedBy { it.date }

    /** Library products, newest first. */
    val sortedProducts: List<FoodProduct> get() = products.sortedByDescending { it.createdAt }

    fun mealsOn(day: LocalDate): List<MealEntry> {
        val start = day.startMillis()
        val end = day.plusDays(1).startMillis()
        return meals.filter { it.date in start until end }.sortedBy { it.date }
    }

    /** Days with at least one logged meal in the last 120 days. */
    val loggedDays: Set<LocalDate>
        get() {
            val since = LocalDate.now().minusDays(120).startMillis()
            return meals.filter { it.date >= since }.map { it.date.toLocalDate() }.toSet()
        }

    fun product(id: String?): FoodProduct? = id?.let { pid -> products.firstOrNull { it.id == pid } }

    fun plan(id: String): MealPlanRecord? = plans.firstOrNull { it.id == id }

    /** Coach conversation, oldest first. */
    val sortedCoachMessages: List<CoachMessage> get() = coachMessages.sortedBy { it.date }

    // Edits that keep relationships consistent.

    fun withProfile(transform: (UserProfile) -> UserProfile): StoreState {
        val current = profile ?: return this
        return copy(profiles = profiles.map { if (it.id == current.id) transform(it) else it })
    }

    fun upsertScan(scan: InBodyScan) = copy(scans = scans.filter { it.id != scan.id } + scan)
    fun upsertProduct(product: FoodProduct) = copy(products = products.filter { it.id != product.id } + product)
    fun upsertMeal(meal: MealEntry) = copy(meals = meals.filter { it.id != meal.id } + meal)
    fun upsertWorkout(workout: WorkoutEntry) = copy(workouts = workouts.filter { it.id != workout.id } + workout)
    fun upsertPlan(plan: MealPlanRecord) = copy(plans = plans.filter { it.id != plan.id } + plan)
    fun updatePlan(id: String, transform: (MealPlanRecord) -> MealPlanRecord) =
        copy(plans = plans.map { if (it.id == id) transform(it) else it })

    /** Deletes a product; meal items that came from it keep their numbers but lose the link. */
    fun deleteProduct(id: String) = copy(
        products = products.filter { it.id != id },
        meals = meals.map { meal ->
            if (meal.items.none { it.productId == id }) meal
            else meal.copy(items = meal.items.map { if (it.productId == id) it.copy(productId = null) else it })
        },
    )
}
