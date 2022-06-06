package kamekamo.gradle.util.charting

internal sealed class VisibleAxis : ChartProperty {
    override val key: String = "chxt"

    object XAxis : VisibleAxis() {
        override val value: String = "x"
    }

    object TAxis : VisibleAxis() {
        override val value: String = "t"
    }

    object YAxis : VisibleAxis() {
        override val value: String = "y"
    }

    object RAxis : VisibleAxis() {
        override val value: String = "r"
    }

    private data class MultipleVisibleAxes(private val axes: Set<VisibleAxis>) : VisibleAxis() {
        override val value: String = axes.joinToString(separator = ",") { it.value }

        override operator fun plus(axis: VisibleAxis): VisibleAxis {
            return MultipleVisibleAxes(axes + axis)
        }
    }

    open operator fun plus(axis: VisibleAxis): VisibleAxis {
        return MultipleVisibleAxes(setOf(this, axis))
    }
}