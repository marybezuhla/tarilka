package ua.tarilka.data

/** Guards the Ukrainian food names against Russian words the photo model sometimes produces. */
object Lang {
    private val RUSSIAN_LETTERS = Regex("[ыэъёЫЭЪЁ]")
    /** Common Russian words written only with letters Ukrainian also has. */
    private val RUSSIAN_WORDS = Regex(
        "(?iu)(?<![\\p{L}])(и|что|это|или|нет|как|если|также|очень|можно|нужно|рекомендуется|белок|овощи|рыба|мясо)(?![\\p{L}])",
    )

    /** Russian stems that never start a Ukrainian word. */
    private val RUSSIAN_STEMS = Regex("(?iu)(?<![\\p{L}])(подсолн|сегодня|сейчас|завтрак)")

    fun looksRussian(s: String): Boolean =
        RUSSIAN_LETTERS.containsMatchIn(s) || RUSSIAN_WORDS.containsMatchIn(s) || RUSSIAN_STEMS.containsMatchIn(s)

    /** A Ukrainian name if it is clean, otherwise the English one. */
    fun safeUk(uk: String, en: String): String = if (uk.isBlank() || looksRussian(uk)) en else uk
}
