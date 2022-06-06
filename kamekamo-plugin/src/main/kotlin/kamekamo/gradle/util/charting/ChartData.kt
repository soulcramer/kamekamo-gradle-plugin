package kamekamo.gradle.util.charting

import kamekamo.gradle.util.charting.ValuesEncoder.encodeExtendedSeries
import kamekamo.gradle.util.charting.ValuesEncoder.encodePositionSeries

internal sealed class ChartData : ChartProperty {
    override val key: String = "chd"

    data class SimpleData(val data: List<Int>, val maxValue: Int = data.maxOrNull() ?: 0) :
        ChartData() {
        override val value: String
            get() = encodeExtendedSeries(data, maxValue)
    }

    data class TimeSeriesData(val data: Map<Int, Int>) : ChartData() {
        override val value: String
            get() = encodePositionSeries(data)
    }
}