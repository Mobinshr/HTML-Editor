# HTML Editor

A lightweight Android code editor focused on editing local files. It is independent from HTML Runner and does not execute HTML, CSS, or JavaScript or provide a browser preview.

## Identity

- App name: HTML Editor
- Application ID: `com.mobin.html.editor`
- Platform: Android
- UI languages: Persian and English (localization foundation to follow)
- Minimum Android: 6.0 (API 23)

## Architecture

- Kotlin + AndroidX host application
- Android WebView with AndroidX WebKit `WebViewAssetLoader`
- CodeMirror 6 editor bundled locally with Vite
- Android Storage Access Framework for user-selected files (implementation phase)
- System-following light/dark appearance

## Supported language modes

HTML, CSS, JavaScript, JSON, XML, and Markdown.

## Build the editor assets

```bash
cd editor
npm ci
npm run build
```

This writes the bundled editor to `app/src/main/assets/editor`.

## Build the Android app

Open the project in Android Studio with JDK 17 and Android SDK 35, or run:

```bash
gradle :app:assembleDebug
```

The editor web assets must be built before the Android build.

## Project status

Phase 1: Android shell, secure local WebView host, and bundled CodeMirror foundation.
File picker, save workflows, multi-tab documents, and full localization are planned for subsequent phases.
