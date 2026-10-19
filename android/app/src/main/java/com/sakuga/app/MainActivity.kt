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
import android.webkit.CookieManager
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sakuga para Android. A cada abertura (e a cada volta ao app) baixa do GitHub a versão mais nova
 * do app e do catálogo que o agente publica de 12 em 12 horas, e guarda no aparelho.
 * Assim tudo se atualiza sozinho, sem instalar outro APK. Sem internet, abre a última cópia guardada
 * (ou a que vem dentro do APK).
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
    private val bundledUrl = "https://appassets.androidplatform.net/assets/site/index.html"
    private val updatedUrl = "https://appassets.androidplatform.net/novo/index.html"
    private val siteDir by lazy { File(filesDir, "site").apply { mkdirs() } }
    private var lastCheck = 0L

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/novo/", WebViewAssetLoader.InternalStoragePathHandler(this, siteDir))
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

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
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
                if (Platforms.isFree(host)) { WatchActivity.open(this@MainActivity, url); return true }
                openOutside(url)
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && request.url.path?.startsWith("/novo/") == true) view.loadUrl(bundledUrl)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400 && request.url.path?.startsWith("/novo/") == true) view.loadUrl(bundledUrl)
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
        } else {
            loadLocal()
        }
        checkUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (System.currentTimeMillis() - lastCheck > 30 * 60 * 1000L) checkUpdates()
    }

    private fun hasUpdated(): Boolean = File(siteDir, "index.html").length() > 20_000 && File(siteDir, "catalog.json").length() > 20_000

    private fun loadLocal() {
        usingLocal = true
        web.loadUrl(if (hasUpdated()) updatedUrl else bundledUrl)
    }

    /** Baixa index.html e catalog.json novos do GitHub (em segundo plano) e troca os guardados se mudaram. */
    private fun checkUpdates() {
        lastCheck = System.currentTimeMillis()
        if (!isOnline()) return
        Thread {
            try {
                val stamp = System.currentTimeMillis() / 300_000
                val html = download("$RAW/index.html?t=$stamp")
                val cat = download("$RAW/catalog.json?t=$stamp")
                if (html == null || cat == null) return@Thread
                val htmlOk = html.size > 20_000 && String(html, 0, 2000, Charsets.UTF_8).contains("<html", ignoreCase = true)
                val catOk = cat.size > 20_000 && cat[0] == '{'.code.toByte() && String(cat, Charsets.UTF_8).contains("\"items\"")
                if (!htmlOk || !catOk) return@Thread
                val firstTime = !hasUpdated()
                val oldHtml = File(siteDir, "index.html").takeIf { it.exists() }?.readBytes()
                write("catalog.json", cat)
                write("index.html", html)
                val codeChanged = oldHtml == null || !oldHtml.contentEquals(html)
                // primeira atualização: troca já para a versão nova; depois, o app novo entra na próxima abertura
                // (o catálogo novo o próprio app já busca sozinho enquanto está aberto)
                if (firstTime && codeChanged) runOnUiThread { if (fullView == null) web.loadUrl(updatedUrl) }
            } catch (e: Exception) {
                // sem rede ou GitHub fora: continua com a cópia guardada
            }
        }.start()
    }

    private fun download(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun write(name: String, data: ByteArray) {
        val tmp = File(siteDir, "$name.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(File(siteDir, name))) { File(siteDir, name).writeBytes(data); tmp.delete() }
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
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
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
        private const val RAW = "https://raw.githubusercontent.com/kaiqueguimacuper-bit/teste-2/main/site"
        private val BG = Color.parseColor("#12132A")
    }
}
