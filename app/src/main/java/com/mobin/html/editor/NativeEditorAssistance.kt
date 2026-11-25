package com.mobin.html.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

/**
 * Small editing helpers for the native Compose editor.
 *
 * Only a single-character insertion with a collapsed selection is assisted.
 * Paste operations, replacements, and selected-range edits stay untouched.
 */
internal fun assistEditorInput(previous: TextFieldValue, proposed: TextFieldValue): TextFieldValue {
    if (!proposed.selection.collapsed) return proposed

    val oldText = previous.text
    val newText = proposed.text
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

    val removed = oldText.substring(prefix, oldEnd)
    val inserted = newText.substring(prefix, newEnd)
    val caret = proposed.selection.start.coerceIn(0, newText.length)
    if (removed.isNotEmpty() || inserted.length != 1 || caret != prefix + 1) return proposed

    val typed = inserted[0]
    val closingFor = mapOf('(' to ')', '[' to ']', '{' to '}', '"' to '"', '\'' to '\'', '`' to '`')

    if (typed in closingFor.keys) {
        val closing = closingFor.getValue(typed)
        if (newText.getOrNull(caret) == closing) {
            return proposed.copy(selection = TextRange(caret + 1))
        }
        val completed = newText.substring(0, caret) + closing + newText.substring(caret)
        return TextFieldValue(completed, selection = TextRange(caret))
    }

    if (typed == '\n') {
        val lineStart = newText.lastIndexOf('\n', caret - 2) + 1
        val lineBeforeBreak = newText.substring(lineStart, caret - 1)
        val baseIndent = lineBeforeBreak.takeWhile { it == ' ' || it == '\t' }
        val lastContent = lineBeforeBreak.trimEnd().lastOrNull()
        val extraIndent = if (lastContent == '{' || lastContent == '[' || lastContent == '(') "    " else ""
        val innerIndent = baseIndent + extraIndent
        val nextContent = newText.substring(caret).dropWhile { it == ' ' || it == '\t' }.firstOrNull()
        val closingAhead = nextContent == '}' || nextContent == ']' || nextContent == ')'
        val insertion = innerIndent + if (closingAhead) "\n$baseIndent" else ""
        val completed = newText.substring(0, caret) + insertion + newText.substring(caret)
        return TextFieldValue(completed, selection = TextRange(caret + innerIndent.length))
    }

    return proposed
}
