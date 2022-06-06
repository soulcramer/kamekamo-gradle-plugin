package kamekamo.gradle.util.charting

internal data class AxisRange(val index: Int, val startValue: Int, val endValue: Int) :
    ChartProperty {
    override val key: String = "chxr"

    override val value: String
        get() = "$index,$startValue,$endValue"
}