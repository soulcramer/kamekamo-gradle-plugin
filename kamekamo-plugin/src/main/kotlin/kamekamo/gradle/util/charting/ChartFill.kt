package kamekamo.gradle.util.charting

internal data class ChartFill(
    val type: FillType,
    val color: Color,
) : ChartProperty {
    override val key: String = "chf"

    override val value: String
        get() = "${type.type},s,${color.hex}"

    enum class FillType(val type: String) {
        TRANSPARENT("a"),
        BACKGROUND("bg"),
        CHART("c")
    }
}