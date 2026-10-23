package app.dropi.work

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import app.dropi.Prefs
import app.dropi.data.Db
import app.dropi.data.Glucose
import app.dropi.data.MealTag
import app.dropi.data.Reading
import app.dropi.data.Tagging
import java.time.Instant
import java.time.ZoneId

/**
 * Two-way sync with Health Connect, for blood glucose only.
 * - Out: every reading typed in Dropi is written there (and updated or removed with it), keyed
 *   by our own id so it never doubles up.
 * - In: readings other apps wrote (a CGM app, another meter app) come into Dropi, once each.
 *   One deleted in Dropi stays deleted.
 */
object HealthSync {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
    )

    private const val DAY = 86_400_000L

    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect needs installing or updating (Android 13 and older). */
    fun needsInstall(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context.applicationContext)

    suspend fun granted(context: Context): Boolean = try {
        available(context) && client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    } catch (e: Exception) { false }

    private fun clientId(r: Reading) = "dropi-${r.id}"

    private fun toRecord(r: Reading, zone: ZoneId): BloodGlucoseRecord {
        val instant = Instant.ofEpochMilli(r.at)
        val relation = when (r.tag) {
            MealTag.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
            MealTag.BEFORE_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
            MealTag.AFTER_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
            else -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
        }
        val hour = instant.atZone(zone).hour
        val meal = when {
            r.tag != MealTag.BEFORE_MEAL && r.tag != MealTag.AFTER_MEAL -> MealType.MEAL_TYPE_UNKNOWN
            hour in 4..10 -> MealType.MEAL_TYPE_BREAKFAST
            hour in 11..15 -> MealType.MEAL_TYPE_LUNCH
            else -> MealType.MEAL_TYPE_DINNER
        }
        return BloodGlucoseRecord(
            time = instant,
            zoneOffset = zone.rules.getOffset(instant),
            level = BloodGlucose.millimolesPerLiter(r.mmol),
            specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
            mealType = meal,
            relationToMeal = relation,
            metadata = Metadata.manualEntry(clientRecordId = clientId(r), clientRecordVersion = System.currentTimeMillis()),
        )
    }

    /** Writes (or updates) Dropi's own readings. Imported ones are left alone. */
    suspend fun push(context: Context, readings: List<Reading>) {
        if (app.dropi.Prefs(context).demo) return // demo readings never leave Dropi
        if (!Prefs(context).healthSync || !granted(context)) return
        val own = readings.filter { !it.imported && it.id != 0L }
        if (own.isEmpty()) return
        val zone = ZoneId.systemDefault()
        own.chunked(100).forEach { batch -> client(context).insertRecords(batch.map { toRecord(it, zone) }) }
    }

    /** Removes a deleted reading: ours from Health Connect, an imported one stays ignored. */
    suspend fun remove(context: Context, r: Reading) {
        if (app.dropi.Prefs(context).demo) return
        val p = Prefs(context)
        if (r.imported) {
            p.ignoredSources = p.ignoredSources + r.source
            return
        }
        if (!p.healthSync || !granted(context)) return
        try {
            client(context).deleteRecords(BloodGlucoseRecord::class, emptyList(), listOf(clientId(r)))
        } catch (e: Exception) { }
    }

    /** Brings in readings other apps wrote in the last [days] days. Returns how many are new. */
    suspend fun pull(context: Context, days: Int = 90): Int {
        if (app.dropi.Prefs(context).demo) return 0
        val p = Prefs(context)
        if (!p.healthSync || !granted(context)) return 0
        val now = Instant.now()
        val range = TimeRangeFilter.between(now.minusMillis(days * DAY), now)
        val zone = ZoneId.systemDefault()
        val db = Db.get(context)
        val have = db.sources() + p.ignoredSources
        val existing = db.since(now.toEpochMilli() - days * DAY)
        var added = 0
        val sensor = ArrayList<app.dropi.data.SensorPoint>()
        var pageToken: String? = null
        var pages = 0
        do {
            val res = client(context).readRecords(
                ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = range, pageToken = pageToken)
            )
            for (rec in res.records) {
                if (rec.metadata.dataOrigin.packageName == context.packageName) continue
                val source = "hc:${rec.metadata.id}"
                if (source in have) continue
                val at = rec.time.toEpochMilli()
                val mmol = rec.level.inMillimolesPerLiter
                if (mmol < 0.5 || mmol > 40) continue
                // A CGM's values (from under the skin) are sensor points, not log entries.
                if (rec.specimenSource == BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID) {
                    if (now.toEpochMilli() - at <= 31 * DAY) sensor += app.dropi.data.SensorPoint(at, mmol.coerceIn(Glucose.MIN_MMOL, Glucose.MAX_MMOL))
                    continue
                }
                // The same reading typed in Dropi too (same minute, same value): don't double it.
                if (existing.any { kotlin.math.abs(it.at - at) < 60_000 && kotlin.math.abs(it.mmol - mmol) < 0.05 }) continue
                val tag = when (rec.relationToMeal) {
                    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> MealTag.FASTING
                    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> MealTag.BEFORE_MEAL
                    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> MealTag.AFTER_MEAL
                    else -> Tagging.suggest(at, existing, zone)
                }
                db.save(Reading(at = at, mmol = mmol.coerceIn(Glucose.MIN_MMOL, Glucose.MAX_MMOL), tag = tag, source = source))
                added++
            }
            pageToken = res.pageToken
            pages++
        } while (pageToken != null && pages < 40)
        if (sensor.isNotEmpty() && db.addSensor(sensor) > 0) LiveCgm.changed(context)
        p.lastHealthSync = System.currentTimeMillis()
        return added
    }
}
