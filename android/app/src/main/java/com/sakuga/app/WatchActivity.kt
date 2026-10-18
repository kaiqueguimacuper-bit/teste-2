package com.sakuga.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Tela interna para assistir nas plataformas grátis oficiais (Pluto TV, Bilibili, iQIYI, YouTube...)
 * sem sair do Sakuga.
 */
class WatchActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var column: LinearLayout
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent?.data ?: run { finish(); return }

        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(8), dp(6), dp(12), dp(6))
        }
        val back = TextView(this).apply {
            text = "‹  Voltar ao Sakuga"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(10), dp(10), dp(16), dp(10))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = Platforms.label(start.host ?: "")
            setTextColor(Color.parseColor("#A3A0C2"))
            textSize = 13f
            maxLines = 1
        }
        bar.addView(back)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF4D8D"))
        }
        web = WebView(this).apply { setBackgroundColor(Color.BLACK) }

        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        column.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        root.setOnApplyWindowInsetsListener { v, insets ->
            if (fullView == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                    v.setPadding(b.left, b.top, b.right, b.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
            } else v.setPadding(0, 0, 0, 0)
            insets
        }

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
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme ?: ""
                if (scheme == "http" || scheme == "https") return false
                // intent://, market:// e afins: deixa o Android decidir (ex.: abrir o app oficial)
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (e: ActivityNotFoundException) { }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullView != null) { callback.onCustomViewHidden(); return }
                fullView = view
                fullCallback = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                column.visibility = View.GONE
                fullscreen(true)
            }

            override fun onHideCustomView() {
                fullView?.let { root.removeView(it) }
                fullView = null
                fullCallback?.onCustomViewHidden()
                fullCallback = null
                column.visibility = View.VISIBLE
                fullscreen(false)
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(start.toString())
    }

    private fun fullscreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else c.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (on)
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            else View.SYSTEM_UI_FLAG_VISIBLE
        }
        root.requestApplyInsets()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }

    override fun onDestroy() {
        root.removeAllViews()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private val BG = Color.parseColor("#12132A")

        fun open(activity: Activity, url: Uri) {
            activity.startActivity(Intent(activity, WatchActivity::class.java).setData(url))
        }
    }
}

/** Plataformas oficiais e gratuitas que abrem dentro do app. */
object Platforms {
    private val hosts = listOf(
        "pluto.tv" to "Pluto TV", "bilibili.tv" to "Bilibili", "bilibili.com" to "Bilibili", "iq.com" to "iQIYI",
        "tubitv.com" to "Tubi", "plex.tv" to "Plex", "abema.tv" to "ABEMA", "tver.jp" to "TVer",
        "nicovideo.jp" to "Niconico", "wetv.vip" to "WeTV", "youku.tv" to "Youku", "laftel.net" to "Laftel",
        "therokuchannel.roku.com" to "The Roku Channel", "retrocrush.tv" to "RetroCrush",
        "youtube.com" to "YouTube", "youtu.be" to "YouTube"
    )

    fun isFree(host: String): Boolean = hosts.any { (h, _) -> host == h || host.endsWith(".$h") }

    fun label(host: String): String = hosts.firstOrNull { (h, _) -> host == h || host.endsWith(".$h") }?.second ?: host
}
