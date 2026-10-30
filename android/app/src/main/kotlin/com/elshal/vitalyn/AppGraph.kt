package com.elshal.vitalyn

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.elshal.vitalyn.data.AppData
import com.elshal.vitalyn.data.Prefs
import com.elshal.vitalyn.data.SnapshotStore
import com.elshal.vitalyn.data.VitalynStore
import com.elshal.vitalyn.features.food.FoodTab
import com.elshal.vitalyn.services.AIService
import com.elshal.vitalyn.services.BackgroundAI
import com.elshal.vitalyn.services.HealthService
import com.elshal.vitalyn.services.NotificationService
import com.elshal.vitalyn.widget.VitalynWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where tapping a notification takes the user. */
enum class OpenTarget(val raw: String) {
    PLAN("plan"), COACH("coach");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

/**
 * The app's long-lived services, created once per process (the iOS app's environment objects).
 * Composables reach it through [LocalApp]; workers and receivers through [AppGraph.get].
 */
class AppGraph(val context: Context) {
    val prefs = Prefs(context)
    val store = VitalynStore(context)
    val health = HealthService(context, prefs)
    val ai = AIService(context, prefs)
    val jobs = BackgroundAI(this)

    /** App-wide scope for work that must outlive a screen (saving to Health Connect, etc.). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Set when a notification is tapped; the root screen opens it and clears it. */
    var openTarget by mutableStateOf<OpenTarget?>(null)

    private var _foodTab by mutableStateOf(FoodTab.fromRaw(prefs.string(FoodTab.STORAGE_KEY)) ?: FoodTab.LOG)

    /** The Food screen's selected section, remembered across launches. */
    var foodTab: FoodTab
        get() = _foodTab
        set(value) {
            _foodTab = value
            prefs.setString(FoodTab.STORAGE_KEY, value.raw)
        }

    /** Recomputes today's summary for the widgets, reloads them and announces new badges. */
    fun publishSnapshot() {
        AppData.snapshot(store.value, health, prefs)?.let { SnapshotStore.save(prefs, it) }
        scope.launch { VitalynWidgets.updateAll(context) }
        NotificationService.announceNewBadges(this)
    }

    /** Foreground refresh: background jobs, Health Connect, widgets, motivation notifications. */
    suspend fun refresh() {
        jobs.restore()
        health.refresh()
        publishSnapshot()
        NotificationService.refreshMotivation(this)
    }

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) { instance ?: AppGraph(context.applicationContext).also { instance = it } }
    }
}

val LocalApp = staticCompositionLocalOf<AppGraph> { error("LocalApp is provided by MainActivity") }

class VitalynApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationService.createChannels(this)
        AppGraph.get(this)
    }
}
