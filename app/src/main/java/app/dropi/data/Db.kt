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
        private const val VERSION = 2

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
                source TEXT NOT NULL DEFAULT ''
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
    }

    private fun values(r: Reading) = ContentValues().apply {
        put("at", r.at)
        put("mmol", r.mmol)
        put("tag", r.tag.name)
        put("note", r.note.trim().take(200))
        put("source", r.source)
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
            "SELECT id, at, mmol, tag, note, source FROM readings WHERE at >= ? ORDER BY at ASC",
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
                )
            }
        }
        out
    }
}
