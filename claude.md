# Chat Overlay Implementation Notes

## Overview
This document describes the implementation of the chat overlay with the rescue ring positioned at the top middle.

## Implementation Approach

### 1. Chat Overlay Layout
- Created `overlay_chat_layout.xml` with:
  - Rescue ring positioned at top center (when minimized)
  - Full-screen chat interface (when expanded)
  - Rescue ring integrated into the top bar of the chat
  - Chat messages, input field, voice button, and send button

### 2. Inline Action Buttons
- Modified `item_chat_message.xml` to include:
  - "Approve" button (✓ Approve)
  - "Refine" button (✎ Refine)
  - Buttons are hidden by default and shown only for AI suggestions

### 3. Screenshot Functionality
**Decision: NOT IMPLEMENTED**

MediaProjection API was considered but rejected because:
- Requires explicit user permission for each screenshot
- Shows persistent notification during capture
- Requires user interaction to grant permission via system dialog
- Too cumbersome for the intended user experience

**Alternative considered:** Taking screenshots is not essential for the core functionality. The overlay can be hidden during AI command execution without requiring a screenshot.

### 4. Overlay Visibility Management
- Hide overlay when AI commands are being executed
- Show overlay when user taps the rescue ring
- Minimize to rescue ring only when user closes the chat

### 5. Key Components

#### ChatOverlayManager
- Manages the chat overlay lifecycle
- Handles showing/hiding the chat interface
- Manages the transition between minimized (ring only) and expanded (full chat) states

#### Modified OverlayService
- Uses the new chat overlay layout instead of just the rescue ring
- Coordinates with ChatOverlayManager for state management
- Handles overlay visibility during AI command execution

#### Modified ChatAdapter
- Supports inline action buttons for AI suggestions
- Handles button click events for approve/refine actions
- Manages button visibility based on message type

## Architecture

```
OverlayService
    ├── ChatOverlayManager
    │   ├── Shows/hides chat interface
    │   ├── Manages rescue ring state
    │   └── Handles user interactions
    ├── GestureDetector (existing)
    │   └── Detects taps on rescue ring
    └── ChatAdapter
        ├── Displays messages
        └── Handles action buttons
```

## User Flow

1. User sees floating rescue ring at top middle of screen
2. User taps rescue ring → Chat overlay expands to full screen
3. User interacts with AI → Messages appear with optional action buttons
4. When AI command is executed → Overlay automatically hides temporarily
5. User taps close button → Chat collapses back to rescue ring only

## Technical Notes

- No additional permissions required (MediaProjection not used)
- Maintains existing overlay permissions
- Uses existing WindowManager overlay system
- Integrates with existing GeminiApiService for AI interactions
