# Screenshot Implementation

## Overview

This implementation adds screenshot capability to the AI Rescue Ring app using Android's accessibility service. It follows the best practices from the [ScreenshotTile](https://github.com/cvzi/ScreenshotTile) library with automatic fallback mechanisms.

## Implementation Details

### Architecture

The screenshot functionality is implemented across three main components:

1. **BackHomeAccessibilityService** (`BackHomeAccessibilityService.kt:205-322`)
   - Core screenshot methods with version-specific implementations
   - Automatic fallback handling

2. **ScreenshotHelper** (`util/ScreenshotHelper.kt`)
   - Utility class for easy access to screenshot functionality
   - Provides user-friendly error handling and toast notifications

3. **Accessibility Service Configuration** (`res/xml/accessibility_service_config.xml`)
   - Enables screenshot capability with `android:canTakeScreenshot="true"`

### Screenshot Methods

The implementation uses **two methods** with automatic selection based on Android version:

#### 1. Modern Method (Android 11+)
- **Primary**: Uses `AccessibilityService.takeScreenshot()` API
- **Fallback**: Automatically falls back to legacy method if modern API fails
- **Features**:
  - Captures screenshots as Bitmap via HardwareBuffer
  - Saves to app-specific directory: `/storage/emulated/0/Android/data/ch.heuscher.airescuering/files/screenshots/`
  - Filename format: `screenshot_yyyyMMdd_HHmmss.png`
  - Returns file path to caller

**Code Location**: `BackHomeAccessibilityService.kt:222-268`

#### 2. Legacy Method (Android 9+)
- **Implementation**: Uses `performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)`
- **Behavior**: Simulates pressing the physical screenshot button combination
- **Features**:
  - Uses device's native screenshot functionality
  - Saves to system default location
  - Shows system screenshot UI/notifications
  - Works on all Android 9+ devices

**Code Location**: `BackHomeAccessibilityService.kt:271-297`

### Fallback Mechanism

The implementation includes **multiple levels of fallback**:

```
Android 11+ Device
    ↓
Try: takeScreenshot() API
    ↓
If fails → performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    ↓
If still fails → Error callback

Android 9-10 Device
    ↓
performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    ↓
If fails → Error callback
```

## Usage

### Basic Usage

```kotlin
// Using ScreenshotHelper (recommended)
ScreenshotHelper.takeScreenshot(
    context = this,
    onSuccess = { filePath ->
        Log.d("Screenshot", "Saved to: $filePath")
    },
    onFailure = { error ->
        Log.e("Screenshot", "Failed: $error")
    }
)
```

### Direct Service Access

```kotlin
// Using BackHomeAccessibilityService directly
val service = BackHomeAccessibilityService.instance
service?.takeScreenshot(
    onSuccess = { path -> /* handle success */ },
    onFailure = { error -> /* handle error */ }
)
```

### Check Availability

```kotlin
if (ScreenshotHelper.isAvailable()) {
    // Take screenshot
} else {
    // Show instructions to enable accessibility service
    val instructions = ScreenshotHelper.getEnableInstructions()
}
```

## Requirements

### Permissions
- **Accessibility Service**: Must be enabled by user in Settings > Accessibility
- **No storage permissions needed**: Screenshots are saved to app-specific directory

### Android Version Support
- **Minimum**: Android 9 (API 28) - uses `performGlobalAction`
- **Recommended**: Android 11+ (API 30) - uses modern `takeScreenshot()` API

### Configuration

The accessibility service must have the `canTakeScreenshot` capability:

```xml
<accessibility-service
    ...
    android:canTakeScreenshot="true"
    ... />
```

## File Storage

### Android 11+ (Modern Method)
- **Location**: App-specific external files directory
- **Path**: `/storage/emulated/0/Android/data/ch.heuscher.airescuering/files/screenshots/`
- **Format**: PNG with 100% quality
- **Filename**: `screenshot_yyyyMMdd_HHmmss.png`
- **Note**: Files are automatically deleted when app is uninstalled

### Android 9-10 (Legacy Method)
- **Location**: System default screenshot location (usually `Pictures/Screenshots/`)
- **Format**: Device default (usually PNG)
- **Filename**: Device default naming scheme

## Error Handling

The implementation provides detailed error callbacks:

- **Accessibility service not enabled**: "Accessibility service is not enabled. Please enable it in Settings."
- **Modern API failure**: Automatically falls back to legacy method
- **Legacy method failure**: "Failed to trigger screenshot. Ensure accessibility service is properly enabled."
- **Bitmap processing error**: "Failed to process screenshot: [error details]"

## Testing

To test the screenshot functionality:

1. **Enable Accessibility Service**:
   - Go to Settings > Accessibility
   - Find "AI Rescue Ring"
   - Enable the service
   - Grant required permissions

2. **Trigger Screenshot**:
   ```kotlin
   // Add to any activity or service
   ScreenshotHelper.takeScreenshot(this)
   ```

3. **Verify Screenshots**:
   - Android 11+: Check app files directory
   - Android 9-10: Check Pictures/Screenshots in gallery

## Integration Examples

### Add to Gesture Handler
```kotlin
// In GestureDetector or similar
when (gesture) {
    Gesture.SCREENSHOT -> {
        ScreenshotHelper.takeScreenshot(
            context = applicationContext,
            onSuccess = { path ->
                // Show notification or share screenshot
            }
        )
    }
}
```

### Add to UI Button
```kotlin
screenshotButton.setOnClickListener {
    if (ScreenshotHelper.isAvailable()) {
        ScreenshotHelper.takeScreenshot(this)
    } else {
        showEnableAccessibilityDialog()
    }
}
```

## Known Limitations

1. **Accessibility Service Required**: User must manually enable the accessibility service
2. **Android 11+ Restrictions**: Some OEMs may restrict accessibility service screenshot capabilities
3. **File Path Not Available (Legacy)**: When using `performGlobalAction`, the exact file path is not returned
4. **Background Screenshots**: May not work when app is in background (device/OEM dependent)

## References

- ScreenshotTile Library: https://github.com/cvzi/ScreenshotTile
- Android Accessibility Service: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- takeScreenshot() API: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,%20java.util.concurrent.Executor,%20android.accessibilityservice.AccessibilityService.TakeScreenshotCallback)
