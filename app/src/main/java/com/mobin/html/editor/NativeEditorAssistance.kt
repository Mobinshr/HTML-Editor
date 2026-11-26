package com.mobin.html.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

/**
 * Small editing helpers for the native Compose editor.
 *
 * Only a single-character insertion with a collapsed selection is assisted.
 * Paste operations, replacements, and selected-range edits stay untouched.
 */
internal fun assistEditorInput(
    previous: TextFieldValue,
    proposed: TextFieldValue,
    fileName: String = ""
): TextFieldValue {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val supportsAngleBrackets = extension == "html" || extension == "htm" ||
        extension == "xml" || extension == "svg"
    val oldText = previous.text
    val newText = proposed.text

    // Skip a closing character that is already immediately under the caret.
    if (
        previous.selection.collapsed &&
        proposed.selection.collapsed &&
        newText.length == oldText.length + 1
    ) {
        val oldCaret = previous.selection.start.coerceIn(0, oldText.length)
        val typedAtCaret = newText.getOrNull(oldCaret)
        val isSkippableClosing = typedAtCaret == ')' || typedAtCaret == ']' ||
            typedAtCaret == '}' || typedAtCaret == '"' || typedAtCaret == '\'' ||
            typedAtCaret == 96.toChar() || (supportsAngleBrackets && typedAtCaret == '>')
        if (
            isSkippableClosing &&
            newText.regionMatches(0, oldText, 0, oldCaret) &&
            newText.regionMatches(oldCaret + 1, oldText, oldCaret, oldText.length - oldCaret) &&
            newText.getOrNull(oldCaret) == oldText.getOrNull(oldCaret) &&
            proposed.selection.start == oldCaret + 1
        ) {
            return TextFieldValue(oldText, selection = TextRange(oldCaret + 1))
        }
    }

    if (!proposed.selection.collapsed) return proposed

    var prefix = 0
    while (prefix < oldText.length && prefix < newText.length && oldText[prefix] == newText[prefix]) {
        prefix++
    }

    var oldEnd = oldText.length
    var newEnd = newText.length
    while (oldEnd > prefix && newEnd > prefix && oldText[oldEnd - 1] == newText[newEnd - 1]) {
        oldEnd--
        newEnd--
    }

    val removedLength = oldEnd - prefix
    val insertedLength = newEnd - prefix
    val caret = proposed.selection.start.coerceIn(0, newText.length)
    if (removedLength != 0 || insertedLength != 1 || caret != prefix + 1) return proposed

    val typed = newText[prefix]
    val closing = when (typed) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '"' -> '"'
        '\'' -> '\''
        else -> if (typed == 96.toChar()) typed else if (supportsAngleBrackets && typed == '<') '>' else null
    }

    if (closing != null) {
        if (newText.getOrNull(caret) == closing) {
            return proposed.copy(selection = TextRange(caret + 1))
        }
        val completed = StringBuilder(newText.length + 1)
            .append(newText, 0, caret)
            .append(closing)
            .append(newText, caret, newText.length)
            .toString()
        return TextFieldValue(completed, selection = TextRange(caret))
    }

    if (typed == '\n') {
        val lineStart = newText.lastIndexOf('\n', caret - 2) + 1
        val baseIndent = newText.substring(lineStart, caret - 1)
            .takeWhile { it == ' ' || it == '\t' }
        val lastContent = newText.substring(lineStart, caret - 1).trimEnd().lastOrNull()
        val extraIndent = if (lastContent == '{' || lastContent == '[' || lastContent == '(') "    " else ""
        val innerIndent = baseIndent + extraIndent
        val nextContent = newText.substring(caret).firstOrNull { it != ' ' && it != '\t' }
        val closingAhead = nextContent == '}' || nextContent == ']' || nextContent == ')'
        val insertion = if (closingAhead) "$innerIndent\n$baseIndent" else innerIndent
        val completed = StringBuilder(newText.length + insertion.length)
            .append(newText, 0, caret)
            .append(insertion)
            .append(newText, caret, newText.length)
            .toString()
        return TextFieldValue(completed, selection = TextRange(caret + innerIndent.length))
    }

    return proposed
}

/** Returns a useful MIME type for the selected filename extension. */
internal fun mimeTypeForFileName(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs", "cjs" -> "application/javascript"
        "json" -> "application/json"
        "xml" -> "text/xml"
        "svg" -> "image/svg+xml"
        "md", "markdown" -> "text/markdown"
        "txt" -> "text/plain"
        else -> "text/plain"
    }
}
