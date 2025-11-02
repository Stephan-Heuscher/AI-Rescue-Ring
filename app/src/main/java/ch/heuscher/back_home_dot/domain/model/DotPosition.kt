package ch.heuscher.back_home_dot.domain.model

/**
 * Represents the position of the floating dot on screen.
 */
data class DotPosition(
    val x: Int,
    val y: Int,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val rotation: Int = 0
) {
    /**
     * Converts pixel position to percentage for persistence.
     */
    fun toPercentages(): DotPositionPercent {
        if (screenWidth == 0 || screenHeight == 0) {
            return DotPositionPercent(0.1f, 0.1f)
        }
        return DotPositionPercent(
            x.toFloat() / screenWidth,
            y.toFloat() / screenHeight
        )
    }

    companion object {
        fun fromPercentages(
            percent: DotPositionPercent,
            screenWidth: Int,
            screenHeight: Int,
            rotation: Int = 0
        ): DotPosition {
            return DotPosition(
                (percent.xPercent * screenWidth).toInt(),
                (percent.yPercent * screenHeight).toInt(),
                screenWidth,
                screenHeight,
                rotation
            )
        }
    }
}

/**
 * Represents dot position as percentages for device-independent storage.
 */
data class DotPositionPercent(
    val xPercent: Float,
    val yPercent: Float
)