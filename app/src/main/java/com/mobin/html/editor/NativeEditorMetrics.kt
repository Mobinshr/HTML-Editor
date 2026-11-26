package com.mobin.html.editor

internal data class EditorCursorMetrics(
    val lineCount: Int,
    val cursorLine: Int,
    val cursorColumn: Int
)

/**
 * Computes the document line count and caret position in a single pass,
 * avoiding temporary substring allocations on each cursor movement.
 */
internal fun calculateEditorCursorMetrics(text: String, cursorOffset: Int): EditorCursorMetrics {
    val cursor = cursorOffset.coerceIn(0, text.length)
    var lineCount = 1
    var cursorLine = 1
    var lineStart = 0
    var index = 0

    while (index < text.length) {
        if (text[index] == '\n') {
            lineCount++
            if (index < cursor) {
                cursorLine++
                lineStart = index + 1
            }
        }
        index++
    }

    return EditorCursorMetrics(
        lineCount = lineCount,
        cursorLine = cursorLine,
        cursorColumn = cursor - lineStart + 1
    )
}
