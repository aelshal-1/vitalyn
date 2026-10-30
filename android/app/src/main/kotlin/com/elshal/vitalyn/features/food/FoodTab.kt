package com.elshal.vitalyn.features.food

enum class FoodTab(val raw: String, val ar: String, val en: String) {
    LOG("log", "سجل الأكل", "Food log"),
    PRODUCTS("products", "منتجاتي", "Products"),
    PLAN("plan", "الجدول", "Weekly plan");

    companion object {
        /** Stored so other screens (Today's plan card, notifications) can open a specific tab. */
        const val STORAGE_KEY = "food-tab"

        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}
