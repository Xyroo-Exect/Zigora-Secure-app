package com.xyroo.zigorasecure

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val SHIZUKU_REQUEST_CODE = 1001
    private val TAG = "ZigoraSecure"

    // Shizuku diakses sepenuhnya lewat reflection supaya app TIDAK CRASH
    // sama sekali kalau Shizuku belum terinstall / belum running / API berubah.
    private var shizukuOk = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge sederhana & aman, tanpa flag berisiko
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        } catch (e: Throwable) {
            Log.w(TAG, "edge-to-edge setup failed, continuing normally", e)
        }

        webView = WebView(this)
        setContentView(webView)

        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "WebView settings failed", e)
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(ShizukuBridge(), "AndroidShell")

        webView.loadUrl("file:///android_asset/webroot/index.html")

        // Inisialisasi Shizuku TIDAK BOLEH bikin app crash apapun yang terjadi
        initShizukuSafely()
    }

    private fun initShizukuSafely() {
        try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")

            // Daftarkan listener lewat reflection, dibungkus try-catch penuh
            try {
                val listenerInterface = Class.forName("rikka.shizuku.Shizuku\$OnBinderReceivedListener")
                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                    listenerInterface.classLoader,
                    arrayOf(listenerInterface)
                ) { _, _, _ -> null }
                val addMethod = shizukuClass.getMethod(
                    "addBinderReceivedListenerSticky",
                    listenerInterface
                )
                addMethod.invoke(null, proxy)
            } catch (e: Throwable) {
                Log.w(TAG, "Shizuku binder listener skip", e)
            }

            shizukuOk = true
            Log.i(TAG, "Shizuku class found, basic init ok")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Shizuku library not found / not linked properly")
            shizukuOk = false
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku init failed safely, app continues", e)
            shizukuOk = false
        }
    }

    override fun onBackPressed() {
        try {
            if (webView.canGoBack()) {
                webView.goBack()
                return
            }
        } catch (_: Throwable) { }
        super.onBackPressed()
    }

    /**
     * Jembatan JS -> Shizuku. Semua method dibungkus try-catch penuh
     * supaya kalau Shizuku tidak ada / belum permission, JS dapat hasil
     * kosong/false dengan rapi — TIDAK PERNAH crash app.
     */
    inner class ShizukuBridge {

        @JavascriptInterface
        fun isShizukuAvailable(): Boolean {
            return try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getMethod("pingBinder")
                method.invoke(null) as? Boolean ?: false
            } catch (e: Throwable) {
                false
            }
        }

        @JavascriptInterface
        fun hasPermission(): Boolean {
            return try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getMethod("checkSelfPermission")
                val result = method.invoke(null) as Int
                result == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        }

        @JavascriptInterface
        fun requestPermission() {
            try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val checkMethod = clazz.getMethod("checkSelfPermission")
                val current = checkMethod.invoke(null) as Int
                if (current != PackageManager.PERMISSION_GRANTED) {
                    val reqMethod = clazz.getMethod("requestPermission", Int::class.java)
                    reqMethod.invoke(null, SHIZUKU_REQUEST_CODE)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "requestPermission failed", e)
            }
        }

        @JavascriptInterface
        fun exec(cmd: String): String {
            if (!isShizukuAvailable()) return ""
            if (!hasPermission()) return ""
            return try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) ?: return ""

                val inputStreamGetter = process.javaClass.getMethod("getInputStream")
                val inputStream = inputStreamGetter.invoke(process) as java.io.InputStream

                val output = StringBuilder()
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                }

                try {
                    val waitForMethod = process.javaClass.getMethod("waitFor")
                    waitForMethod.invoke(process)
                } catch (_: Throwable) { }

                try {
                    val destroyMethod = process.javaClass.getMethod("destroy")
                    destroyMethod.invoke(process)
                } catch (_: Throwable) { }

                output.toString()
            } catch (e: Throwable) {
                Log.w(TAG, "exec failed: $cmd", e)
                ""
            }
        }
    }
}
