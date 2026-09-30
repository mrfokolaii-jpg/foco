# Multi-Provider Free AI + Local Library — Design Spec

Date: 2026-09-22
Project: هوش پارسی (`com.aistudio.persianai.utufha`)
Status: Approved for implementation after user review of this file

## Goal

Extend the existing Jetpack Compose Android app so the user can chat with many **free / open** AI models (not only Gemini), pick a model from a catalog, and keep a **strong on-device library** (folders, pin, text export) stored in Room.

Models never run on-device. All generation goes through HTTP APIs. Keys are optional and entered by the user in Settings.

## Non-goals

- Running large local GGUF / llama.cpp models on the phone
- Paid-only providers (OpenAI, Anthropic, Together paid tiers)
- Publishing APK to Play Store in this cycle
- Changing Fokoone Iran-services tab behavior beyond navigation remaining intact

## Architecture

```
UI (Compose screens)
  ChatScreen | ModelsScreen | LibraryScreen | SettingsScreen | Image/Media/Tools (existing)
        |
        v
ViewModels
        |
        v
AiRouter  -----> GroqProvider
              -> HuggingFaceProvider
              -> OpenRouterProvider
              -> GeminiProvider (existing GeminiApiService, kept)
        |
        v
Room (conversations, messages, creations, library folders, pins, saved items)
Encrypted SharedPreferences (API keys)
```

### Units

| Unit | Responsibility | Depends on |
|------|----------------|------------|
| `AiModelCatalog` | Static list of free models with provider, type, titles in Persian | none |
| `AiProvider` interface | `chat(messages, system, modelId)` returns `ApiResult<ChatApiResponse>` | OkHttp |
| `GroqProvider` | Groq OpenAI-compatible chat completions | user Groq key |
| `HuggingFaceProvider` | HF Inference router `POST /v1/chat/completions` | user HF token |
| `OpenRouterProvider` | OpenRouter chat completions, free models only | user OpenRouter key |
| `GeminiProvider` | Wrapper around existing `GeminiApiService` | Gemini key via BuildConfig or settings |
| `AiRouter` | Picks provider from selected catalog model and forwards the call | providers + key store |
| `ApiKeyStore` | Read/write keys in EncryptedSharedPreferences; never log keys | Android context |
| `LibraryRepository` | Folders, pin, save message/creation, export text | Room DAOs |

## Providers and models (free-first)

Default chat model: Groq `llama-3.3-70b-versatile` when a Groq key exists; otherwise first configured free provider; Gemini only if its key exists.

### Groq (https://api.groq.com/openai/v1/chat/completions)

| Catalog id | API model | Persian title | Type |
|------------|-----------|---------------|------|
| groq-llama33 | llama-3.3-70b-versatile | لاما ۳.۳ هفتاد میلیارد | TEXT |
| groq-llama31-8b | llama-3.1-8b-instant | لاما ۳.۱ هشت میلیارد (فوری) | TEXT |
| groq-mixtral | mixtral-8x7b-32768 | میکس‌ترال ۸x۷ | TEXT |
| groq-gemma2 | gemma2-9b-it | Gemma ۲ نه میلیارد | TEXT |
| groq-qwen32 | qwen/qwen3-32b | Qwen ۳ سی‌ودو میلیارد | TEXT |
| groq-gptoss | openai/gpt-oss-120b | GPT-OSS ۱۲۰ میلیارد | TEXT |

If a Groq model id returns 404, the provider maps to the current Groq public list and shows a Persian error; catalog ids stay stable.

### Hugging Face Inference (https://router.huggingface.co/v1/chat/completions)

| Catalog id | API model | Persian title | Type |
|------------|-----------|---------------|------|
| hf-qwen25 | Qwen/Qwen2.5-72B-Instruct | Qwen ۲.۵ هفتاد میلیارد | TEXT |
| hf-mistral | mistralai/Mistral-7B-Instruct-v0.3 | میسترال ۷ میلیارد | TEXT |
| hf-deepseek | deepseek-ai/DeepSeek-R1-Distill-Qwen-32B | دیپ‌سیک R1 تقطیرشده | TEXT / CODE |
| hf-llama | meta-llama/Llama-3.1-8B-Instruct | لاما ۳.۱ هشت میلیارد (HF) | TEXT |

### OpenRouter free (https://openrouter.ai/api/v1/chat/completions)

Only models whose catalog flag `isFree = true`. Request header `HTTP-Referer` set to `https://hoosh-parsi.app` and `X-Title` to `Hoosh Parsi`.

| Catalog id | API model | Persian title |
|------------|-----------|---------------|
| or-gemini-flash | google/gemini-2.0-flash-exp:free | جمینای فلش آزمایشی (رایگان) |
| or-llama | meta-llama/llama-3.3-70b-instruct:free | لاما ۳.۳ اوپن‌روتر |
| or-qwen | qwen/qwen-2.5-72b-instruct:free | Qwen اوپن‌روتر |
| or-deepseek | deepseek/deepseek-r1:free | دیپ‌سیک R1 رایگان |

If an OpenRouter free id is retired, show: «این مدل رایگان دیگر در دسترس نیست. مدل دیگری انتخاب کنید.»

### Gemini (existing)

Keep `AiModelOption` FLASH / PRO / LITE and image/music/video studio on Gemini. Chat catalog also lists them with provider `GEMINI` so one picker covers all text models.

## API keys

Screen: Settings (new bottom-nav item «تنظیمات»).

Fields (password-style, show/hide toggle):

- Groq API key
- Hugging Face token
- OpenRouter key
- Gemini key override (optional; if empty, use `BuildConfig.GEMINI_API_KEY`)

Storage: `EncryptedSharedPreferences` file `ai_keys` via `androidx.security:security-crypto`. If the device has no Android Keystore, fall back to regular `SharedPreferences` with MODE_PRIVATE. Values never appear in logs, Room, or crash reports.

Gradle: add `androidx.security:security-crypto` in `app/build.gradle.kts`.

Empty key for a provider: that provider’s models are visible in the catalog but marked «نیاز به کلید». Sending a message with such a model shows a Persian snackbar and a button that opens Settings.

No demo/public unauthenticated proxy. If no keys at all, chat shows a setup card listing where to get free keys (Groq console, HF token, OpenRouter).

## Chat changes

- Model picker bottom sheet lists **catalog models grouped by provider**, not only Gemini enum.
- `ChatUiState.selectedCatalogModel: CatalogModel` replaces exclusive use of `AiModelOption` for routing. Gemini-specific thinking / Google Search toggles stay enabled only when provider is GEMINI.
- `ChatViewModel.sendMessage` calls `AiRouter.chat(...)` instead of `GeminiApiService` directly.
- Conversation entity already stores `modelId`; store catalog id string (e.g. `groq-llama33`).
- System instruction remains Persian-only as today.

## Models screen (new tab)

Route: `models`. Bottom nav between Chat and Library (or after Chat). Six items max: reuse existing six by merging History into Library and adding Models + Settings.

Final bottom nav (RTL labels):

1. گفتگو — Chat
2. مدل‌ها — Models catalog
3. استودیو تصویر — Image (existing)
4. چندرسانه‌ای — Media (existing)
5. کتابخانه — Library (replaces History)
6. تنظیمات — Settings

Iran Services (`FokooneServicesScreen`) and Smart Tools remain reachable from Settings as list rows «خانه خدمات ایران» and «ابزارها», so the bar stays six items.

Catalog UI:

- Search by Persian or English name
- Filter chips: همه / متن / کد / تصویر
- Filter chips: همه پروایدرها / Groq / HuggingFace / OpenRouter / Gemini
- Card: title, description, provider badge, «رایگان», key-status
- Tap card: set as current chat model and navigate to Chat
- Image-type rows open Image Studio with that Gemini image model (no fake local image models)

## Library (strong)

Replaces History tab. Same Room DB, version bump 1 → 2 with destructive fallback already enabled.

### New entities

`library_folders`

- id, name, createdAt, sortOrder

`library_items`

- id, folderId (nullable = Inbox)
- sourceType: `CHAT_MESSAGE` | `CONVERSATION` | `CREATION`
- sourceId
- title, previewText
- isPinned (Boolean)
- createdAt, updatedAt

Default folders created on first launch: «صندوق», «چت‌های مهم», «تصاویر», «یادداشت».

### Capabilities

- Pin / unpin any conversation, saved message, or creation (pinned section at top of list)
- Move item to folder
- Create / rename folder (delete folder moves items to Inbox; no cascade wipe of messages)
- Save current chat message to library from the message overflow (existing copy/share row)
- Save whole conversation as one library item (title = conversation title, preview = last message)
- Search title + preview
- Export: share/save UTF-8 `.txt` of a conversation (`عنوان`, timestamps, USER/ASSISTANT labels in Persian) via Android share sheet. File written to app cache then `Intent.ACTION_SEND`.
- Existing delete conversation / clear all remain

HistoryScreen logic moves into `LibraryScreen` + `LibraryViewModel`. `onSelectConversation` still opens Chat.

## Data flow (chat send)

1. User selects catalog model (Models screen or chat sheet)
2. User types message → Room insert user message
3. `AiRouter` reads keys, maps catalog id → provider + api model string
4. HTTP call with 65s timeouts (same as Gemini client)
5. On success: insert assistant message with `modelUsed` = catalog id; update conversation `updatedAt`
6. On error: insert error bubble (`isError = true`) with Persian text; do not crash

## Error handling

| Case | User-facing Persian message |
|------|-----------------------------|
| Missing key | کلید این پروایدر تنظیم نشده. از تنظیمات یک کلید رایگان وارد کنید. |
| 401 / 403 | کلید نامعتبر است یا منقضی شده. |
| 429 | سهمیه رایگان تمام شده. مدل یا پروایدر دیگری را امتحان کنید. |
| 404 model | این مدل دیگر در دسترس نیست. |
| Network | اتصال اینترنت برقرار نشد. |
| Empty body | پاسخی از مدل دریافت نشد. |

## UI language and RTL

All new copy in Persian. Layout already RTL via theme; keep `textAlign` and nav labels consistent with ChatScreen.

## Testing

- Unit: `AiRouter` maps catalog id to provider; missing key returns Error without HTTP
- Unit: Groq/OpenRouter JSON request body shape (mocked OkHttp not required if parsing helpers are pure)
- Existing Robolectric screenshot test remains; do not break `GreetingScreenshotTest` unless that screen is gone
- Manual: send one Groq message when key present; library pin + export produces a share intent

## Files to add / change (implementation map)

Add:

- `data/model/CatalogModels.kt`
- `data/remote/AiProvider.kt`
- `data/remote/GroqProvider.kt`
- `data/remote/HuggingFaceProvider.kt`
- `data/remote/OpenRouterProvider.kt`
- `data/remote/AiRouter.kt`
- `data/local/ApiKeyStore.kt`
- `data/local` folder/item entities + DAOs; bump `AppDatabase`
- `ui/screens/models/ModelsScreen.kt` + ViewModel
- `ui/screens/library/LibraryScreen.kt` + ViewModel (History becomes wrapper or is replaced)
- `ui/screens/settings/SettingsScreen.kt` + ViewModel

Change:

- `ChatViewModel` / `ChatScreen` model picker
- `MainActivity` nav
- `Entities.kt`, `Daos.kt`, `AppDatabase.kt`
- `strings.xml` app name stays «هوش پارسی»

## YAGNI

No streaming tokens in this cycle (existing Gemini path is non-stream). No multi-agent compare. No cloud sync.
