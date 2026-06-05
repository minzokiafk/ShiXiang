package com.pixelpoint.mediadownloader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding

class WebsiteLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var titleView: TextView
    private lateinit var urlView: TextView
    private lateinit var statusView: TextView
    private var sourceUrl: String = ""
    private var currentUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty()
        currentUrl = sourceUrl
        val initialTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "网站登录" }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SURFACE_COLOR)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBars.bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(10), dp(8))
            setBackgroundColor(SURFACE_COLOR)
        }
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top + dp(8))
            insets
        }

        val backButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "返回"
            setColorFilter(TEXT_COLOR)
            setOnClickListener { finish() }
        }
        topBar.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(this).apply {
            text = initialTitle
            setTextColor(TEXT_COLOR)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        urlView = TextView(this).apply {
            text = currentUrl
            setTextColor(SUBTLE_TEXT_COLOR)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titleGroup.addView(titleView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        titleGroup.addView(urlView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        topBar.addView(titleGroup, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val saveButton = TextView(this).apply {
            text = "保存"
            setTextColor(PRIMARY_COLOR)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(10), 0)
            setOnClickListener { saveAndFinish() }
        }
        topBar.addView(saveButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))

        statusView = TextView(this).apply {
            text = "正在加载页面"
            setTextColor(PRIMARY_COLOR)
            textSize = 13f
            setPadding(dp(16), dp(4), dp(16), dp(4))
            visibility = View.GONE
        }

        CookieManager.getInstance().setAcceptCookie(true)
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    val cleaned = title.orEmpty().usableCapturedPageTitle()
                    if (cleaned.isNotBlank()) titleView.text = cleaned
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme.orEmpty()
                    return if (scheme == "http" || scheme == "https") {
                        false
                    } else {
                        AppLogger.event("cookie", "websiteLoginActivityNonHttpBlocked", "url" to uri.toString())
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    currentUrl = url.orEmpty().ifBlank { currentUrl }
                    urlView.text = currentUrl
                    statusView.text = "正在加载页面"
                    statusView.visibility = View.VISIBLE
                    AppLogger.event("cookie", "websiteLoginActivityPageStarted", "url" to currentUrl)
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    currentUrl = url.orEmpty().ifBlank { currentUrl }
                    urlView.text = currentUrl
                    statusView.visibility = View.GONE
                    CookieManager.getInstance().flush()
                    AppLogger.event("cookie", "websiteLoginActivityPageFinished", "url" to currentUrl)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        statusView.text = "页面加载失败，请返回后重试"
                        statusView.visibility = View.VISIBLE
                        AppLogger.warn(
                            "cookie",
                            "websiteLoginActivityPageFailed",
                            "url" to request.url.toString(),
                            "code" to error?.errorCode,
                            "description" to error?.description
                        )
                    }
                    super.onReceivedError(view, request, error)
                }
            }
            loadUrl(sourceUrl)
        }

        root.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(statusView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun saveAndFinish() {
        CookieManager.getInstance().flush()
        val cookie = webViewCookieFileForTask(sourceUrl, currentUrl)
            .ifBlank { CookieManager.getInstance().mergedCookiesForTask(sourceUrl, currentUrl) }
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_SOURCE_URL, sourceUrl)
                .putExtra(EXTRA_CURRENT_URL, currentUrl)
                .putExtra(EXTRA_COOKIE, cookie)
        )
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SOURCE_URL = "source_url"
        const val EXTRA_CURRENT_URL = "current_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COOKIE = "cookie"
        private const val SURFACE_COLOR = 0xFFF8F5EE.toInt()
        private const val TEXT_COLOR = 0xFF111111.toInt()
        private const val SUBTLE_TEXT_COLOR = 0xFF5F5B66.toInt()
        private const val PRIMARY_COLOR = 0xFF004D47.toInt()

        fun intent(context: Context, sourceUrl: String, title: String): Intent {
            return Intent(context, WebsiteLoginActivity::class.java)
                .putExtra(EXTRA_SOURCE_URL, sourceUrl)
                .putExtra(EXTRA_TITLE, title.ifBlank { Uri.parse(sourceUrl).host.orEmpty().ifBlank { "网站登录" } })
        }
    }
}
