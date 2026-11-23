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
                chooseFolder.launch(intent)
            }
        }
        @JavascriptInterface fun listFolder() { this@MainActivity.listFolder() }
        @JavascriptInterface fun readFile(uriText: String) {
            io.execute {
                val result = try {
                    val uri = Uri.parse(uriText)
                    val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    JSONObject().put("ok", true).put("uri", uriText).put("name", displayName(uri)).put("text", text)
                } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Unable to read file") }
                runOnUiThread { callJs("onOpenResult", result) }
            }
        }
        @JavascriptInterface fun createEntry(name: String, directory: Boolean) {
            val root = folderRoot() ?: return
            val safeName = name.trim().takeIf { it.isNotEmpty() && !it.contains("/") && !it.contains("\\") } ?: return
            val created = if (directory) root.createDirectory(safeName) else root.createFile(mimeFor(safeName), safeName)
            listFolder(JSONObject().put("message", if (created != null) "created" else "create_failed"))
        }
        @JavascriptInterface fun renameEntry(uriText: String, name: String) {
            val file = DocumentFile.fromSingleUri(this@MainActivity, Uri.parse(uriText))
            val safeName = name.trim().takeIf { it.isNotEmpty() && !it.contains("/") && !it.contains("\\") }
            val ok = safeName != null && file?.renameTo(safeName) == true
            listFolder(JSONObject().put("message", if (ok) "renamed" else "rename_failed"))
        }
        @JavascriptInterface fun deleteEntry(uriText: String) {
            val file = DocumentFile.fromSingleUri(this@MainActivity, Uri.parse(uriText))
            val ok = file?.delete() == true
            listFolder(JSONObject().put("message", if (ok) "deleted" else "delete_failed"))
        }
    }

    private fun folderRoot(): DocumentFile? {
        val value = preferences.getString("folder_uri", null) ?: return null
        return DocumentFile.fromTreeUri(this, Uri.parse(value))
    }

    private fun listFolder(extra: JSONObject = JSONObject()) {
        io.execute {
            val items = JSONArray()
            val root = folderRoot()
            root?.listFiles()?.sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })?.forEach { file ->
                items.put(JSONObject().put("name", file.name ?: "Unnamed").put("uri", file.uri.toString())
                    .put("directory", file.isDirectory).put("mime", file.type ?: ""))
            }
            val result = JSONObject().put("ok", root != null).put("folder", root?.name ?: "").put("items", items).put("extra", extra)
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
        "json" -> "application/json"
        "xml" -> "application/xml"
        "md" -> "text/markdown"
        else -> "text/plain"
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
