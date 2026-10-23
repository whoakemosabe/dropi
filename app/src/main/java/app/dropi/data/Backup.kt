package app.dropi.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup files: every reading plus check times, targets and lab A1C results, as JSON, sealed
 * with a password (PBKDF2-SHA256 → AES-256-GCM). Without the password the file is unreadable;
 * a wrong password or a damaged file fails the GCM check instead of restoring garbage.
 *
 * Layout: "DROPI1" · iterations (4 bytes) · salt (16) · IV (12) · ciphertext + tag.
 */
object Backup {
    private val MAGIC = "DROPI1".toByteArray()
    private const val ITERATIONS = 210_000
    private const val SALT = 16
    private const val IV = 12

    class WrongPassword : Exception("Wrong password, or the file is damaged")
    class NotABackup : Exception("This isn't a Dropi backup")

    data class Contents(
        val readings: List<Reading>,
        val slots: List<Slot>?,
        val targets: Targets?,
        val labA1c: List<LabA1c>,
        val made: Long,
    )

    fun toJson(c: Contents): String {
        val rs = JSONArray()
        c.readings.forEach { r ->
            rs.put(
                JSONObject()
                    .put("at", r.at).put("mmol", r.mmol).put("tag", r.tag.name).put("note", r.note).put("source", r.source)
                    .apply {
                        r.carbs?.let { put("carbs", it) }
                        r.insulin?.let { put("insulin", it) }
                        r.insulinKind?.let { put("insulinKind", it.name) }
                        if (r.meds) put("meds", true)
                        r.exercise?.let { put("exercise", it) }
                    }
            )
        }
        return JSONObject()
            .put("app", "dropi").put("version", 1).put("made", c.made)
            .put("readings", rs)
            .apply {
                c.slots?.let { put("slots", JSONArray(Schedule.slotsToJson(it))) }
                c.targets?.let { put("targets", JSONObject().put("beforeLow", it.beforeLow).put("beforeHigh", it.beforeHigh).put("afterHigh", it.afterHigh)) }
                put("labA1c", JSONArray(A1c.toJson(c.labA1c)))
            }
            .toString()
    }

    fun fromJson(json: String): Contents {
        val o = try { JSONObject(json) } catch (e: Exception) { throw NotABackup() }
        if (o.optString("app") != "dropi") throw NotABackup()
        val a = o.optJSONArray("readings") ?: JSONArray()
        val readings = (0 until a.length()).mapNotNull { i ->
            val r = a.getJSONObject(i)
            val mmol = r.optDouble("mmol", Double.NaN)
            if (mmol.isNaN() || mmol < 0.5 || mmol > 40) return@mapNotNull null
            Reading(
                at = r.getLong("at"),
                mmol = mmol,
                tag = MealTag.fromKey(r.optString("tag")),
                note = r.optString("note").take(200),
                source = r.optString("source"),
                carbs = if (r.has("carbs")) r.getInt("carbs") else null,
                insulin = if (r.has("insulin")) r.getDouble("insulin") else null,
                insulinKind = InsulinKind.fromKey(r.optString("insulinKind")),
                meds = r.optBoolean("meds"),
                exercise = if (r.has("exercise")) r.getInt("exercise") else null,
            )
        }
        val t = o.optJSONObject("targets")?.let {
            Targets(it.getDouble("beforeLow"), it.getDouble("beforeHigh"), it.getDouble("afterHigh")).safe()
        }
        return Contents(
            readings = readings,
            slots = o.optJSONArray("slots")?.let { Schedule.slotsFromJson(it.toString()) },
            targets = t,
            labA1c = A1c.fromJson(o.optJSONArray("labA1c")?.toString()),
            made = o.optLong("made"),
        )
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun seal(plain: String, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return ByteArrayOutputStream().apply {
            write(MAGIC)
            write(byteArrayOf((iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte()))
            write(salt)
            write(iv)
            write(body)
        }.toByteArray()
    }

    fun open(file: ByteArray, password: CharArray): String {
        val head = MAGIC.size + 4 + SALT + IV
        if (file.size <= head || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        var p = MAGIC.size
        val iterations = ((file[p].toInt() and 0xFF) shl 24) or ((file[p + 1].toInt() and 0xFF) shl 16) or
            ((file[p + 2].toInt() and 0xFF) shl 8) or (file[p + 3].toInt() and 0xFF)
        if (iterations !in 10_000..5_000_000) throw NotABackup()
        p += 4
        val salt = file.copyOfRange(p, p + SALT); p += SALT
        val iv = file.copyOfRange(p, p + IV); p += IV
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return try {
            String(cipher.doFinal(file.copyOfRange(p, file.size)), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        } catch (e: javax.crypto.BadPaddingException) {
            throw WrongPassword()
        }
    }
}
