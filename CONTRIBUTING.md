# Contributing to NanoChat

First off, thank you for considering contributing to NanoChat! It's people like you that make it a
great tool.

## 📜 Code of Conduct

By participating in this project, you agree to abide by
the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/code_of_conduct.md).

## 🛠 How Can I Contribute?

### Reporting Bugs

- Use the GitHub Issue Tracker.
- Describe the bug and include steps to reproduce it.
- Mention your device model and Android version.

### Suggesting Enhancements

- Open an issue with the "enhancement" tag.
- Explain why the feature would be useful.

### Pull Requests

1. **Fork the repository** and create your branch from `main`.
2. **Setup your environment**: Ensure you are using the latest stable Android Studio.
3. **Follow the style guide**:
    - We follow
      the [Official Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html).
    - Use Material 3 components and Material You guidelines for UI changes.
    - Ensure your code is properly formatted (use Android Studio's default formatter).
4. **Test your changes**: Run `./gradlew test` and verify on a physical device or emulator (API
   31+).
5. **Keep PRs focused**: Small, atomic PRs are easier to review.
6. **Update documentation**: If you're adding a feature or changing behavior, update the `README.md`
   or other relevant docs.

## 🏗 Development Workflow

- **Branching**: Use descriptive branch names like `feature/add-new-backend` or `fix/issue-123`.
- **Commits**: Use clear, imperative commit messages (e.g., `Add support for model downloading`).
- **Build**: Use `./gradlew assembleDebug` to ensure the project builds correctly.

## 🎨 UI Guidelines

- Use Jetpack Compose exclusively.
- Stick to Material 3 tokens. Avoid hardcoded colors; use `MaterialTheme.colorScheme`.
- Support both Light and Dark modes.
- Ensure proper accessibility with `contentDescription` and touch targets.

## 🔒 Security

If you find a security vulnerability, please do **not** open a public issue. Instead, contact the
maintainers privately.

Thank you for contributing!
