# HTML Editor

**Latest app version:** 1.8.0 (versionCode 18)

<p align="center">
  <img src="assets/images/icon.webp" alt="HTML Editor app icon" width="180" />
</p>

A lightweight Android code editor for local web and text files. The launcher experience and primary editing surface use Kotlin and Jetpack Compose. Android WebView is used only for HTML preview; the app has one native Compose editor.

## Identity

- App name: HTML Editor
- Application ID: `com.mobin.html.editor`
- Platform: Android
- UI languages: Persian and English
- Minimum Android: 6.0 (API 23)

## Architecture

- Kotlin + AndroidX host application
- Jetpack Compose native launcher, toolbar, document actions, and primary editing surface
- Native Compose syntax colouring for HTML, CSS, JavaScript, JSON, XML, SVG, and Markdown
- Android Storage Access Framework for opening and saving user-selected files
- Android WebView used only for HTML preview

## Native editor features

- Native code input with monospace typography and a line-number gutter.
- Line-number gutter and code area share vertical scrolling.
- Syntax colouring based on the current file extension.
- Automatic closing pairs for brackets, braces, parentheses, quotes, and backticks.
- Smart indentation after Enter, including a blank inner line before a matching closing bracket.
- Open and save documents through Android's system file picker, using a MIME type that matches the filename.
- Warn before opening another file or leaving with unsaved changes.
- Save a stable text snapshot and keep the document marked dirty if typing continues during a save.
- Skip existing closing brackets and quotes rather than inserting duplicates.
- Follow the Android system light or dark theme.
- In-app HTML preview.
- Current file name, language, and unsaved-change status.
- Large files fall back to plain text highlighting to keep editing responsive.

## Supported native highlighting

HTML, CSS, JavaScript, JSON, XML, SVG, and Markdown. Other extensions are displayed as plain text.

## Build

```bash
gradle :app:assembleDebug
```

Use JDK 17 and Android SDK 35. GitHub Actions runs native editor unit tests and assembles the Android APK.

## Automatic builds and releases

GitHub Actions builds the debug APK when relevant app, editor, Gradle, or workflow files change on `main`. Successful builds are published to the repository's `latest` GitHub Release.

- [Build history](https://github.com/Mobinshr/HTML-Editor/actions)
- [Latest APK release](https://github.com/Mobinshr/HTML-Editor/releases/tag/latest)

## Project status

The native editor now includes syntax colouring, bracket/quote pairing, smart indentation, and synchronized line-number scrolling. Native autocomplete, native multi-tab editing, and full native project-folder management remain future milestones. Unit tests cover editor pairing and save MIME mapping; real-device interaction tests are still required. The Compose editor is the only code-editing interface.
