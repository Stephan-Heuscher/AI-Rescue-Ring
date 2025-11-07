package ch.heuscher.back_home_dot.domain.model

/**
 * Represents different types of gestures that can be performed on the floating dot.
 */
enum class Gesture {
    TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    QUADRUPLE_TAP,
    LONG_PRESS,
    DRAG_START,
    DRAG_MOVE,
    DRAG_END
}

/**
 * Represents the different modes the overlay can operate in.
 */
enum class OverlayMode {
    NORMAL
}