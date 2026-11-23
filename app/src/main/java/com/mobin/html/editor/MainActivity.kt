package com.mobin.html.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {
    private lateinit var editorView: WebView

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
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

        editorView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): Boolean = request.url.host != APP_ASSET_HOST
        }
        editorView.webChromeClient = WebChromeClient()
        setContentView(editorView)
        editorView.loadUrl(EDITOR_URL)
    }

    override fun onDestroy() {
        editorView.stopLoading()
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
