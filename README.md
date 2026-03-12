# NanoChat

NanoChat is a lightweight, modern Android chat application built with **Jetpack Compose** and *
*Material 3**. It features an extensible backend system supporting both local inference (Gemini Nano
via AICore) and remote OpenAI-compatible APIs.

## 🚀 Features

- **Material You Design**: Fully expressive Material 3 theming with dynamic color support (Android
  12+).
- **Dual Inference Modes**:
    - **AICore**: Local on-device inference using Gemini Nano (requires API 31+ and compatible
      hardware).
    - **Remote**: Stream responses from any OpenAI-compatible API endpoint.
- **Local Persistence**: Chat history and sessions stored locally using Room.
- **Secure Storage**: API keys and sensitive tokens are stored using EncryptedSharedPreferences.
- **Modern Architecture**: Built with Kotlin Coroutines, Flow, and MVVM.

## 🛠 Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Language**: Kotlin 2.1.10
- **Local Database**: Room
- **Settings**: DataStore & EncryptedSharedPreferences
- **Networking**: OkHttp with Server-Sent Events (SSE) support
- **Inference**: Google AICore (Local), OpenAI-style REST (Remote)

## 🏁 Getting Started

### Prerequisites

- Android Studio Ladybug or newer.
- Android SDK 35 (Compile/Target) and Min SDK 31.
- To use AICore (Gemini Nano), ensure it is enabled in your device's Developer Options.

### Building

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/NanoChat.git
   ```
2. Open the project in Android Studio.
3. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details (if
applicable).
