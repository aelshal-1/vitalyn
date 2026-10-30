package com.elshal.vitalyn.services

import com.elshal.vitalyn.AppGraph
import com.elshal.vitalyn.core.CoachAction
import com.elshal.vitalyn.core.CoachActionType
import com.elshal.vitalyn.data.WaterStore
import com.elshal.vitalyn.data.WorkoutEntry
import kotlinx.coroutines.launch
import java.time.Instant

/** Carries out what the coach decided (log a workout or water); meals and videos are shown as buttons. */
object CoachActions {
    fun apply(action: CoachAction, graph: AppGraph) {
        when (action.type) {
            CoachActionType.LOG_WORKOUT -> {
                val workout = action.workout ?: return
                val start = Instant.now().minusSeconds((workout.minutes * 60).toLong())
                val entry = WorkoutEntry(
                    date = start.toEpochMilli(),
                    nameAr = workout.nameAr,
                    nameEn = workout.nameEn,
                    kindRaw = workout.kind.raw,
                    minutes = workout.minutes,
                    kcal = workout.kcal,
                )
                graph.store.update { it.upsertWorkout(entry) }
                graph.scope.launch {
                    val saved = graph.health.saveWorkout(workout.kind, start, workout.minutes, workout.kcal)
                    graph.store.update { state -> state.upsertWorkout(entry.copy(savedToHealth = saved)) }
                    graph.publishSnapshot()
                }
            }
            CoachActionType.LOG_WATER -> {
                val glasses = action.waterGlasses ?: 1
                WaterStore.add(graph.prefs, glasses)
                graph.scope.launch { graph.health.saveWater(glasses, Instant.now()) }
                graph.publishSnapshot()
            }
            CoachActionType.LOG_MEAL, CoachActionType.SUGGEST_VIDEOS -> Unit
        }
    }
}
