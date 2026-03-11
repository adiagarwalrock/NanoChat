AGENTS PLAYBOOK — NanoChat Android (Compose + Material 3 + AI backends)
This file orients agentic coders working here. Kotlin official style; Material You expressive theming. No Cursor rules (.cursor/, .cursorrules) or Copilot instructions (.github/copilot-instructions.md) are present.

Quick Context
- App: Android, single app module `app`, Compose-only UI, package `com.fcm.nanochat`.
- SDK/Tooling: minSdk 31, target/compile 35, Java/Kotlin 17, Kotlin 2.1.10, Compose BOM 2024.12.01, AGP 9.1.0, KSP 2.1.10-1.0.29.
- Tabs: Chat, Models (stub), Settings; bottom nav in `NanoChatApp`.
- Backends: `InferenceMode` has `AICORE` (local Gemini Nano via reflection) and `REMOTE` (OpenAI-compatible SSE). Downloaded models deferred.
- Persistence: Room (sessions/messages), DataStore (non-secret), EncryptedSharedPreferences (secrets).
- Key files: `MainActivity`, `NanoChatApp`, `ChatViewModel`, `ChatRepository`, `InferenceClient*`, `AppPreferences`, `ui/theme`.

Build & Run
- Use repo wrapper: `./gradlew assembleDebug` for dev builds.
- Full check: `./gradlew build` (assemble + unit tests + lint tasks wired by AGP).
- Install to device/emulator: `./gradlew installDebug`; requires API ≥31.
- Lint only: `./gradlew :app:lint` or `./gradlew :app:lintDebug`.
- Clean if IDE cache drifts: `./gradlew clean` (use sparingly).
- When scripting outside repo root, add `-p /mnt/d/NanoChat` to Gradle commands.
- Keep Gradle JVM at 17; do not bump without AGP validation.
- Do not commit `local.properties`; it holds SDK paths.
- If enabling configuration cache, verify Compose + KSP compatibility per build.

Windows / PowerShell Notes
- Use `.\\gradlew.bat assembleDebug` (cmd/PowerShell) instead of `./gradlew`; same for test/lint/install.
- Single-test examples on Windows: `.\\gradlew.bat test --tests "com.fcm.nanochat.viewmodel.StreamingMessageAssemblerTest"` and `.\\gradlew.bat test --tests "com.fcm.nanochat.inference.PromptFormatterTest.historyWindow keeps the most recent turns"`.
- Quote paths with spaces (e.g., `"C:\\Program Files\\Android\\Android Studio"`); avoid backslash-escaped quotes in PowerShell scripts.
- Set env vars per shell: PowerShell `$Env:ANDROID_SDK_ROOT="C:\\Android\\sdk"`; cmd `set ANDROID_SDK_ROOT=C:\\Android\\sdk`.
- Ensure `platform-tools` (adb) is on PATH for device installs; in PowerShell: `$Env:Path += ";$Env:ANDROID_SDK_ROOT\\platform-tools"`.
- Git on Windows: enable LF normalization (`git config core.autocrlf input`) to avoid CRLF churn; keep Kotlin sources LF.

Device & Emulator
- Target devices/emulators must be API 31+; prefer Pixel emulators for consistent behavior.
- AICore/Gemini Nano requires enabling the on-device model in Developer Options on supported hardware; verify before testing AICORE mode.
- Remote backend requires network; avoid using metered connections for streaming tests.
- Keep animations enabled when validating Material You motion; disable only when debugging jank.
- Ensure adb uses the same platform-tools as Android Studio to avoid version mismatch.

Tests
- All unit tests: `./gradlew test`.
- Single unit test class: `./gradlew test --tests "com.fcm.nanochat.viewmodel.StreamingMessageAssemblerTest"` (replace FQCN).
- Single unit test method: `./gradlew test --tests "com.fcm.nanochat.inference.PromptFormatterTest.historyWindow keeps the most recent turns"` (quote display name).
- Instrumentation (device/emulator): `./gradlew connectedAndroidTest` or `connectedDebugAndroidTest`; device API ≥31.
- Single instrumentation class: `./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fcm.nanochat.ExampleInstrumentedTest`.
- Keep tests deterministic; mock network (OkHttp) and flows; avoid real network.
- Milestone test focus: backend selection, history window limits, streaming assembly behavior, settings split, UI flows for mode switching and remote config validation.

Lint & Formatting
- Kotlin style: official (`kotlin.code.style=official`; `.idea/codeStyles/Project.xml`).
- No ktlint/spotless configured; rely on IDE formatter/ktfmt defaults; do not add new formatters without alignment.
- Imports: auto-sort, avoid wildcards, prefer explicit material icons.
- XML: respect namespace ordering per Project.xml; keep attributes ordered and indented.
- Avoid trailing commas unless IDE adds; wrap params/args per Kotlin style (~100–120 col soft limit).
- Use `@Suppress` narrowly with explanation; remove dead/commented code.

Architecture Snapshot
- DI: simple `AppContainer` built in `NanoChatApplication`; ViewModel factories created per screen.
- Data: Room entities `ChatSessionEntity`, `ChatMessageEntity`; DAOs for sessions/messages; destructive migration for now (replace before release).
- Preferences: `AppPreferences` combines DataStore (non-secret) + EncryptedSharedPreferences (secrets) into `SettingsSnapshot` Flow.
- Domain: `InferenceClient` interface with `availability` + `streamChat`; `InferenceClientSelector`, `PromptFormatter`, `StreamingMessageAssembler` implement mode-specific behavior.
- UI: Compose Material 3 components in `ui/`; state from `ChatScreenState` and `SettingsScreenState` flows.

Data & Persistence Rules
- History window defaults: 10 turns for AICORE, 20 for REMOTE (`ChatRepository.recentTurnsFor`).
- Session titles trimmed to 32 chars with ellipsis; reuse helper.
- Use suspend DAO operations; never block main thread with DB calls.
- Destructive migration is temporary—add migrations alongside schema changes and tests before any release.
- Timestamps use `System.currentTimeMillis()`; keep consistent.

Inference & Networking
- Remote client: SSE-like stream via OkHttp to `baseUrl/chat/completions`; headers `Authorization: Bearer <apiKey>`, `Accept: text/event-stream`; `stream=true`; messages built from history + prompt.
- Remote parsing: ignore empty chunks; stop on `[DONE]`; wrap errors in `InferenceException.RemoteFailure` with HTTP code context.
- Local client: reflection bridge checks for `com.google.ai.edge.aicore` classes; returns `BackendUnavailable` when missing/not ready; uses `PromptFormatter.flattenForAicore` and final-only emission.
- Availability: fail fast with `BackendAvailability.Unavailable` and user-readable messages; do not attempt send when unavailable.
- Secrets must not enter logs or exceptions; redact tokens and base URLs in errors.
- When adding new backends, extend `InferenceMode`, storage defaults, selector, UI chips, and tests together.

State & ViewModels
- State management: `StateFlow` + `stateIn` with `SharingStarted.WhileSubscribed(5_000)`; avoid mutable state exposure.
- `ChatViewModel`: fields for draft, notice, isSending, selectedSessionId, lastUserPrompt; cancels prior send job before streaming; uses `StreamingMessageAssembler` to handle mode semantics (REMOTE incremental, AICORE final-only).
- `SettingsViewModel`: updates baseUrl/modelName/apiKey/huggingFaceToken via AppPreferences; saves secrets to encrypted store.
- UI collects via `collectAsStateWithLifecycle`; no direct Flow collects in composables beyond lifecycle-aware helpers.
- Add new settings by extending `SettingsSnapshot`, DataStore keys, secure keys, ViewModel fields, and UI inputs atomically.

Compose & UI (Material You Expressive)
- Base theme in `ui/theme/Theme.kt`; dynamic color enabled on API 31+; darkTheme via system. Move palette toward dynamic/tonal (replace hardcoded purple when updating).
- Use Material 3 components; avoid Material 2. Leverage `primaryContainer`/`secondaryContainer` for emphasis; surface variants for cards.
- Edge-to-edge enabled (`enableEdgeToEdge()`); keep `safeDrawingPadding()` where needed.
- Provide content descriptions for interactive icons (nav icons currently null—add on edits).
- Keep layouts responsive: avoid fixed widths beyond the session pane; test portrait/landscape.
- Prefer meaningful motion: `AnimatedContent`/`Crossfade` for tab/body transitions; avoid gratuitous micro-animations.
- Typography: use `Typography` tokens; avoid ad-hoc fonts unless added centrally.
- Avoid hard-coded colors; use theme tokens or dynamic color. Respect dark/light parity.
- Compose patterns: hoist state; keep composables stateless where possible; use `rememberSaveable` for user text; use stable `LazyColumn` keys; keep modifiers ordered (layout → appearance → interaction).

Error Handling & Notices
- Map exceptions to user-facing copy: Configuration/BackendUnavailable/Busy/RemoteFailure. Keep messages concise and actionable.
- On send failure, update assistant placeholder with friendly fallback to avoid empty bubble; clear notice once shown.
- Do not leak secrets in error text; redact tokens/URLs.
- Provide retry affordance (already present via `Retry last`).
- Logging: avoid verbose logs in commits; prefer `Log.d` with tags stripped of secrets when necessary for debugging, and remove before release builds.

Strings & Localization
- Keep user-facing strings in `res/values/strings.xml`; avoid hardcoding in composables.
- Keep copy concise and actionable; prefer sentence case and plain English.
- Avoid leaking secrets or URLs in strings.

Types, Naming, Imports
- Package remains `com.fcm.nanochat`; do not rename.
- Prefer `data class` for models, `sealed interface/class` for closed hierarchies (e.g., `BackendAvailability`).
- Explicit public types; allow inference for locals.
- Naming: PascalCase classes/enums; camelCase functions/properties; UPPER_SNAKE for consts.
- Imports: no wildcards; logical grouping (androidx, compose, kotlin, third-party, project).
- Avoid `!!`; use safe calls/early returns; handle nullability via Flow defaults rather than nullable state.

Coroutines & Flow
- Use `viewModelScope`; cancel long-running jobs on new actions (send). Avoid `GlobalScope`.
- Keep Flows cold; expose `StateFlow` to UI; avoid `collect` inside `combine` unless necessary.
- For callback bridges, use `callbackFlow` + `awaitClose` (as in Remote client); close with meaningful exceptions.
- If adding blocking IO, switch to Dispatchers.IO explicitly; never block main.
- Tests with coroutines should prefer `runTest` and test dispatchers when async paths are added.

Security
- Secrets (apiKey, huggingFaceToken) live only in `EncryptedSharedPreferences`; never store in DataStore or Room.
- Never log or assert on secret values; do not serialize them in crash reports.
- Keep network to HTTPS; validate baseUrl input; trim trailing slashes before building paths.
- Do not commit keystores, service accounts, or `local.properties`.

Performance & UX
- Remote streaming updates incrementally; AICORE emits final only—preserve assembler semantics when editing.
- Message list uses `LazyColumn` with stable keys; keep cells lightweight; avoid heavy recomposition inside items.
- Composer should remain responsive; avoid blocking send while streaming beyond current guard.
- Consider accessibility: readable contrast, focus order, snackbar semantics. Avoid rapid toasts/snackbars.
- Respect IME and insets; keep input areas above nav bars using `safeDrawingPadding()`.
- Test both orientations where layout changes (session pane width, list height).

Dependency Guidance
- Versions pinned inline in `app/build.gradle.kts`; prefer BOM-managed artifacts for Compose. When upgrading, revalidate AICore reflection and Mediapipe placeholders.
- OkHttp 4.12.0; keep callbacks on background (already). If adding Retrofit/Ktor, ensure coexistence with existing client or replace intentionally.

Contribution & Hygiene
- Run `./gradlew assembleDebug test lint` before publishing changes.
- Keep AGENTS.md updated when adding backends, tabs, migrations, or theming shifts; target ~150 lines.
- Avoid adding new top-level modules unless required; align with milestone scope (Models tab stays stub for now).
- No commits requested automatically; stage/commit only when asked by user.
- Respect existing user changes; do not revert unrelated edits. Avoid destructive git commands.
- Use small, reviewable PRs; describe what and why (focus on user-facing impact and backend behavior).
- If adding migrations/version bumps, update tests and document in AGENTS.md.
