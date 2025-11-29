# AI Rescue Ring - Project Context

## Project Overview
Android app providing AI-powered assistance through a floating rescue ring UI. Users can access AI help from anywhere on their device via a persistent floating button. **Voice-first design optimized for elderly users**.

## Tech Stack
- **Language**: Kotlin
- **Platform**: Android
- **AI Model**: Gemini 3 Pro Preview (gemini-3-pro-preview)
- **API**: Google Generative AI API
- **UI**: Material Design components, RecyclerView for chat
- **Voice**: Android TTS and Speech Recognition for voice-first interaction

## Key Requirements

### AI Model Configuration
- **Default Model**: `gemini-3-pro-preview`
  - Configured in: `app/src/main/java/ch/heuscher/airescuering/data/local/SecureAIHelperDataSource.kt:33`
- **Computer Use**: Enabled with proper tool definitions
- **User Approval Flow**: ALL AI-suggested actions MUST be approved by user via dialog before execution

### Voice-First Mode (Elderly-Friendly)
- **Voice-First Mode**: Auto-start voice input after greeting (default: ON)
- **Auto-Speak Responses**: Read all AI responses aloud via TTS (default: ON)
- **Ring Size Presets**: Small (48dp), Medium (64dp), Large (96dp), Extra Large (128dp)
- **PiP Overlay Mode**: Floating step-by-step instructions as backup display mode
- **Voice Limitations**: Voice input via overlay not available (Android RecognizerIntent restriction). Users can enable voice through accessibility service settings.

### User Interface
- **Chat Interface**: Semi-transparent overlay over the current screen
- **Floating Ring**: Persistent accessibility service-based overlay (configurable size)
- **Message Display**: RecyclerView with user/assistant message differentiation
- **Voice Input**: Supported via Android's RecognizerIntent
- **TTS Output**: All responses spoken aloud in voice-first mode

### API Integration
- **Service**: `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiService.kt`
- **Models**: `app/src/main/java/ch/heuscher/airescuering/data/api/GeminiApiModels.kt`
- **Tool Support**: Computer Use tool with ENVIRONMENT_BROWSER
- **Response Types**: Text responses OR function calls (requires user approval)

### Data Storage
- **API Key**: Stored unencrypted in SharedPreferences for cloud backup support
- **Preferences**: `ai_helper_prefs` in standard SharedPreferences
- **Configuration**: AIHelperConfig domain model

## Architecture

### Key Files
- **AIHelperActivity.kt**: Main chat interface with approval dialogs
- **GeminiApiService.kt**: API communication layer
- **GeminiApiModels.kt**: Request/response data models with Computer Use support
- **SecureAIHelperDataSource.kt**: Local data persistence
- **AIHelperConfig.kt**: Domain model for configuration

### Function Call Flow
1. User sends message
2. API returns `GeminiContentResult` (text OR functionCall)
3. If functionCall → show approval dialog (`showActionApprovalDialog`)
4. User approves/denies → decision recorded in chat
5. (Action execution not yet implemented)

## Development Guidelines

### Git Workflow
- Commit messages should be descriptive and follow conventional commits format
- Include context in commit messages (what/why)
- Test changes before committing when possible
- Branch naming: Claude automatically creates branches with session IDs

### Code Conventions
- **File Operations**: Always prefer `Edit` over `Write` for existing files
- **Read First**: Always use `Read` tool before editing files
- **Kotlin Style**: Follow existing code patterns in the codebase
- **Emojis**: Never use emojis unless explicitly requested by user
- **Logging**: Use Android Log.d/Log.e with appropriate tags

### Model Response Handling
When handling Gemini API responses:
```kotlin
result.onSuccess { response ->
    when {
        response.hasText -> {
            // Display text in chat
        }
        response.hasFunctionCall -> {
            // Show approval dialog
            showActionApprovalDialog(response.functionCall!!)
        }
    }
}
```

## Important Constraints

### Computer Use Tool
- Tool must be included in API requests when using computer-use model
- Format: `Tool(computerUse = ComputerUse(environment = "ENVIRONMENT_BROWSER"))`
- Model expects tool definitions or will return error

### User Approval
- **Critical**: NO action execution without explicit user approval
- Approval dialog shows: action name, parameters, approve/deny buttons
- User decisions recorded in chat history
- Currently shows toast notification (execution not implemented)

## Common Tasks

### Changing AI Model
Edit `DEFAULT_MODEL` in `SecureAIHelperDataSource.kt:33`

### Modifying System Prompt
Edit `systemPrompt` in `GeminiApiService.kt:157-176` (generateAssistanceSuggestion method)

### Adding New Tool Definitions
Add to `GeminiApiModels.kt` following the pattern of `Tool` and `ComputerUse` classes

### Updating API Request Structure
Modify `GeminiRequest` in `GeminiApiModels.kt` and corresponding serialization in `GeminiApiService.kt`

## Testing Notes
- Gradle builds may fail in sandboxed environments (network restrictions)
- Focus on code correctness and architecture
- Test compilation when possible, but network errors are expected

## Recent Changes (Session: November 29, 2025)

### Issues Fixed
1. **Microphone Accessibility**: Voice button on overlay shows informative toast (Android doesn't allow RecognizerIntent from overlay apps). Users can use accessibility service instead.
2. **Step Parsing**: Improved step parsing in ChatOverlayManager to use regex `findAll` instead of `split` - now correctly handles LLM responses with intro text before steps, preventing "success after one step" issue.
3. **Settings Layout Consistency**: Fixed settings activity to have fixed orange header outside ScrollView (matching main activity and legal notice).
4. **Step Hint Timing**: Fixed off-by-one issue - now shows confirmation for current step instead of announcing next step too early.
5. **PiP Completion Flow**: Window now stays open with congratulations message when all steps completed, instead of immediately closing. User must tap X button to close.
6. **UI Consistency**: All three main activities (main, settings, legal notice) now have consistent fixed orange headers.
7. **Accessibility Text**: Shortened "allow_navigation_message" dialog from very long explanation to concise, elderly-friendly version.

### Key Code Changes
- **ChatOverlayManager.kt**: Improved step parsing with regex findAll to extract steps from any position in response. Voice button shows toast instead of trying RecognizerIntent.
- **StepPipManager.kt**: Completion button now shows congratulations and disables further navigation instead of closing.
- **activity_settings.xml**: Restructured to have fixed orange header outside ScrollView (matches activity_main.xml and activity_impressum.xml).
- **activity_impressum.xml**: Already had fixed orange header outside ScrollView.
- **strings.xml**: Shortened `allow_navigation_message` from ~15 lines to 4 lines.


- API keys stored unencrypted for backup compatibility (documented in PRIVACY_POLICY.md)
- No telemetry or analytics
- User messages not logged externally
