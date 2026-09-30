# Multi-Provider Free AI + Library Implementation Plan

> **For agentic workers:** Inline execution in this session. Tasks run in parallel where independent.

**Goal:** Chat with free Groq/HF/OpenRouter/Gemini models and keep a local library with folders, pins, and text export.

**Architecture:** `AiRouter` + provider classes over OkHttp; Room v2 for library; Settings stores keys in private SharedPreferences.

**Tech Stack:** Kotlin, Jetpack Compose, Room, OkHttp, Material3.

## Global Constraints

- All user-facing copy in Persian
- Never log API keys
- Free providers only
- Destructive Room migration already enabled
- Do not commit unless asked

### Task 1: Catalog + providers + router + keys
### Task 2: Room library entities/DAOs
### Task 3: Models / Settings / Library screens + nav
### Task 4: Wire ChatViewModel + model picker
### Task 5: Unit tests
