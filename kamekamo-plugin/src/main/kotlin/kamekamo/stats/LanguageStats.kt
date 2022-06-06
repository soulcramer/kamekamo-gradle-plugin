package kamekamo.stats

import com.squareup.moshi.Json

// Example
// "HTML" :{
//  "nFiles": 1000,
//  "blank": 3575,
//  "comment": 0,
//  "code": 116111},
internal data class LanguageStats(
    @Json(name = "nFiles") val files: Int,
    val code: Int,
    val comment: Int,
    val blank: Int
) {

    val total: Int
        get() = code + comment + blank

    companion object {
        val EMPTY = LanguageStats(0, 0, 0, 0)
    }

    operator fun plus(other: LanguageStats): LanguageStats {
        return LanguageStats(
            files + other.files,
            code + other.code,
            comment + other.comment,
            blank + other.blank
        )
    }
}

/** Merges this map with [other]. Keys present in both will have their values merged together. */
internal fun Map<String, LanguageStats>.mergeWith(
    other: Map<String, LanguageStats>
): Map<String, LanguageStats> {
    return (keys + other.keys)
        .associateWith { key -> listOfNotNull(get(key), other[key]) }
        .mapValues { (_, values) -> values.reduce(LanguageStats::plus) }
}
