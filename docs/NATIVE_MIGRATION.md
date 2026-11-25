# Native migration

The migration moves the primary editor experience to Kotlin + Jetpack Compose while retaining the previous CodeMirror implementation as a fallback until feature parity is reached.

## Native milestone 1
- Native launcher, toolbar, document open/save flows, editor surface, and HTML preview container.
- HTML preview remains an isolated Android WebView because it must execute browser HTML/CSS/JavaScript.
- Existing CodeMirror screen remains available from the native editor overflow menu.

## Native milestone 2
- Added native syntax colouring for HTML, CSS, JavaScript, JSON, XML, SVG, and Markdown.
- Highlighting is applied through Compose VisualTransformation, so the primary editor does not load CodeMirror or editor JavaScript.
- Files larger than 120,000 characters fall back to plain text to avoid making typing sluggish.

## Follow-up work
- Add native autocomplete and bracket/quote assistance.
- Add native multi-tab editing and file switching.
- Migrate SAF project-folder browsing and file operations.
- Keep the same application ID and stable debug certificate so preview builds can be installed as updates.
- Continue using WebView only where browser rendering is required for HTML preview.
