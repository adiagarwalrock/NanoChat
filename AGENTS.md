AGENTS PLAYBOOK — NanoChat Android (Compose + Material 3 + AI backends)
For agentic coders in /mnt/d/NanoChat. Kotlin official style; Material You expressive 3 theming mandated. No Cursor rules (.cursor/, .cursorrules) or Copilot instructions (.github/copilot-instructions.md) currently present—add to this file if they appear.

Quick Context (from Plans)
- App: Android single-module app `app`, Compose-only UI, package `com.fcm.nanochat`.
- Tooling: Java/Kotlin 17, Kotlin 2.1.10, KSP 2.1.10-1.0.29, AGP 9.1.0, Compose BOM 2024.12.01, minSdk 31, target/compile 35.
- Navigation: chat-first shell. Chat is the primary surface; Models and Settings are secondary
  screens accessed via drawer/settings flows (no bottom nav).
- Backends: `InferenceMode` supports `AICORE`, `DOWNLOADED` (LiteRT / MediaPipe runtime for local
  files), and `REMOTE` (OpenAI-compatible SSE).
- Persistence: Room for sessions/messages; DataStore for non-secret settings; EncryptedSharedPreferences for secrets.
- Key files: MainActivity, NanoChatApp, ChatViewModel, SettingsViewModel, ChatRepository, InferenceClient*, PromptFormatter, StreamingMessageAssembler, AppPreferences, ui/theme.

Build & Run (best practices)
- Use Gradle wrapper from repo root. Dev build: `./gradlew assembleDebug` (POSIX) or `.\\gradlew.bat assembleDebug` (cmd/PowerShell).
- Full validation: `./gradlew build` (assemble + unit tests + lint wired by AGP).
- Lint only: `./gradlew :app:lint` or `:app:lintDebug`.
- Install on device/emulator: `./gradlew installDebug` (API 31+).
- Clean only when cache drifts: `./gradlew clean` (rare).
- Running from elsewhere: add `-p /mnt/d/NanoChat`.
- Keep Gradle JVM at 17; avoid version bumps without AGP/KSP/Compose validation.
- Do not commit `local.properties`; holds SDK/NDK paths.
- If Gradle sync fails on KSP/artifact resolution, ensure `gradlePluginPortal()` exists and `android.disablekotlinsourcesets=false` in `gradle.properties` (see PRD history).
- If enabling configuration cache, verify Compose + KSP compatibility per build; disable if diagnostics degrade.

Device & Emulator
- Target devices/emulators must be API 31+; prefer Pixel images for parity with AICore behavior.
- AICore/Gemini Nano requires enabling in Developer Options; expect `BackendUnavailable` until enabled/downloaded.
- Remote backend needs network; avoid metered connections when streaming.
- Keep animations on when validating Material You motion; disable only when debugging jank.
- Use same platform-tools across adb/Studio to avoid version mismatch.

Windows / Shell Notes
- Use `.\\gradlew.bat <task>` for cmd/PowerShell. Quote class names with double quotes.
- Example single test (class): `.\\gradlew.bat test --tests "com.fcm.nanochat.viewmodel.StreamingMessageAssemblerTest"`.
- Example single test (method): `.\\gradlew.bat test --tests "com.fcm.nanochat.inference.PromptFormatterTest.historyWindow keeps the most recent turns"`.
- Env vars: PowerShell `$Env:ANDROID_SDK_ROOT="C:\\Android\\sdk"`; cmd `set ANDROID_SDK_ROOT=C:\\Android\\sdk`.
- PATH add for adb (PowerShell): `$Env:Path += ";$Env:ANDROID_SDK_ROOT\\platform-tools"`.
- Git CRLF: `git config core.autocrlf input`; keep sources LF to avoid churn.
- Quote paths with spaces (e.g., "C:\\Program Files\\Android\\Android Studio").

Tests
- All unit tests: `./gradlew test`.
- Single unit class: `./gradlew test --tests "com.fcm.nanochat.viewmodel.StreamingMessageAssemblerTest"` (replace FQCN).
- Single unit method: `./gradlew test --tests "com.fcm.nanochat.inference.PromptFormatterTest.historyWindow keeps the most recent turns"` (quote display name).
- Instrumentation: `./gradlew connectedAndroidTest` or `connectedDebugAndroidTest`; device/emulator API ≥31.
- Single instrumentation class: `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fcm.nanochat.ExampleInstrumentedTest`.
- Keep tests deterministic: mock network/flows; no live network. Target scenarios: backend selection, history window limits, streaming assembly, settings persistence split, UI mode switching/config validation.

Lint & Formatting
- Kotlin official style (`kotlin.code.style=official`; .idea/codeStyles/Project.xml). No ktlint/spotless—use IDE/ktfmt defaults only.
- Imports: no wildcards; auto-sort; explicit Material icons.
- Line width soft 100–120; avoid trailing commas unless IDE inserts.
- XML: keep namespace ordering/indent per Project.xml; avoid reflowing attribute order without need.
- Use `@Suppress` narrowly with justification; delete dead/commented code.

Architecture Snapshot
- DI: simple `AppContainer` in `NanoChatApplication`; ViewModel factories per screen.
- Data: Room entities `ChatSessionEntity`, `ChatMessageEntity`, `InstalledModelEntity`; DAOs for
  session/message/model install; explicit
  migrations are in place (`1->2`, `2->3`, `3->4`).
- Preferences: `AppPreferences` merges DataStore (non-secret) + EncryptedSharedPreferences (secrets) into `SettingsSnapshot` Flow.
- Domain: `InferenceClient` interface with availability + streamChat; selector + formatter + assembler implement backend semantics.
- UI: Compose Material 3 screens in `ui/`; state from `ChatScreenState` and `SettingsScreenState` flows.

Data & Persistence Rules

- History window defaults: 10 turns AICORE, 20 turns DOWNLOADED/REMOTE (
  `ChatRepository.recentTurnsFor`).
- Session titles trimmed to 32 chars with ellipsis; reuse helper.
- DAO calls are suspend; never block main thread.
- Time source: `System.currentTimeMillis()` consistently.
- Room uses explicit migrations; keep migration coverage updated for every schema/version change.

File Structure Snapshot (PRD)
- `app/src/main/java/com/fcm/nanochat/` root.
- Key packages: `data/` (AppPreferences, db entities/daos/database, repositories), `inference/` (
  AICore/Downloaded/Remote clients), `models/` (allowlist, compatibility, download, registry,
  runtime), `viewmodel/`, `ui/`.
- Models tab is production-oriented for allowlisted local model download, activation, and
  diagnostics.

Inference & Networking
- Remote client: OkHttp SSE to `baseUrl/chat/completions`; headers `Authorization: Bearer <apiKey>`, `Accept: text/event-stream`; `stream=true`; build messages from history + prompt (ChatML style from PRD).
- Remote parsing: skip empty chunks; stop on `[DONE]`; wrap failures in `InferenceException.RemoteFailure` with HTTP code context; redact secrets.
- Local client: reflection guard for `com.google.ai.edge.aicore`; return `BackendUnavailable` when missing/not ready; use `PromptFormatter.flattenForAicore`; emit final-only chunk.
- Adding backends: extend `InferenceMode`, selector, storage defaults, UI chips, tests together; keep error surfaces consistent.

State & ViewModels
- Expose StateFlow; use `stateIn` with `SharingStarted.WhileSubscribed(5_000)`; avoid mutable state leaks.
- `ChatViewModel`: manages draft/notice/isSending/selectedSessionId/lastUserPrompt; cancels prior send job before streaming; uses assembler for REMOTE token patching and AICORE final-only.
- `SettingsViewModel`: reads/writes baseUrl/modelName/apiKey/huggingFaceToken; secrets saved only to encrypted store.
- UI collects via `collectAsStateWithLifecycle`; avoid raw Flow collects in composables.
- Add new settings atomically across SettingsSnapshot, DataStore keys, secure keys, ViewModels, UI.

Compose & UI (Material You Expressive 3)
- Theme: `ui/theme/Theme.kt`; dynamic color on API 31+; dark theme follows system; prefer tonal palettes and Material You expressive guidance.
- Use Material 3 components; avoid Material 2. Emphasize `primaryContainer/secondaryContainer`, surface variants for cards; avoid hard-coded colors—use tokens/dynamic palettes.
- Edge-to-edge enabled via `enableEdgeToEdge()`; keep `safeDrawingPadding()` where needed.
- Typography: use defined `Typography`; avoid ad-hoc fonts unless added centrally.
- Motion: prefer meaningful transitions (`AnimatedContent`, `Crossfade`) over micro-animations; keep system animations on when validating.
- Accessibility: add content descriptions to interactive icons; maintain contrast; respect IME/insets; stable `LazyColumn` keys.
- Layout: avoid fixed widths; test portrait/landscape; keep modifiers ordered (layout → appearance → interaction).

Error Handling & Notices
- Map exceptions to user-facing copy: Configuration, BackendUnavailable, Busy, RemoteFailure. Keep copy concise/actionable.
- On send failure, patch assistant placeholder with friendly fallback; clear notice after display.
- Redact secrets/URLs in errors and logs. Prefer `Log.d` with safe tags; remove noisy logs before release.
- Provide retry affordance (Retry last) and avoid empty bubbles.

Strings & Localization
- All user-visible strings in `res/values/strings.xml`; avoid hardcoding in composables.
- Keep wording concise, sentence case, plain English; no secrets/URLs.

Types, Naming, Imports
- Package stays `com.fcm.nanochat` (do not rename).
- Prefer `data class` for models; `sealed interface/class` for closed sets (e.g., BackendAvailability); explicit public types, inferred locals.
- Naming: PascalCase types/enums; camelCase functions/properties; UPPER_SNAKE for consts.
- Avoid `!!`; use safe calls + early returns; prefer Flow defaults over nullable state.

Coroutines & Flow
- Use `viewModelScope`; cancel long-running jobs on new sends; avoid `GlobalScope`.
- Keep Flows cold; expose `StateFlow`; avoid nesting `collect` inside `combine` unless necessary.
- Callback bridges via `callbackFlow` + `awaitClose`; close with meaningful exceptions.
- For blocking IO, switch to Dispatchers.IO explicitly; never block main.
- Tests: use `runTest` and test dispatchers for coroutine code.

Security
- Secrets (apiKey, huggingFaceToken) only in EncryptedSharedPreferences; never in DataStore or Room.
- Never log or assert secret values; avoid placing secrets in crash reports/strings.
- Use HTTPS for remote; validate baseUrl; trim trailing slashes before building requests.
- Do not commit keystores, service accounts, or `local.properties`.

Performance & UX
- Respect assembler semantics: REMOTE streams tokens; AICORE final only. Do not block UI thread.
- `LazyColumn` with stable keys; keep row composables light to minimize recomposition.
- Maintain responsiveness while streaming; avoid heavy work in composables.
- Test both orientations; consider focus order and snackbar semantics; avoid rapid toasts.
- Respect IME and insets; keep input areas above nav bars using `safeDrawingPadding()`.
- Prefer snackbar retry over toasts for actionable errors; avoid noisy vibration/haptics.

Model Downloads (future)

- `Downloaded` inference mode uses LiteRT-LM packaged `.litertlm` artifacts; keep storage/backends
  extensible.
- Model catalog lives under `models/catalog`; manager stubs handle download/move/delete; no partial impls in milestone 1.
- When enabling downloads, store HF token securely; copy rather than rename when moving across storage.

Dependency Guidance

- Versions pinned in `app/build.gradle.kts`; prefer Compose BOM. Revalidate AICore reflection +
  LiteRT-LM when upgrading.
- OkHttp 4.12.0 present; if adding Retrofit/Ktor, ensure coexistence or intentionally replace; keep callbacks off main.

Contribution & Hygiene
- Before publish: `./gradlew assembleDebug test lint` (or Windows wrapper). Avoid configuration cache unless verified.
- Keep AGENTS.md updated when backends, tabs, migrations, or theming change; target ~150 lines.
- Avoid new top-level modules unless necessary; Models tab currently stub by design per PLAN_1.
- Respect user changes; no destructive git commands. Only stage/commit when explicitly asked.
- Use small, reviewable PRs with clear what/why, focusing on user-facing impact and backend behavior.
- If adding migrations/version bumps, add tests and document here.
