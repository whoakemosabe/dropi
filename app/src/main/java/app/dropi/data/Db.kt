package app.dropi.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Readings in a small SQLite database on the phone ("dropi.db"). Plain SQLite, no annotation
 * processing: one table, a time index, and every call off the main thread.
 */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dropi.db", null, VERSION) {

    companion object {
        private const val VERSION = 5

        @Volatile private var instance: Db? = null
        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(context).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                at INTEGER NOT NULL,
                mmol REAL NOT NULL,
                tag TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                source TEXT NOT NULL DEFAULT '',
                carbs INTEGER,
                insulin REAL,
                insulin_kind TEXT,
                meds INTEGER NOT NULL DEFAULT 0,
                exercise INTEGER,
                foods TEXT NOT NULL DEFAULT '',
                meal TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX readings_at ON readings(at)")
        db.execSQL("CREATE INDEX readings_source ON readings(source)")
        createSensor(db)
    }

    /** Sensor (CGM) points, one per minute at most: kept apart so they never flood the log. */
    private fun createSensor(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sensor (minute INTEGER PRIMARY KEY, at INTEGER NOT NULL, mmol REAL NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Columns are only ever added, never dropped, so readings survive every update.
        if (oldVersion < 2) {
            // Where a reading came from: "" for typed in Dropi, "hc:<id>" for Health Connect.
            db.execSQL("ALTER TABLE readings ADD COLUMN source TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX readings_source ON readings(source)")
        }
        if (oldVersion < 3) {
            // Optional details on a reading: carbs, insulin, tablets, exercise.
            db.execSQL("ALTER TABLE readings ADD COLUMN carbs INTEGER")
            db.execSQL("ALTER TABLE readings ADD COLUMN insulin REAL")
            db.execSQL("ALTER TABLE readings ADD COLUMN insulin_kind TEXT")
            db.execSQL("ALTER TABLE readings ADD COLUMN meds INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE readings ADD COLUMN exercise INTEGER")
        }
        if (oldVersion < 4) {
            // Foods eaten with a reading, and sensor points from a CGM.
            db.execSQL("ALTER TABLE readings ADD COLUMN foods TEXT NOT NULL DEFAULT ''")
            createSensor(db)
        }
        if (oldVersion < 5) {
            // The meal's breakdown (foods, portions, carbs, sugars, fibre) as JSON.
            db.execSQL("ALTER TABLE readings ADD COLUMN meal TEXT")
        }
    }

    private fun values(r: Reading) = ContentValues().apply {
        put("at", r.at)
        put("mmol", r.mmol)
        put("tag", r.tag.name)
        put("note", r.note.trim().take(200))
        put("source", r.source)
        if (r.carbs != null) put("carbs", r.carbs) else putNull("carbs")
        if (r.insulin != null) put("insulin", r.insulin) else putNull("insulin")
        if (r.insulinKind != null) put("insulin_kind", r.insulinKind.name) else putNull("insulin_kind")
        put("meds", if (r.meds) 1 else 0)
        if (r.exercise != null) put("exercise", r.exercise) else putNull("exercise")
        put("foods", Foods.pack(r.foods))
        if (r.meal != null) put("meal", r.meal.toJson()) else putNull("meal")
    }

    /** Saves a new reading (id 0) or updates an existing one; returns the saved reading. */
    suspend fun save(r: Reading): Reading = withContext(Dispatchers.IO) {
        val db = writableDatabase
        if (r.id == 0L) {
            val id = db.insertOrThrow("readings", null, values(r))
            r.copy(id = id)
        } else {
            db.update("readings", values(r), "id = ?", arrayOf(r.id.toString()))
            r
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("readings", "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** Every reading ever logged, oldest first (History and backups). */
    suspend fun all(): List<Reading> = since(Long.MIN_VALUE)

    /** Adds readings from a backup, skipping any already here (same minute and value). */
    suspend fun merge(incoming: List<Reading>): Int = withContext(Dispatchers.IO) {
        val have = since(Long.MIN_VALUE)
        val keys = have.map { (it.at / 60_000) to Math.round(it.mmol * 10) }.toHashSet()
        var added = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in incoming) {
                val k = (r.at / 60_000) to Math.round(r.mmol * 10)
                if (k in keys) continue
                db.insertOrThrow("readings", null, values(r.copy(id = 0)))
                keys += k
                added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        added
    }

    /** The sources already imported (Health Connect ids), so nothing comes in twice. */
    suspend fun sources(): Set<String> = withContext(Dispatchers.IO) {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT source FROM readings WHERE source != ''", null).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        out
    }

    /** Readings taken at or after [since] (epoch ms), oldest first. */
    suspend fun since(since: Long): List<Reading> = withContext(Dispatchers.IO) {
        val out = ArrayList<Reading>()
        readableDatabase.rawQuery(
            "SELECT id, at, mmol, tag, note, source, carbs, insulin, insulin_kind, meds, exercise, foods, meal FROM readings WHERE at >= ? ORDER BY at ASC",
            arrayOf(since.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out += Reading(
                    id = c.getLong(0),
                    at = c.getLong(1),
                    mmol = c.getDouble(2),
                    tag = MealTag.fromKey(c.getString(3)),
                    note = c.getString(4) ?: "",
                    source = c.getString(5) ?: "",
                    carbs = if (c.isNull(6)) null else c.getInt(6),
                    insulin = if (c.isNull(7)) null else c.getDouble(7),
                    insulinKind = InsulinKind.fromKey(c.getString(8)),
                    meds = c.getInt(9) != 0,
                    exercise = if (c.isNull(10)) null else c.getInt(10),
                    foods = Foods.unpack(c.getString(11)),
                    meal = Meal.fromJson(c.getString(12)),
                )
            }
        }
        out
    }

    // ---- Sensor points ----

    /** Adds sensor points (one per minute; a repeat of a minute is ignored). Returns how many were new. */
    suspend fun addSensor(points: List<SensorPoint>): Int = withContext(Dispatchers.IO) {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (p in points) {
                val v = ContentValues().apply {
                    put("minute", p.at / 60_000)
                    put("at", p.at)
                    put("mmol", p.mmol)
                }
                if (db.insertWithOnConflict("sensor", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            // A month is plenty for trends and food curves.
            db.delete("sensor", "at < ?", arrayOf((System.currentTimeMillis() - 31L * 86_400_000).toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        added
    }

    /** Sensor points at or after [since], oldest first. */
    suspend fun sensorSince(since: Long): List<SensorPoint> = withContext(Dispatchers.IO) {
        val out = ArrayList<SensorPoint>()
        readableDatabase.rawQuery("SELECT at, mmol FROM sensor WHERE at >= ? ORDER BY at ASC", arrayOf(since.toString())).use { c ->
            while (c.moveToNext()) out += SensorPoint(c.getLong(0), c.getDouble(1))
        }
        out
    }
}
