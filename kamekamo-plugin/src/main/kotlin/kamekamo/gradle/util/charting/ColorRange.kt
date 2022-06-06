package kamekamo.gradle.util.charting

internal data class ColorRange(val color: Color, val startIndex: Int, val endIndex: Int) :
    ChartProperty {
    override val key: String = "chm"

    override val value: String
        get() = "B,${color.hex},0,$startIndex:$endIndex,0"
}