package com.mobin.html.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEditorAssistanceTest {
    @Test
    fun typingExistingClosingParenthesisSkipsItWithoutDuplicating() {
        val previous = TextFieldValue("()", selection = TextRange(1))
        val proposed = TextFieldValue("())", selection = TextRange(2))

        val result = assistEditorInput(previous, proposed)

        assertEquals("()", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun typingExistingClosingBraceSkipsItWithoutDuplicating() {
        val previous = TextFieldValue("{}", selection = TextRange(1))
        val proposed = TextFieldValue("{}}", selection = TextRange(2))

        val result = assistEditorInput(previous, proposed)

        assertEquals("{}", result.text)
        assertEquals(2, result.selection.start)
    }

    @Test
    fun openingParenthesisStillAddsItsPair() {
        val previous = TextFieldValue("")
        val proposed = TextFieldValue("(", selection = TextRange(1))

        val result = assistEditorInput(previous, proposed)

        assertEquals("()", result.text)
        assertEquals(1, result.selection.start)
    }

    @Test
    fun htmlAngleBracketPairProducesCompleteTag() {
        val empty = TextFieldValue("")
        val typedLessThan = TextFieldValue("<", selection = TextRange(1))

        val afterOpen = assistEditorInput(empty, typedLessThan, "index.html")

        assertEquals("<>", afterOpen.text)
        assertEquals(1, afterOpen.selection.start)

        val beforeClosing = TextFieldValue("<Html>", selection = TextRange(5))
        val typedClosing = TextFieldValue("<Html>>", selection = TextRange(6))
        val afterClose = assistEditorInput(beforeClosing, typedClosing, "index.html")

        assertEquals("<Html>", afterClose.text)
        assertEquals(6, afterClose.selection.start)
    }

    @Test
    fun angleBracketsAreNotAutoPairedInJavaScript() {
        val result = assistEditorInput(
            TextFieldValue(""),
            TextFieldValue("<", selection = TextRange(1)),
            "app.js"
        )

        assertEquals("<", result.text)
    }

    @Test
    fun cursorMetricsHandleEmptyAndSingleLineText() {
        assertEquals(
            EditorCursorMetrics(lineCount = 1, cursorLine = 1, cursorColumn = 1),
            calculateEditorCursorMetrics("", 0)
        )
        assertEquals(
            EditorCursorMetrics(lineCount = 1, cursorLine = 1, cursorColumn = 4),
            calculateEditorCursorMetrics("abc", 3)
        )
    }

    @Test
    fun cursorMetricsHandleMultipleLinesAndTrailingNewline() {
        assertEquals(
            EditorCursorMetrics(lineCount = 3, cursorLine = 2, cursorColumn = 2),
            calculateEditorCursorMetrics("abc\ndef\n", 5)
        )
        assertEquals(
            EditorCursorMetrics(lineCount = 3, cursorLine = 3, cursorColumn = 1),
            calculateEditorCursorMetrics("abc\ndef\n", 8)
        )
    }

    @Test
    fun mimeTypeFollowsFileExtension() {
        assertEquals("text/html", mimeTypeForFileName("page.html"))
        assertEquals("text/css", mimeTypeForFileName("styles.css"))
        assertEquals("application/javascript", mimeTypeForFileName("app.js"))
        assertEquals("application/json", mimeTypeForFileName("data.json"))
        assertEquals("text/plain", mimeTypeForFileName("notes.txt"))
    }
}
