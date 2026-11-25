package com.mobin.html.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFF0B1422)
private val AppSurface = Color(0xFF121E2E)
private val EditorSurface = Color(0xFF0E1928)
private val GutterSurface = Color(0xFF152337)
private val PrimaryAccent = Color(0xFF8BC7FF)
private val MainText = Color(0xFFE5EDF7)
private val MutedText = Color(0xFF8FA5BE)

class NativeMainActivity : ComponentActivity() {
    private var editorText by mutableStateOf("")
    private var fileName by mutableStateOf("Untitled.html")
    private var fileUri by mutableStateOf<Uri?>(null)
    private var dirty by mutableStateOf(false)
    private var preview by mutableStateOf(false)
    private var message by mutableStateOf("آماده")

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readDocument(uri)
    }
    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) writeDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = PrimaryAccent,
                background = AppBackground,
                surface = AppSurface,
                surfaceVariant = GutterSurface
            )) {
                NativeEditorScreen(
                    text = editorText,
                    fileName = fileName,
                    dirty = dirty,
                    preview = preview,
                    message = message,
                    onTextChange = { editorText = it; dirty = true; message = "تغییرات ذخیره‌نشده" },
                    onOpen = { openFile.launch(arrayOf("*/*")) },
                    onSave = { saveCurrent() },
                    onSaveAs = { saveAs.launch(fileName.ifBlank { "Untitled.html" }) },
                    onPreview = { preview = !preview },
                    onLegacy = { startActivity(Intent(this, MainActivity::class.java)) }
                )
            }
        }
    }

    private fun readDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("فایل قابل خواندن نیست")
                }
                fileUri = uri
                fileName = queryName(uri) ?: "Untitled.html"
                editorText = content
                dirty = false
                preview = false
                message = "فایل باز شد"
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) { }
            } catch (e: Exception) {
                message = e.message ?: "خطا در باز کردن فایل"
            }
        }
    }

    private fun saveCurrent() {
        val target = fileUri
        if (target == null) {
            saveAs.launch(fileName.ifBlank { "Untitled.html" })
            return
        }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(editorText)
                    } ?: throw IllegalStateException("فایل برای نوشتن در دسترس نیست")
                }
                dirty = false
                message = "ذخیره شد"
            } catch (e: Exception) {
                message = e.message ?: "خطا در ذخیرهٔ فایل"
            }
        }
    }

    private fun writeDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(editorText)
                    } ?: throw IllegalStateException("فایل برای نوشتن در دسترس نیست")
                }
                fileUri = uri
                fileName = queryName(uri) ?: fileName
                dirty = false
                message = "ذخیره شد"
            } catch (e: Exception) {
                message = e.message ?: "خطا در ذخیرهٔ فایل"
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
}

@Composable
private fun NativeEditorScreen(
    text: String,
    fileName: String,
    dirty: Boolean,
    preview: Boolean,
    message: String,
    onTextChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onPreview: () -> Unit,
    onLegacy: () -> Unit
) {
    val syntaxHighlighter = remember(fileName) { NativeCodeHighlighter(fileName) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var menuExpanded by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(text) {
        if (text != fieldValue.text) {
            val caret = fieldValue.selection.start.coerceIn(0, text.length)
            fieldValue = TextFieldValue(text, selection = TextRange(caret))
        }
    }

    val lineCount = maxOf(1, fieldValue.text.count { it == '\n' } + 1)
    val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
    val textBeforeCursor = fieldValue.text.substring(0, cursor)
    val cursorLine = textBeforeCursor.count { it == '\n' } + 1
    val cursorColumn = cursor - textBeforeCursor.lastIndexOf('\n')
    val lineNumberWidth = lineCount.toString().length
    val lineNumbers = (1..lineCount).joinToString("\n") { it.toString().padStart(lineNumberWidth, ' ') }

    Surface(Modifier.fillMaxSize(), color = AppBackground) {
        Column(Modifier.fillMaxSize()) {
            // Compact header: file identity is separated from the action controls.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppSurface)
                    .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        fileName,
                        color = MainText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        languageName(fileName) + "  ·  HTML Editor",
                        color = MutedText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "باز کردن فایل", tint = MainText)
                }
                IconButton(onClick = onSave, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "ذخیره فایل", tint = MainText)
                }
                IconButton(onClick = onPreview, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "پیش‌نمایش", tint = PrimaryAccent)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌های بیشتر", tint = MainText)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ذخیره با نام") },
                            onClick = { menuExpanded = false; onSaveAs() }
                        )
                        DropdownMenuItem(
                            text = { Text("ویرایشگر کلاسیک") },
                            onClick = { menuExpanded = false; onLegacy() }
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().size(height = 1.dp).background(Color(0xFF223149)))

            if (preview) {
                Column(Modifier.fillMaxSize().background(AppBackground)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(AppSurface)
                            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "پیش‌نمایش صفحه",
                            color = MainText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPreview) { Text("بازگشت به کد") }
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(Color.White),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                        },
                        update = { web ->
                            val version = text.hashCode()
                            if (web.tag != version) {
                                web.tag = version
                                web.loadDataWithBaseURL(
                                    "https://preview.html-editor.local/",
                                    text,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(AppBackground)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .widthIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GutterSurface)
                            .verticalScroll(verticalScrollState)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            lineNumbers,
                            color = Color(0xFF8196B0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { proposed ->
                            val assisted = assistEditorInput(fieldValue, proposed)
                            fieldValue = assisted
                            if (assisted.text != text) onTextChange(assisted.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(EditorSurface)
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = MainText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 21.sp
                        ),
                        visualTransformation = syntaxHighlighter,
                        cursorBrush = SolidColor(PrimaryAccent),
                        decorationBox = { inner ->
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "کد خود را اینجا بنویسید…",
                                    color = Color(0xFF71869F),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    )
                }
            }

            // Keep editor feedback and cursor position in one quiet status strip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppSurface)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    languageName(fileName),
                    color = PrimaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ln $cursorLine, Col $cursorColumn",
                    color = MutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (dirty) Color(0xFFE7B65C) else Color(0xFF61C995), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (dirty) "ذخیره‌نشده" else message,
                    color = if (dirty) Color(0xFFE7B65C) else MutedText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 116.dp)
                )
            }
        }
    }
}
