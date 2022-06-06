package kamekamo.gradle.util.charting

internal data class Color(val hex: String) {
    constructor(value: Int) : this(String.format("%06X", 0xFFFFFF and value))

    companion object {
        val BLACK = Color(0x000000)
        val WHITE = Color(0xFFFFFF)
    }
}