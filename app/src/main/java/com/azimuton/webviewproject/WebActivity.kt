package com.azimuton.webviewproject

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import com.azimuton.webviewproject.databinding.ActivityWebBinding
import org.json.JSONObject
import java.io.File
import java.io.IOException

private const val FILECHOOSE = 111
class WebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebBinding
    private var link: String = ""
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    lateinit var power: PowerManager
    lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.FULL_WAKE_LOCK, "MyApp::MyWakelockTag")

            binding.wvWeb.loadUrl("https://cosmolot.ua/")
            setWebClient()
    }

    @SuppressLint("WakelockTimeout")
    override fun onResume() {
        wakeLock.acquire()
        Log.d("MyWakeLock", "Main Resume: hold=${wakeLock.isHeld}")
        super.onResume()
    }

    override fun onPause() {
        if (isTaskRoot) {
            wakeLock.release()
            Log.d("MyWakeLock", "Main Pause: hold=${wakeLock.isHeld}")
        }
        super.onPause()
    }

    internal inner class MyWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int
        ) {
            super.onProgressChanged(view, newProgress)
            binding.pbWeb.progress = newProgress
            if (newProgress < 100 && binding.pbWeb.visibility == ProgressBar.INVISIBLE) {
                binding.pbWeb.visibility = ProgressBar.VISIBLE
                binding.tvWeb.visibility = ProgressBar.VISIBLE
            }
            if (newProgress == 100) {
                binding.pbWeb.visibility = ProgressBar.INVISIBLE
                binding.tvWeb.visibility = ProgressBar.INVISIBLE
            }
        }

        @SuppressLint("QueryPermissionsNeeded")
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            if (uploadMessage != null) {
                uploadMessage!!.onReceiveValue(null)
            }
            uploadMessage = filePathCallback
            var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent!!.resolveActivity(this@WebActivity.packageManager) != null) {
                val photoFile: File? = null
                try {
                    // photoFile = createImageFile()
                    takePictureIntent.putExtra("PhotoPath", link)
                } catch (ex: IOException) {
                    Log.e("Webview", "Image file creation failed", ex)
                }
                if (photoFile != null) {
                    link = "file:" + photoFile.absolutePath
                    takePictureIntent.putExtra(
                        MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile)
                    )
                } else {
                    takePictureIntent = null
                }
            }
            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
            contentSelectionIntent.type = "*/*"
            val intentArray: Array<Intent> = takePictureIntent?.let { arrayOf(it) } ?: arrayOf()
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
            startActivityForResult(chooserIntent, FILECHOOSE)
            return true
        }
    }

    fun openFileChooser(uploadMsg: ValueCallback<Uri?>?) {
        this.openFileChooser(uploadMsg, "*/*")
    }

    private fun openFileChooser(
        uploadMsg: ValueCallback<Uri?>?,
        acceptType: String?
    ) {
        this.openFileChooser(uploadMsg, acceptType, null)
    }

    private fun openFileChooser(
        uploadMsg: ValueCallback<Uri?>?,
        acceptType: String?,
        capture: String?
    ) {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        this@WebActivity.startActivityForResult(
            Intent.createChooser(i, "File Browser"), FILECHOOSE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, intent)
        var results: Array<Uri>? = null
        //Check if response is positive
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == FILECHOOSE) {
                if (null == uploadMessage) {
                    return
                }
                if (intent == null) {
                    if (link != null) {
                        results = arrayOf(Uri.parse(link))
                    }
                } else {
                    val dataString = intent.dataString
                    if (dataString != null) {
                        results = arrayOf(Uri.parse(dataString))
                    }
                }
            }
        }
        if(uploadMessage != null){
            uploadMessage!!.onReceiveValue(results)
        }
        uploadMessage = null
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setWebClient() {
        val webSettings = binding.wvWeb.settings
        binding.wvWeb.isLongClickable = false
        binding.wvWeb.setOnLongClickListener { true }
        binding.wvWeb.webChromeClient = MyWebChromeClient()
        binding.wvWeb.addJavascriptInterface(this, "androidObj")


        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.wvWeb, true)
        cookieManager.flush()

        webSettings.javaScriptEnabled = true
        webSettings.loadWithOverviewMode = true
        webSettings.domStorageEnabled = true
        webSettings.useWideViewPort = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.displayZoomControls = false
        webSettings.builtInZoomControls = false
        webSettings.loadsImagesAutomatically = true
        webSettings.allowUniversalAccessFromFileURLs = false
        webSettings.allowFileAccessFromFileURLs = false
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.databaseEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false

        binding.wvWeb.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val headers = request?.requestHeaders
                Log.d("Headers WebView", headers.toString())
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                super.onReceivedSslError(view, handler, error)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.wvWeb.evaluateJavascript("javascript: androidObj.call(obj)"){
                    result ->
                    Log.d("TAG", "Result: $result")
                }
            }
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (binding.wvWeb.canGoBack()) {
                        binding.wvWeb.goBack()
                    } else {
                        finishAffinity()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

}