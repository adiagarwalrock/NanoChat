# NanoChat Milestone 1 Plan

## Summary
Implement NanoChat directly in `/mnt/d/NanoChat` from the current template app, targeting a first milestone that delivers a buildable 3-tab shell with working chat for `REMOTE` and `AICORE`, persistent chat history, persistent app settings, and a stubbed Models screen. Remote responses should stream token-by-token; AICore can be buffered per response. Secrets should not be stored in plain DataStore.

## Key Changes
### App structure
- Replace the current single-screen `MainActivity` scaffold with a bottom-nav app shell: `Chat`, `Models`, `Settings`.
- Keep package `com.fcm.nanochat` everywhere; no package-rename work should be included in this milestone because the repo already uses it.
- Add app state wiring with Compose + ViewModels for the three tabs.

### Data and persistence
- Add Room entities/DAOs/database for:
    - `ChatSession`
    - `ChatMessage`
- Add preferences storage split by sensitivity:
    - DataStore for non-secret settings: `InferenceMode`, `baseUrl`, `modelName`, active session metadata if needed
    - encrypted storage for `apiKey` and `huggingFaceToken` interface placeholders
- Use destructive migration only for this milestone, with an explicit TODO to replace before any release candidate.

### Inference layer
- Add `InferenceMode` with only these active values in milestone 1:
    - `AICORE`
    - `REMOTE`
- Define a stable `InferenceClient` contract used by the repository and ViewModel:
    - availability check
    - chat/send API returning streamed chunks or final text
    - normalized error surface for unavailable backend, auth/config errors, and transient busy errors
- Implement:
    - `RemoteInferenceClient` using OkHttp against an OpenAI-compatible API, with SSE/token streaming support
    - `LocalInferenceClient` wrapping AICore, flattening recent conversation history into a single prompt
- Defer `DOWNLOADED` backend implementation entirely; do not add partial MediaPipe execution code in this milestone.

### Chat behavior
- Build `ChatRepository` to resolve the active backend from persisted settings and route requests.
- Build `ChatViewModel` with:
    - current session state
    - message list loading/saving
    - inference mode switching
    - optimistic assistant placeholder insertion
    - token patching for remote streams
    - buffered final assistant message for AICore
- History window defaults:
    - Nano: last 10 turns
    - Remote: last 20 turns
- Add explicit UI handling for:
    - missing Remote config
    - AICore unavailable / developer option disabled
    - AICore busy/quota conditions
    - in-flight send state and retry affordance

### UI scope
- `Chat` tab:
    - session list access, current conversation, composer, mode chips for `Nano` and `Remote`
    - streaming assistant bubble updates for Remote
- `Models` tab:
    - stub screen only, explaining downloaded-model support is planned later
    - optional placeholder for saved HF token setting if needed by future work, but no catalog/download actions
- `Settings` tab:
    - editable base URL, model name, API key
    - backend status summary for Remote/AICore availability

## Public Interfaces / Types
- `enum class InferenceMode { AICORE, REMOTE }` for this milestone
- `interface InferenceClient` with a single normalized chat API used by the repository; the implementation should support streamed chunk emission even if some backends emit only one final chunk
- Room schema additions for `ChatSession` and `ChatMessage`
- preferences/storage abstraction separating secret and non-secret settings so `DOWNLOADED` support can be added later without refactoring callers

## Test Plan
- Unit tests for repository/backend selection by `InferenceMode`
- Unit tests for prompt flattening/history-window rules for AICore and Remote
- Unit tests for streaming assembly: placeholder insertion, incremental token patching, final persisted assistant message
- Unit tests for settings persistence split: DataStore for non-secrets, encrypted storage for secrets
- UI tests for:
    - mode switching between Nano and Remote
    - chat send with remote streaming
    - AICore unavailable state messaging
    - Models tab stub rendering
    - Settings validation for missing API key/base URL/model
- Manual/device validation:
    - Pixel 9 with Gemini Nano enabled in Developer Options
    - Remote call against a known OpenAI-compatible endpoint
    - process restart preserves sessions and settings

## Assumptions
- The current repo is the source of truth; no external scaffold import is part of this milestone.
- Gradle/plugin versions in the repo remain the baseline unless build validation proves otherwise.
- Build verification is currently blocked in this environment by sandboxed Gradle distribution download, so the plan should include local validation in Android Studio or an environment with Gradle network access.
- MediaPipe downloaded-model management, model catalog, download progress, move/delete flows, and active local model selection are all deferred to milestone 2.
