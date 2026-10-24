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
class Db private constructor(context: Context, name: String) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    companion object {
        private const val VERSION = 6
        const val REAL = "dropi.db"

        /** Demo data lives in its own file, so the real log is never touched while it's on. */
        const val DEMO = "dropi-demo.db"

        private val open = HashMap<String, Db>()

        private fun named(context: Context, name: String): Db = synchronized(this) {
            open.getOrPut(name) { Db(context, name) }
        }

        /** The log in use: the demo one while demo data is on (Settings › Developer), else the real one. */
        fun get(context: Context): Db = named(context, if (app.dropi.Prefs(context).demo) DEMO else REAL)

        /** Always the real log (backups and restores). */
        fun real(context: Context): Db = named(context, REAL)

        /** Throws the demo log away (leaving demo data, or loading a fresh set). */
        fun wipeDemo(context: Context) = synchronized(this) {
            open.remove(DEMO)?.close()
            context.applicationContext.deleteDatabase(DEMO)
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
        createMeals(db)
    }

    /** Meals as their own entries, at the time they were eaten. */
    private fun createMeals(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meals (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, name TEXT NOT NULL, meal TEXT NOT NULL, before_id INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS meals_at ON meals(at)")
    }

    /**
     * Meals that were stored on readings (before meals had their own entries) become meal
     * entries at the same time, with that reading as their starting value. The reading keeps
     * its number and moment; its foods and the carbs that came with them move to the meal.
     */
    private fun moveFoodsToMeals(db: SQLiteDatabase) {
        val zone = java.time.ZoneId.systemDefault()
        // reading id, meal time, meal JSON, the reading it starts from
        data class Move(val id: Long, val at: Long, val json: String, val before: Long?)
        val moves = ArrayList<Move>()
        db.rawQuery("SELECT id, at, foods, meal, carbs, tag FROM readings WHERE foods != ''", null).use { c ->
            while (c.moveToNext()) {
                val foods = Foods.unpack(c.getString(2))
                if (foods.isEmpty()) continue
                val logged = if (c.isNull(4)) null else c.getInt(4).toDouble()
                val meal = Meal.fromJson(c.getString(3))?.let { m ->
                    // Carbs changed by hand on the reading after the breakdown: the meal keeps that total.
                    if (logged == null || kotlin.math.abs(logged - m.carbs) < 0.5) m
                    else if (m.carbs > 0) m.copy(items = m.items.map { it.copy(carbs = Grams.round(it.carbs * logged / m.carbs)) })
                    else m.copy(items = m.items + MealItem("carbs added by hand", "1 portion", carbs = logged))
                } ?: run {
                    val carbs = if (c.isNull(4)) 0.0 else c.getInt(4).toDouble()
                    val each = Grams.round(carbs / foods.size)
                    Meal(foods.mapIndexed { i, f -> MealItem(f, "1 portion", carbs = if (i == 0) Grams.round(carbs - each * (foods.size - 1)) else each) }, false, "typed")
                }
                val id = c.getLong(0)
                val at = c.getLong(1)
                if (c.getString(5) == MealTag.AFTER_MEAL.name) {
                    // Foods logged on the after reading: the meal started at the reading before it
                    // (up to 3 h earlier), or about 2 h before when there was none.
                    var start: Pair<Long, Long>? = null
                    db.rawQuery(
                        "SELECT id, at FROM readings WHERE at < ? AND at >= ? AND tag IN (?, ?) ORDER BY at DESC LIMIT 1",
                        arrayOf(at.toString(), (at - 3 * 3_600_000L).toString(), MealTag.BEFORE_MEAL.name, MealTag.FASTING.name),
                    ).use { b -> if (b.moveToFirst()) start = b.getLong(0) to b.getLong(1) }
                    val s = start
                    moves += if (s != null) Move(id, s.second, meal.toJson(), s.first) else Move(id, at - 2 * 3_600_000L, meal.toJson(), null)
                } else {
                    moves += Move(id, at, meal.toJson(), id)
                }
            }
        }
        for (m in moves) {
            db.insert("meals", null, ContentValues().apply {
                put("at", m.at); put("name", MealEntry.nameFor(m.at, zone)); put("meal", m.json)
                if (m.before != null) put("before_id", m.before) else putNull("before_id")
            })
            db.execSQL("UPDATE readings SET foods = '', meal = NULL, carbs = NULL WHERE id = ?", arrayOf(m.id))
        }
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
        if (oldVersion < 6) {
            createMeals(db)
            moveFoodsToMeals(db)
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

    // ---- Meals ----

    suspend fun saveMeal(e: MealEntry): MealEntry = withContext(Dispatchers.IO) {
        val v = ContentValues().apply {
            put("at", e.at); put("name", e.name); put("meal", e.meal.toJson())
            if (e.before != null) put("before_id", e.before) else putNull("before_id")
        }
        val db = writableDatabase
        if (e.id == 0L) e.copy(id = db.insertOrThrow("meals", null, v))
        else { db.update("meals", v, "id = ?", arrayOf(e.id.toString())); e }
    }

    suspend fun deleteMeal(id: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("meals", "id = ?", arrayOf(id.toString()))
        Unit
    }

    /** Meals eaten at or after [since], oldest first. */
    suspend fun mealsSince(since: Long): List<MealEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<MealEntry>()
        readableDatabase.rawQuery("SELECT id, at, name, meal, before_id FROM meals WHERE at >= ? ORDER BY at ASC", arrayOf(since.toString())).use { c ->
            while (c.moveToNext()) {
                val meal = Meal.fromJson(c.getString(3)) ?: continue
                out += MealEntry(c.getLong(0), c.getLong(1), c.getString(2), meal, if (c.isNull(4)) null else c.getLong(4))
            }
        }
        out
    }

    /** Adds meals from a backup, skipping any already here (same minute and foods). [MealEntry.before] must already be an id here. */
    suspend fun mergeMeals(incoming: List<MealEntry>): Int = withContext(Dispatchers.IO) {
        val have = mealsSince(Long.MIN_VALUE).map { (it.at / 60_000) to it.foods.sorted() }.toHashSet()
        var added = 0
        for (e in incoming) {
            val k = (e.at / 60_000) to e.foods.sorted()
            if (k in have) continue
            saveMeal(e.copy(id = 0))
            have += k
            added++
        }
        added
    }

    /** Old backups carry meals on readings: after restoring one, move them into meal entries. */
    suspend fun tidyMeals() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try { moveFoodsToMeals(db); db.setTransactionSuccessful() } finally { db.endTransaction() }
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
            // Uses the minute key (indexed), so it's quick even on every new value.
            db.delete("sensor", "minute < ?", arrayOf(((System.currentTimeMillis() - 31L * 86_400_000) / 60_000).toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        added
    }

    /** Sensor points at or after [since], oldest first. */
    suspend fun sensorSince(since: Long): List<SensorPoint> = withContext(Dispatchers.IO) {
        val out = ArrayList<SensorPoint>()
        // By the minute key, which is the table's index: no full scan.
        readableDatabase.rawQuery("SELECT at, mmol FROM sensor WHERE minute >= ? ORDER BY minute ASC", arrayOf((since / 60_000).toString())).use { c ->
            while (c.moveToNext()) out += SensorPoint(c.getLong(0), c.getDouble(1))
        }
        out
    }
}
