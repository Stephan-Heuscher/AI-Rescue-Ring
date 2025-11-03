package ch.heuscher.back_home_dot.domain.model

/**
 * Represents the different tap behavior modes for the floating dot.
 */
enum class TapBehavior {
    /**
     * Standard mode: 1 tap = Home, 2 taps = Back
     * Always: 3 taps = Switch apps, 4 taps = Open this app, Long press = Home
     */
    STANDARD,

    /**
     * Back mode: 1 tap = Back, 2 taps = Switch to previous app
     * Always: 3 taps = Switch apps, 4 taps = Open this app, Long press = Home
     */
    BACK;

    fun getDisplayName(): String = when (this) {
        STANDARD -> "Standard"
        BACK -> "Back"
    }

    fun getDescription(): String = when (this) {
        STANDARD -> "1 tap = Home, 2 taps = Back"
        BACK -> "1 tap = Back, 2 taps = Switch to previous app"
    }
}
