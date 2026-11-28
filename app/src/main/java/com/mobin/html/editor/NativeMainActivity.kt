package com.mobin.html.editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EditorPalette(
    val appBackground: Color,
    val appSurface: Color,
    val editorSurface: Color,
    val gutterSurface: Color,
    val primaryAccent: Color,
    val mainText: Color,
    val mutedText: Color,
    val lineNumberText: Color,
    val divider: Color,
    val placeholder: Color,
    val unsaved: Color,
    val saved: Color
)

private val DarkPalette = EditorPalette(
    appBackground = Color(0xFF0B1422),
    appSurface = Color(0xFF121E2E),
    editorSurface = Color(0xFF0E1928),
    gutterSurface = Color(0xFF152337),
    primaryAccent = Color(0xFF8BC7FF),
    mainText = Color(0xFFE5EDF7),
    mutedText = Color(0xFF8FA5BE),
    lineNumberText = Color(0xFF8196B0),
    divider = Color(0xFF223149),
    placeholder = Color(0xFF71869F),
    unsaved = Color(0xFFE7B65C),
    saved = Color(0xFF61C995)
)

private val LightPalette = EditorPalette(
    appBackground = Color(0xFFF3F6FA),
    appSurface = Color(0xFFFFFFFF),
    editorSurface = Color(0xFFFAFCFF),
    gutterSurface = Color(0xFFE8EEF5),
    primaryAccent = Color(0xFF1769AA),
    mainText = Color(0xFF1A2635),
    mutedText = Color(0xFF65758A),
    lineNumberText = Color(0xFF718096),
    divider = Color(0xFFD8E1EB),
    placeholder = Color(0xFF8795A8),
    unsaved = Color(0xFFAD6B13),
    saved = Color(0xFF26875D)
)

private const val MAX_DOCUMENT_CHARACTERS = 512_000

class NativeMainActivity : ComponentActivity() {
    private enum class PendingAction { OPEN, EXIT }

    // Keep the live buffer outside Compose state. The text field owns its local value,
    // so typing should not recompose the entire Activity tree.
    private var editorText = ""
    private var documentRevision by mutableStateOf(0L)
    private var fileName by mutableStateOf("Untitled.html")
    private var fileUri by mutableStateOf<Uri?>(null)
    private var dirty by mutableStateOf(false)
    private var preview by mutableStateOf(false)
    private var message by mutableStateOf("آماده")
    private var showUnsavedDialog by mutableStateOf(false)
    private var saveInProgress by mutableStateOf(false)
    private var editRevision = 0L
    private var pendingAction: PendingAction? = null
    private var pendingSaveContent: String? = null
    private var pendingSaveRevision = 0L
    private var pendingSaveContinuation: (() -> Unit)? = null

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readDocument(uri)
    }

    private val saveAsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val snapshot = pendingSaveContent
        val revision = pendingSaveRevision
        val continuation = pendingSaveContinuation
        pendingSaveContent = null
        pendingSaveContinuation = null

        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            writeDocument(uri, snapshot ?: editorText, revision, continuation)
        } else {
            message = "ذخیره لغو شد"
            if (continuation != null) showUnsavedDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode = isSystemInDarkTheme()
            val palette = if (darkMode) DarkPalette else LightPalette

            window.statusBarColor = palette.appBackground.toArgb()
            window.navigationBarColor = palette.appBackground.toArgb()
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkMode
                isAppearanceLightNavigationBars = !darkMode
            }

            val scheme = if (darkMode) {
                darkColorScheme(
                    primary = palette.primaryAccent,
                    background = palette.appBackground,
                    surface = palette.appSurface,
                    surfaceVariant = palette.gutterSurface
                )
            } else {
                lightColorScheme(
                    primary = palette.primaryAccent,
                    background = palette.appBackground,
                    surface = palette.appSurface,
                    surfaceVariant = palette.gutterSurface
                )
            }

            MaterialTheme(colorScheme = scheme) {
                NativeEditorScreen(
                    text = editorText,
                    documentRevision = documentRevision,
                    fileName = fileName,
                    dirty = dirty,
                    preview = preview,
                    message = message,
                    palette = palette,
                    showUnsavedDialog = showUnsavedDialog,
                    onTextChange = {
                        editorText = it
                        editRevision += 1
                        dirty = true
                        message = "تغییرات ذخیره‌نشده"
                    },
                    onOpen = { requestPendingAction(PendingAction.OPEN) },
                    onSave = { saveCurrent() },
                    onSaveAs = { launchSaveAs() },
                    onPreview = { preview = !preview },
                    onRequestBack = { requestPendingAction(PendingAction.EXIT) },
                    onSaveAndContinue = { saveAndContinue() },
                    onDiscardAndContinue = { pendingAction?.let { runPendingAction(it) } },
                    onCancelPending = { cancelPendingAction() }
                )
            }
        }
    }

    private fun requestPendingAction(action: PendingAction) {
        if (saveInProgress) {
            message = "ذخیره در حال انجام است"
            return
        }
        if (dirty) {
            pendingAction = action
            showUnsavedDialog = true
        } else {
            runPendingAction(action)
        }
    }

    private fun runPendingAction(action: PendingAction) {
        pendingAction = null
        showUnsavedDialog = false
        when (action) {
            PendingAction.OPEN -> openFile.launch(arrayOf("*/*"))
            PendingAction.EXIT -> finish()
        }
    }

    private fun cancelPendingAction() {
        pendingAction = null
        showUnsavedDialog = false
    }

    private fun saveAndContinue() {
        if (saveInProgress) {
            message = "ذخیره در حال انجام است"
            return
        }
        val action = pendingAction ?: return
        showUnsavedDialog = false
        saveCurrent {
            if (pendingAction == action) runPendingAction(action)
        }
    }

    private fun readDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("فایل قابل خواندن نیست")
                    input.bufferedReader(Charsets.UTF_8).use { reader ->
                        val output = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            if (output.length + count > MAX_DOCUMENT_CHARACTERS) {
                                throw IllegalStateException("این فایل از سقف ۵۱۲ هزار نویسه بزرگ‌تر است؛ برای جلوگیری از کندی و مصرف حافظه باز نشد")
                            }
                            output.append(buffer, 0, count)
                        }
                        output.toString()
                    }
                }
                fileUri = uri
                fileName = queryName(uri) ?: "Untitled.html"
                editorText = content
                documentRevision += 1
                editRevision += 1
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

    private fun saveCurrent(afterSave: (() -> Unit)? = null) {
        if (saveInProgress) {
            message = "ذخیره در حال انجام است"
            return
        }
        val target = fileUri
        if (target == null) {
            launchSaveAs(afterSave)
            return
        }
        writeDocument(target, editorText, editRevision, afterSave)
    }

    private fun launchSaveAs(afterSave: (() -> Unit)? = null) {
        if (saveInProgress) {
            message = "ذخیره در حال انجام است"
            return
        }
        val suggestedName = fileName.ifBlank { "Untitled.html" }
        pendingSaveContent = editorText
        pendingSaveRevision = editRevision
        pendingSaveContinuation = afterSave
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypeForFileName(suggestedName)
            putExtra(Intent.EXTRA_TITLE, suggestedName)
        }
        saveAsLauncher.launch(intent)
    }

    private fun writeDocument(
        uri: Uri,
        contentSnapshot: String,
        revisionSnapshot: Long,
        afterSave: (() -> Unit)?
    ) {
        if (saveInProgress) {
            message = "ذخیره در حال انجام است"
            if (afterSave != null) showUnsavedDialog = true
            return
        }
        saveInProgress = true
        lifecycleScope.launch {
            var savedSuccessfully = false
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(contentSnapshot)
                    } ?: throw IllegalStateException("فایل برای نوشتن در دسترس نیست")
                }
                fileUri = uri
                fileName = queryName(uri) ?: fileName
                val changedDuringSave = editRevision != revisionSnapshot
                dirty = changedDuringSave
                message = if (changedDuringSave) {
                    "نسخه ذخیره شد؛ تغییرات جدید ذخیره‌نشده‌اند"
                } else {
                    "ذخیره شد"
                }
                savedSuccessfully = true
            } catch (e: Exception) {
                message = e.message ?: "خطا در ذخیرهٔ فایل"
                if (afterSave != null) showUnsavedDialog = true
            } finally {
                saveInProgress = false
            }

            if (savedSuccessfully && afterSave != null) {
                if (editRevision == revisionSnapshot) {
                    afterSave()
                } else {
                    showUnsavedDialog = true
                }
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
    documentRevision: Long,
    fileName: String,
    dirty: Boolean,
    preview: Boolean,
    message: String,
    palette: EditorPalette,
    showUnsavedDialog: Boolean,
    onTextChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onPreview: () -> Unit,
    onRequestBack: () -> Unit,
    onSaveAndContinue: () -> Unit,
    onDiscardAndContinue: () -> Unit,
    onCancelPending: () -> Unit
) {
    BackHandler { onRequestBack() }

    val syntaxHighlighter = remember(fileName) { NativeCodeHighlighter(fileName) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var menuExpanded by remember { mutableStateOf(false) }
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(documentRevision) {
        if (text != fieldValue.text) {
            val caret = fieldValue.selection.start.coerceIn(0, text.length)
            fieldValue = TextFieldValue(text, selection = TextRange(caret))
        }
    }

    val cursor = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
    val cursorMetrics = calculateEditorCursorMetrics(fieldValue.text, cursor)
    val lineCount = cursorMetrics.lineCount
    val cursorLine = cursorMetrics.cursorLine
    val cursorColumn = cursorMetrics.cursorColumn
    val lineNumberWidth = lineCount.toString().length
    val lineNumbers = remember(lineCount, lineNumberWidth) {
        (1..lineCount).joinToString("\n") { it.toString().padStart(lineNumberWidth, ' ') }
    }

    Surface(Modifier.fillMaxSize(), color = palette.appBackground) {
        Column(Modifier.fillMaxSize()) {
            // Compact header: file identity is separated from the action controls.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(palette.appSurface)
                    .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        fileName,
                        color = palette.mainText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        languageName(fileName) + "  ·  HTML Editor",
                        color = palette.mutedText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpen, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "باز کردن فایل", tint = palette.mainText)
                }
                IconButton(onClick = onSave, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "ذخیره فایل", tint = palette.mainText)
                }
                IconButton(onClick = onPreview, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "پیش‌نمایش", tint = palette.primaryAccent)
                }
                AppUpdateControl()
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "گزینه‌های بیشتر", tint = palette.mainText)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ذخیره با نام") },
                            onClick = { menuExpanded = false; onSaveAs() }
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

            if (preview) {
                Column(Modifier.weight(1f).fillMaxWidth().background(palette.appBackground)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.appSurface)
                            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "پیش‌نمایش صفحه",
                            color = palette.mainText,
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
                            if (web.tag != text) {
                                web.tag = text
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
                        .weight(1f)
                        .fillMaxWidth()
                        .background(palette.appBackground)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .widthIn(min = 36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.gutterSurface)
                            .verticalScroll(verticalScrollState)
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            lineNumbers,
                            color = palette.lineNumberText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { proposed ->
                            val previousText = fieldValue.text
                            val assisted = assistEditorInput(fieldValue, proposed, fileName)
                            fieldValue = assisted
                            if (assisted.text != previousText) onTextChange(assisted.text)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.editorSurface)
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        textStyle = TextStyle(
                            color = palette.mainText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 21.sp
                        ),
                        visualTransformation = syntaxHighlighter,
                        cursorBrush = SolidColor(palette.primaryAccent),
                        decorationBox = { inner ->
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "کد خود را اینجا بنویسید…",
                                    color = palette.placeholder,
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
                    .background(palette.appSurface)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    languageName(fileName),
                    color = palette.primaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ln $cursorLine, Col $cursorColumn",
                    color = palette.mutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (dirty) palette.unsaved else palette.saved, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (dirty) "ذخیره‌نشده" else message,
                    color = if (dirty) palette.unsaved else palette.mutedText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 116.dp)
                )
            }
        }
        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = onCancelPending,
                title = { Text("تغییرات ذخیره نشده‌اند") },
                text = { Text("پیش از ادامه، تغییرات فعلی را ذخیره می‌کنید؟") },
                confirmButton = {
                    TextButton(onClick = onSaveAndContinue) {
                        Text("ذخیره و ادامه")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = onDiscardAndContinue) {
                            Text("ادامه بدون ذخیره")
                        }
                        TextButton(onClick = onCancelPending) {
                            Text("انصراف")
                        }
                    }
                }
            )
        }
    }
}
