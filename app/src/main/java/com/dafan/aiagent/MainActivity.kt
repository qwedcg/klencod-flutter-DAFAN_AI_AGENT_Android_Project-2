package com.dafan.aiagent

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
        }
        webView.addJavascriptInterface(SiteBridge(), "DafanAndroid")
        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    inner class SiteBridge {
        @JavascriptInterface
        fun inspectUrl(rawUrl: String, callbackId: Int) {
            executor.execute {
                val result = try {
                    val parsed = URL(rawUrl.trim())
                    require(parsed.protocol == "https" || parsed.protocol == "http") {
                        "الرابط يجب أن يبدأ بـ http أو https"
                    }
                    val connection = (parsed.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 12000
                        readTimeout = 15000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "DAFAN-AI-Agent/1.0")
                        setRequestProperty("Accept", "text/html,text/plain,*/*")
                    }
                    try {
                        val code = connection.responseCode
                        val stream = if (code in 200..399) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(900_000) } ?: ""
                        if (code !in 200..399) "HTTP $code\n$body" else body
                    } finally { connection.disconnect() }
                } catch (e: Exception) {
                    "تعذر جلب الموقع: ${e.message ?: e.javaClass.simpleName}"
                }
                runOnUiThread {
                    val safe = JSONObject.quote(result)
                    webView.evaluateJavascript("window.onSiteFetched($callbackId, $safe)", null)
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }
}
