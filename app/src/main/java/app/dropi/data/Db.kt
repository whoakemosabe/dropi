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
        private const val VERSION = 3

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
                exercise INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX readings_at ON readings(at)")
        db.execSQL("CREATE INDEX readings_source ON readings(source)")
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
            "SELECT id, at, mmol, tag, note, source, carbs, insulin, insulin_kind, meds, exercise FROM readings WHERE at >= ? ORDER BY at ASC",
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
                )
            }
        }
        out
    }
}
