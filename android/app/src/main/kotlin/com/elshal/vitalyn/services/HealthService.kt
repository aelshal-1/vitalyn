package com.elshal.vitalyn.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Volume
import com.elshal.vitalyn.core.MacroTargets
import com.elshal.vitalyn.core.WorkoutKind
import com.elshal.vitalyn.data.Prefs
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

data class DayValue(val date: LocalDate, val value: Double, val instant: Instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant())

data class HealthWorkout(
    val id: String,
    val name: String,
    val kind: WorkoutKind,
    val start: Instant,
    val minutes: Double,
    val kcal: Double,
)

/**
 * Reads activity and body data from Health Connect and writes weight, workouts and nutrition back
 * (the iOS HealthService over HealthKit). Values are Compose state.
 */
class HealthService(private val context: Context, private val prefs: Prefs) {
    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect exists but needs installing or updating (Android 13 and older). */
    val needsInstall: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private val client: HealthConnectClient? by lazy { if (isAvailable) HealthConnectClient.getOrCreate(context) else null }

    var requested by mutableStateOf(prefs.bool(REQUESTED_KEY))
        private set

    var stepsToday by mutableIntStateOf(0)
    var activeKcalToday by mutableIntStateOf(0)
    var distanceKmToday by mutableDoubleStateOf(0.0)
    var restingHeartRate by mutableStateOf<Int?>(null)
    var sleepHoursLastNight by mutableStateOf<Double?>(null)
    var weekSteps by mutableStateOf<List<DayValue>>(emptyList())
    var monthSteps by mutableStateOf<List<DayValue>>(emptyList())
    var weights by mutableStateOf<List<DayValue>>(emptyList())
    var workouts by mutableStateOf<List<HealthWorkout>>(emptyList())
    var bestStepsDay by mutableIntStateOf(0)
    var lastError by mutableStateOf<String?>(null)

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    val permissions: Set<String> get() = readPermissions + writePermissions

    /** Call with the result of the permission screen (see [rememberHealthPermissionRequest]). */
    suspend fun onPermissionsResult(granted: Set<String>) {
        if (granted.isNotEmpty()) {
            requested = true
            prefs.setBool(REQUESTED_KEY, true)
            refresh()
        }
    }

    /** Opens the Play Store page for Health Connect (needed on Android 13 and older). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun refresh() {
        val client = client ?: return
        if (!requested) return
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now()
        val startOfDay = today.atStartOfDay(zone).toInstant()

        stepsToday = safe(0L) { client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startOfDay, now)))[StepsRecord.COUNT_TOTAL] ?: 0L }.toInt()
        activeKcalToday = safe(0.0) { activeKcal(client, startOfDay, now) }.toInt()
        distanceKmToday = safe(0.0) {
            client.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), TimeRangeFilter.between(startOfDay, now)))[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
        }
        restingHeartRate = safe(null) { latest(client, RestingHeartRateRecord::class)?.beatsPerMinute?.toInt() }
        sleepHoursLastNight = safe(null) { sleepHours(client, now) }

        weekSteps = safe(emptyList()) { dailySteps(client, today.minusDays(6), now) }
        monthSteps = safe(emptyList()) { dailySteps(client, today.minusDays(29), now) }
        bestStepsDay = (monthSteps.maxOfOrNull { it.value } ?: 0.0).toInt()

        weights = safe(emptyList()) {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(now.minus(Duration.ofDays(180))), ascendingOrder = true))
                .records.map { DayValue(it.time.atZone(zone).toLocalDate(), it.weight.inKilograms, it.time) }
        }
        workouts = safe(emptyList()) { recentWorkouts(client, 14) }
    }

    // Reading

    private suspend fun <T> safe(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: Exception) {
        // A missing permission or an unavailable provider throws; show what we have.
        lastError = error.localizedMessage
        fallback
    }

    private suspend fun activeKcal(client: HealthConnectClient, start: Instant, end: Instant): Double =
        client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(start, end)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0

    private suspend fun dailySteps(client: HealthConnectClient, from: LocalDate, to: Instant): List<DayValue> {
        val end = LocalDateTime.ofInstant(to, ZoneId.systemDefault())
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), end),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        val byDay = groups.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble() }
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end.toLocalDate()) }
        return days.map { DayValue(it, byDay[it] ?: 0.0) }.toList()
    }

    private suspend fun <T : Record> latest(client: HealthConnectClient, type: KClass<T>): T? =
        client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.before(Instant.now().plusSeconds(60)), ascendingOrder = false, pageSize = 1)).records.firstOrNull()

    private suspend fun sleepHours(client: HealthConnectClient, end: Instant): Double? {
        val sessions = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(end.minus(Duration.ofHours(24)), end))).records
        if (sessions.isEmpty()) return null
        val awake = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
        val seconds = sessions.sumOf { session ->
            val total = Duration.between(session.startTime, session.endTime).seconds
            val awakeSeconds = session.stages.filter { it.stage in awake }.sumOf { Duration.between(it.startTime, it.endTime).seconds }
            (total - awakeSeconds).coerceAtLeast(0)
        }
        return if (seconds > 0) seconds / 3600.0 else null
    }

    private suspend fun recentWorkouts(client: HealthConnectClient, days: Long): List<HealthWorkout> {
        val sessions = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(days))), ascendingOrder = false)).records
        return sessions.map { session ->
            val kind = kind(session.exerciseType)
            HealthWorkout(
                id = session.metadata.id,
                name = session.title ?: kind.raw,
                kind = kind,
                start = session.startTime,
                minutes = Duration.between(session.startTime, session.endTime).seconds / 60.0,
                kcal = safe(0.0) { activeKcal(client, session.startTime, session.endTime) },
            )
        }
    }

    data class HealthProfile(
        val heightCm: Double? = null,
        val weightKg: Double? = null,
        val weightDate: Instant? = null,
        val bodyFatPercent: Double? = null,
        val bodyFatDate: Instant? = null,
        /** Health Connect has no sex or birth date; kept so screens read like the iOS version. */
        val sex: com.elshal.vitalyn.core.Sex? = null,
        val birthYear: Int? = null,
    ) {
        val isEmpty: Boolean get() = heightCm == null && weightKg == null && bodyFatPercent == null && sex == null && birthYear == null
    }

    suspend fun latestHeightCm(): Double? {
        val client = client ?: return null
        return safe(null) { latest(client, HeightRecord::class)?.height?.inMeters?.times(100) }
    }

    /** Everything Health Connect knows that the profile needs. */
    suspend fun profileFromHealth(): HealthProfile {
        val client = client ?: return HealthProfile()
        if (!requested) return HealthProfile()
        val weight = safe(null) { latest(client, WeightRecord::class) }
        val bodyFat = safe(null) { latest(client, BodyFatRecord::class) }
        return HealthProfile(
            heightCm = latestHeightCm(),
            weightKg = weight?.weight?.inKilograms,
            weightDate = weight?.time,
            bodyFatPercent = bodyFat?.percentage?.value,
            bodyFatDate = bodyFat?.time,
        )
    }

    // Writing

    private fun offset(at: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(at)

    private suspend fun insert(records: List<Record>): Boolean {
        val client = client ?: return false
        if (!requested) return false
        return try {
            client.insertRecords(records)
            true
        } catch (error: Exception) {
            lastError = error.localizedMessage
            false
        }
    }

    suspend fun saveBody(weightKg: Double, bodyFatPercent: Double?, leanMassKg: Double?, date: Instant) {
        val records = mutableListOf<Record>(WeightRecord(time = date, zoneOffset = offset(date), weight = Mass.kilograms(weightKg)))
        bodyFatPercent?.let { records += BodyFatRecord(time = date, zoneOffset = offset(date), percentage = Percentage(it)) }
        leanMassKg?.let { records += LeanBodyMassRecord(time = date, zoneOffset = offset(date), mass = Mass.kilograms(it)) }
        insert(records)
    }

    suspend fun saveNutrition(totals: MacroTargets, date: Instant): Boolean = insert(
        listOf(
            NutritionRecord(
                startTime = date,
                startZoneOffset = offset(date),
                endTime = date.plusSeconds(60),
                endZoneOffset = offset(date),
                energy = Energy.kilocalories(totals.calories.toDouble()),
                protein = Mass.grams(totals.proteinGrams.toDouble()),
                totalCarbohydrate = Mass.grams(totals.carbsGrams.toDouble()),
                totalFat = Mass.grams(totals.fatGrams.toDouble()),
            ),
        ),
    )

    suspend fun saveWater(glasses: Int, date: Instant) {
        if (glasses <= 0) return
        insert(
            listOf(
                HydrationRecord(
                    startTime = date,
                    startZoneOffset = offset(date),
                    endTime = date.plusSeconds(60),
                    endZoneOffset = offset(date),
                    volume = Volume.milliliters(glasses * 250.0),
                ),
            ),
        )
    }

    suspend fun saveWorkout(kind: WorkoutKind, start: Instant, minutes: Double, kcal: Double): Boolean {
        val end = start.plusSeconds((minutes * 60).toLong().coerceAtLeast(60))
        val records = mutableListOf<Record>(
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset(start),
                endTime = end,
                endZoneOffset = offset(end),
                exerciseType = exerciseType(kind),
                title = kind.en,
            ),
        )
        if (kcal > 0) {
            records += ActiveCaloriesBurnedRecord(
                startTime = start,
                startZoneOffset = offset(start),
                endTime = end,
                endZoneOffset = offset(end),
                energy = Energy.kilocalories(kcal),
            )
        }
        val saved = insert(records)
        if (saved) refresh()
        return saved
    }

    companion object {
        private const val REQUESTED_KEY = "health-requested"

        fun exerciseType(kind: WorkoutKind): Int = when (kind) {
            WorkoutKind.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            WorkoutKind.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            WorkoutKind.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            WorkoutKind.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            WorkoutKind.FOOTBALL -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
            WorkoutKind.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            WorkoutKind.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            WorkoutKind.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            WorkoutKind.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }

        fun kind(type: Int): WorkoutKind = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutKind.WALKING
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutKind.RUNNING
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutKind.CYCLING
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WorkoutKind.SWIMMING
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> WorkoutKind.FOOTBALL
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> WorkoutKind.STRENGTH
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WorkoutKind.HIIT
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> WorkoutKind.YOGA
            else -> WorkoutKind.OTHER
        }
    }
}

/**
 * Returns a function that opens Health Connect's permission screen; when the user is done,
 * [HealthService.onPermissionsResult] runs. Use it for "Connect Health Connect" buttons.
 */
@Composable
fun rememberHealthPermissionRequest(health: HealthService, onDone: suspend (Set<String>) -> Unit = {}): () -> Unit {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            health.onPermissionsResult(granted)
            onDone(granted)
        }
    }
    return {
        when {
            health.isAvailable -> launcher.launch(health.permissions)
            else -> runCatching { context.startActivity(health.installIntent()) }
        }
    }
}

val WorkoutKind.ar: String
    get() = when (this) {
        WorkoutKind.WALKING -> "مشي"
        WorkoutKind.RUNNING -> "جري"
        WorkoutKind.CYCLING -> "عجلة"
        WorkoutKind.SWIMMING -> "سباحة"
        WorkoutKind.FOOTBALL -> "كورة"
        WorkoutKind.STRENGTH -> "أوزان"
        WorkoutKind.HIIT -> "HIIT"
        WorkoutKind.YOGA -> "يوجا"
        WorkoutKind.OTHER -> "تمرين"
    }

val WorkoutKind.en: String
    get() = when (this) {
        WorkoutKind.WALKING -> "Walking"
        WorkoutKind.RUNNING -> "Running"
        WorkoutKind.CYCLING -> "Cycling"
        WorkoutKind.SWIMMING -> "Swimming"
        WorkoutKind.FOOTBALL -> "Football"
        WorkoutKind.STRENGTH -> "Strength"
        WorkoutKind.HIIT -> "HIIT"
        WorkoutKind.YOGA -> "Yoga"
        WorkoutKind.OTHER -> "Workout"
    }

/** Material icon for the iOS SF Symbol of each workout kind. */
val WorkoutKind.icon: ImageVector
    get() = when (this) {
        WorkoutKind.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
        WorkoutKind.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
        WorkoutKind.CYCLING -> Icons.AutoMirrored.Rounded.DirectionsBike
        WorkoutKind.SWIMMING -> Icons.Rounded.Pool
        WorkoutKind.FOOTBALL -> Icons.Rounded.SportsSoccer
        WorkoutKind.STRENGTH -> Icons.Rounded.FitnessCenter
        WorkoutKind.HIIT -> Icons.Rounded.Bolt
        WorkoutKind.YOGA -> Icons.Rounded.SelfImprovement
        WorkoutKind.OTHER -> Icons.Rounded.SportsGymnastics
    }
