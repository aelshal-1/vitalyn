package com.elshal.vitalyn.core

import kotlinx.serialization.Serializable
import kotlin.math.max

/** A bilingual motivational notification. */
@Serializable
data class MotivationMessage(
    val titleAr: String,
    val titleEn: String,
    val bodyAr: String,
    val bodyEn: String
)

@Serializable
data class MotivationBatch(val messages: List<MotivationMessage>)

/** The user's progress, used to personalise messages. */
data class MotivationInput(
    val name: String,
    val streakDays: Int,
    val lostKg: Double,
    val remainingKg: Double,
    val proteinTarget: Int,
    val stepsGoal: Int
)

/** Built-in messages, used when Claude isn't available. Progress-based ones come first when they apply. */
object Motivation {
    fun messages(input: MotivationInput, count: Int, seed: Int): List<MotivationMessage> {
        val name = input.name.split(' ').firstOrNull { it.isNotEmpty() } ?: ""
        val hi = if (name.isEmpty()) "" else "$name، "
        val hiEn = if (name.isEmpty()) "" else "$name, "
        val personal = mutableListOf<MotivationMessage>()

        if (input.lostKg >= 0.5) {
            val kg = format(input.lostKg)
            personal += MotivationMessage(
                titleAr = "نزلت $kg كجم 💪", titleEn = "$kg kg down 💪",
                bodyAr = "${hi}ده مجهودك انت. كمّل بنفس الهدوء.", bodyEn = "${hiEn}that's all your work. Keep the same steady pace."
            )
        }
        if (input.streakDays >= 3) {
            personal += MotivationMessage(
                titleAr = "${input.streakDays} يوم ورا بعض 🔥", titleEn = "${input.streakDays}-day streak 🔥",
                bodyAr = "متقطعش السلسلة النهارده — سجّل أول وجبة.", bodyEn = "Don't break the chain today — log your first meal."
            )
        }
        if (input.remainingKg > 0.5) {
            val kg = format(input.remainingKg)
            personal += MotivationMessage(
                titleAr = "فاضل $kg كجم على هدفك 🎯", titleEn = "$kg kg to your goal 🎯",
                bodyAr = "كل يوم ملتزم فيه بيقرّبك. انت أقرب من امبارح.", bodyEn = "Every on-plan day gets you closer. You're closer than yesterday."
            )
        }

        val general = listOf(
            MotivationMessage(titleAr = "اشرب مية 💧", titleEn = "Drink some water 💧",
                bodyAr = "كوباية مية دلوقتي بتقلل الجوع وبتفوقك.", bodyEn = "A glass of water now curbs hunger and wakes you up."),
            MotivationMessage(titleAr = "البروتين أولاً 🍗", titleEn = "Protein first 🍗",
                bodyAr = "هدفك ${input.proteinTarget} جم بروتين النهارده — خلّي كل وجبة فيها حاجة منه.", bodyEn = "Your target is ${input.proteinTarget} g protein today — put some in every meal."),
            MotivationMessage(titleAr = "اتحرك شوية 🚶", titleEn = "Move a little 🚶",
                bodyAr = "عشر دقايق مشي دلوقتي بيقرّبوك من ${input.stepsGoal} خطوة.", bodyEn = "Ten minutes of walking now gets you closer to ${input.stepsGoal} steps."),
            MotivationMessage(titleAr = "مش لازم تكون مثالي ✨", titleEn = "You don't need to be perfect ✨",
                bodyAr = "يوم مش مظبوط مش بيبوظ الرحلة. المهم ترجع الوجبة الجاية.", bodyEn = "One off day doesn't ruin the journey. Just get back on track at the next meal."),
            MotivationMessage(titleAr = "صوّر قبل ما تاكل 📸", titleEn = "Snap before you eat 📸",
                bodyAr = "صورة واحدة وVitalyn يحسبلك كل حاجة.", bodyEn = "One photo and Vitalyn counts everything for you."),
            MotivationMessage(titleAr = "النوم جزء من الدايت 😴", titleEn = "Sleep is part of the diet 😴",
                bodyAr = "نوم 7 ساعات بيقلل الجوع والرغبة في السكريات.", bodyEn = "Seven hours of sleep reduces hunger and sugar cravings."),
            MotivationMessage(titleAr = "انت بتعمل ده لنفسك ❤️", titleEn = "You're doing this for you ❤️",
                bodyAr = "${hi}صحتك تستاهل. خطوة صغيرة النهارده تكفي.", bodyEn = "${hiEn}your health is worth it. One small step today is enough."),
            MotivationMessage(titleAr = "خضار في كل طبق 🥗", titleEn = "Veggies on every plate 🥗",
                bodyAr = "نص الطبق خضار بيشبعك بسعرات أقل.", bodyEn = "Half a plate of vegetables fills you up for fewer calories.")
        )

        val start = if (general.isEmpty()) 0 else ((seed % general.size) + general.size) % general.size
        val rotated = general.subList(start, general.size) + general.subList(0, start)
        return (personal + rotated).take(max(0, count))
    }

    internal fun format(value: Double): String = formatOneDecimal(value)
}
