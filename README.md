# AI Rescue Ring

> **Your intelligent assistant, always ready to help** – An AI-powered Android app providing instant assistance through a floating rescue ring

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<!-- TODO: [Visuals] Add a demo GIF here showing the ring floating, avoiding the keyboard, and opening the chat. 
Example: ![AI Rescue Ring Demo](docs/demo.gif) 
-->

## 🛟 What does the app do?

AI Rescue Ring is your intelligent companion on Android - always visible, always ready to help. Tap the rescue ring whenever you need assistance, and a powerful AI will help you with any task on your device.

### Key Features

- **👀 Context-Aware Help**: The AI "sees" what you see—instantly analyzes your current screen to provide relevant answers.
- **🤖 AI-Powered Chat**: Voice or text chat powered by Google's **Gemini 2.5 Flash**.
- **⌨️ Smart Positioning**: The ring automatically detects your keyboard and floats out of the way so it never blocks your typing.
- **🎨 Customizable**: Choose your ring's color, transparency, and size to match your style.
- **🔒 Privacy-First**: Your API key stays on your device; requests go directly to Google.

## 🚀 Installation

<!-- TODO: [Download] Add a link to your latest release or Play Store listing. -->
[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-blue?style=for-the-badge&logo=android)](../../releases) 

### 📱 Requirements
- Android 8.0 (API Level 26) or higher
- Google Gemini API key (free from [ai.google.dev](https://ai.google.dev))

### ⚙️ Permissions Explained
This app requires two specific permissions to function:
1.  **Display over other apps**: To show the floating ring on top of your screen.
2.  **Accessibility Service**: 
    *   *Why?* Used strictly to detect if the keyboard is open (to move the ring) and to read screen content **only when you ask for help**. 
    *   *Privacy:* No data is read or stored unless you actively tap the ring to chat.

### 🛠️ Build from Source
If you prefer to build it yourself:
1. Clone the repository:
   ```bash
   git clone https://github.com/Stephan-Heuscher/AI-Rescue-Ring.git
   ```
2. Open in **Android Studio** (Ladybug or newer recommended).
3. Add your API key in `local.properties` (optional, or enter in-app).
4. Run on your device.

## 🎮 How to Use

- **Tap** → Opens the AI chat. The app takes a temporary screenshot so the AI knows what you are looking at.
- **Voice or Text** → Ask your question (e.g., "What does this error message mean?" or "Translate this paragraph").
- **Long Press + Drag** → Move the ring to a spot that works for you.

## 🛠️ Technical Details

### 🏗️ Architecture
**AI Rescue Ring** follows **Clean Architecture** principles with clear separation of concerns:
- **Domain Layer**: Pure business logic.
- **Data Layer**: Repositories and Data Sources.
- **Presentation Layer**: UI and ViewModels.

### 🤖 AI Integration
- **Gemini API**: Uses Google's **Gemini 2.5 Flash** for low-latency, multimodal responses.
- **Secure Storage**: API keys are encrypted using the Android KeyStore system.
- **Direct Privacy**: All API calls go directly from your phone to Google's servers—no intermediary backend.

### 📡 Android APIs Used
- **Overlay API**: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **Accessibility API**: `AccessibilityService` for system integration
- **WindowInsets API**: Keyboard height detection (Android R+)
- **Speech Recognition**: Voice input for hands-free interaction

## 🔒 Privacy & Security

- ✅ **No data collection**: App doesn't collect or store your data.
- ✅ **Secure API storage**: Your Gemini API key is encrypted locally.
- ✅ **Direct API calls**: All AI requests go directly to Google.
- ✅ **Open source**: Full transparency - review the code yourself.

## 📋 Roadmap

### 🚀 **Planned Features**

#### 🎯 **High Priority**
- **"Make it so"**: Agentic capabilities to let the AI perform actions based on its suggestions.
- **Gemini 3.0 Support**: Upgrade to the newest Gemini 3.0 model for enhanced reasoning.

#### 💡 **Possible Features**
- **Custom Prompts**: Pre-configured AI prompts for common tasks.
- **Conversation History**: Save and review past AI interactions.
- **Themes**: Dark/light theme for the chat overlay.

### 💬 **Give Feedback**
- **GitHub Issues**: [Create new request](../../issues/new)
- **Bug Reports**: Tag with "bug" label and include reproduction steps.

## 📄 License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## 👤 Author

**Stephan Heuscher**
- GitHub: [@Stephan-Heuscher](https://github.com/Stephan-Heuscher)

## 🙏 Acknowledgments
- Developed with support from Claude (Anthropic).
- Icons from Material Design.
- AI powered by Google Gemini.

---

**Note**: This app uses AI to provide assistance and may not always provide accurate information. Always verify important information independently. Your API key is stored securely on your device and never sent to our servers.

Made with ❤️ for everyone who needs a helping hand