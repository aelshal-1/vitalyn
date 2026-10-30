package com.elshal.vitalyn.data

import com.elshal.vitalyn.core.*
import com.elshal.vitalyn.services.HealthService
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Read-side helpers that combine the store, Health Connect and the calculator (the iOS `AppData`). */
object AppData {
    fun currentWeight(profile: UserProfile, scans: List<InBodyScan>, health: HealthService): Double {
        val scan = scans.lastOrNull()
        val healthLatest = health.weights.lastOrNull()
        return when {
            scan != null && healthLatest != null ->
                if (healthLatest.instant.toEpochMilli() > scan.date) healthLatest.value else scan.weightKg
            scan != null -> scan.weightKg
            healthLatest != null -> healthLatest.value
            else -> profile.startWeightKg
        }
    }

    fun bodyMetrics(profile: UserProfile, scans: List<InBodyScan>, health: HealthService): BodyMetrics {
        val latest = scans.lastOrNull()
        return BodyMetrics(
            weightKg = currentWeight(profile, scans, health),
            heightCm = profile.heightCm,
            ageYears = profile.ageYears,
            sex = profile.sex,
            bodyFatPercent = latest?.effectiveBodyFatPercent,
            measuredBMR = if (profile.manualBMR > 0) profile.manualBMR else latest?.bmrKcal,
        )
    }

    data class CalculatedBMR(val kcal: Int, val sourceAr: String, val sourceEn: String)

    /** The BMR the app would use without a manual value, and where it comes from. */
    fun calculatedBMR(profile: UserProfile, scans: List<InBodyScan>, health: HealthService): CalculatedBMR {
        scans.lastOrNull()?.bmrKcal?.let { return CalculatedBMR(it.roundToInt(), "من آخر InBody", "from your last InBody") }
        val body = bodyMetrics(profile, scans, health).copy(measuredBMR = null)
        val value = NutritionCalculator.bmr(body).roundToInt()
        return if (body.leanMassKg != null) {
            CalculatedBMR(value, "محسوب من كتلة جسمك الصافية", "calculated from lean mass")
        } else {
            CalculatedBMR(value, "محسوب من الطول والوزن والسن", "calculated from height, weight and age")
        }
    }

    fun targets(profile: UserProfile, scans: List<InBodyScan>, health: HealthService): MacroTargets =
        NutritionCalculator.targets(
            body = bodyMetrics(profile, scans, health),
            activityFactor = profile.activity.factor,
            weeklyLossKg = profile.weeklyLossKg,
            conditions = profile.conditions,
        )

    fun eatenToday(state: StoreState): MacroTargets =
        Portions.total(state.mealsOn(LocalDate.now()).flatMap { meal -> meal.sortedItems.map { it.analyzed } })

    fun userContext(state: StoreState, health: HealthService): UserContext? {
        val profile = state.profile ?: return null
        val scans = state.sortedScans
        val meals = state.mealsOn(LocalDate.now())
        val weight = currentWeight(profile, scans, health)
        val first = scans.firstOrNull()
        val trend = when {
            first != null && scans.size > 1 -> "First scan ${first.weightKg} kg on ${first.date.isoDay()}; now $weight kg."
            health.weights.isNotEmpty() -> health.weights.first().let { "Health weight ${it.value} kg on ${it.date}; now $weight kg." }
            else -> null
        }
        return UserContext(
            name = profile.name,
            ageYears = profile.ageYears,
            sex = profile.sex,
            heightCm = profile.heightCm,
            weightKg = weight,
            goalWeightKg = profile.goalWeightKg,
            weeklyLossKg = profile.weeklyLossKg,
            activity = profile.activity,
            conditions = profile.conditions,
            otherConditions = profile.otherConditions,
            medications = profile.medications,
            allergies = profile.allergies,
            preferences = profile.preferences,
            targets = targets(profile, scans, health),
            eatenToday = Portions.total(meals.flatMap { meal -> meal.sortedItems.map { it.analyzed } }),
            mealsToday = meals.map { it.promptLine },
            stepsToday = health.stepsToday,
            activeCaloriesToday = health.activeKcalToday,
            latestInBody = scans.lastOrNull()?.promptSummary,
            weightTrend = trend,
            healthInsights = profile.currentHealthInsights.map {
                "${it.nameEn} (typed \"${it.input}\", ${it.kind.raw}): ${it.whatItIsEn} ${it.notesEn}"
            },
        )
    }

    /** Today's summary for the widgets. */
    fun snapshot(state: StoreState, health: HealthService, prefs: Prefs): TodaySnapshot? {
        val profile = state.profile ?: return null
        val scans = state.sortedScans
        val eaten = eatenToday(state)
        val weight = currentWeight(profile, scans, health)
        val start = scans.firstOrNull()?.weightKg ?: profile.startWeightKg
        return TodaySnapshot(
            date = Instant.now(),
            targets = targets(profile, scans, health),
            eatenCalories = eaten.calories,
            activeCalories = health.activeKcalToday,
            proteinGrams = eaten.proteinGrams,
            carbsGrams = eaten.carbsGrams,
            fatGrams = eaten.fatGrams,
            steps = health.stepsToday,
            stepsGoal = profile.stepsGoal,
            waterGlasses = WaterStore.glasses(prefs),
            waterGoal = profile.waterGoal,
            streakDays = ProgressMath.streak(state.loggedDays),
            weightKg = weight,
            weightChangeKg = weight - start,
        )
    }

    fun achievementInput(state: StoreState, health: HealthService): AchievementInput? {
        val profile = state.profile ?: return null
        val scans = state.sortedScans
        return AchievementInput(
            scanCount = scans.size,
            startWeightKg = scans.firstOrNull()?.weightKg ?: profile.startWeightKg,
            currentWeightKg = currentWeight(profile, scans, health),
            streakDays = ProgressMath.streak(state.loggedDays),
            bestStepsDay = health.bestStepsDay,
            loggedMealCount = state.meals.size,
            goalWeightKg = profile.goalWeightKg,
        )
    }
}
