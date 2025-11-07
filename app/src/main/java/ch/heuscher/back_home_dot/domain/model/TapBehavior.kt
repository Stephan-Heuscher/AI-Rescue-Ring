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
     * Navi mode: 1 tap = Back, 2 taps = Switch to previous app
     * Always: 3 taps = Switch apps, 4 taps = Open this app, Long press = Home
     */
    NAVI,

    /**
     * Safe Home mode: All taps and gestures go to Home
     * Can only be moved on the home screen for safety
     */
    SAFE_HOME
}
