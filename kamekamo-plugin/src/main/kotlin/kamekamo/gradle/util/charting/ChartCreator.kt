package kamekamo.gradle.util.charting

import kamekamo.gradle.util.charting.ChartFill.FillType.BACKGROUND
import kamekamo.gradle.util.charting.Color.Companion.BLACK
import kamekamo.gradle.util.charting.Color.Companion.WHITE
import okhttp3.HttpUrl

/**
 * Simple API for Google Charts.
 *
 * https://developers.google.com/chart/image/docs/making_charts
 *
 * Models shared with us from our friends at Square.
 */
internal object ChartCreator {
    private val RED = Color(0xF8696A)
    private val YELLOW = Color(0xFEE783)
    private val GREEN = Color(0x75C37C)

    fun createChartUrl(
        data: List<Int>,
        xRange: IntRange = 1..data.size,
        yRange: IntRange = 0..(data.maxOrNull() ?: 0)
    ): String {
        check(data.isNotEmpty()) { "Empty data!" }
        var currentColor = colorForValue(data.first())

        val colorRanges = mutableListOf<ColorRange>()
        var startIndex = 0

        data.forEachIndexed { index, value ->
            val color = colorForValue(value)
            if (currentColor != color) {
                colorRanges += ColorRange(currentColor, startIndex, index - 1)
                currentColor = color
                startIndex = index - 1
            }
        }

        colorRanges += ColorRange(currentColor, startIndex, data.size - 1)

        val chartProperties = buildList {
            add(ChartType.LineChart)
            add(ChartSize(1000, 300))
            add(ChartFill(BACKGROUND, BLACK))
            add(VisibleAxis.XAxis + VisibleAxis.YAxis)
            add(AxisStyle(0, WHITE) + AxisStyle(1, WHITE))
            add(AxisRange(0, xRange.first, xRange.last) + AxisRange(1, yRange.first, yRange.last))
            add(ChartData.SimpleData(data))
            add(SeriesColors(WHITE))
            add(colorRanges.collapse())
        }

        return HttpUrl.Builder()
            .scheme("https")
            .host("chart.googleapis.com")
            .addPathSegment("chart")
            .apply {
                for (prop in chartProperties) {
                    addQueryParameter(prop.key, prop.value)
                }
            }
            .build()
            .toString()
    }

    private fun colorForValue(value: Int): Color {
        return when {
            value <= 70 -> RED
            value <= 90 -> YELLOW
            else -> GREEN
        }
    }
}