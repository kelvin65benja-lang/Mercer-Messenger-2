package com.mercer.messenger

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.google.firebase.messaging.FirebaseMessaging
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var executor: Executor
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = ContextCompat.getMainExecutor(this)
        createNotificationChannel()
        requestNotifications()
        web = WebView(this)
        web.addJavascriptInterface(NativeBridge(), "MercerAndroid")
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        CookieManager.getInstance().setAcceptCookie(true)
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }
        web.loadUrl(BuildConfig.WEB_APP_URL)
    }

    inner class NativeBridge {
        @JavascriptInterface fun biometricUnlock() { runOnUiThread { authenticateWithFingerprint() } }
        @JavascriptInterface fun isNativeApp(): Boolean = true
        @JavascriptInterface fun requestPushToken() {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result ?: return@addOnCompleteListener
                    val escaped = org.json.JSONObject.quote(token)
                    runOnUiThread { web.evaluateJavascript("window.dispatchEvent(new CustomEvent('mercerNativePushToken',{detail:{token:$escaped}}))", null) }
                }
            }
        }
    }

    fun authenticateWithFingerprint() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Fingerprint/biometric is not available on this device", Toast.LENGTH_SHORT).show(); return
        }
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                web.evaluateJavascript("window.dispatchEvent(new Event('mercerNativeBiometricSuccess'))", null)
            }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                web.evaluateJavascript("window.dispatchEvent(new Event('mercerNativeBiometricFailed'))", null)
            }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Mercer Messenger").setSubtitle("Use fingerprint, face, or device PIN").setAllowedAuthenticators(authenticators).build())
    }

    private fun requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("mercer_messages", "Mercer Messenger", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
