package kamekamo.gradle.util.charting

internal sealed class ChartType : ChartProperty {
    override val key: String = "cht"

    object LineChart : ChartType() {
        override val value: String = "lc"
    }

    object SparklineChart : ChartType() {
        override val value: String = "ls"
    }

    object TimeSeriesChart : ChartType() {
        override val value: String = "lxy"
    }
}