package kamekamo.gradle.util.charting

internal data class AxisStyle(
    val index: Int,
    val color: Color,
) : ChartProperty {
    override val key: String = "chxs"

    override val value: String
        get() = "$index,${color.hex}"
}