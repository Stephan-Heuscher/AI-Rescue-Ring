# Privacy Policy for AI Rescue Ring

**Last Updated: November 9, 2025**

## Introduction

AI Rescue Ring ("we," "our," or "the app") is committed to protecting your privacy. This Privacy Policy explains how we handle information when you use our Android application.

## Developer Information

- **App Name**: AI Rescue Ring
- **Developer**: Stephan Heuscher
- **Contact Email**: s.heuscher@gmail.com
- **GitHub**: https://github.com/Stephan-Heuscher/AI-Rescue-Ring

## Data Collection and Usage

### Information We DO NOT Collect

AI Rescue Ring itself **does not collect, store, or transmit** any personal information to our servers. The app:

- Does not collect your name, email, or contact information
- Does not track your location
- Does not record your screen or app usage
- Does not access your contacts, photos, or other personal files
- Does not monitor which apps you use or how you use them
- Does not store your AI conversations on our servers

### Information Stored Locally

The app stores the following settings **only on your device**:

- Position of the floating rescue ring
- Selected color preferences
- Transparency settings
- Activation status (on/off)
- Tap behavior mode
- Animation speed configuration
- **Google Gemini API key** (encrypted using Android KeyStore)

This data:
- Never leaves your device (except AI queries sent directly to Google)
- Is not transmitted to our servers
- Is only accessible by the app itself
- Is automatically deleted when you uninstall the app

### AI Functionality and Data Handling

#### Google Gemini API Integration

When you use the AI assistance features:

- **API Key Storage**: Your Gemini API key is stored encrypted on your device using Android KeyStore
- **AI Queries**: Your questions (text or voice) are sent **directly to Google's Gemini API**
- **No Intermediary**: We do not intercept, log, or store your AI conversations
- **Direct Communication**: All AI interactions occur between your device and Google servers
- **Voice Data**: Voice input is processed locally for speech recognition, then sent as text to Gemini

**Important**: For information about how Google handles your Gemini API requests, please review:
- [Google Gemini API Terms](https://ai.google.dev/gemini-api/terms)
- [Google Privacy Policy](https://policies.google.com/privacy)

## Permissions Explained

### 1. Display Over Other Apps (SYSTEM_ALERT_WINDOW)

**Purpose**: Allows the app to display the floating rescue ring on top of other apps.

**What We Access**: Only the ability to draw the rescue ring on your screen.

**What We DON'T Do**: We do not read, record, or access content from other apps.

### 2. Accessibility Service

**Purpose**: Enables system integration for intelligent device assistance.

**What We Access**: Minimal system functions needed for navigation gestures only.

**What We DON'T Do**:
- We do NOT read or record screen content
- We do NOT log your activities
- We do NOT access text from other apps
- We do NOT monitor which apps you use
- We do NOT transmit any accessibility data

**Important**: The accessibility service is used exclusively for executing system actions when you interact with the rescue ring. It has no ability to collect or transmit data.

### 3. Internet Access (INTERNET)

**Purpose**: Enables communication with Google Gemini API for AI functionality.

**What We Access**: Ability to send requests to Google's Gemini API.

**What We DON'T Do**:
- We do NOT send data to our own servers
- We do NOT track your usage
- We do NOT use analytics services
- We do NOT display ads

### 4. Audio Recording (RECORD_AUDIO)

**Purpose**: Enables voice input for AI queries.

**What We Access**: Microphone access for speech recognition.

**What We DON'T Do**:
- We do NOT record or store audio
- We do NOT transmit audio to our servers
- Audio is only processed locally for speech-to-text conversion

### 5. Start at Boot (RECEIVE_BOOT_COMPLETED)

**Purpose**: Allows the app to restart automatically after device reboot.

**What We Access**: Notification when device boots.

**What We DON'T Do**: No data is collected through this permission.

## Data Security

- **API Key Protection**: Your Gemini API key is encrypted using Android KeyStore, the most secure storage available on Android
- **Local Storage**: App settings are stored locally using Android's secure SharedPreferences
- **No External Transmission**: No data is transmitted to our servers
- **Open Source**: The app code is publicly available for security review
- **Direct API Calls**: All AI requests go directly to Google, ensuring data security

## Third-Party Services

### Google Gemini API

When you use AI features, your queries are sent directly to Google Gemini API. Google's privacy policies apply to this data:
- [Google Privacy Policy](https://policies.google.com/privacy)
- [Google Gemini API Terms](https://ai.google.dev/gemini-api/terms)
- [Google GDPR Compliance](https://privacy.google.com/businesses/compliance/)

### No Other Third Parties

The app does not use:
- Analytics services
- Advertising networks
- Crash reporting services
- Social media integrations

## Children's Privacy

AI Rescue Ring does not knowingly collect any personal information from children. Parents should supervise children's use of AI features and manage the API key accordingly.

## Your Rights and Choices

You have the right to:
- Disable the app at any time
- Revoke permissions in Android Settings → Apps → AI Rescue Ring → Permissions
- Disable the accessibility service in Settings → Accessibility
- Remove your API key from the app
- Uninstall the app (which automatically deletes all local data including the encrypted API key)

## GDPR Compliance (European Users)

If you are in the European Economic Area (EEA):

**Data Controller**: The user is the data controller for their own API key and AI queries.

**Legal Basis**:
- **App Settings**: Processed based on your consent and our legitimate interest in providing app functionality
- **AI Queries**: Processed by Google Gemini API under Google's privacy policies

**Your GDPR Rights**:
- Right to access your data
- Right to rectification
- Right to erasure ("right to be forgotten")
- Right to restrict processing
- Right to data portability
- Right to object to processing
- Right to withdraw consent

**Exercising Your Rights**: Since data is stored locally on your device, you can access, modify, or delete it at any time through the app settings or by uninstalling the app.

## California Privacy Rights (CCPA)

If you are a California resident: Since we do not collect, sell, or share personal information with third parties (except direct API calls to Google under your control), CCPA obligations are minimal. You maintain full control over your data.

## Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Changes will be posted on this page with an updated "Last Updated" date. Continued use of the app after changes constitutes acceptance of the updated policy.

## Data Retention

- **App Settings**: Stored locally until you uninstall the app
- **API Key**: Stored encrypted until you remove it or uninstall the app
- **AI Conversations**: Not stored by our app; see Google's data retention policies

## Contact Us

If you have questions, concerns, or requests regarding this Privacy Policy:

- **Email**: s.heuscher@gmail.com
- **GitHub Issues**: https://github.com/Stephan-Heuscher/AI-Rescue-Ring/issues

## Transparency Commitment

We believe in transparency. This app is open source, and you can review the code at:
https://github.com/Stephan-Heuscher/AI-Rescue-Ring

---

**Summary**: AI Rescue Ring itself collects no personal data. Settings and your encrypted API key are stored only on your device. AI queries are sent directly to Google Gemini API. You have full control over permissions and can disable features or uninstall at any time.
