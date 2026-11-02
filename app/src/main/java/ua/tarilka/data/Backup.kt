package ua.tarilka.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Backup of everything the user entered, as one JSON file.
 * Built-in foods are referred to by their stable code, so a backup restores
 * correctly on a fresh install or a newer app version.
 */
object Backup {
    const val FORMAT = "tarilka-backup"
    /** 2: sleep, bowel, workouts, cycle and the cycle/steps settings; 3: waist. */
    const val VERSION = 3

    private const val ENCRYPTED = "tarilka-backup-encrypted"
    private const val ITERATIONS = 600_000

    /** AES-256-GCM with a key derived from the password (PBKDF2-HMAC-SHA256). */
    fun encrypt(plain: String, password: String): String {
        val rnd = java.security.SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), javax.crypto.spec.GCMParameterSpec(128, iv))
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = java.util.Base64.getEncoder()
        val head = JSONObject().put("format", ENCRYPTED).put("version", 1)
            .put("kdf", "PBKDF2WithHmacSHA256").put("iterations", ITERATIONS)
            .put("salt", b64.encodeToString(salt)).put("iv", b64.encodeToString(iv)).toString()
        // The long data goes last, so the format is always near the start (see isEncrypted). Base64 needs no escaping.
        return head.dropLast(1) + ",\"data\":\"" + b64.encodeToString(data) + "\"}"
    }

    /** Problems with a backup file that people can act on; anything else is "damaged". */
    class BackupError(val kind: Kind) : Exception(kind.name) {
        enum class Kind { NOT_BACKUP, NEWER, TOO_LARGE }
    }

    /** Looks only at the start of the file (the format comes first), so a large backup is not parsed twice. */
    fun isEncrypted(text: String): Boolean =
        text.take(400).contains("\"$ENCRYPTED\"") && runCatching { JSONObject(text).optString("format") == ENCRYPTED }.getOrDefault(false)

    /** Throws if the password is wrong (GCM checks integrity). */
    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text)
        val b64 = java.util.Base64.getDecoder()
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            key(password, b64.decode(o.getString("salt")), o.getInt("iterations").also { require(it in 100_000..2_000_000) { "iterations" } }),
            javax.crypto.spec.GCMParameterSpec(128, b64.decode(o.getString("iv"))),
        )
        return String(c.doFinal(b64.decode(o.getString("data"))), Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val raw = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return javax.crypto.spec.SecretKeySpec(raw, "AES")
    }

    data class Summary(val diary: Int, val fluids: Int, val weights: Int, val foods: Int)

    suspend fun export(db: AppDatabase, prefs: Prefs): String {
        val builtinCodes = db.foods().codes().associate { it.id to it.code }
        val custom = db.foods().customOnce()
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", Instant.now().toString())
            .put("profile", prefs.profile.value.let { p ->
                JSONObject().put("sex", p.sex.name).put("age", p.age).put("heightCm", p.heightCm)
                    .put("weightKg", p.weightKg).put("activity", p.activity).put("goal", p.goal.name)
                    .put("customKcal", p.customKcal ?: JSONObject.NULL).put("isSet", p.isSet).put("pregnant", p.pregnant).put("breastfeeding", p.breastfeeding)
            })
            .put("customFoods", JSONArray().apply {
                custom.forEach { f ->
                    put(
                        JSONObject().put("id", f.id).put("nameUk", f.nameUk).put("nameEn", f.nameEn).put("category", f.category)
                            .put("kcal", f.kcal).put("protein", f.protein).put("fat", f.fat)
                            .put("carbs", f.carbs).put("fiber", f.fiber)
                            .put("portionG", f.portionG ?: JSONObject.NULL).put("favorite", f.favorite)
                            .put("source", f.source).put("recipe", f.recipe ?: JSONObject.NULL),
                    )
                }
            })
            .put("favorites", JSONArray(db.foods().favoriteCodes()))
            .put("diary", JSONArray().apply {
                db.diary().allOnce().forEach { e ->
                    val o = JSONObject().put("day", LocalDate.ofEpochDay(e.day).toString()).put("meal", e.meal)
                        .put("nameUk", e.nameUk).put("nameEn", e.nameEn).put("grams", e.grams)
                        .put("kcal", e.kcal).put("protein", e.protein).put("fat", e.fat)
                        .put("carbs", e.carbs).put("fiber", e.fiber).put("fromAi", e.fromAi)
                        .put("createdAt", e.createdAt)
                    e.reason?.let { o.put("reason", it) }
                    e.hunger?.let { o.put("hunger", it) }
                    if (e.hardToStop) o.put("hardToStop", true)
                    e.foodId?.let { id ->
                        val code = builtinCodes[id]
                        if (code != null) o.put("foodCode", code) else o.put("customFoodId", id)
                    }
                    put(o)
                }
            })
            .put("fluids", JSONArray().apply {
                db.fluids().allOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("ml", it.ml).put("createdAt", it.createdAt))
                }
            })
            .put("notes", JSONArray().apply {
                db.notes().allOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("text", it.text))
                }
            })
            .put("sleep", JSONArray().apply {
                db.sleep().allOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("hours", it.hours).put("quality", it.quality ?: JSONObject.NULL))
                }
            })
            .put("bowel", JSONArray().apply {
                db.bowel().allOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("count", it.count).put("form", it.form ?: JSONObject.NULL))
                }
            })
            .put("workouts", JSONArray().apply {
                db.workouts().allOnce().forEach {
                    put(
                        JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("type", it.type)
                            .put("minutes", it.minutes).put("intensity", it.intensity).put("createdAt", it.createdAt),
                    )
                }
            })
            .put("periods", JSONArray().apply {
                db.cycle().periodsOnce().forEach {
                    put(JSONObject().put("start", LocalDate.ofEpochDay(it.start).toString()).put("end", it.end?.let { e -> LocalDate.ofEpochDay(e).toString() } ?: JSONObject.NULL))
                }
            })
            .put("periodDays", JSONArray().apply {
                db.cycle().daysOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("flow", it.flow ?: JSONObject.NULL).put("pain", it.pain ?: JSONObject.NULL))
                }
            })
            .put("settings", JSONObject()
                .put("cycleTypical", prefs.cycleTypical.value)
                .put("hideKcal", prefs.hideKcal.value)
                .put("stepLog", JSONObject(prefs.loggedSteps().mapKeys { it.key.toString() })))
            .put("waist", JSONArray().apply {
                db.waist().allOnce().forEach { put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("cm", it.cm)) }
            })
            .put("weights", JSONArray().apply {
                db.weights().allOnce().forEach {
                    put(JSONObject().put("day", LocalDate.ofEpochDay(it.day).toString()).put("kg", it.kg).put("minutes", it.minutes ?: JSONObject.NULL))
                }
            })
        return root.toString()
    }

    /** Checks the file and counts what is inside, without changing anything. */
    fun peek(json: String): Summary = peek(JSONObject(json))

    private fun peek(o: JSONObject): Summary {
        if (o.optString("format") != FORMAT) throw BackupError(BackupError.Kind.NOT_BACKUP)
        return Summary(
            o.optJSONArray("diary")?.length() ?: 0,
            o.optJSONArray("fluids")?.length() ?: 0,
            o.optJSONArray("weights")?.length() ?: 0,
            o.optJSONArray("customFoods")?.length() ?: 0,
        )
    }

    /** Replaces all user data with the backup. Built-in foods stay. */
    suspend fun restore(db: AppDatabase, prefs: Prefs, json: String): Summary {
        val o = JSONObject(json)
        if (o.optString("format") != FORMAT) throw BackupError(BackupError.Kind.NOT_BACKUP)
        // A newer file may hold data this version would silently drop.
        if (o.optInt("version", 1) > VERSION) throw BackupError(BackupError.Kind.NEWER)
        val summary = peek(o)
        db.withTransaction {
            db.clearDiary()
            db.foods().deleteCustom()

            val idMap = mutableMapOf<Long, Long>()
            o.optJSONArray("customFoods")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    val uk = f.text("nameUk", 1000)
                    val en = f.text("nameEn", 1000)
                    val newId = db.foods().insert(
                        Food(
                            category = OwnKinds.clean(f.optString("category", "custom")), nameUk = uk, nameEn = en,
                            kcal = f.amount("kcal", 10_000.0), protein = f.amount("protein", 1000.0), fat = f.amount("fat", 1000.0),
                            carbs = f.amount("carbs", 1000.0), fiber = f.amount("fiber", 1000.0, 0.0),
                            portionG = if (f.isNull("portionG")) null else f.amount("portionG", 10_000.0),
                            portionUk = if (f.isNull("portionG")) null else "порція",
                            portionEn = if (f.isNull("portionG")) null else "serving",
                            source = f.optString("source", "CUSTOM").take(16), favorite = f.optBoolean("favorite"),
                            recipe = if (f.isNull("recipe")) null else f.text("recipe", 64_000),
                        ),
                    )
                    idMap[f.getLong("id")] = newId
                }
            }
            // Recipes refer to own foods by id; ids change on restore, so remap them.
            if (idMap.isNotEmpty()) {
                db.foods().customOnce().filter { it.recipe != null }.forEach { f ->
                    val r = JSONObject(f.recipe!!)
                    val items = r.optJSONArray("items") ?: return@forEach
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        // An id we can't map (food deleted before export) must not point at another food.
                        if (it.has("id")) it.put("id", idMap[it.getLong("id")] ?: -1L)
                    }
                    db.foods().update(f.copy(recipe = r.toString()))
                }
            }
            val favs = o.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            db.foods().setFavorites(favs)

            val byCode = db.foods().codes().associate { it.code to it.id }
            o.optJSONArray("diary")?.let { arr ->
                db.diary().insertAll((0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i)
                    val foodId = when {
                        e.has("foodCode") -> byCode[e.getString("foodCode")]
                        e.has("customFoodId") -> idMap[e.getLong("customFoodId")]
                        else -> null
                    }
                    DiaryEntry(
                        day = e.day("day"), meal = e.getInt("meal").coerceIn(0, 3),
                        foodId = foodId, nameUk = e.text("nameUk", 1000), nameEn = e.text("nameEn", 1000),
                        grams = e.amount("grams", 10_000.0), kcal = e.amount("kcal", 1_000_000.0), protein = e.amount("protein", 100_000.0),
                        fat = e.amount("fat", 100_000.0), carbs = e.amount("carbs", 100_000.0), fiber = e.amount("fiber", 100_000.0, 0.0),
                        fromAi = e.optBoolean("fromAi"), createdAt = e.optLong("createdAt", System.currentTimeMillis()),
                        // Values out of range (an edited file, a newer version) are dropped, never shown.
                        reason = e.optInt("reason", -1).takeIf { it in Reasons.ALL },
                        hunger = e.optInt("hunger", -1).takeIf { it in 1..5 },
                        hardToStop = e.optBoolean("hardToStop"),
                    )
                })
            }
            o.optJSONArray("fluids")?.let { arr ->
                db.fluids().insertAll((0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i)
                    val day = LocalDate.ofEpochDay(e.day("day"))
                    val noon = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    FluidEntry(day = day.toEpochDay(), ml = e.amount("ml", 100_000.0), createdAt = e.optLong("createdAt", noon).takeIf { it > 0 } ?: noon)
                })
            }
            o.optJSONArray("notes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    db.notes().upsert(DayNote(e.day("day"), e.text("text", 50_000)))
                }
            }
            o.optJSONArray("sleep")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    db.sleep().upsert(
                        SleepEntry(e.day("day"), e.amount("hours", 100.0), e.optInt("quality", -1).takeIf { it in 0..2 }),
                    )
                }
            }
            o.optJSONArray("bowel")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    db.bowel().upsert(
                        BowelEntry(e.day("day"), e.getInt("count").coerceIn(0, 3), e.optInt("form", -1).takeIf { it in 0..2 }),
                    )
                }
            }
            o.optJSONArray("workouts")?.let { arr ->
                db.workouts().insertAll((0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i)
                    WorkoutEntry(
                        day = e.day("day"),
                        type = e.optInt("type", Workouts.TYPES.size - 1).coerceIn(0, Workouts.TYPES.size - 1),
                        minutes = e.optInt("minutes", 30).coerceIn(1, 600),
                        intensity = e.optInt("intensity", 1).coerceIn(0, 2),
                        createdAt = e.optLong("createdAt", System.currentTimeMillis()),
                    )
                })
            }
            o.optJSONArray("periods")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    db.cycle().upsert(
                        PeriodEntry(e.day("start"), if (e.isNull("end")) null else e.day("end")),
                    )
                }
            }
            o.optJSONArray("periodDays")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    db.cycle().upsertDay(
                        PeriodDay(e.day("day"), e.optInt("flow", -1).takeIf { it in 0..2 }, e.optInt("pain", -1).takeIf { it in 0..2 }),
                    )
                }
            }
            o.optJSONArray("waist")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    val cm = e.optDouble("cm", -1.0)
                    if (cm in 40.0..250.0) db.waist().upsert(WaistEntry(e.day("day"), cm))
                }
            }
            o.optJSONArray("weights")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    // Weights out of a human range (a damaged or edited file) are skipped, never shown.
                    val kg = e.optDouble("kg", -1.0)
                    if (kg in 20.0..400.0) db.weights().upsert(WeightEntry(e.day("day"), kg, e.optInt("minutes", -1).takeIf { it in 0..1439 }))
                }
            }
        }
        // The cycle settings belong to the replaced cycle records.
        prefs.clearCycle()
        o.optJSONObject("settings")?.let { st ->
            st.optInt("cycleTypical", -1).let { prefs.setCycleTypical(if (it in 15..60) it else -1) }
            // Only ever turned on by a restore: someone who hid calories must not see them again on a new phone.
            if (st.optBoolean("hideKcal")) prefs.setHideKcal(true)
            st.optJSONObject("stepLog")?.let { log ->
                // One write for all days (the log keeps every year); days and counts in a sane range only.
                val max = java.time.LocalDate.now().toEpochDay()
                val days = log.keys().asSequence().mapNotNull { k ->
                    val d = k.toLongOrNull()?.takeIf { it in 0..max } ?: return@mapNotNull null
                    log.optLong(k, -1).takeIf { it in 0..200_000 }?.let { d to it }
                }.toMap()
                prefs.mergeSteps(days)
            }
        }
        o.optJSONObject("profile")?.let { p ->
            if (p.optBoolean("isSet")) {
                prefs.saveProfile(strict = true, p =
                    Profile(
                        sex = runCatching { Sex.valueOf(p.getString("sex")) }.getOrDefault(Sex.FEMALE),
                        // Values out of range (a damaged file) fall back to the defaults.
                        age = p.optInt("age", 30).takeIf { it in 14..100 } ?: 30,
                        heightCm = p.optInt("heightCm", 165).takeIf { it in 120..230 } ?: 165,
                        weightKg = p.optDouble("weightKg", 65.0).takeIf { it in 30.0..300.0 } ?: 65.0,
                        activity = p.optInt("activity", 1).coerceIn(0, 4),
                        goal = runCatching { Goal.valueOf(p.getString("goal")) }.getOrDefault(Goal.MAINTAIN),
                        customKcal = if (p.isNull("customKcal")) null else p.optInt("customKcal").takeIf { it in 800..6000 },
                        pregnant = p.optBoolean("pregnant", false),
                        breastfeeding = p.optBoolean("breastfeeding", false),
                    ),
                )
            }
        }
        return summary
    }

    /** The diary as a spreadsheet-friendly CSV (one row per eaten item). Under 18 ([showKcal] false) there is no kcal column. */
    suspend fun diaryCsv(db: AppDatabase, ukrainian: Boolean, showKcal: Boolean = true): String {
        val zone = ZoneId.systemDefault()
        val kcalCol = if (showKcal) (if (ukrainian) "ккал;" else "kcal;") else ""
        val header = if (ukrainian) {
            "дата;час;прийом;продукт;грами;${kcalCol}білки;жири;вуглеводи;клітковина;ШІ;чому;голод 1-5;важко зупинитися"
        } else {
            "date;time;meal;food;grams;${kcalCol}protein;fat;carbs;fiber;ai;why;hunger 1-5;hard to stop"
        }
        val reasons = if (ukrainian) listOf("голод", "стрес", "нудьга", "компанія", "звичка") else Reasons.EN
        val meals = if (ukrainian) listOf("сніданок", "обід", "вечеря", "перекус") else listOf("breakfast", "lunch", "dinner", "snack")
        fun n(d: Double) = String.format(java.util.Locale.ROOT, "%.1f", d)
        val sb = StringBuilder("﻿").append(header).append('\n')
        db.diary().allOnce().forEach { e ->
            val time = Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalTime().withNano(0).withSecond(0)
            // A name from someone else's backup must not become a spreadsheet formula.
            val name = (if (ukrainian) e.nameUk else e.nameEn).replace(';', ',')
                .replace(Regex("[\\r\\n\\t\"]"), " ").trimStart()
                .let { if (it.firstOrNull() in listOf('=', '+', '-', '@')) "'$it" else it }
            sb.append(LocalDate.ofEpochDay(e.day)).append(';').append(time).append(';')
                .append(meals.getOrElse(e.meal) { "" }).append(';').append(name).append(';')
                .append(n(e.grams)).append(';')
                .append(if (showKcal) n(e.kcal) + ";" else "").append(n(e.protein)).append(';')
                .append(n(e.fat)).append(';').append(n(e.carbs)).append(';').append(n(e.fiber)).append(';')
                .append(if (e.fromAi) 1 else 0).append(';')
                .append(e.reason?.let { reasons.getOrNull(it) } ?: "").append(';')
                .append(e.hunger ?: "").append(';')
                .append(if (e.hardToStop) 1 else "").append('\n')
        }
        return sb.toString()
    }
}

/**
 * A number from a backup: a damaged or crafted file must not bring NaN, infinity, negative or absurdly large values
 * into the diary (they would break sums and rounding everywhere, even on every start). Missing → [default], or an error.
 */
private fun JSONObject.amount(key: String, max: Double, default: Double? = null): Double {
    val v = if (has(key) && !isNull(key)) optDouble(key) else default ?: throw org.json.JSONException("no $key")
    require(v.isFinite() && v >= 0 && v <= max) { "bad number: $key" }
    return v
}

/** A text from a backup, with a length limit: one huge text could make its database row unreadable (2 MB per row). */
private fun JSONObject.text(key: String, max: Int): String =
    getString(key).also { require(it.length <= max) { "text too long: $key" } }

/** A date from a backup, only from 2000 to a year ahead: an absurd date would make "all time" reports loop for ever. */
private fun JSONObject.day(key: String): Long {
    val d = LocalDate.parse(getString(key)).toEpochDay()
    require(d in FIRST_DAY..LocalDate.now().plusYears(1).toEpochDay()) { "bad date: $key" }
    return d
}

private val FIRST_DAY = LocalDate.of(2000, 1, 1).toEpochDay()
