# HTML Editor

**Latest app version:** 1.5.0 (versionCode 15)

<p align="center">
  <img src="assets/images/icon.webp" alt="HTML Editor app icon" width="180" />
</p>

A lightweight Android code editor for local web and text files. The launcher experience and primary editing surface use Kotlin and Jetpack Compose. An isolated WebView is retained for HTML preview and the optional legacy CodeMirror editor.

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
- Android WebView used for HTML preview and the optional legacy CodeMirror screen
- Legacy CodeMirror assets bundled locally with Vite; no remote editor scripts are required

## Native editor features

- Native code input with monospace typography and a line-number gutter.
- Line-number gutter and code area share vertical scrolling.
- Syntax colouring based on the current file extension.
- Automatic closing pairs for brackets, braces, parentheses, quotes, and backticks.
- Smart indentation after Enter, including a blank inner line before a matching closing bracket.
- Open and save documents through Android's system file picker.
- In-app HTML preview.
- Current file name, language, and unsaved-change status.
- Large files fall back to plain text highlighting to keep editing responsive.

## Legacy CodeMirror features

The classic editor remains available from the overflow menu while the native editor continues to gain features. It currently provides:

- CodeMirror 6 language modes and syntax highlighting.
- Multiple open-file tabs.
- Project folder browsing and file operations.
- HTML preview with project-relative resources.

The legacy screen is retained as a fallback; it is not the primary launcher experience.

## Supported native highlighting

HTML, CSS, JavaScript, JSON, XML, SVG, and Markdown. Other extensions are displayed as plain text.

## Build

```bash
cd editor
npm install
npm run build
cd ..
gradle :app:assembleDebug
```

Use JDK 17 and Android SDK 35. GitHub Actions builds the editor assets before assembling the Android APK.

## Automatic builds and releases

GitHub Actions builds the debug APK when relevant app, editor, Gradle, or workflow files change on `main`. Successful builds are published to the repository's `latest` GitHub Release.

- [Build history](https://github.com/Mobinshr/HTML-Editor/actions)
- [Latest APK release](https://github.com/Mobinshr/HTML-Editor/releases/tag/latest)

## Project status

The native editor now includes syntax colouring, bracket/quote pairing, smart indentation, and synchronized line-number scrolling. Native autocomplete, native multi-tab editing, and full native project-folder management remain future milestones. The classic CodeMirror editor is preserved as a fallback while those capabilities are migrated.
