package sky.universidad.UnApp.presentation.portal

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import sky.universidad.UnApp.core.network.UnapEndpoints
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.normalizeResourceUrl

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPortalScreen(
    bottomContentPadding: PaddingValues,
    initialUrl: String? = null,
    onPreparePortalCookies: (List<String>) -> Unit,
    onSyncCookiesFromWebView: () -> Unit = {},
    onBack: () -> Unit
) {
    var portalIndex by remember(initialUrl) {
        mutableIntStateOf(
            if (initialUrl?.contains("intranet", ignoreCase = true) == true) 1 else 0
        )
    }
    var progress by remember { mutableIntStateOf(0) }
    val resolvedInitialUrl = initialUrl?.let { normalizeResourceUrl(it) }?.takeIf { it.isNotBlank() }
    val targetUrl = resolvedInitialUrl ?: if (portalIndex == 0) {
        UnapEndpoints.BASE_AULA_VIRTUAL
    } else {
        UnapEndpoints.BASE_INTRANET
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        onPreparePortalCookies(UnapEndpoints.SESSION_ORIGINS)
    }

    LaunchedEffect(resolvedInitialUrl) {
        val urlToOpen = resolvedInitialUrl
        if (!urlToOpen.isNullOrBlank()) {
            webViewRef.value?.loadUrl(urlToOpen)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSyncCookiesFromWebView()
            webViewRef.value?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = UnAppIcons.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column {
                Text(
                    text = "Portal web UNAP",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Sesión compartida con Aula Virtual e Intranet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AppleSegmentedControl(
            options = listOf("Aula Virtual", "Intranet"),
            selectedIndex = portalIndex,
            onSelect = { idx ->
                portalIndex = idx
                val next = if (idx == 0) UnapEndpoints.BASE_AULA_VIRTUAL else UnapEndpoints.BASE_INTRANET
                webViewRef.value?.loadUrl(next)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        configureUnapPortalWebView()
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 50) {
                                    injectUnapPortalFix(view, view?.url)
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                injectUnapPortalFix(view, url)
                                CookieManager.getInstance().flush()
                                onSyncCookiesFromWebView()
                            }
                        }
                        loadUrl(targetUrl)
                        webViewRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
internal fun WebView.configureUnapPortalWebView() {
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    isFocusable = true
    isFocusableInTouchMode = true
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (!v.hasFocus()) {
                v.requestFocus()
            }
        }
        false
    }
}

internal fun injectUnapPortalFix(webView: WebView?, url: String?) {
    val targetUrl = url ?: webView?.url ?: return
    if (!targetUrl.contains("unap.edu.pe", ignoreCase = true)) return
    val js = """
        (function() {
          try {
            if (document.body) {
              document.body.classList.remove('m-page--loading');
            }
            if (!document.getElementById('unapp-portal-fix')) {
              var style = document.createElement('style');
              style.id = 'unapp-portal-fix';
              style.innerHTML = 'html, body { height: 100% !important; min-height: 100vh !important; overflow-y: auto !important; } ' +
                '.m-page-loader { display: none !important; } ' +
                '#particles-js, .fondo-container { pointer-events: none !important; } ' +
                '.m-page, .m-grid, .wrapper, .intro { min-height: 100vh !important; height: auto !important; overflow: visible !important; display: block !important; opacity: 1 !important; visibility: visible !important; } ' +
                '.intro .left-ovh, .left-ovh { position: relative !important; left: auto !important; top: auto !important; transform: none !important; width: 100% !important; max-width: 460px !important; height: auto !important; max-height: none !important; overflow: visible !important; margin: 0 auto !important; z-index: 10 !important; } ' +
                '.left-side, .m-login__wrapper { height: auto !important; min-height: 85vh !important; padding: 24px 18px !important; } ' +
                'input, button, select, textarea { pointer-events: auto !important; }';
              (document.head || document.documentElement).appendChild(style);
            }
          } catch (e) {}
        })();
    """.trimIndent()
    webView?.evaluateJavascript(js, null)
}
