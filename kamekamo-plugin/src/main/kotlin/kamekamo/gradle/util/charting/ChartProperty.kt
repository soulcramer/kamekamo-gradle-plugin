package kamekamo.gradle.util.charting

/**
 * Represents a configurable property of the chart. All properties end up in a URL, using the [key]
 * as the query parameter name and the [value] as the query parameter value.
 *
 * Currently only certain features of line charts are supported, so the reference at
 * https://developers.google.com/chart/image/docs/gallery/line_charts is a good place to start.
 */
internal interface ChartProperty {
    val key: String
    val value: String

    /**
     * Combines both [ChartProperty]s into a one, with their values combined as [multiline] values.
     * The keys of both properties must be the same.
     */
    operator fun plus(other: ChartProperty): ChartProperty {
        require(key == other.key) {
            "This property's key '$key' does not match the other property's key '${other.key}'"
        }

        return MultilineChartProperty(this, other)
    }
}

/**
 * Combines all of the given [ChartProperty]s into a single one, with their values combined as
 * [multiline] values. The keys of all properties must be the same.
 *
 * This is useful for combining repeated values like [ColorRange]s.
 */
internal fun Collection<ChartProperty>.collapse(): ChartProperty = reduce(ChartProperty::plus)

internal fun multiline(vararg lines: String): String = multiline(lines.toList())

internal fun multiline(lines: Collection<String>): String = lines.joinToString(separator = "|")

private data class MultilineChartProperty(val lines: Collection<ChartProperty>) : ChartProperty {
    override val key: String
        get() = lines.first().key

    override val value: String
        get() = multiline(lines.map { it.value })

    constructor(vararg lines: ChartProperty) : this(lines.toList())

    override operator fun plus(other: ChartProperty): ChartProperty {
        require(key == other.key) {
            "This property's key '$key' does not match the other property's key '${other.key}'"
        }

        return MultilineChartProperty(lines + other)
    }
}