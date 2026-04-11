# J-AI-mes - Project Context

## Project Overview
Android app providing AI-powered personal butler assistance through a floating overlay button. Users access J-AI-mes from anywhere on their device via a persistent floating butler button. **Voice-first design** with speech as primary input/output. Formerly "AI Rescue Ring", rebranded while keeping the same Play Store listing.

## Tech Stack
- **Language**: Kotlin
- **Platform**: Android
- **AI Model**: Gemini 3.1 Flash Lite (gemini-3.1-flash-lite-preview) for conversation
- **AI Model (Actions)**: Gemini 3 Pro Preview for computer use / complex tasks
- **API**: Google Generative AI API (via Firebase proxy)
- **UI**: Material Design components, floating overlay
- **Voice**: Android TTS (Google engine, butler-tuned) and Speech Recognition

## Key Requirements

### AI Model Configuration
- **Default Model**: `gemini-3.1-flash-lite-preview`
  - Configured in: `app/src/main/java/ch/heuscher/airescuering/data/local/SecureAIHelperDataSource.kt`
- **Computer Use**: Enabled with proper tool definitions for `gemini-3-pro-preview`
- **User Approval Flow**: ALL AI-suggested actions MUST be approved by user via dialog before execution

### Voice-First Butler Mode
- **Butler Voice**: British English male (en-gb) or German male (de-de) via Google TTS
- **Butler Tuning**: Pitch 0.92, Speech rate 0.88x (measured, dignified)
- **Speaking Speed**: User-configurable 0.5x - 1.5x slider
- **Auto-Speak**: All responses spoken aloud via TTS (always on)
- **Auto-Listen**: Voice input auto-activates after greeting
- **Locale**: German for 'de' locale, British English otherwise

### Butler Personality
- Warm, professional, attentive concierge
- British English: "sir/ma'am", "Very well", "Certainly"
- German: formal "Sie", "Sehr wohl", "Selbstverständlich"  
- Time-aware greetings (Good morning / Guten Morgen)
- Context-aware: knows which app user is in
- Concise: max 2-3 sentences for spoken responses

### Proactive Monitoring (Configurable)
- **Level 0 (Passive)**: Only responds when tapped
- **Level 1 (Notifications)**: Notices notifications, offers to read
- **Level 2 (Screen)**: Periodic screen analysis for helpable situations
- **Level 3 (Full Companion)**: All above + periodic check-ins

### User Interface
- **Butler Button**: Floating overlay (96dp default, navy+gold butler icon)
- **Speech Bubble**: Minimal overlay showing J-AI-mes' words near button
- **Voice Input Bar**: Bottom of screen during active conversation
- **applicationId**: `ch.heuscher.airescuering` (kept for Play Store continuity)

### API Integration
- **Service**: `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiService.kt`
- **Models**: `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiModels.kt`
- **Tool Support**: Computer Use tool with ENVIRONMENT_BROWSER
- **Future**: Firebase proxy for API key management (zero user friction)

### Data Storage
- **Preferences**: `ai_helper_prefs` in standard SharedPreferences
- **Speaking Speed**: `KEY_SPEAKING_SPEED` (float, default 0.88)
- **Proactive Level**: `KEY_PROACTIVE_LEVEL` (int, 0-3, default 0)

## Architecture

### Key Files
- **ButlerVoiceManager.kt**: TTS voice selection, speed control, butler phrases
- **ButlerPersonality.kt**: System prompts, greetings, locale-aware persona
- **GeminiApiService.kt**: API communication layer
- **SecureAIHelperDataSource.kt**: Local data persistence
- **OverlayService.kt**: Floating butler button management
- **AIRescueRingAccessibilityService.kt**: Screen reading + gesture execution
- **ComputerUseAgent.kt**: Agentic action execution loop

### Brand Colors
- Primary (Navy): #1A1B3A
- Accent (Gold): #D4AF37
- Text: #F5F0E8 (warm white)
- Success: #2E7D4F
- Error: #C44D56

## Development Guidelines

### Git Workflow
- Commit messages should be descriptive and follow conventional commits format
- Include context in commit messages (what/why)

### Code Conventions
- **File Operations**: Always prefer `Edit` over `Write` for existing files
- **Read First**: Always use `Read` tool before editing files
- **Kotlin Style**: Follow existing code patterns in the codebase
- **Emojis**: Never use emojis unless explicitly requested by user
- **Logging**: Use Android Log.d/Log.e with appropriate tags
- **Butler Voice**: All user-facing text should sound natural when spoken aloud
- **Locale**: Always provide German (de) and English (en) versions of strings

## Important Constraints

### Play Store Continuity
- **CRITICAL**: Keep `applicationId = "ch.heuscher.airescuering"` 
- Only the user-facing name changes to "J-AI-mes"
- Existing users receive J-AI-mes as a seamless update

### Privacy
- API keys stored unencrypted for backup compatibility (transitioning to Firebase proxy)
- No telemetry or analytics
- User messages not logged externally
- NEVER publicly state the accessibility target audience
