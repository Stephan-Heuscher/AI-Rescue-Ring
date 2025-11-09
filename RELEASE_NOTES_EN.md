# Release Notes - AI Rescue Ring

## Version 3.0.0 (2025-11-09)

### Complete Rebranding & AI Integration

We're excited to announce **AI Rescue Ring v3.0.0** - a complete transformation from a navigation assistant to an intelligent AI-powered helper!

#### 🤖 AI-Powered Assistance (NEW!)
- **Gemini Integration**: Tap the rescue ring to chat with Google's Gemini 2.5 Flash AI
- **Voice Input**: Speak your questions naturally
- **Text Input**: Type your queries
- **Direct API Connection**: Your queries go directly to Google - we don't intercept or store them
- **Secure Storage**: API keys encrypted with Android KeyStore
- **Instant Help**: Get AI assistance anywhere on your device

#### 🎨 Complete Visual Rebrand
- **New Name**: Assistive Tap → AI Rescue Ring
- **Package Change**: `ch.heuscher.back_home_dot` → `ch.heuscher.airescuering`
- **Updated UI**: All text updated to focus on AI assistance
- **Rescue Ring Theme**: New branding emphasizing help and assistance
- **Ring Icon**: 🛟 Life ring emoji representing rescue/help

#### 🎮 Simple Interaction
- **Tap** = Open AI chat
- **Voice or text input** = Ask your question
- **Long press + drag** = Reposition ring

#### 🔒 Privacy & Security
- **No data collection**: We don't collect or store your conversations
- **Encrypted API keys**: Your Gemini API key stored with Android KeyStore
- **Direct communication**: All AI requests go directly to Google
- **Open source**: Full transparency - review the code yourself

#### 📱 New Features
- **AI Helper Activity**: Dedicated chat interface for AI conversations
- **Settings Integration**: Easy API key management
- **Voice Recognition**: Built-in speech-to-text
- **Secure Data Storage**: All sensitive data encrypted

### Technical Changes
- **Package renamed**: `ch.heuscher.back_home_dot` → `ch.heuscher.airescuering`
- **New dependencies**: OkHttp, Kotlinx Serialization, Security Crypto
- **Enhanced architecture**: AI repository layer added
- **Internet permission**: Added for Gemini API communication
- **Audio permission**: Added for voice input

### Breaking Changes
- **New package name**: Users will need to reinstall (cannot update from old version)
- **API key required**: AI features require free Google Gemini API key
- **New permissions**: Internet and microphone access required for AI features

---

## Version 2.1.0 (2025-11-08)

### Safe-Home Mode & UX Improvements

#### 🏠 Safe-Home Mode
- **Always Home**: All taps lead to home screen
- **Square Design**: Button becomes a rounded square (8dp radius)
- **Protected Dragging**: Button only movable after 500ms long-press
- **Visual Feedback**: Pulsing white halo (128dp) shows when draggable
- **Drag Anywhere**: In drag mode, button can be placed anywhere

#### 🎨 Design Improvements
- **Mode-based Design**: Circle (Standard/Navi) vs Square (Safe-Home)
- **Halo Effect**: Doubled in size (128dp) for better visibility
- **Smooth Animation**: Pulsing halo during drag mode

#### 🔧 Technical Improvements
- **Auto-Restart**: App automatically restarts after updates
- **Tablet Fix**: Button can now move across entire screen
- **Layout Optimization**: Fixed 128dp layout prevents shifting

### Bug Fixes
- Tablet restriction fixed (was limited to 62% of screen)
- Halo no longer shifts button position
- Keyboard detection returns 0 when keyboard not visible

---

## Version 2.0.0 (2025-11-05)

### Major Refactoring - Clean Architecture

#### 🏗️ Architecture Improvements
- **Component Extraction**: Specialized components from monolithic service
  - KeyboardManager (273 lines): Complete keyboard avoidance
  - PositionAnimator (86 lines): Smooth animations
  - OrientationHandler (97 lines): Rotation transformations
- **Code Reduction**: OverlayService reduced 31% (670→459 lines)
- **Clean Architecture**: Strict layer separation
- **Testability**: All components independently testable

#### 🔄 Rotation Handling - Zero Jump
- **Hide During Rotation**: Dot hidden to eliminate jumping
- **Smart Detection**: 16ms polling detects changes immediately
- **Perfect Positioning**: Reappears at correct position

#### ⌨️ Keyboard Avoidance
- **Fully Extracted**: Dedicated KeyboardManager class
- **Smart Margin**: 1.5x dot diameter from keyboard
- **Debouncing**: Prevents position flickering

---

## Version 1.1.1 (2025-11-03)

### New Features

#### 🎯 Tap Behavior Modes
- **STANDARD Mode**: 1 tap = Home, 2 taps = Back
- **BACK Mode**: 1 tap = Back, 2 taps = Switch to previous app
- **Always Available**: 3 taps = All apps, 4 taps = Open app, long press = Home

#### ⌨️ Keyboard Avoidance
- Automatic positioning when keyboard appears
- Smart detection and adjustment
- Seamless typing experience

#### 🎨 Dynamic UI
- Context-aware instructions
- Settings optimization
- Enhanced accessibility

---

## Version 1.0.0 (2025-10-27)

### Initial Release

First public release of the navigation assistant (pre-AI integration).

---

## Installation

1. Download APK from [GitHub Releases](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/releases)
2. Get free Gemini API key from [ai.google.dev](https://ai.google.dev)
3. Install APK on your device
4. Open app and follow setup instructions
5. Grant required permissions
6. Add your Gemini API key in settings

---

## Feedback & Support

- **GitHub Issues**: [Report a problem](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues)
- **Feature Requests**: [Suggest an enhancement](https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues/new)
- **Email**: s.heuscher@gmail.com

---

## Credits

- **Developed by**: Stephan Heuscher
- **AI powered by**: Google Gemini
- **With support from**: Claude (Anthropic)
- **Icons**: Material Design

---

**Note**: This app uses AI to provide assistance and may not always provide accurate information. Always verify important information independently.

Made with ❤️ for everyone who needs a helping hand
