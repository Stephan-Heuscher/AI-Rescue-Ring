# AI Rescue Ring

> **Your intelligent assistant, always ready to help** – An AI-powered Android app providing instant assistance through a floating rescue ring

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 🛟 What does the app do?

AI Rescue Ring is your intelligent companion on Android - always visible, always ready to help. Tap the rescue ring whenever you need assistance, and a powerful AI will help you with any task on your device.

### Key Features

- **🤖 AI-Powered Help**: Tap the ring to chat with AI via voice or text
- **🎨 Customizable Ring**: Choose colors, transparency, and position
- **⌨️ Smart Positioning**: Automatically moves away from keyboard
- **🔒 Privacy-First**: Your API key stays on your device
- **♿ Accessibility**: Built with accessibility in mind

### How to Use

- **Tap** → Open AI chat
- **Voice or text** → Ask your question
- **Long press + drag** → Reposition ring

## 🚀 Installation

### Requirements
- Android 8.0 (API Level 26) or higher
- Google Gemini API key (free from [ai.google.dev](https://ai.google.dev))
- Two permissions required:
  - **Overlay permission**: For the floating ring
  - **Accessibility access**: For system assistance features

### Download & Setup

1. Download APK from [Releases](../../releases)
2. Install APK on your device
3. Open app and follow setup instructions
4. Grant permissions:
   - Enable overlay permission
   - Turn on "AI Rescue Ring" in accessibility settings
5. Add your Gemini API key in settings

## 🎮 How it works

1. **Enable ring**: Activate the switch in the app
2. **Move ring**: Long press + drag to reposition
3. **Get help**: Tap the ring and ask your question
4. **Customize**: Adjust color and transparency in settings

The ring automatically avoids the keyboard and stays in the correct position when rotating the device.

## 🛠️ Technical Details

### 🏗️ Architecture

**AI Rescue Ring** follows **Clean Architecture** principles with clear separation of concerns:

```
AI Rescue Ring/
├── domain/                    # Business logic & models
│   ├── model/
│   │   ├── DotPosition.kt     # Position model
│   │   ├── Gesture.kt         # Gesture enumeration
│   │   ├── OverlaySettings.kt # Settings model
│   │   ├── AIHelperConfig.kt  # AI configuration
│   │   └── AIMessage.kt       # AI message model
│   └── repository/
│       ├── SettingsRepository.kt    # Settings data access
│       └── AIHelperRepository.kt    # AI helper data access
├── data/                      # Data access layer
│   ├── local/
│   │   ├── SharedPreferencesDataSource.kt
│   │   └── SecureAIHelperDataSource.kt # Encrypted API key storage
│   ├── api/
│   │   ├── GeminiApiService.kt
│   │   └── GeminiApiModels.kt
│   └── repository/
│       ├── SettingsRepositoryImpl.kt
│       └── AIHelperRepositoryImpl.kt
├── service/                   # Service components
│   └── overlay/
│       ├── OverlayService.kt       # Main service
│       ├── KeyboardManager.kt      # Keyboard avoidance
│       ├── PositionAnimator.kt     # Position animations
│       ├── OrientationHandler.kt   # Rotation handling
│       ├── KeyboardDetector.kt     # Keyboard detection
│       ├── GestureDetector.kt      # Gesture recognition
│       └── OverlayViewManager.kt   # Overlay management
├── ui/                        # User interface
│   ├── MainActivity.kt        # Main screen
│   ├── SettingsActivity.kt    # Settings screen
│   ├── AIHelperActivity.kt    # AI chat interface
│   └── ImpressumActivity.kt   # Legal notice
├── util/                      # Utilities
│   └── AppConstants.kt        # Centralized constants
├── di/                        # Dependency Injection
│   ├── ServiceLocator.kt      # Manual DI
│   └── AppModule.kt           # Hilt module
└── BackHomeAccessibilityService.kt # Accessibility service
```

### 🧩 Architecture Principles

- **🧹 Clean Architecture**: Strict separation between Domain, Data and Presentation layers
- **🔄 Dependency Inversion**: Dependencies only point inward (to Domain)
- **📦 Single Responsibility**: Each class has exactly one responsibility
- **🧪 Testability**: Components are independently testable
- **🔧 Dependency Injection**: Loose coupling through ServiceLocator (Hilt-ready)

### 🤖 AI Integration

- **Gemini API**: Google's powerful AI model (Gemini 2.5 Flash)
- **Secure Storage**: API keys encrypted with Android KeyStore
- **Privacy**: All API calls go directly to Google - no intermediary servers
- **Voice Input**: Speech-to-text for hands-free interaction

### 🔧 Technology Stack

- **Language**: Kotlin 1.9+
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 36
- **UI Framework**: Material Design 3
- **Architecture**: Clean Architecture with ServiceLocator DI
- **Async**: Kotlin Coroutines + Flows
- **Security**: Android KeyStore for API key encryption
- **Networking**: OkHttp + Kotlin Serialization
- **Build**: Gradle Kotlin DSL

### 📡 Android APIs Used

- **Overlay API**: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **Accessibility API**: `AccessibilityService` for system integration
- **WindowInsets API**: Keyboard height detection (Android R+)
- **KeyStore API**: Secure API key storage
- **Speech Recognition**: Voice input for AI queries
- **SharedPreferences**: Persistent configuration
- **Gesture Detection**: Custom touch handler

## 🔒 Privacy & Security

- ✅ **No data collection**: App doesn't collect or store your data
- ✅ **Secure API storage**: Your Gemini API key is encrypted locally
- ✅ **Direct API calls**: All AI requests go directly to Google
- ✅ **No tracking**: No analytics, no advertising networks
- ✅ **Open source**: Full transparency - review the code yourself

## 💻 Development

### 🚀 Build Instructions

```bash
# Clone repository
git clone https://github.com/Stephan-Heuscher/AI-Rescue-Ring.git
cd AI-Rescue-Ring

# Open with Android Studio
# File → Open → Select project folder

# Sync dependencies
./gradlew build

# Create debug build
./gradlew assembleDebug

# Create release build (version auto-incremented)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest
```

#### 🔢 Automatic Versioning

Release builds automatically increment the version:
- **Version Code**: Incremented by 1 on each release build
- **Version Name**: Patch version (last digit) incremented by 1
- **Example**: `1.1.0` (Code: 6) → `1.1.1` (Code: 7)

Version stored in `version.properties` and updated before each release build.

### 🧪 Testing

```bash
# Unit tests for all modules
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport

# Integration tests (future)
./gradlew connectedAndroidTest
```

### Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add: AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📋 Roadmap

### 🚀 **Planned Features**

Development is driven by **your feedback**!

#### 🎯 **High Priority**
- **Hilt Migration**: From ServiceLocator to Hilt DI
- **Extended Tests**: Unit tests for all components
- **Performance Monitoring**: Battery and memory optimization
- **Accessibility Audit**: Full WCAG 2.2 AA compliance

#### 💡 **Possible Features**
- **Custom Prompts**: Pre-configured AI prompts for common tasks
- **Conversation History**: Save and review past AI interactions
- **Offline Mode**: Basic functionality without internet
- **Multi-Language**: Support for more languages
- **Themes**: Dark/light theme for overlay
- **Backup/Restore**: Export and import settings

### 💬 **Give Feedback**

- **GitHub Issues**: [Create new request](../../issues/new)
- **Feature Requests**: Tag with "enhancement" label
- **Bug Reports**: Tag with "bug" label and include reproduction steps

## 🐛 Known Limitations

- **Overlay over System UI**: Android doesn't allow overlays over system settings (security feature)
- **Battery Optimization**: Aggressive battery optimization may stop the service
- **API Key Required**: You need a free Google Gemini API key for AI features

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## 👤 Author

**Stephan Heuscher**

- GitHub: [@Stephan-Heuscher](https://github.com/Stephan-Heuscher)

## 🙏 Acknowledgments

- Developed with support from Claude (Anthropic)
- Icons from Material Design
- AI powered by Google Gemini

## 📞 Support

For questions or issues:
- Open an [Issue](../../issues)
- Contact the developer via GitHub

---

**Note**: This app uses AI to provide assistance and may not always provide accurate information. Always verify important information independently. Your API key is stored securely on your device and never sent to our servers.

Made with ❤️ for everyone who needs a helping hand
