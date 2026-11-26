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
    fun mimeTypeFollowsFileExtension() {
        assertEquals("text/html", mimeTypeForFileName("page.html"))
        assertEquals("text/css", mimeTypeForFileName("styles.css"))
        assertEquals("application/javascript", mimeTypeForFileName("app.js"))
        assertEquals("application/json", mimeTypeForFileName("data.json"))
        assertEquals("text/plain", mimeTypeForFileName("notes.txt"))
    }
}
