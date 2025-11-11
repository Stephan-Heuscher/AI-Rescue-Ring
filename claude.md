# AI Rescue Ring - Claude Development Guide

This document provides technical context for Claude (Anthropic AI assistant) when working on the AI Rescue Ring codebase.

## Project Overview

AI Rescue Ring is an Android application that provides an intelligent floating rescue ring interface with AI-powered assistance through Google Gemini. The app displays a customizable overlay button that users can tap to access AI help.

## Architecture

The project follows **Clean Architecture** principles with strict layer separation:

### Layer Structure

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  (Activities, Services, UI)         │
├─────────────────────────────────────┤
│          Domain Layer               │
│  (Models, Repository Interfaces)    │
├─────────────────────────────────────┤
│           Data Layer                │
│  (Repository Implementations,       │
│   Data Sources, API Services)       │
└─────────────────────────────────────┘
```

### Key Components

#### 1. Domain Layer (`domain/`)
- **Models**: Data classes representing core business entities
  - `OverlaySettings.kt`: Overlay configuration including color, position, tap behavior
  - `TapBehavior.kt`: Enum defining three modes (STANDARD, NAVI, SAFE_HOME)
  - `AIHelperConfig.kt`: AI assistant configuration
  - `AIMessage.kt`: AI conversation messages
  - `DotPosition.kt`: Position model for overlay
  - `Gesture.kt`: Gesture enumeration

- **Repository Interfaces**: Define contracts for data access
  - `SettingsRepository.kt`: Settings data access
  - `AIHelperRepository.kt`: AI helper configuration access

#### 2. Data Layer (`data/`)
- **Local Data Sources**:
  - `SharedPreferencesDataSource.kt`: Unencrypted settings storage
  - `SecureAIHelperDataSource.kt`: Encrypted API key storage using Android KeyStore

- **API Services**:
  - `GeminiApiService.kt`: Google Gemini API integration
  - `GeminiApiModels.kt`: API request/response models

- **Repository Implementations**:
  - `SettingsRepositoryImpl.kt`: Implements SettingsRepository
  - `AIHelperRepositoryImpl.kt`: Implements AIHelperRepository

#### 3. Service Layer (`service/overlay/`)
- **OverlayService.kt**: Main foreground service managing the floating ring
- **GestureDetector.kt**: Custom gesture recognition (tap, double-tap, long-press, drag)
- **KeyboardManager.kt**: Automatic keyboard avoidance
- **PositionAnimator.kt**: Smooth position animations
- **OrientationHandler.kt**: Screen rotation handling
- **KeyboardDetector.kt**: Keyboard visibility detection
- **OverlayViewManager.kt**: Overlay view lifecycle management

#### 4. UI Layer
- **MainActivity.kt**: Main entry point and overlay toggle
- **SettingsActivity.kt**: Settings screen with color picker and preferences
- **AIHelperActivity.kt**: AI chat interface
- **ImpressumActivity.kt**: Legal information

#### 5. Utilities
- **AppConstants.kt**: Centralized constants (IMPORTANT: Contains default tap behavior)

#### 6. Accessibility
- **BackHomeAccessibilityService.kt**: Accessibility service for system integration

## Key Features Implementation

### 1. Tap Behavior Modes

**Location**: `domain/model/TapBehavior.kt`

Three modes are defined:
- **STANDARD**: 1 tap = Home, 2 taps = Back, 3 taps = Recent apps
- **NAVI**: Navigation mode (deprecated in favor of SAFE_HOME)
- **SAFE_HOME**: Requires long press (500ms) before dragging, all taps go to home

**Default Mode**: Safe Home (as of v3.1.0)
- Set in: `util/AppConstants.kt` line 76: `DEFAULT_TAP_BEHAVIOR = "SAFE_HOME"`

**Mode Storage**:
- SharedPreferences key: `tap_behavior` (defined in AppConstants.kt)
- Repository: `SettingsRepository.getTapBehavior()` and `setTapBehavior()`

**Mode Activation**:
- `OverlayService.kt` lines 201-226: Observes tap behavior changes
- `GestureDetector.kt` lines 52-66: Implements long-press requirement for Safe Home mode

### 2. Color Picker

**Location**: `SettingsActivity.kt` lines 125-207

**Features**:
- 8 preset colors (Blue, Red, Green, Orange, Purple, Cyan, Yellow, Gray)
- Custom color picker with:
  - RGB sliders (0-255 for each channel)
  - Hex input field (#RRGGBB format)
  - Bi-directional synchronization between sliders and hex input
  - Real-time color preview

**Layout**: `res/layout/color_picker_dialog.xml`

**Implementation Details**:
- Hex input validates pattern: `^#[0-9A-Fa-f]{6}$`
- Updates prevent circular loops using `isUpdatingFromHex` flag
- Color preview shows current selection
- RGB values displayed numerically

### 3. Gesture Detection

**Location**: `service/overlay/GestureDetector.kt`

**Gestures**:
- Single tap
- Double tap (300ms timeout)
- Long press (500ms timeout, required for Safe Home dragging)
- Drag (with optional long-press requirement)

**Safe Home Mode**:
- `requiresLongPressToDrag` flag set to true for Safe Home mode
- Pulsing white halo (128dp) shows when draggable
- Button becomes square (8dp radius) in Safe Home mode

### 4. Keyboard Avoidance

**Location**: `service/overlay/KeyboardManager.kt`

**Features**:
- Automatic detection of keyboard visibility
- Smart repositioning with 1.5x dot diameter margin
- Debouncing to prevent flickering
- WindowInsets API for accurate keyboard height (Android R+)
- Fallback to height estimation for older versions

**Constants** (in AppConstants.kt):
- `KEYBOARD_CHECK_INTERVAL_MS = 100L`
- `KEYBOARD_HEIGHT_ESTIMATE_PERCENT = 0.38f`
- `KEYBOARD_THRESHOLD_PERCENT = 0.15f`
- `KEYBOARD_MARGIN_MULTIPLIER = 1.0f`

### 5. AI Integration

**Location**: `data/api/GeminiApiService.kt`

**Features**:
- Google Gemini 2.5 Flash API integration
- Encrypted API key storage (Android KeyStore)
- Voice and text input support
- Direct API communication (no intermediary servers)

**Security**:
- API keys stored in: `SecureAIHelperDataSource.kt`
- Uses Android KeyStore for encryption
- Keys never transmitted to developer servers

## Development Workflow

### Building the App

```bash
# Debug build
./gradlew assembleDebug

# Release build (auto-increments version)
./gradlew assembleRelease

# Run tests
./gradlew testDebugUnitTest
```

### Version Management

**Location**: `version.properties`

Release builds automatically:
- Increment version code by 1
- Increment patch version (e.g., 3.1.0 → 3.1.1)

### Dependency Injection

**Location**: `di/ServiceLocator.kt`

Manual dependency injection using ServiceLocator pattern (Hilt-ready architecture).

## Important Files Reference

| Purpose | File Path | Key Lines |
|---------|-----------|-----------|
| Default tap behavior | `util/AppConstants.kt` | 76 |
| Tap behavior enum | `domain/model/TapBehavior.kt` | 6-24 |
| Gesture detection | `service/overlay/GestureDetector.kt` | 41-66 |
| Mode activation | `service/overlay/OverlayService.kt` | 201-226 |
| Color picker logic | `SettingsActivity.kt` | 125-207 |
| Color picker layout | `res/layout/color_picker_dialog.xml` | 1-130 |
| Settings storage | `data/local/SharedPreferencesDataSource.kt` | 128-135 |

## Constants Overview

All application constants are centralized in `util/AppConstants.kt`:

### Overlay Appearance
- `DOT_SIZE_DP = 48`
- `DOT_STROKE_WIDTH_DP = 3`
- `OVERLAY_LAYOUT_SIZE_DP = 64`

### Gesture Timeouts
- `GESTURE_DOUBLE_TAP_TIMEOUT_MS = 300L`
- `GESTURE_LONG_PRESS_TIMEOUT_MS = 500L`

### Default Values
- `DEFAULT_COLOR = 0xFF2196F3` (Blue)
- `DEFAULT_TAP_BEHAVIOR = "SAFE_HOME"`
- `DEFAULT_KEYBOARD_AVOIDANCE = true`
- `DEFAULT_ENABLED = false`

### SharedPreferences Keys
- `PREFS_NAME = "overlay_settings"`
- `KEY_COLOR = "overlay_color"`
- `KEY_TAP_BEHAVIOR = "tap_behavior"`
- `KEY_KEYBOARD_AVOIDANCE = "keyboard_avoidance"`
- `KEY_POSITION_X = "position_x"`
- `KEY_POSITION_Y = "position_y"`

## Testing Strategy

### Unit Tests
- Domain models should be pure and easily testable
- Repository implementations should be testable with mocked data sources
- Use `./gradlew test` to run unit tests

### Integration Tests
- Test overlay service lifecycle
- Test gesture detection with various timing
- Test keyboard avoidance logic

## Permissions Required

1. **SYSTEM_ALERT_WINDOW**: Display overlay
2. **BIND_ACCESSIBILITY_SERVICE**: System integration
3. **INTERNET**: Gemini API communication
4. **RECORD_AUDIO**: Voice input
5. **RECEIVE_BOOT_COMPLETED**: Auto-start after reboot

## Recent Changes (v3.1.0)

1. **Default Mode Change**: Standard mode replaced with Safe Home mode as default
   - Changed in `AppConstants.kt` line 76
   - Updated all documentation

2. **Enhanced Color Picker**:
   - Added hex input field to `color_picker_dialog.xml`
   - Implemented bi-directional sync in `SettingsActivity.kt`
   - Improved user experience with direct hex color entry

3. **Documentation**:
   - Created this `claude.md` file
   - Updated README.md, RELEASE_NOTES (EN/DE), PRIVACY_POLICY.md

## Coding Conventions

- **Language**: Kotlin 1.9+
- **Style**: Android Kotlin style guide
- **Architecture**: Clean Architecture with dependency inversion
- **Async**: Kotlin Coroutines + Flow for reactive data
- **Null Safety**: Leverage Kotlin's null safety features
- **Constants**: Centralize in `AppConstants.kt`

## Common Tasks

### Adding a New Setting

1. Add constant to `AppConstants.kt`:
   ```kotlin
   const val KEY_NEW_SETTING = "new_setting"
   const val DEFAULT_NEW_SETTING = value
   ```

2. Add to `OverlaySettings.kt` model:
   ```kotlin
   val newSetting: Type = AppConstants.DEFAULT_NEW_SETTING
   ```

3. Add repository methods in `SettingsRepository.kt`:
   ```kotlin
   fun getNewSetting(): Flow<Type>
   suspend fun setNewSetting(value: Type)
   ```

4. Implement in `SettingsRepositoryImpl.kt` and `SharedPreferencesDataSource.kt`

5. Add UI in `SettingsActivity.kt` or `activity_settings.xml`

### Modifying Gesture Behavior

1. Update logic in `GestureDetector.kt`
2. Adjust timeouts in `AppConstants.kt` if needed
3. Update mode-specific behavior in `OverlayService.kt`
4. Consider backward compatibility with existing saved settings

### Changing Default Behavior

1. Update constant in `AppConstants.kt`
2. Test fresh install vs. upgrade scenarios
3. Update all documentation files (README, RELEASE_NOTES, PRIVACY_POLICY, this file)

## Troubleshooting

### Common Issues

1. **Overlay not showing**: Check SYSTEM_ALERT_WINDOW permission
2. **Gestures not working**: Check accessibility service enabled
3. **Keyboard avoidance not working**: Check WindowInsets API availability
4. **Settings not persisting**: Check SharedPreferences initialization

### Debugging Tips

- Use `Log.d()` tags consistently (e.g., "OverlayService", "GestureDetector")
- Check `OverlayService` is running in foreground notification
- Verify accessibility service is active in Android settings
- Test on multiple Android versions (min API 26, target API 36)

## Future Enhancements

See README.md "Roadmap" section for planned features.

Priority areas:
- Hilt migration for dependency injection
- Extended test coverage
- Performance optimization
- Accessibility improvements

## Contact & Support

- **Developer**: Stephan Heuscher
- **GitHub**: https://github.com/Stephan-Heuscher/AI-Rescue-Ring
- **Issues**: https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues

---

**Last Updated**: 2025-11-11 (v3.1.0)
