# Native editor

The application uses one native Kotlin + Jetpack Compose code editor.

## Current architecture
- Native Compose toolbar, document open/save flows, editor surface, line-number gutter, and status strip.
- Compose-based syntax colouring for HTML, CSS, JavaScript, JSON, XML, SVG, and Markdown.
- Android Storage Access Framework for user-selected documents.
- Android WebView is isolated to HTML preview; it is not used for code editing.

## Editing assistance
- Automatic closing pairs for brackets, braces, parentheses, quotes, and backticks.
- Automatic angle-bracket pairing for HTML, XML, and SVG files only.
- Typing an existing closing character skips it rather than duplicating it.
- Smart indentation after Enter, with an inner line and aligned closing line for common bracket blocks.
- Input assistance only handles single-character insertions; paste and replacement operations remain unchanged.

## Follow-up work
- Add native autocomplete and multi-tab editing.
- Consider a native project-folder browser and file operations.
- Keep the same application ID and stable debug certificate so new builds can be installed as updates.
- Keep WebView only where browser rendering is required for HTML preview.
