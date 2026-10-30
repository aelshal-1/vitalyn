package com.elshal.vitalyn.features.food

import com.elshal.vitalyn.core.MealLog
import com.elshal.vitalyn.core.MealSlot
import com.elshal.vitalyn.data.FoodProduct

/** How a meal is being entered (opens [MealCaptureSheet]). */
sealed class MealInput {
    data object Photo : MealInput()
    data object Text : MealInput()
    data class Product(val product: FoodProduct) : MealInput()
    data class Prefilled(val log: MealLog, val slot: MealSlot? = null) : MealInput()
}
