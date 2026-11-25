# Native migration

This branch starts the Kotlin + Jetpack Compose migration without replacing the stable release.

## Native milestone 1
- The launcher, toolbar, editor surface, document opening and saving are native Android UI.
- HTML preview remains an isolated Android WebView surface because it must execute browser HTML/CSS/JavaScript.
- The previous CodeMirror screen remains available as a fallback while native editing grows toward feature parity.

## Follow-up work
- Add language-aware syntax highlighting, autocomplete, multi-tab editing and full SAF folder management.
- Keep the same application ID and stable debug certificate so preview builds can be installed as updates.
- The preview workflow uploads an APK artifact only; it does not modify the existing `latest` release.
