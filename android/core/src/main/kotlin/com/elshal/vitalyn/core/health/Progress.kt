package com.elshal.vitalyn.core

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Streaks, achievements and the body "journey" between scans. */
object ProgressMath {
    /**
     * Consecutive days with at least one logged meal, ending today (or yesterday if today has nothing yet).
     * Days are calendar dates in the user's zone (e.g. `instant.atZone(ZoneId.systemDefault()).toLocalDate()`).
     */
    fun streak(loggedDays: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var cursor = today
        if (cursor !in loggedDays) {
            val yesterday = cursor.minusDays(1)
            if (yesterday !in loggedDays) return 0
            cursor = yesterday
        }
        var count = 0
        while (cursor in loggedDays) {
            count += 1
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Linear interpolation from first scan → now → goal, `t` in 0...1 where 0.5 is now. */
    fun journey(first: Double, now: Double, goal: Double, t: Double): Double {
        val t = min(1.0, max(0.0, t))
        if (t <= 0.5) return first + (now - first) * (t / 0.5)
        return now + (goal - now) * ((t - 0.5) / 0.5)
    }
}

data class Badge(
    val id: String,
    val ar: String,
    val en: String,
    /** SF Symbol name on iOS; map it to an icon on Android. */
    val symbol: String,
    val achieved: Boolean
)

data class AchievementInput(
    val scanCount: Int,
    val startWeightKg: Double?,
    val currentWeightKg: Double?,
    val streakDays: Int,
    val bestStepsDay: Int,
    val loggedMealCount: Int,
    val goalWeightKg: Double?
) {
    val lostKg: Double
        get() {
            val start = startWeightKg ?: return 0.0
            val current = currentWeightKg ?: return 0.0
            return max(0.0, start - current)
        }
}

object Achievements {
    fun badges(input: AchievementInput): List<Badge> {
        val goal = input.goalWeightKg
        val current = input.currentWeightKg
        val reachedGoal = goal != null && current != null && current <= goal
        return listOf(
            Badge(id = "first-meal", ar = "أول وجبة", en = "First meal", symbol = "fork.knife", achieved = input.loggedMealCount >= 1),
            Badge(id = "first-inbody", ar = "أول InBody", en = "First InBody", symbol = "doc.text.magnifyingglass", achieved = input.scanCount >= 1),
            Badge(id = "streak-7", ar = "7 أيام التزام", en = "7-day streak", symbol = "flame", achieved = input.streakDays >= 7),
            Badge(id = "streak-21", ar = "21 يوم", en = "21-day streak", symbol = "flame.fill", achieved = input.streakDays >= 21),
            Badge(id = "steps-10k", ar = "10,000 خطوة", en = "10k steps", symbol = "figure.walk", achieved = input.bestStepsDay >= 10000),
            Badge(id = "lost-3", ar = "أول 3 كيلو", en = "First 3 kg", symbol = "arrow.down.circle", achieved = input.lostKg >= 3),
            Badge(id = "lost-5", ar = "أول 5 كيلو", en = "First 5 kg", symbol = "arrow.down.circle.fill", achieved = input.lostKg >= 5),
            Badge(id = "lost-10", ar = "10 كيلو", en = "10 kg down", symbol = "star.circle", achieved = input.lostKg >= 10),
            Badge(id = "goal", ar = "الهدف", en = "Goal reached", symbol = "trophy", achieved = reachedGoal)
        )
    }
}
