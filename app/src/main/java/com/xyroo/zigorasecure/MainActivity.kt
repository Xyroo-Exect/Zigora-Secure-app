package com.xyroo.zigorasecure

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val SHIZUKU_REQUEST_CODE = 1001

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        runOnUiThread {
            webView.evaluateJavascript(
                "window.__shizukuPermissionResult && window.__shizukuPermissionResult($granted)",
                null
            )
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        // Shizuku service tersambung
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen / edge-to-edge agar terasa native
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(ShizukuBridge(), "AndroidShell")

        webView.loadUrl("file:///android_asset/webroot/index.html")

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Biarkan JS handle back navigation dulu (lihat history.popstate di index.html)
        if (webView.canGoBack().not()) {
            // Tidak ada history WebView untuk di-back — biarkan app exit normal
            super.onBackPressed()
        } else {
            webView.goBack()
        }
    }

    /**
     * Jembatan JS -> Shizuku.
     * Dipanggil dari index.html lewat window.AndroidShell.exec(cmd)
     * Menggantikan window.ksu.exec() / window.axeron.exec() versi Magisk module.
     */
    inner class ShizukuBridge {

        @JavascriptInterface
        fun isShizukuAvailable(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Throwable) {
                false
            }
        }

        @JavascriptInterface
        fun hasPermission(): Boolean {
            return try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        }

        @JavascriptInterface
        fun requestPermission() {
            try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
            } catch (e: Throwable) {
                // Shizuku service belum jalan
            }
        }

        /**
         * Eksekusi shell command lewat Shizuku.
         * Shizuku.newProcess() bersifat hidden/deprecated tapi masih satu-satunya cara
         * simpel untuk run command tanpa bikin UserService terpisah. Dipanggil via
         * reflection supaya tidak error compile time di versi Shizuku manapun.
         */
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
                )

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
                ""
            }
        }
    }
}
