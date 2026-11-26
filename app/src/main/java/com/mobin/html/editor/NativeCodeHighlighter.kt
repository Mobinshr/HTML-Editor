package com.mobin.html.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal fun languageName(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "HTML"
    "css" -> "CSS"
    "js", "mjs" -> "JavaScript"
    "json", "map" -> "JSON"
    "xml", "svg" -> "XML"
    "md", "markdown" -> "Markdown"
    else -> "Text"
}

/**
 * Lightweight syntax colouring rendered by Compose itself.
 * Very large files intentionally use plain text to keep editing responsive.
 */
internal class NativeCodeHighlighter(fileName: String) : VisualTransformation {
    private val extension = fileName.substringAfterLast('.', "").lowercase()

    private var cachedInput: AnnotatedString? = null
    private var cachedResult: TransformedText? = null

    private val tokenPattern: Regex? = when (extension) {
        "html", "htm", "xml", "svg" -> Regex(
            """<!--[\s\S]*?-->|<!DOCTYPE\b[^>]*>|</?\s*[A-Za-z][A-Za-z0-9:_-]*|[A-Za-z_:][A-Za-z0-9:._-]*(?=\s*=)|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""",
            RegexOption.IGNORE_CASE
        )
        "js", "mjs" -> Regex(
            """/\*[\s\S]*?\*/|//[^\r\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b(?:async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|if|import|in|instanceof|let|new|of|return|static|super|switch|this|throw|try|typeof|var|void|while|yield)\b|-?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?"""
        )
        "css" -> Regex(
            """/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|@[A-Za-z_-][\w-]*|#[\da-fA-F]{3,8}\b|-?(?:\d+\.?\d*|\.\d+)(?:%|px|rem|em|vh|vw|s|ms)?\b|[A-Za-z_-][A-Za-z0-9_-]*(?=\s*:)"""
        )
        "json", "map" -> Regex(
            """"(?:\\.|[^"\\])*"|-?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b"""
        )
        "md", "markdown" -> Regex(
            """(?m)^#{1,6}\s+.*$|\[[^\]]+\]\([^)]+\)|https?://\S+"""
        )
        else -> null
    }

    override fun filter(text: AnnotatedString): TransformedText {
        // Compose may ask for the same transformation more than once during layout.
        // Reuse the previous result instead of running the regex scanner again.
        if (cachedInput === text) {
            return cachedResult ?: TransformedText(text, OffsetMapping.Identity)
        }

        val source = text.text
        val pattern = tokenPattern
        val result = if (pattern == null || source.isEmpty() || source.length > MAX_HIGHLIGHT_LENGTH) {
            TransformedText(text, OffsetMapping.Identity)
        } else {
            val highlighted = buildAnnotatedString {
                append(source)
                pattern.findAll(source).forEach { match ->
                    val token = match.value
                    val color = tokenColor(token, source, match.range.last + 1) ?: return@forEach
                    addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
                }
            }
            TransformedText(highlighted, OffsetMapping.Identity)
        }

        cachedInput = text
        cachedResult = result
        return result
    }

    private fun tokenColor(token: String, source: String, endExclusive: Int): Color? {
        if (token.startsWith("<!--") || token.startsWith("/*") || token.startsWith("//")) {
            return COMMENT_COLOR
        }

        return when (extension) {
            "html", "htm", "xml", "svg" -> when {
                token.startsWith("<!") -> KEYWORD_COLOR
                token.startsWith("<") -> TAG_COLOR
                token.startsWith("\"") || token.startsWith("'") -> STRING_COLOR
                else -> ATTRIBUTE_COLOR
            }
            "js", "mjs" -> when {
                token.startsWith("\"") || token.startsWith("'") -> STRING_COLOR
                isNumber(token) -> NUMBER_COLOR
                token in JS_KEYWORDS -> KEYWORD_COLOR
                else -> null
            }
            "css" -> when {
                token.startsWith("\"") || token.startsWith("'") -> STRING_COLOR
                token.startsWith("@") -> KEYWORD_COLOR
                token.startsWith("#") -> TAG_COLOR
                isNumber(token) -> NUMBER_COLOR
                else -> ATTRIBUTE_COLOR
            }
            "json", "map" -> when {
                token.startsWith("\"") -> {
                    val next = source.substring(endExclusive).firstOrNull { !it.isWhitespace() }
                    if (next == ':') ATTRIBUTE_COLOR else STRING_COLOR
                }
                token == "true" || token == "false" || token == "null" -> KEYWORD_COLOR
                else -> NUMBER_COLOR
            }
            "md", "markdown" -> if (token.startsWith("#")) KEYWORD_COLOR else TAG_COLOR
            else -> null
        }
    }

    private fun isNumber(token: String): Boolean {
        val first = token.firstOrNull() ?: return false
        return first.isDigit() || first == '.' || (first == '-' && token.getOrNull(1)?.isDigit() == true)
    }

    private companion object {
        const val MAX_HIGHLIGHT_LENGTH = 120_000
        val COMMENT_COLOR = Color(0xFF7F91A8)
        val TAG_COLOR = Color(0xFF7DD3FC)
        val ATTRIBUTE_COLOR = Color(0xFFFFC078)
        val STRING_COLOR = Color(0xFFA7E3A1)
        val KEYWORD_COLOR = Color(0xFFC4A7FF)
        val NUMBER_COLOR = Color(0xFFF6C177)
        val JS_KEYWORDS = setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
            "for", "from", "function", "if", "import", "in", "instanceof", "let", "new",
            "of", "return", "static", "super", "switch", "this", "throw", "try", "typeof",
            "var", "void", "while", "yield"
        )
    }
}
