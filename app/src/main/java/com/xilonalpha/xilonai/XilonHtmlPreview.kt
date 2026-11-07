package com.xilonalpha.xilonai

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Explicit, isolated preview for self-contained HTML. Network, file and
 * content access are blocked. Generated JavaScript stays inside WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XilonHtmlPreview(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean = true

                    override fun shouldInterceptRequest(
                        view: WebView?, request: WebResourceRequest?
                    ): WebResourceResponse {
                        if (request?.url?.toString() == "https://xilon-preview.invalid/three.min.js") {
                            return WebResourceResponse(
                                "application/javascript", "UTF-8",
                                context.assets.open("three/three.min.js")
                            )
                        }
                        if (request?.url?.toString() == "https://xilon-preview.invalid/three-module-adapter.js") {
                            return WebResourceResponse(
                                "application/javascript", "UTF-8",
                                context.assets.open("three/three-module-adapter.js")
                            )
                        }
                        return WebResourceResponse(
                            "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                loadDataWithBaseURL(
                    "https://xilon-preview.invalid/",
                    XilonThreeOffline.prepare(html),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL(
                    "https://xilon-preview.invalid/", XilonThreeOffline.prepare(html), "text/html", "UTF-8", null
                )
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        }
    )
}
