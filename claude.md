# AI Rescue Ring - Project Context

## Project Overview
Android app providing AI-powered assistance through a floating rescue ring UI. Users can access AI help from anywhere on their device via a persistent floating button.

## Tech Stack
- **Language**: Kotlin
- **Platform**: Android
- **AI Model**: Gemini 2.5 Computer Use Preview (gemini-2.5-computer-use-preview-10-2025)
- **API**: Google Generative AI API
- **UI**: Material Design components, RecyclerView for chat

## Key Requirements

### AI Model Configuration
- **Default Model**: `gemini-2.5-computer-use-preview-10-2025`
  - Configured in: `app/src/main/java/ch/heuscher/airescuering/data/local/SecureAIHelperDataSource.kt:33`
- **Computer Use**: Enabled with proper tool definitions
- **User Approval Flow**: ALL AI-suggested actions MUST be approved by user via dialog before execution

### User Interface
- **Chat Interface**: Semi-transparent overlay over the current screen
- **Floating Ring**: Persistent accessibility service-based overlay
- **Message Display**: RecyclerView with user/assistant message differentiation
- **Voice Input**: Supported via Android's RecognizerIntent

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

## Privacy & Security
- API keys stored unencrypted for backup compatibility (documented in PRIVACY_POLICY.md)
- No telemetry or analytics
- User messages not logged externally
