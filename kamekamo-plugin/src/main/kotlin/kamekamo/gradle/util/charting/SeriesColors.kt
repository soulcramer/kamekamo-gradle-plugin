package kamekamo.gradle.util.charting

internal data class SeriesColors(
    val colors: List<Color>,
) : ChartProperty {
    override val key: String = "chco"

    override val value: String
        get() = colors.joinToString(separator = ",") { it.hex }

    constructor(color: Color) : this(listOf(color))

    constructor(vararg colors: Color) : this(colors.toList())
}