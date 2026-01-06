package com.dkajan.tmail.link

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.ColorProtection
import androidx.core.view.insets.ProtectionLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class EmailDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL_URL = "email_url"
        private const val TAG = "EmailDetailActivity"
    }

    private lateinit var emailFrom: TextView
    private lateinit var emailTo: TextView
    private lateinit var emailSubject: TextView
    private lateinit var emailDate: TextView
    private lateinit var emailWebView: WebView
    private lateinit var buttonBack: Button
    private lateinit var buttonDelete: Button

    private var deleteUrl: String? = null
    private var csrfToken: String? = null

    private val cookieJar = object : CookieJar {
        private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { cookieStore[url.host] = cookies }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: listOf()
    }

    private val client = OkHttpClient.Builder().cookieJar(cookieJar).build()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_email_detail)

        val rootView = findViewById<View>(R.id.rootViewDetailActivity)

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

        val listProtection = findViewById<ProtectionLayout>(R.id.protectionLayoutDetailActivity)
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

        emailFrom = findViewById(R.id.emailFrom)
        emailTo = findViewById(R.id.emailTo)
        emailSubject = findViewById(R.id.emailSubject)
        emailDate = findViewById(R.id.emailDate)
        emailWebView = findViewById(R.id.emailWebView)
        buttonBack = findViewById(R.id.buttonBack)
        buttonDelete = findViewById(R.id.buttonDelete)

        emailWebView.settings.apply {
            loadWithOverviewMode = true
            useWideViewPort = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            builtInZoomControls = true
            displayZoomControls = false
        }

        emailWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open URL in browser: $url", e)
                        Toast.makeText(this@EmailDetailActivity, "Failed to open URL", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return false
            }
        }

        val url = intent.getStringExtra(EXTRA_EMAIL_URL)
        if (url != null) loadEmail(url)
        else Toast.makeText(this, "Email URL is not defined", Toast.LENGTH_SHORT).show()

        buttonBack.setOnClickListener { finish() }

        buttonDelete.setOnClickListener {
            deleteUrl?.let { urlToDelete ->
                csrfToken?.let { token ->
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d(TAG, "Sending POST request to: $urlToDelete")
                            Log.d(TAG, "Using CSRF Token: $token")

                            val formBody = FormBody.Builder()
                                .add("csrfmiddlewaretoken", token)
                                .add("delete", "1")
                                .build()

                            val request = Request.Builder()
                                .url(urlToDelete)
                                .post(formBody)
                                .addHeader("X-CSRFToken", token)
                                .addHeader("Referer", urlToDelete)
                                .addHeader("User-Agent", "Mozilla/5.0")
                                .build()

                            val response = client.newCall(request).execute()
                            val responseCode = response.code
                            response.close()

                            withContext(Dispatchers.Main) {
                                if (responseCode == 200 || responseCode == 302) {
                                    Toast.makeText(this@EmailDetailActivity, "Email deleted", Toast.LENGTH_SHORT).show()
                                    val resultIntent = Intent().apply { putExtra("EMAIL_DELETED", true) }
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                } else {
                                    Toast.makeText(this@EmailDetailActivity, "Deletion failed. Code: $responseCode", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error while deleting email", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@EmailDetailActivity, "Error while deleting email", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } ?: Toast.makeText(this, "CSRF token is missing. Cannot delete email.", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "No URL to delete email", Toast.LENGTH_SHORT).show()
        }
    }

    fun Int.dpToPx(view: View): Int {
        return (this * view.resources.displayMetrics.density).toInt()
    }

    fun setLabelValueBold(label: String, value: String, textView: TextView) {
        val fullText = "$label: $value"
        val spannable = SpannableString(fullText)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            label.length + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        textView.text = spannable
    }

    private fun loadEmail(url: String) {

        fun unescapeUnicode(text: String): String {
            return text.replace(Regex("\\\\u([0-9a-fA-F]{4})")) {
                Character.toChars(it.groupValues[1].toInt(16)).joinToString("")
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                response.close()

                val fromRow = doc.select("tr:has(b:contains(From))").firstOrNull()
                val toRow = doc.select("tr:has(b:contains(To))").firstOrNull()
                val subjectRow = doc.select("tr:has(b:contains(Subject))").firstOrNull()
                val dateRow = doc.select("tr:has(b:contains(Date))").firstOrNull()
                val from = fromRow?.select("td:nth-child(2)")?.text()?.trim() ?: ""
                val to = toRow?.select("td:nth-child(2)")?.text()?.trim() ?: ""
                val subject = subjectRow?.select("td:nth-child(2)")?.text()?.trim() ?: ""
                val date = dateRow?.select("td:nth-child(2)")?.text()?.trim() ?: ""
                val form = doc.selectFirst("form")
                val action = form?.attr("action") ?: ""
                deleteUrl = if (action.isEmpty()) url else {
                    if (action.startsWith("http")) action else URL(url).protocol + "://" + URL(url).host + action
                }
                csrfToken = form?.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value")

                val baseUrl = url.substringBeforeLast("/") + "/"
                var rawBodyHtml: String

                val iframe = doc.selectFirst("iframe")
                if (iframe != null) {
                    val iframeSrc = iframe.attr("src")
                    val iframeUrl = URL(URL(url), iframeSrc).toString()
                    val iframeRequest = Request.Builder().url(iframeUrl).build()
                    val iframeResponse = client.newCall(iframeRequest).execute()
                    rawBodyHtml = iframeResponse.body?.string() ?: "<p>Email content not found in iframe.</p>"
                    iframeResponse.close()
                } else {
                    val fullBody = doc.body()
                    fullBody?.select("table")?.remove()
                    fullBody?.select("form")?.remove()
                    rawBodyHtml = fullBody?.html() ?: "<p>Email content not found.</p>"
                }

                var bodyHtml = unescapeUnicode(rawBodyHtml)
                val attachmentsHtml = doc.select("div > p > a").joinToString("<br>") { it.outerHtml() }
                if (attachmentsHtml.isNotEmpty()) {
                    bodyHtml += "<br><hr><h3>Attachments:</h3>$attachmentsHtml"
                }

                val isDarkTheme = (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

                val highlightColor = if (isDarkTheme) {
                    ContextCompat.getColor(this@EmailDetailActivity, android.R.color.system_accent1_400)
                } else {
                    ContextCompat.getColor(this@EmailDetailActivity, android.R.color.system_accent1_400)
                }

                fun Int.toHexColor(): String = String.format("#%06X", 0xFFFFFF and this)

                val styledHtml = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 4px;
                    }
                    * {
                        max-width: 100% !important;
                        box-sizing: border-box;
                    }
                    img {
                        height: auto !important;
                    }
                    ::selection {

            background: ${highlightColor.toHexColor()};

                        color: #ffffff; /* voliteľne, text farba pri výbere */
                    }
                </style>
            </head>
            <body>
                $bodyHtml
            </body>
            </html>
            """.trimIndent()

                withContext(Dispatchers.Main) {
                    emailWebView.settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    setLabelValueBold("From", from, emailFrom)
                    setLabelValueBold("To", to, emailTo)
                    setLabelValueBold("Subject", subject, emailSubject)
                    val date = dateRow?.select("td:nth-child(2)")?.text()?.trim() ?: ""
                    setLabelValueBold("Date", formatEmailDate(date), emailDate)
                    emailWebView.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "utf-8", null)
                }
            } catch (e: Exception) {
                Log.e("EmailDetail", "Error while loading email", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EmailDetailActivity, "Error while loading email: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun formatEmailDate(dateString: String): String {
        return try {
            val cleaned = dateString.replace(Regex("\\s*\\(.*\\)"), "").trim()

            val parser = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val zonedDateTime = ZonedDateTime.parse(cleaned, parser)

            val localZoned = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())

            val formatter = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm", Locale.getDefault())
            localZoned.format(formatter)
        } catch (e: Exception) {
            dateString
        }
    }

}
