# HTML Editor

**Latest app version:** 0.1.3 (versionCode 4)

<p align="center">
  <img src="assets/images/icon.png" alt="HTML Editor app icon" width="180" />
</p>

A lightweight Android code editor for working with local web and text files. It combines a CodeMirror 6 editing surface with Android file access and an in-app HTML preview.

## Identity

- App name: HTML Editor
- Application ID: `com.mobin.html.editor`
- Platform: Android
- UI languages: Persian and English
- Minimum Android: 6.0 (API 23)

## Architecture

- Kotlin + AndroidX host application
- Android WebView with AndroidX WebKit `WebViewAssetLoader`
- CodeMirror 6 editor bundled locally with Vite
- Android Storage Access Framework for user-selected folders and files
- Android document APIs for creating, renaming, deleting, opening, and saving project files
- Light/dark appearance support

## Supported language modes

HTML, CSS, JavaScript, JSON, XML, and Markdown.

## Current features

- Syntax-aware code editing with line numbers and cursor position.
- Multiple open-file tabs.
- File manager for a user-selected folder.
- Create files and folders, rename entries, and delete entries.
- Open files through Android's system picker and save edited content.
- HTML preview inside the app.
- Persian and English interface text.

## Preview notes

The preview is intended for HTML files. Relative project resources such as stylesheets, scripts, and images use the selected project folder as their base path. Their behavior should be verified with real projects on an Android device; the editor's current preview implementation is still under active testing.

## Build the editor assets

```bash
cd editor
npm install
npm run build
```

This writes the bundled editor to `app/src/main/assets/editor`.

## Build the Android app

Open the project in Android Studio with JDK 17 and Android SDK 35, or run:

```bash
gradle :app:assembleDebug
```

The editor web assets must be built before the Android build.

## Automatic builds and releases

GitHub Actions builds the debug APK when relevant app, editor, Gradle, or workflow files change on `main`, and can also be started manually from the Actions tab. Successful builds are published to the repository's latest GitHub Release.

- [Build history](https://github.com/Mobinshr/HTML-Editor/actions)
- [Latest APK release](https://github.com/Mobinshr/HTML-Editor/releases/tag/latest)

## Project status

The initial editor foundation and core file-management interface are implemented. File creation, save flows, folder access, and HTML preview are being tested and refined. Some edge cases and local-resource preview behavior remain under development.
