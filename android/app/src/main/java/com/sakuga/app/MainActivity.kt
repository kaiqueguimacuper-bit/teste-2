package com.sakuga.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader

/**
 * Sakuga para Android: abre o site publicado no GitHub Pages (sempre atualizado pelo agente)
 * e, sem internet, abre a cópia que vem dentro do APK.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var usingLocal = false

    private val remoteUrl = BuildConfig.SITE_URL
    private val remoteHost = Uri.parse(BuildConfig.SITE_URL).host ?: ""
    private val localUrl = "https://appassets.androidplatform.net/assets/site/index.html"

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        web = WebView(this).apply { setBackgroundColor(BG) }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        applyInsets()

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString SakugaApp/${BuildConfig.VERSION_NAME}"
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val host = url.host ?: ""
                if (host == remoteHost && url.path?.endsWith(".apk") != true) return false
                if (host == "appassets.androidplatform.net") return false
                openOutside(url)
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !usingLocal) loadLocal()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && !usingLocal && errorResponse.statusCode >= 400) loadLocal()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullView != null) { callback.onCustomViewHidden(); return }
                fullView = view
                fullCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                web.visibility = View.GONE
                setFullscreenUi(true)
            }

            override fun onHideCustomView() {
                fullView?.let { root.removeView(it) }
                fullView = null
                fullCallback?.onCustomViewHidden()
                fullCallback = null
                web.visibility = View.VISIBLE
                setFullscreenUi(false)
            }
        }

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else if (isOnline()) {
            web.loadUrl(remoteUrl)
        } else {
            loadLocal()
        }
    }

    private fun loadLocal() {
        usingLocal = true
        web.loadUrl(localUrl)
    }

    private fun openOutside(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // nenhum app abre este link; ignora
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (fullView == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
            } else {
                v.setPadding(0, 0, 0, 0)
            }
            insets
        }
    }

    private fun setFullscreenUi(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                c.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (on) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        root.requestApplyInsets()
    }

    @Deprecated("Usado pelo seletor de arquivos do WebView")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Botão voltar")
    override fun onBackPressed() {
        when {
            fullView != null -> web.webChromeClient?.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        root.removeAllViews()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val REQ_FILE = 4021
        private val BG = Color.parseColor("#12132A")
    }
}
