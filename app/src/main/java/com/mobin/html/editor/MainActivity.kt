package com.mobin.html.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var editorView: WebView
    private val io = Executors.newSingleThreadExecutor()
    private var pendingSaveText = ""
    private var pendingSaveName = "Untitled.html"
    private val preferences by lazy { getSharedPreferences("html_editor", MODE_PRIVATE) }

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/project/", ProjectPathHandler())
            .build()
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            io.execute {
                val result = try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    JSONObject().put("ok", true).put("uri", uri.toString()).put("name", displayName(uri)).put("text", text)
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Unable to open file") }
                runOnUiThread { callJs("onOpenResult", result) }
            }
        }
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            io.execute {
                val result = try {
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(pendingSaveText) }
                    JSONObject().put("ok", true).put("uri", uri.toString()).put("name", displayName(uri))
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Unable to save file") }
                runOnUiThread { callJs("onSaveResult", result) }
            }
        } else callJs("onSaveResult", JSONObject().put("ok", false).put("cancelled", true))
    }

    private val chooseFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            val flags = result.data?.flags ?: 0
            try { contentResolver.takePersistableUriPermission(uri, flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) } catch (_: Exception) {}
            preferences.edit().putString("folder_uri", uri.toString()).apply()
            listFolder()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editorView = WebView(this)
        editorView.setBackgroundColor(Color.TRANSPARENT)
        editorView.overScrollMode = View.OVER_SCROLL_NEVER
        editorView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        editorView.addJavascriptInterface(NativeBridge(), "NativeBridge")
        editorView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                request.url.host != APP_ASSET_HOST
        }
        editorView.webChromeClient = WebChromeClient()
        setContentView(editorView)
        editorView.loadUrl(EDITOR_URL)
    }

    private inner class NativeBridge {
        @JavascriptInterface fun openFile() { runOnUiThread { openDocument.launch(arrayOf("*/*")) } }
        @JavascriptInterface fun saveAs(text: String, name: String) {
            pendingSaveText = text
            pendingSaveName = name.ifBlank { "Untitled.html" }
            runOnUiThread { createDocument.launch(pendingSaveName) }
        }
        @JavascriptInterface fun saveFile(uriText: String, text: String) {
            io.execute {
                val result = try {
                    val uri = Uri.parse(uriText)
                    require(uri.scheme == "content") { "Invalid document location" }
                    contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                    JSONObject().put("ok", true).put("uri", uriText)
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Unable to save file") }
                runOnUiThread { callJs("onSaveResult", result) }
            }
        }
        @JavascriptInterface fun chooseFolder() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                this@MainActivity.chooseFolder.launch(intent)
            }
        }
        @JavascriptInterface fun listFolder(pathJson: String) { this@MainActivity.listFolder(pathJson) }
        @JavascriptInterface fun readFile(uriText: String, projectPathJson: String) {
            io.execute {
                val result = try {
                    val uri = Uri.parse(uriText)
                    val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    JSONObject().put("ok", true).put("uri", uriText).put("name", displayName(uri)).put("text", text)
                        .put("projectPath", JSONArray(projectPathJson))
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Unable to read file") }
                runOnUiThread { callJs("onOpenResult", result) }
            }
        }
        @JavascriptInterface fun createEntry(name: String, directory: Boolean, pathJson: String) {
            val parent = folderAt(pathJson) ?: return
            val safeName = name.trim().takeIf { it.isNotEmpty() && !it.contains("/") && !it.contains("\\") } ?: return
            val created = if (directory) parent.createDirectory(safeName) else parent.createFile(mimeFor(safeName), safeName)
            listFolder(pathJson, JSONObject().put("message", if (created != null) "created" else "create_failed"))
        }
        @JavascriptInterface fun renameEntry(uriText: String, name: String, pathJson: String) {
            val file = DocumentFile.fromSingleUri(this@MainActivity, Uri.parse(uriText))
            val safeName = name.trim().takeIf { it.isNotEmpty() && !it.contains("/") && !it.contains("\\") }
            val ok = safeName != null && file?.renameTo(safeName) == true
            listFolder(pathJson, JSONObject().put("message", if (ok) "renamed" else "rename_failed"))
        }
        @JavascriptInterface fun deleteEntry(uriText: String, pathJson: String) {
            val file = DocumentFile.fromSingleUri(this@MainActivity, Uri.parse(uriText))
            val ok = file?.delete() == true
            listFolder(pathJson, JSONObject().put("message", if (ok) "deleted" else "delete_failed"))
        }
    }

    private inner class ProjectPathHandler : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): android.webkit.WebResourceResponse? {
            return try {
                val segments = path.split('/').filter { it.isNotBlank() }.map { Uri.decode(it) }
                if (segments.isEmpty() || segments.any {
                        it == "." || it == ".." || it.contains('/') || it.contains('\\') || it.contains('\u0000')
                    }) return null

                var current = folderRoot() ?: return null
                for (segment in segments.dropLast(1)) {
                    current = current.findFile(segment)?.takeIf { it.isDirectory } ?: return null
                }
                val file = current.findFile(segments.last())?.takeIf { it.isFile } ?: return null
                val name = file.name ?: segments.last()
                val mime = mimeFor(name)
                val stream = contentResolver.openInputStream(file.uri) ?: return null
                val charset = if (mime.startsWith("text/") || mime.contains("javascript") ||
                    mime.contains("json") || mime.contains("xml") || mime.contains("svg")) "UTF-8" else null
                android.webkit.WebResourceResponse(mime, charset, stream)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun folderRoot(): DocumentFile? {
        val value = preferences.getString("folder_uri", null) ?: return null
        return DocumentFile.fromTreeUri(this, Uri.parse(value))
    }

    private fun folderAt(pathJson: String): DocumentFile? {
        var current = folderRoot() ?: return null
        val path = try { JSONArray(pathJson) } catch (_: Exception) { JSONArray() }
        for (index in 0 until path.length()) {
            val child = current.findFile(path.optString(index)) ?: return null
            if (!child.isDirectory) return null
            current = child
        }
        return current
    }

    private fun listFolder(pathJson: String = "[]", extra: JSONObject = JSONObject()) {
        io.execute {
            val items = JSONArray()
            val path = try { JSONArray(pathJson) } catch (_: Exception) { JSONArray() }
            val current = folderAt(pathJson)
            current?.listFiles()?.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })?.forEach { file ->
                items.put(JSONObject().put("name", file.name ?: "Unnamed").put("uri", file.uri.toString())
                    .put("directory", file.isDirectory).put("mime", file.type ?: ""))
            }
            val pathNames = JSONArray()
            for (index in 0 until path.length()) pathNames.put(path.optString(index))
            val result = JSONObject().put("ok", current != null)
                .put("folder", current?.name ?: "").put("path", pathNames)
                .put("items", items).put("extra", extra)
            runOnUiThread { callJs("onFolderListed", result) }
        }
    }

    private fun displayName(uri: Uri): String {
        var name = "Untitled.html"
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) ?: name }
        return name
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "text/javascript"
        "json", "map" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg", "jpe" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "eot" -> "application/vnd.ms-fontobject"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun callJs(method: String, payload: JSONObject) {
        val encoded = JSONObject.quote(payload.toString())
        editorView.evaluateJavascript("window.htmlEditor && window.htmlEditor." + method + "(" + encoded + ")", null)
    }

    override fun onDestroy() {
        io.shutdown()
        editorView.stopLoading()
        editorView.removeJavascriptInterface("NativeBridge")
        editorView.webChromeClient = null
        editorView.webViewClient = WebViewClientCompat()
        editorView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val APP_ASSET_HOST = "appassets.androidplatform.net"
        const val EDITOR_URL = "https://appassets.androidplatform.net/assets/editor/index.html"
    }
}
