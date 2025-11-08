package ch.heuscher.back_home_dot.service.overlay

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.WindowManager
import ch.heuscher.back_home_dot.domain.model.DotPosition

/**
 * Handles screen orientation changes and position transformations.
 * Preserves dot's physical position on screen during rotations.
 */
class OrientationHandler(private val context: Context) {
    companion object {
        private const val TAG = "OrientationHandler"
    }

    /**
     * Get the current screen rotation (0, 1, 2, or 3 representing 0°, 90°, 180°, 270°)
     */
    fun getCurrentRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.defaultDisplay.rotation
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    /**
     * Get the usable screen size for the current orientation
     */
    fun getUsableScreenSize(): Point {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            size.x = bounds.width()
            size.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
        }
        return size
    }

    /**
     * Transform a position from one screen orientation to another.
     * This preserves the physical location on the screen during rotation.
     *
     * @param position The dot position to transform
     * @param oldWidth Screen width before rotation
     * @param oldHeight Screen height before rotation
     * @param oldRotation Screen rotation before change (0-3)
     * @param newRotation Screen rotation after change (0-3)
     * @return Transformed position that preserves physical screen location
     */
    fun transformPosition(
        position: DotPosition,
        oldWidth: Int,
        oldHeight: Int,
        oldRotation: Int,
        newRotation: Int
    ): DotPosition {
        val delta = (newRotation - oldRotation + 4) % 4  // 0,1,2,3 for 0,90,180,270 degrees clockwise
        val x = position.x
        val y = position.y

        Log.d(TAG, "transformPosition: delta=$delta, oldRot=$oldRotation, newRot=$newRotation, input=($x,$y), oldSize=${oldWidth}x${oldHeight}")

        val result = when (delta) {
            0 -> DotPosition(x, y)                           // No rotation
            1 -> DotPosition(y, oldWidth - x)                // 90° clockwise
            2 -> DotPosition(oldWidth - x, oldHeight - y)    // 180°
            3 -> DotPosition(oldHeight - y, x)               // 270° clockwise (or 90° counter-clockwise)
            else -> DotPosition(x, y)
        }

        Log.d(TAG, "transformPosition: result=(${result.x},${result.y})")
        return result
    }

    /**
     * Transform a center-based position during rotation.
     * Used for calculating the physical center point that should be preserved.
     *
     * @param centerPosition Center point of the dot
     * @param oldWidth Screen width before rotation
     * @param oldHeight Screen height before rotation
     * @param oldRotation Screen rotation before change
     * @param newRotation Screen rotation after change
     * @return Transformed center position
     */
    fun transformCenterPosition(
        centerPosition: DotPosition,
        oldWidth: Int,
        oldHeight: Int,
        oldRotation: Int,
        newRotation: Int
    ): DotPosition {
        return transformPosition(centerPosition, oldWidth, oldHeight, oldRotation, newRotation)
    }
}
