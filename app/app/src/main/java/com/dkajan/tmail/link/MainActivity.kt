package com.dkajan.tmail.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.ColorProtection
import androidx.core.view.insets.ProtectionLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var emailTextView: TextView
    private lateinit var copyButton: Button
    private lateinit var regenerateButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmailAdapter
    private val emailList = mutableListOf<Email>()
    private lateinit var webView: WebView
    private lateinit var noContentLayout: LinearLayout
    private lateinit var noContentText: TextView
    private lateinit var noContentGif: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var isInboxLoading = false

    private val inboxRefreshRunnable = object : Runnable {
        override fun run() {
            val savedEmail = getSavedEmail(this@MainActivity)
            val hasInternet = checkInternetConnection()

            runOnUiThread { updateUIForInternetConnection(hasInternet) }

            if (savedEmail != null) {
                if (hasInternet && !isInboxLoading) {
                    loadInbox(savedEmail)
                } else if (!hasInternet) {
                    runOnUiThread {
                        emailList.clear()
                        adapter.notifyDataSetChanged()
                        updateNoContentView(
                            true,
                            "No internet connection. Unable to load inbox.",
                            R.drawable.pentol_quby_animation
                        )
                    }
                }
            }

            handler.postDelayed(this, 5000)
        }
    }



    companion object {
        private const val PREFS_NAME = "TMAIL_APP_PREFS"
        private const val KEY_SAVED_EMAIL = "SAVED_EMAIL"
        private const val REQUEST_EMAIL_DETAIL = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        //SPLASH SCREEN DELAY
        var keepSplashAlive = true

        runBlocking {
            delay(500)
            keepSplashAlive = false
        }

        installSplashScreen().setKeepOnScreenCondition {
            keepSplashAlive
        }
        */

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.rootView)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                sysBars.left + 0.dpToPx(v),
                sysBars.top + 0.dpToPx(v),
                sysBars.right + 0.dpToPx(v),
                sysBars.bottom + 0.dpToPx(v)
            )
            insets
        }

        val isDarkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val listProtection = findViewById<ProtectionLayout>(R.id.protectionLayout)
        val accentColor = if (isDarkTheme) {
            ContextCompat.getColor(this, android.R.color.system_accent1_500)
        } else {
            ContextCompat.getColor(this, android.R.color.system_accent1_100)
        }
        window.navigationBarColor = accentColor
        window.isNavigationBarContrastEnforced = false

        val protectionList = listOf(
            ColorProtection(WindowInsetsCompat.Side.TOP, accentColor),
            ColorProtection(WindowInsetsCompat.Side.BOTTOM, accentColor)
        )
        listProtection.setProtections(protectionList)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isDarkTheme) {
                window.decorView.systemUiVisibility = 0
            } else {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isDarkTheme) {
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }

        emailTextView = findViewById(R.id.emailTextView)
        copyButton = findViewById(R.id.copyButton)
        regenerateButton = findViewById(R.id.regenerateButton)
        recyclerView = findViewById(R.id.recyclerView)
        noContentLayout = findViewById(R.id.noContentLayout)
        noContentText = findViewById(R.id.noContentText)
        noContentGif = findViewById(R.id.noContentGif)

        val savedEmail = getSavedEmail(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        val divider = DividerItemDecoration(this, (recyclerView.layoutManager as LinearLayoutManager).orientation)
        val drawable = ContextCompat.getDrawable(this, R.drawable.divider)
        if (drawable != null) {
            divider.setDrawable(drawable)
        }
        recyclerView.addItemDecoration(divider)

        adapter = EmailAdapter(emailList, userEmail = savedEmail ?: "") { emailUrl ->
            val intent = Intent(this, EmailDetailActivity::class.java)
            intent.putExtra("EMAIL_URL", emailUrl)
            startActivityForResult(intent, REQUEST_EMAIL_DETAIL)
        }
        recyclerView.adapter = adapter

        webView = WebView(this)
        webView.visibility = View.GONE
        webView.settings.javaScriptEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        copyButton.setOnClickListener {
            val emailToCopy = emailTextView.text.toString()
            if (emailToCopy.isNotEmpty() && emailToCopy != "Generating email...") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("T-mail Address", emailToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Email copied!", Toast.LENGTH_SHORT).show()
            }
        }

        regenerateButton.setOnClickListener {
            regenerateEmail()
        }

        if (savedEmail != null) {
            emailTextView.text = savedEmail
            loadInbox(savedEmail)
        } else {
            generateNewEmail()
        }


        val fab = findViewById<FloatingActionButton>(R.id.helpFab)
        fab.show()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0 && fab.isShown) {
                    fab.hide()
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    fab.show()
                }
            }
        })


        fab.setOnClickListener {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)

            val colorPrimary = ContextCompat.getColor(this, typedValue.resourceId)

            val hexColor = String.format("#%06X", (0xFFFFFF and colorPrimary))

            val messageHtml = "<font color=\"$hexColor\"><b>© 2025 tmail.link</b></font><br><br>" +
                    "This application is not affiliated with or endorsed by <b><i>tmail.link</i></b>. " +
                    "It uses this service to provide temporary email addresses for user convenience. " +
                    "We are not responsible for the content of emails received or the security of the service.<br>"

            val customView = LayoutInflater.from(this).inflate(R.layout.dialog_about_footer, null)

            val githubFooter: LinearLayout = customView.findViewById(R.id.github_footer)

            githubFooter.setOnClickListener {
                val githubUrl = "https://github.com/dkajan19/tmail.link" // Zmeňte URL na váš GitHub profil
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                startActivity(browserIntent)
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("About")
                .setMessage(HtmlCompat.fromHtml(messageHtml, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setView(customView)
                .setPositiveButton("Got It", null)
                .show()
        }

    }

    override fun onResume() {
        super.onResume()
        handler.post(inboxRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(inboxRefreshRunnable)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EMAIL_DETAIL && resultCode == RESULT_OK) {
            val emailDeleted = data?.getBooleanExtra("EMAIL_DELETED", false) ?: false
            if (emailDeleted) {
                regenerateEmail()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(inboxRefreshRunnable)
    }

    private fun Int.dpToPx(view: View): Int {
        return (this * view.resources.displayMetrics.density).toInt()
    }

    private fun regenerateEmail() {
        clearEmail(this)
        emailList.clear()
        adapter.notifyDataSetChanged()
        isInboxLoading = false
        emailTextView.text = "Generating new address..."
        generateNewEmail()
    }

    private fun generateNewEmail() {
        if (!checkInternetConnection()) {
            showNoConnectionError()
            return
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    """
                    (function pollEmail() {
                        var emailContent = document.body.textContent;
                        var emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6}/;
                        var emailMatch = emailContent.match(emailRegex);
                        if(emailMatch && emailMatch.length > 0){
                            AndroidBridge.processEmail(emailMatch[0]);
                        } else {
                            setTimeout(pollEmail, 500);
                        }
                    })();
                    """.trimIndent(), null
                )
            }
        }
        webView.loadUrl("https://tmail.link/")
    }

    private fun loadInbox(email: String) {
        if (!checkInternetConnection()) {
            showNoConnectionError()
            return
        }

        isInboxLoading = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    """
                    var messages = document.querySelectorAll('#messages li');
                    var emails = [];
                    messages.forEach(function(li){
                        var a = li.querySelector('a');
                        var span = li.querySelector('span');
                        var fullText = span ? span.innerText : '';
                        var afterLinkText = fullText.replace(a ? a.innerText : '', '').trim();
                        if(afterLinkText.startsWith('-')){
                            afterLinkText = afterLinkText.slice(1).trim();
                        }
                        emails.push({
                            subject: a ? a.innerText : '',
                            href: a ? a.href : '',
                            sender: afterLinkText
                        });
                    });
                    AndroidBridge.processInbox(JSON.stringify(emails));
                    """.trimIndent(), null
                )
                isInboxLoading = false
            }
        }

        webView.loadUrl("https://tmail.link/inbox/$email/")
    }

    private fun saveEmail(context: Context, email: String) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        with(sharedPref.edit()) { putString(KEY_SAVED_EMAIL, email); apply() }
    }

    private fun getSavedEmail(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPref.getString(KEY_SAVED_EMAIL, null)
    }

    private fun clearEmail(context: Context) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        with(sharedPref.edit()) { remove(KEY_SAVED_EMAIL); apply() }
    }

    private fun checkInternetConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
    }

    private fun showNoConnectionError() {
        updateNoContentView(
            true,
            "Please check your internet connection and try again.",
            R.drawable.pentol_quby_animation
        )
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun processEmail(email: String?) {
            if (!email.isNullOrEmpty()) {
                saveEmail(this@MainActivity, email)
                runOnUiThread {
                    emailTextView.text = email
                    loadInbox(email)
                }
            }
        }

        @JavascriptInterface
        fun processInbox(json: String) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val parsedEmails = mutableListOf<Email>()
                    val jsonArray = JSONArray(json)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        parsedEmails.add(
                            Email(
                                obj.getString("subject"),
                                obj.getString("sender"),
                                obj.getString("href")
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        if (parsedEmails.isEmpty()) {
                            updateNoContentView(
                                true,
                                "No emails available. Your inbox is currently empty.",
                                R.drawable.pentol_quby_animation
                            )
                        } else {
                            emailList.clear()
                            emailList.addAll(parsedEmails)
                            adapter.notifyDataSetChanged()
                            updateNoContentView(false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebAppInterface", "Error processing inbox JSON", e)
                    withContext(Dispatchers.Main) {
                        updateNoContentView(
                            true,
                            "An error occurred. Please try again later.",
                            R.drawable.pentol_quby_animation
                        )
                    }
                }
            }
        }
    }

    private fun updateNoContentView(show: Boolean, message: String? = null, gifResource: Int? = null) {
        if (show) {
            noContentLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            message?.let { noContentText.text = it }
            gifResource?.let {
                Glide.with(this)
                    .asGif()
                    .load(it)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(noContentGif)
            }
        } else {
            noContentLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateUIForInternetConnection(isConnected: Boolean) {
        regenerateButton.isEnabled = isConnected
        if (!isConnected) {
            regenerateButton.alpha = 0.5f
        } else {
            regenerateButton.alpha = 1f
        }
    }
}
