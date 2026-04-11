# J-AI-mes

> **Your personal AI butler, always at your service** – An AI-powered Android app providing a personal concierge through a floating butler button

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API Level](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 🎩 What is J-AI-mes?

J-AI-mes (a play on "James" + "AI") is your personal AI butler for Android - always visible, always listening, always ready to help. Tap the butler button, speak your request, and J-AI-mes handles it for you.

### Key Features

- **🗣️ Voice-First**: Speak naturally - J-AI-mes listens and responds aloud with a refined butler voice
- **👀 Context-Aware**: J-AI-mes "sees" your screen and understands what you're looking at
- **🤖 Agentic**: Not just advice - J-AI-mes can tap, swipe, and type on your behalf
- **🎩 Butler Personality**: Warm, professional, and always polite. Like having a world-class concierge in your pocket
- **🌍 Multilingual**: British English or German, automatically matching your phone's language
- **⌨️ Smart Positioning**: The button automatically avoids your keyboard

## 🚀 Installation

[![Download APK](https://img.shields.io/badge/Download-Latest%20APK-blue?style=for-the-badge&logo=android)](../../releases) 

### Requirements
- Android 8.0 (API Level 26) or higher

### Permissions Explained
1. **Display over other apps**: To show J-AI-mes' floating butler button
2. **Accessibility Service**: To see your screen (only when you ask for help) and perform actions on your behalf

### Build from Source
```bash
git clone https://github.com/Stephan-Heuscher/AI-Rescue-Ring.git
```
Open in **Android Studio**, build and run.

## 🎮 How to Use

- **Tap** the butler button → J-AI-mes greets you and starts listening
- **Speak** your request naturally ("Read me this message", "Open Settings")
- **J-AI-mes responds** aloud and can perform actions on your phone
- **Long press** → Quick action menu
- **Drag** → Reposition the button

## 🏗️ Architecture

- **AI Model**: Google Gemini 3.1 Flash Lite (fast conversation) + Gemini 3 Pro (complex actions)
- **Voice**: Google TTS with butler-tuned British English / German voice
- **Design**: Material Design with premium navy + gold aesthetic

## 🔒 Privacy

- ✅ No data collection
- ✅ Direct API calls to Google
- ✅ Open source - review the code yourself

## 📄 License

MIT License - see [LICENSE](LICENSE)

## 👤 Author

**Stephan Heuscher** - [@Stephan-Heuscher](https://github.com/Stephan-Heuscher)

---

*Made with 🎩 for everyone who deserves a personal butler*