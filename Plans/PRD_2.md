# NanoChat — Project Summary & PRD

**Document type:** Collaborator handoff  
**Date:** March 10, 2026  
**Status:** Scaffold complete, Gradle sync in progress  
**Package:** `com.fcm.nanochat`

---

## 1. Project Vision

NanoChat is a personal Android chat application that gives the user a single, unified interface to
interact with three distinct AI inference backends — switchable on the fly with a single tap:

1. **Nano** — Gemini Nano on-device via Google AICore (zero network, zero cost, private)
2. **Model** — A locally downloaded open-source LLM (via MediaPipe LLM Inference, stored on device)
3. **Remote** — Any OpenAI-compatible cloud API (configurable base URL, model name, API key)

The core philosophy is: *use the smallest, fastest model that can answer the question*. Local for
speed and privacy; cloud as a fallback for complexity.

---

## 2. Why This Exists

The user has a Pixel 9 with 5.58 GB of AICore storage (Gemini Nano already on-device). Google's
AICore API (experimental, `com.google.ai.edge.aicore:aicore:0.0.1-exp01`) allows third-party apps to
call Gemini Nano directly — no API key, no network, no cost per inference.

The goal is a personal JARVIS-style app that:

- Works fully offline for everyday queries
- Downloads and runs open-source models (Qwen2.5, DeepSeek R1, Phi, Gemma) locally
- Can optionally route harder questions to a cloud model (OpenAI, Groq, Anthropic, etc.)
- Persists all chat history in a local Room database
- Lets the user manage (download, delete, move) local models from within the app

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  ChatScreen  ──  FilterChip: [Nano] [Model] [Remote]           │
│                                                                  │
│  ChatViewModel                                                   │
│    └── ChatRepository                                            │
│          └── InferenceClient (interface)                         │
│                ├── LocalInferenceClient   → AICore / Gemini Nano│
│                ├── DownloadedModelClient  → MediaPipe LLM       │
│                └── RemoteInferenceClient  → OkHttp SSE stream   │
│                                                                  │
│  Room DB  (ChatSession + ChatMessage + DownloadedModel)          │
│  DataStore (InferenceMode, API key, base URL, HF token,         │
│             activeModelId)                                       │
└─────────────────────────────────────────────────────────────────┘
```

### Inference Mode Enum

```kotlin
enum class InferenceMode { AICORE, DOWNLOADED, REMOTE }
```

Stored in DataStore, read by `ChatViewModel`, determines which `InferenceClient` impl is used.

---

## 4. Full File Structure

```
app/src/main/java/com/fcm/nanochat/
├── MainActivity.kt
│     3-tab bottom-nav: Chat / Models / Settings
│
├── data/
│   ├── AppPreferences.kt
│   │     DataStore keys: InferenceMode enum, apiKey, baseUrl,
│   │     modelName, huggingFaceToken, activeModelId
│   │
│   ├── db/
│   │   ├── Entities.kt          — ChatSession + ChatMessage Room entities
│   │   ├── Daos.kt              — SessionDao + MessageDao
│   │   └── AppDatabase.kt       — Room DB v2, includes DownloadedModel entity
│   │                              TypeConverters for Date
│   │
│   └── repository/
│       └── ChatRepository.kt    — buildClient(InferenceMode, activeModelId?)
│                                  factory; wires ViewModel to correct client
│
├── inference/
│   ├── InferenceClient.kt       — interface: chat(history, msg): Flow<String>
│   │                              isAvailable(): Boolean
│   │                              + LocalInferenceClient (AICore wrapper)
│   │                              + RemoteInferenceClient (OkHttp SSE)
│   │
│   └── DownloadedModelClient.kt — MediaPipe LlmInference wrapper
│                                  ChatML prompt format:
│                                  <|user|> ... <|assistant|>
│
├── models/
│   ├── catalog/
│   │   └── ModelCatalog.kt      — 6 curated entries:
│   │         Qwen2.5-1.5B-Instruct  (free, HuggingFace)
│   │         Qwen2.5-3B-Instruct    (free)
│   │         DeepSeek-R1-1.5B       (free)
│   │         Phi-2                  (free, Microsoft)
│   │         Gemma-3-1B-Instruct    (requires HF token)
│   │         Gemma-3-4B-Instruct    (requires HF token)
│   │
│   └── manager/
│       ├── DownloadedModel.kt   — Room entity:
│       │     catalogId, displayName, filePath, storageLocation
│       │     (INTERNAL / EXTERNAL), sizeBytes, progressPercent,
│       │     isDownloading
│       │
│       └── ModelManager.kt      — download() via OkHttp with byte-level
│                                  progress; delete(); moveTo(location)
│                                  = copy to target + delete source
│
├── viewmodel/
│   ├── ChatViewModel.kt         — inferenceMode state, setInferenceMode(),
│   │                              streaming: empty placeholder inserted
│   │                              immediately, patched token-by-token via
│   │                              updateMessageContent()
│   │
│   ├── SettingsViewModel.kt     — apiKey, baseUrl, modelName read/write
│   │
│   └── ModelManagerViewModel.kt — download jobs (per catalogId),
│                                  cancel(), delete(), moveTo(),
│                                  huggingFaceToken save
│
└── ui/screens/
    ├── ChatScreen.kt            — 3-way FilterChip mode selector
    │                              streaming chat bubbles
    │                              "Nano not available" fallback chip
    │
    ├── SessionAndSettings.kt    — Drawer: session list + new session
    │                              Settings screen: API key, base URL,
    │                              model name inputs
    │
    └── ModelManagerScreen.kt    — Model cards with:
                                   download progress bars
                                   move dialog (Internal ↔ External)
                                   delete confirm dialog
                                   HuggingFace token input (for Gemma)
```

---

## 5. Key Design Decisions

| Decision              | Choice                                     | Rationale                                    |
|-----------------------|--------------------------------------------|----------------------------------------------|
| Inference abstraction | `InferenceClient` interface                | Swappable backends without ViewModel changes |
| Streaming strategy    | Empty assistant placeholder → token patch  | Immediate UI feedback, no flicker            |
| AICore multi-turn     | Flatten history into single prompt string  | Nano doesn't natively support multi-turn     |
| History window        | Last 10 turns for Nano, last 20 for Remote | Balance context vs. token budget             |
| Move = copy + delete  | Not a filesystem `rename`                  | Cross-volume moves require full copy         |
| Room version          | v2 with `fallbackToDestructiveMigration()` | Safe for development, revisit before release |
| Model prompt format   | ChatML (`<                                 | user                                         |>`, `<|assistant|>`) | Universal across Qwen, DeepSeek, Phi |
| HF token storage      | DataStore (encrypted prefs)                | Persistent across sessions, not hardcoded    |

---

## 6. Model Catalog

| Model                         | Size (approx.) | HF Token Required | Notes                      |
|-------------------------------|----------------|-------------------|----------------------------|
| Qwen2.5-1.5B-Instruct         | ~1 GB          | No                | Fast, good for basic Q&A   |
| Qwen2.5-3B-Instruct           | ~2 GB          | No                | Better reasoning           |
| DeepSeek-R1-Distill-Qwen-1.5B | ~1 GB          | No                | Chain-of-thought reasoning |
| Phi-2                         | ~1.5 GB        | No                | Microsoft, strong for size |
| Gemma-3-1B-Instruct           | ~700 MB        | Yes               | Google, gated model        |
| Gemma-3-4B-Instruct           | ~2.5 GB        | Yes               | Google, gated model        |

All models are downloaded as `.task` files (LiteRT format) from HuggingFace and stored either in
internal app storage or external SD card, user's choice.

---

## 7. AICore / Gemini Nano Notes

- The 5.58 GB AICore storage on the Pixel 9 is Gemini Nano — already downloaded, no model download
  needed.
- Must be enabled in **Developer Options → Gemini Nano** before third-party apps can call it.
- API dependency: `com.google.ai.edge.aicore:aicore:0.0.1-exp01` (experimental, not for production
  use).
- Restrictions enforced by OS:
    - Foreground-only (background calls return `BACKGROUND_USE_BLOCKED`)
    - Per-app inference quota (too many calls → `BUSY` error → use exponential backoff)
    - Per-app daily battery quota (`PER_APP_BATTERY_USE_QUOTA_EXCEEDED`)
- Suitable for: summarization, rephrasing, smart replies, proofreading, basic Q&A.
- Not suitable for: deep reasoning, long multi-turn, complex code generation.

---

## 8. Build Configuration

### Environment

| Tool           | Version                      |
|----------------|------------------------------|
| Android Studio | Latest stable                |
| Gradle         | 9.3.1                        |
| AGP            | 9.1.0                        |
| Kotlin         | 2.1.10                       |
| KSP            | 2.1.10-1.0.29                |
| Java           | 17 (JBR from Android Studio) |
| Min SDK        | 31 (Android 12)              |
| Target SDK     | 35 (Android 15)              |
| Compile SDK    | 35                           |

### Key Dependencies

```toml
# libs.versions.toml
[versions]
kotlin = "2.1.10"
ksp = "2.1.10-1.0.29"
agp = "9.1.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// app/build.gradle.kts — plugins block
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
```

```kotlin
// Key runtime dependencies
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp01")
implementation("com.google.mediapipe:tasks-genai:0.10.22")
```

### Gradle Issues Resolved (History)

| Error                                                    | Fix                                                                                       |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------|
| KSP plugin not found (network)                           | Added `gradlePluginPortal()` to `dependencyResolutionManagement` in `settings.gradle.kts` |
| `kotlin-android` + `kotlin-compose` conflict             | Removed `kotlin-android`; `kotlin-compose` includes it                                    |
| `kotlinOptions` unresolved                               | Replaced with `kotlin { compilerOptions { jvmTarget.set(...) } }`                         |
| `kotlin.sourceSets` DSL not allowed with built-in Kotlin | Added `android.disablekotlinsourcesets=false` to `gradle.properties`                      |
| KSP version `2.1.21-1.0.32` not resolving                | Downgraded Kotlin + KSP to `2.1.10` / `2.1.10-1.0.29`                                     |

---

## 9. Current Status

| Item                                                     | Status                                                           |
|----------------------------------------------------------|------------------------------------------------------------------|
| v1 scaffold (basic chat, AICore + Remote)                | ✅ Complete — output at `/NanoChat/`                              |
| v2 scaffold (+ Model gallery, HF token, move)            | ✅ Complete — output at `/NanoChat_v2/`                           |
| Package rename (`com.adi.nanochat` → `com.fcm.nanochat`) | ⚠️ Pending — needs find-replace across all files                 |
| Gradle sync                                              | 🔄 In progress — resolving final `disablekotlinsourcesets` error |
| Gemini Nano Developer Options enable                     | ⏳ Pending physical device step                                   |
| First build & run                                        | ⏳ Pending Gradle sync success                                    |

---

## 10. Immediate Next Steps

1. **Verify Gradle sync** after adding `android.disablekotlinsourcesets=false` to
   `gradle.properties`
2. **Copy all scaffold files** from the `NanoChat_v2` output into `D:\NanoChat\app\src\main\java\`
3. **Find-replace package name** — change all occurrences of `com.adi.nanochat` → `com.fcm.nanochat`
   across all `.kt` files
4. **Enable Gemini Nano** on Pixel 9: Settings → Developer Options → Gemini Nano → Enable
5. **Run the app** on device; verify all 3 inference modes work
6. **Test model download** — download Qwen2.5-1.5B, verify progress bar, set as active, send a
   message in Model mode

---

## 11. Future Roadmap (Not Yet Built)

- Hybrid auto-routing: detect query complexity → automatically escalate from Nano to Remote
- Voice input (Android SpeechRecognizer)
- System prompt customization per session
- Export chat history to markdown/text
- Prompt templates / quick-action buttons
- Model benchmarking screen (latency + quality scores)
- Migration from `fallbackToDestructiveMigration` to proper Room migrations before any production
  release