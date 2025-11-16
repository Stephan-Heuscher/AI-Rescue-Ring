# Screenshot Functionality Documentation

## Current Status: NOT IMPLEMENTED ⚠️

**Important**: Despite the branch name `claude/screenshot-functionality-update-01HMgtvjG4mDebSzV83TuGcZ`, screenshot capture functionality is **NOT currently implemented** in the AI Rescue Ring codebase.

This document provides technical documentation for future screenshot functionality implementation.

---

## Overview

The AI Rescue Ring app currently provides:
- Floating overlay ring UI
- Text and voice chat with Gemini AI
- Gesture detection (tap, long-press, drag)
- Smart keyboard avoidance
- Accessibility service integration

**What's Missing**: Screen capture, screenshot processing, and image transmission to AI.

---

## Current Architecture Analysis

### 1. Permissions (AndroidManifest.xml:1-78)

**Existing Permissions**:
```xml
android.permission.SYSTEM_ALERT_WINDOW    - Overlay functionality ✓
android.permission.RECEIVE_BOOT_COMPLETED - Auto-start ✓
android.permission.INTERNET               - API calls ✓
android.permission.RECORD_AUDIO           - Voice input ✓
```

**Missing for Screenshot Functionality**:
```xml
<!-- Required for MediaProjection API (screen capture) -->
<!-- No dangerous permissions needed - runtime permission request only -->

<!-- Optional: Save screenshots to external storage -->
android.permission.WRITE_EXTERNAL_STORAGE (API < 29)
android.permission.READ_MEDIA_IMAGES (API 33+)
```

### 2. Service Layer

#### OverlayService.kt (service/overlay/OverlayService.kt:1-536)

**Current Behavior**: Manages floating ring and launches AIHelperActivity on tap.

**Key Methods**:
- `handleTap()` (line 299) - Detects single tap, launches AI chat
- `launchAIHelper()` (line 315) - Opens AIHelperActivity

**Screenshot Integration Point**:
```kotlin
// Future implementation location: OverlayService.kt:299-326
private fun handleTap() {
    // Current: Just launches chat
    launchAIHelper()

    // TODO: Add screenshot capture option
    // Option 1: Capture screenshot before launching chat
    // Option 2: Add button in AIHelperActivity to capture on demand
}
```

#### AIHelperActivity.kt (AIHelperActivity.kt:1-373)

**Current Functionality**:
- Text and voice input
- Chat message display
- API key entry
- Message history with RecyclerView

**Critical TODO** (line 298):
```kotlin
private fun showSuggestionDialog(suggestion: String) {
    onApprove = {
        Toast.makeText(this, "Suggestion approved", Toast.LENGTH_SHORT).show()
        // TODO: Execute the approved action
    }
}
```

**Screenshot Integration Points**:
- Add "Capture Screenshot" button in UI
- Display captured screenshot preview in chat
- Attach screenshot to next AI message

### 3. API Layer

#### GeminiApiModels.kt (data/api/GeminiApiModels.kt:1-79)

**Current Structure** (TEXT ONLY):
```kotlin
@Serializable
data class Part(
    val text: String? = null  // Only supports text currently
)
```

**Required Changes for Screenshots**:
```kotlin
@Serializable
data class Part(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: InlineData? = null  // ADD: Image support
)

@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,  // "image/jpeg" or "image/png"
    val data: String       // Base64 encoded image
)
```

#### GeminiApiService.kt (data/api/GeminiApiService.kt:1-179)

**Current**: Sends text-only requests to Gemini API.

**Required Changes**: Support multimodal requests with both text and images.

---

## Implementation Requirements

### Phase 1: Screen Capture Setup

#### 1.1 Add MediaProjection Permission Request

**New Service**: `ScreenCaptureService.kt`
```kotlin
package ch.heuscher.airescuering.service

import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder

class ScreenCaptureService : Service() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    fun requestScreenCapturePermission(activity: Activity) {
        val projectionManager = activity.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        activity.startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }
}
```

**Location**: Create new file `app/src/main/java/ch/heuscher/airescuering/service/ScreenCaptureService.kt`

#### 1.2 Capture Screenshot with MediaProjection

```kotlin
private fun captureScreenshot(data: Intent, width: Int, height: Int) {
    val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
        as MediaProjectionManager
    val projection = projectionManager.getMediaProjection(
        Activity.RESULT_OK,
        data
    )

    val imageReader = ImageReader.newInstance(
        width, height,
        PixelFormat.RGBA_8888, 1
    )

    val virtualDisplay = projection.createVirtualDisplay(
        "ScreenCapture",
        width, height, resources.displayMetrics.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface, null, null
    )

    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage()
        val bitmap = imageToBitmap(image)
        image.close()

        // Process screenshot
        onScreenshotCaptured(bitmap)

        virtualDisplay.release()
        projection.stop()
    }, Handler(Looper.getMainLooper()))
}
```

#### 1.3 Convert Bitmap to Base64

```kotlin
private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
```

### Phase 2: API Integration

#### 2.1 Update API Models (GeminiApiModels.kt)

Add the `InlineData` class and update `Part` as shown in section 3 above.

#### 2.2 Update Repository (AIHelperRepository.kt)

Add method to send messages with screenshots:

```kotlin
interface AIHelperRepository {
    // Existing
    suspend fun sendMessage(message: String): Result<String>

    // NEW: Add screenshot support
    suspend fun sendMessageWithScreenshot(
        message: String,
        screenshotBase64: String,
        mimeType: String = "image/jpeg"
    ): Result<String>
}
```

#### 2.3 Update AIHelperActivity (AIHelperActivity.kt)

Add UI elements:
```kotlin
// Add to layout
private lateinit var captureButton: ImageButton
private lateinit var screenshotPreview: ImageView
private var currentScreenshot: Bitmap? = null

private fun setupScreenshotButton() {
    captureButton.setOnClickListener {
        // Request screenshot capture
        requestScreenCapture()
    }
}

private fun onScreenshotCaptured(bitmap: Bitmap) {
    currentScreenshot = bitmap
    screenshotPreview.setImageBitmap(bitmap)
    screenshotPreview.visibility = View.VISIBLE
}

private fun sendMessageWithAttachment() {
    val text = messageInput.text.toString()
    val screenshot = currentScreenshot

    if (screenshot != null) {
        // Send with screenshot
        val base64 = bitmapToBase64(screenshot)
        viewModel.sendMessageWithScreenshot(text, base64)
    } else {
        // Send text only
        viewModel.sendMessage(text)
    }
}
```

### Phase 3: User Flow

**Proposed User Experience**:

1. **Option A: Automatic Screenshot on Tap**
   - User taps rescue ring
   - App captures screenshot automatically
   - Opens chat with screenshot attached
   - User adds context/question
   - Sends to AI

2. **Option B: Manual Screenshot Capture**
   - User taps rescue ring
   - Opens chat interface
   - User taps "📷 Capture Screenshot" button
   - App minimizes, captures screenshot
   - Returns to chat with screenshot attached
   - User sends message

**Recommendation**: Option B (Manual) for better user control and privacy.

---

## Implementation Checklist

### Core Functionality
- [ ] Create `ScreenCaptureService.kt`
- [ ] Add MediaProjection permission request flow
- [ ] Implement screenshot capture with `ImageReader`
- [ ] Add bitmap to Base64 conversion utility
- [ ] Update `GeminiApiModels.kt` to support images
- [ ] Extend `AIHelperRepository` with screenshot methods
- [ ] Update `AIHelperActivity` UI with screenshot button

### UI/UX Enhancements
- [ ] Add screenshot preview in chat
- [ ] Add "Remove screenshot" option
- [ ] Show loading state during capture
- [ ] Handle screenshot compression for large images
- [ ] Add screenshot size/quality settings

### Error Handling
- [ ] Handle MediaProjection permission denial
- [ ] Handle low memory during capture
- [ ] Handle API errors with image attachments
- [ ] Add user-friendly error messages

### Testing
- [ ] Test screenshot capture on various devices
- [ ] Test with different screen resolutions
- [ ] Test API integration with images
- [ ] Verify Base64 encoding correctness
- [ ] Test memory usage with large screenshots

### Documentation
- [ ] Update README.md with screenshot feature
- [ ] Add permission documentation
- [ ] Update privacy policy if needed
- [ ] Create user guide for screenshot feature

---

## Technical Considerations

### Performance

**Screenshot Size Optimization**:
- Compress JPEG quality to 70-85% for API transmission
- Consider downscaling for very high-resolution displays
- Typical size: 1080x2400 → ~200-400KB after JPEG compression

**Memory Management**:
```kotlin
// Always recycle bitmaps after use
bitmap.recycle()
currentScreenshot = null

// Consider using BitmapFactory.Options for large images
val options = BitmapFactory.Options().apply {
    inSampleSize = 2  // Downsample by 2x
    inPreferredConfig = Bitmap.Config.RGB_565  // Lower quality
}
```

### Privacy & Security

**Best Practices**:
1. **User Consent**: Always show MediaProjection system dialog
2. **Temporary Storage**: Don't save screenshots permanently without consent
3. **API Transmission**: Warn users that screenshots are sent to Google AI
4. **Sensitive Content**: Add warning about capturing passwords/PINs
5. **Clear Cache**: Delete screenshot data after AI response

**Privacy Notice Example**:
```
⚠️ Screenshot Capture
The captured image will be sent to Google Gemini AI.
Make sure no sensitive information is visible.
```

### API Limits

**Gemini API Image Constraints**:
- Max image size: 10MB (Base64 encoded)
- Supported formats: JPEG, PNG, WebP, HEIC, HEIF
- Recommended: JPEG with 80% quality
- Multiple images: Supported in single request

### Battery Impact

**Optimization**:
- Use `ImageReader` (hardware accelerated) instead of `PixelCopy`
- Release `VirtualDisplay` immediately after capture
- Don't keep `MediaProjection` running continuously
- Capture only when user explicitly requests

---

## Dependencies

**No New Dependencies Required** ✓

All screenshot functionality uses Android SDK APIs:
- `android.media.projection.MediaProjectionManager` (API 21+)
- `android.media.ImageReader` (API 19+)
- `android.util.Base64` (Standard library)

**Current Min SDK**: API 26 (Android 8.0) - Fully supported ✓

---

## Related Files

| File | Purpose | Screenshot Integration |
|------|---------|----------------------|
| `app/src/main/AndroidManifest.xml` | Permissions | No changes needed |
| `app/src/main/java/ch/heuscher/airescuering/service/overlay/OverlayService.kt` | Overlay management | Trigger screenshot on tap |
| `app/src/main/java/ch/heuscher/airescuering/AIHelperActivity.kt` | Chat interface | Add screenshot UI/logic |
| `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiModels.kt` | API models | Add `InlineData` class |
| `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiService.kt` | API service | Support multimodal requests |
| `app/src/main/java/ch/heuscher/airescuering/domain/repository/AIHelperRepository.kt` | Repository interface | Add screenshot methods |

---

## Future Enhancements

### Advanced Features
- **Area Selection**: Let users select specific screen areas to capture
- **OCR Integration**: Extract text from screenshots before sending
- **Annotation**: Allow users to draw/highlight on screenshots
- **Screenshot History**: Save previous screenshots in conversation
- **Batch Processing**: Send multiple screenshots in one request

### AI-Powered Features
- **Auto-Detection**: Detect when AI needs visual context
- **Smart Cropping**: Automatically crop irrelevant UI elements
- **Visual Search**: "What is this?" button on any screen
- **Live Screen Sharing**: Continuous screen context for AI

---

## References

### Android Documentation
- [MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection)
- [ImageReader](https://developer.android.com/reference/android/media/ImageReader)
- [Screen Capture Guide](https://developer.android.com/media/grow/media-projection)

### Gemini API Documentation
- [Gemini Vision API](https://ai.google.dev/gemini-api/docs/vision)
- [Multimodal Prompts](https://ai.google.dev/gemini-api/docs/prompting-strategies#multimodal-prompts)
- [Image Best Practices](https://ai.google.dev/gemini-api/docs/vision#image-requirements)

---

## Questions & Contact

For questions about screenshot implementation:
- Create an issue: [GitHub Issues](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues)
- Tag: `enhancement`, `screenshot`, `feature-request`

---

**Last Updated**: 2025-11-16
**Status**: Documentation complete, implementation pending
**Branch**: `claude/screenshot-functionality-update-01HMgtvjG4mDebSzV83TuGcZ`
