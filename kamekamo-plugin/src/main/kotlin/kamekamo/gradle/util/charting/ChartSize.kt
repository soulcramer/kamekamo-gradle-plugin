package kamekamo.gradle.util.charting

internal data class ChartSize(val width: Int, val height: Int) : ChartProperty {
    override val key: String = "chs"

    override val value: String
        get() = "${width}x$height"
}