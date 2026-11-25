package com.mobin.html.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                primary = Color(0xFF8BC7FF),
                background = Color(0xFF0B1220),
                surface = Color(0xFF151F30)
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
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
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
                    contentResolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(editorText) }
                        ?: throw IllegalStateException("فایل برای نوشتن در دسترس نیست")
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
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(editorText) }
                        ?: throw IllegalStateException("فایل برای نوشتن در دسترس نیست")
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
        return contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
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
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1220)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF141F31)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fileName + if (dirty) " •" else "", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(languageName(fileName) + " • " + message, color = Color(0xFF8FA5BE), fontSize = 11.sp, maxLines = 1)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Default.FolderOpen, "بازکردن", tint = Color.White) }
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, "ذخیره", tint = Color.White) }
                IconButton(onClick = onPreview) { Icon(Icons.Default.PlayArrow, "پیش‌نمایش", tint = Color(0xFF8BC7FF)) }
                IconButton(onClick = onLegacy) { Icon(Icons.Default.MoreHoriz, "ویرایشگر کلاسیک", tint = Color.White) }
            }
            if (preview) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF1A2A40)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("پیش‌نمایش HTML", color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onPreview) { Text("بازگشت به کد") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize().background(Color.White),
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
                            web.loadDataWithBaseURL("https://preview.html-editor.local/", text, "text/html", "UTF-8", null)
                        }
                    }
                )
            } else {
                Row(Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = (1..maxOf(1, text.count { it == '\n' } + 1)).joinToString("\n"),
                        modifier = Modifier.background(Color(0xFF111B2A), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 10.dp),
                        color = Color(0xFF61758D),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(1.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxSize().padding(start = 8.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                        textStyle = TextStyle(
                            color = Color(0xFFE4EDF7),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        visualTransformation = syntaxHighlighter,
                        cursorBrush = SolidColor(Color(0xFF8BC7FF)),
                        decorationBox = { inner -> 
                            if (text.isEmpty()) Text("کد خود را اینجا بنویسید…", color = Color(0xFF61758D), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            inner()
                        }
                    )
                }
            }
        }
    }
}
