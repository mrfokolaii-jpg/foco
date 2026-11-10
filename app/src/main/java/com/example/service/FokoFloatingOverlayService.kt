package com.example.service

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.agent.AgentEngine
import com.example.data.agent.AgentEvent
import com.example.data.agent.AgentPhase
import com.example.data.connectors.ConnectorStore
import com.example.data.local.ApiKeyStore
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.SystemPersona
import com.example.data.remote.AiRouter
import com.example.util.SpeechTextCleaner
import com.example.util.FokoProxy
import com.example.data.local.VoicePrefs
import com.example.voice.BargeInDetector
import com.example.voice.NaturalTts
import com.example.voice.VoskEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class FokoFloatingOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var root: LinearLayout? = null
    private var orb: FrameLayout? = null
    private var orbView: OrbView? = null
    private var orbGlyph: TextView? = null
    private var panel: LinearLayout? = null
    private var statusText: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var inputField: EditText? = null
    private var params: WindowManager.LayoutParams? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private var phase: AgentPhase = AgentPhase.IDLE
    private var expanded = false
    private var hidden = false
    private var isDragging = false
    private var busy = false
    private val transcript = mutableListOf<ChatMessage>()

    /** حالت مکالمه دوطرفه: گوش دادن → جواب → دوباره گوش دادن، حتی بیرون از اپ. */
    private var conversationMode = false
    private var conversationPaused = false
    private var silentErrors = 0

    private lateinit var voicePrefs: VoicePrefs
    private var bargeIn: BargeInDetector? = null
    private var listeningOffline = false

    // صف گفتار برای خواندن جمله به جمله
    private var pendingUtterances = 0
    private var finalQueued = false
    private var utteranceCounter = 0
    private val sentenceBuffer = StringBuilder()
    private var streamedText = StringBuilder()

    // توضیح زنده صفحه
    private val liveExplainRunnable = Runnable { runLiveExplain() }
    private var lastExplainedPackage = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FokoProxy.install(applicationContext)
        voicePrefs = VoicePrefs(applicationContext)
        bargeIn = BargeInDetector(applicationContext)
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildUi()
        initTts()
        initRecognizer()
        appendLog("فوکو", "سلام! من فوکو هستم. میکروفون را بزن و صحبت کن یا بنویس.")
        registerScreenListener()
        registerNotificationListener()
        mainHandler.postDelayed({ startWakeWordIfIdle() }, 800)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONVERSATION -> startConversation()
            ACTION_PAUSE -> pauseConversation()
            ACTION_RESUME -> resumeConversation()
            ACTION_END -> endConversation()
            ACTION_REFRESH_SETTINGS -> { registerScreenListener(); startWakeWordIfIdle() }
        }
        return START_STICKY
    }

    private fun startConversation() {
        conversationMode = true
        conversationPaused = false
        silentErrors = 0
        if (hidden) setHidden(false)
        if (recognizer == null) initRecognizer()
        appendLog("فوکو", "مکالمه دوطرفه شروع شد. می‌توانی از اپ بیرون بروی؛ من گوش می‌دهم.")
        refreshNotification()
        mainHandler.postDelayed({ startListening() }, 300)
    }

    private fun pauseConversation() {
        if (!conversationMode) return
        conversationPaused = true
        stopListening()
        try { tts?.stop() } catch (_: Exception) {}
        setPhase(AgentPhase.IDLE, "مکث")
        refreshNotification()
        startWakeWordIfIdle()
    }

    private fun resumeConversation() {
        if (!conversationMode) { startConversation(); return }
        conversationPaused = false
        silentErrors = 0
        refreshNotification()
        startListening()
    }

    private fun endConversation() {
        conversationMode = false
        conversationPaused = false
        stopListening()
        stopSpeaking()
        setPhase(AgentPhase.IDLE)
        refreshNotification()
        startWakeWordIfIdle()
    }

    // ───────── کلمه بیداری «فوکو» ─────────

    private fun startWakeWordIfIdle() {
        if (!voicePrefs.wakeWordEnabled) { if (!listeningOffline) VoskEngine.stop(); return }
        if (conversationMode && !conversationPaused) return
        if (busy || phase == AgentPhase.SPEAKING || phase == AgentPhase.LISTENING) return
        if (!VoskEngine.isModelReady(applicationContext)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        VoskEngine.startWakeWord(applicationContext, onWake = {
            appendLog("فوکو", "جانم؟")
            startConversation()
        }, onError = { mainHandler.postDelayed({ startWakeWordIfIdle() }, 5000) })
    }

    // ───────── توضیح زنده صفحه ─────────

    private fun registerScreenListener() {
        if (!voicePrefs.liveExplainEnabled) {
            FokoAccessibilityService.screenChangeListener = null
            return
        }
        FokoAccessibilityService.screenChangeListener = { pkg: String, _: String ->
            mainHandler.post {
                if (pkg != lastExplainedPackage) {
                    mainHandler.removeCallbacks(liveExplainRunnable)
                    pendingExplainPackage = pkg
                    mainHandler.postDelayed(liveExplainRunnable, 2500)
                }
            }
        }
    }

    private var pendingExplainPackage = ""

    private fun runLiveExplain() {
        if (!voicePrefs.liveExplainEnabled || busy || phase == AgentPhase.LISTENING || phase == AgentPhase.SPEAKING) return
        lastExplainedPackage = pendingExplainPackage
        sendMessage(
            "صفحه گوشی عوض شد. با ابزار read_screen صفحه را ببین و در حداکثر دو جمله کوتاه بگو روی صفحه چه چیزی هست و اگر متن خارجی است ترجمه کن.",
            showInLog = false
        )
    }

    private fun toggleLiveExplain() {
        voicePrefs.liveExplainEnabled = !voicePrefs.liveExplainEnabled
        registerScreenListener()
        val msg = if (voicePrefs.liveExplainEnabled) "توضیح زنده صفحه روشن شد" else "توضیح زنده صفحه خاموش شد"
        appendLog("فوکو", msg)
        if (voicePrefs.liveExplainEnabled && !FokoAccessibilityService.isRunning()) {
            appendLog("فوکو", "برای این کار باید سرویس دسترسی‌پذیری فوکو روشن باشد.")
        }
    }

    // ───────── خواندن پیام‌های تازه ─────────

    private val messagingPackages = setOf(
        "org.telegram.messenger", "org.telegram.plus", "org.thunderdog.challegram", "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "ir.eitaa.messenger", "ir.nasim", "mobi.mmdt.ottplus", "com.instagram.android", "com.bale.messenger"
    )

    private fun registerNotificationListener() {
        FokoNotificationListener.listener = { n ->
            mainHandler.post {
                if (conversationMode && !conversationPaused && !busy &&
                    phase != AgentPhase.SPEAKING && n.packageName in messagingPackages
                ) {
                    val app = FokoNotificationListener.appLabel(n.packageName)
                    val line = "پیام تازه در $app از ${n.title}: ${n.text.take(200)}. می‌خوای جواب بدی؟"
                    stopListening()
                    appendLog("فوکو", line)
                    transcript.add(ChatMessage(sender = MessageSender.ASSISTANT, text = line))
                    beginReplySpeech()
                    speakChunk(line, isFinal = true)
                }
            }
        }
    }

    /** بعد از جواب یا سکوت، اگر مکالمه فعال است دوباره گوش بده. */
    private fun continueConversationIfActive(delayMs: Long = 250) {
        if (conversationMode && !conversationPaused && !busy) {
            mainHandler.postDelayed({
                if (conversationMode && !conversationPaused && !busy) startListening()
            }, delayMs)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun buildUi() {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(180)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        params = p
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM or Gravity.START
        }
        container.addView(buildOrb())
        container.addView(buildPanel())
        root = container
        try { windowManager?.addView(container, p) } catch (_: Exception) {}
    }

    private fun buildOrb(): View {
        val wrapper = FrameLayout(this)
        val size = dp(ORB_SIZE_DP)
        wrapper.layoutParams = LinearLayout.LayoutParams(size, size)
        val view = OrbView(this)
        wrapper.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        orbView = view
        val glyph = TextView(this).apply {
            text = "\uD83C\uDF99"
            setTextColor(AColor.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        }
        wrapper.addView(glyph, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        orbGlyph = glyph
        var startX = 0; var startY = 0; var downRawX = 0f; var downRawY = 0f; var moved = false; var downTime = 0L
        wrapper.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; downRawX = event.rawX; downRawY = event.rawY
                    moved = false; downTime = System.currentTimeMillis(); isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) { moved = true; isDragging = true }
                    if (moved) {
                        lp.x = startX + dx; lp.y = startY + dy
                        try { root?.let { windowManager?.updateViewLayout(it, lp) } } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val held = System.currentTimeMillis() - downTime
                    if (!moved) {
                        if (phase == AgentPhase.SPEAKING) interruptAndListen()
                        else if (held > 550) toggleHidden() else togglePanel()
                    } else snapToEdge()
                    mainHandler.postDelayed({ isDragging = false }, 80); true
                }
                else -> false
            }
        }
        orb = wrapper
        return wrapper
    }

    private fun buildPanel(): View {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AColor.parseColor("#F20B1220"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8); gravity = Gravity.BOTTOM
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "FOKO AI"; setTextColor(AColor.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusText = TextView(this).apply {
            text = "آماده"; setTextColor(AColor.parseColor("#94A3B8")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        header.addView(title); header.addView(statusText)
        header.addView(miniButton("\u2013") { toggleHidden() })
        header.addView(miniButton("\u2715") { stopSelf() })
        p.addView(header)
        logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
            isFillViewport = true
        }
        logText = TextView(this).apply {
            setTextColor(AColor.parseColor("#E2E8F0")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        logScroll?.addView(logText); p.addView(logScroll)
        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0)
        }
        quick.addView(chip("صفحه") { sendMessage("محتوای همین صفحه را بخوان و تحلیل کن") })
        quick.addView(chip("مرورگر") { sendMessage("آخرین خبرهای مهم امروز را از وب جستجو کن") })
        quick.addView(chip("گیت‌هاب") { sendMessage("وضعیت اتصال گیت‌هاب من را بررسی کن") })
        quick.addView(chip("توضیح زنده") { toggleLiveExplain() })
        quick.addView(chip("اعلان‌ها") { sendMessage("اعلان‌ها و پیام‌های تازه گوشی را بخوان و خلاصه کن") })
        p.addView(quick)
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0)
        }
        inputField = EditText(this).apply {
            hint = "پیام یا دستور..."; setHintTextColor(AColor.parseColor("#64748B"))
            setTextColor(AColor.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { submitInput(); true } else false
            }
        }
        inputRow.addView(inputField)
        inputRow.addView(miniButton("\uD83C\uDFA4") { startListening() })
        inputRow.addView(miniButton("\u27A4") { submitInput() })
        p.addView(inputRow)
        panel = p
        return p
    }

    private fun miniButton(label: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = label; setTextColor(AColor.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER; setPadding(dp(8), dp(4), dp(8), dp(4)); setOnClickListener { onClick() }
        }

    private fun chip(label: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = label; setTextColor(AColor.parseColor("#7DD3FC")); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setBackgroundColor(AColor.parseColor("#1E293B")); setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) }
        }

    private fun togglePanel() {
        expanded = !expanded
        if (expanded) {
            if (hidden) setHidden(false)
            panel?.visibility = View.VISIBLE
            params?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            clampIntoView(); inputField?.requestFocus()
        } else {
            panel?.visibility = View.GONE
            params?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            hideKeyboard()
        }
        updateLayout()
    }

    private fun toggleHidden() { if (expanded) togglePanel(); setHidden(!hidden) }

    private fun setHidden(value: Boolean) {
        hidden = value
        val size = if (hidden) dp(HIDDEN_SIZE_DP) else dp(ORB_SIZE_DP)
        orb?.layoutParams = LinearLayout.LayoutParams(size, size)
        orb?.alpha = if (hidden) 0.4f else 1f
        orbGlyph?.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (hidden) 12f else 22f)
        updateLayout()
    }

    private fun clampIntoView() {
        val lp = params ?: return
        val metrics = resources.displayMetrics
        val maxX = metrics.widthPixels - dp(300) - dp(ORB_SIZE_DP) - dp(16)
        if (lp.x > maxX) lp.x = maxOf(0, maxX)
        if (lp.y > metrics.heightPixels - dp(260)) lp.y = maxOf(0, metrics.heightPixels - dp(260))
    }

    private fun snapToEdge() {
        val lp = params ?: return
        val metrics = resources.displayMetrics
        lp.x = if (lp.x + dp(ORB_SIZE_DP) / 2 < metrics.widthPixels / 2) dp(8) else metrics.widthPixels - dp(ORB_SIZE_DP) - dp(8)
        lp.y = lp.y.coerceIn(0, metrics.heightPixels - dp(ORB_SIZE_DP))
        updateLayout()
    }

    private fun updateLayout() {
        try { val lp = params ?: return; root?.let { windowManager?.updateViewLayout(it, lp) } } catch (_: Exception) {}
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(root?.windowToken, 0)
    }

    private fun setPhase(newPhase: AgentPhase, message: String? = null) {
        phase = newPhase
        val color = when (newPhase) {
            AgentPhase.LISTENING -> AColor.parseColor("#22C55E")
            AgentPhase.THINKING -> AColor.parseColor("#EF4444")
            AgentPhase.SPEAKING -> AColor.parseColor("#3B82F6")
            AgentPhase.ERROR -> AColor.parseColor("#F59E0B")
            AgentPhase.IDLE -> AColor.parseColor("#0F766E")
        }
        orbView?.setStateColor(color, newPhase == AgentPhase.LISTENING || newPhase == AgentPhase.SPEAKING)
        orbGlyph?.text = when (newPhase) {
            AgentPhase.LISTENING -> "\uD83D\uDC42"
            AgentPhase.THINKING -> "\uD83E\uDDE0"
            AgentPhase.SPEAKING -> "\uD83D\uDD0A"
            AgentPhase.ERROR -> "\u26A0"
            AgentPhase.IDLE -> "\uD83C\uDF99"
        }
        if (conversationMode && lastNotifiedPhase != newPhase) {
            lastNotifiedPhase = newPhase
            refreshNotification()
        }
        statusText?.text = message ?: when (newPhase) {
            AgentPhase.LISTENING -> "در حال شنیدن..."
            AgentPhase.THINKING -> "در حال تفکر..."
            AgentPhase.SPEAKING -> "در حال پاسخ..."
            AgentPhase.ERROR -> "خطا"
            AgentPhase.IDLE -> "آماده"
        }
    }

    private var lastNotifiedPhase: AgentPhase? = null

    private fun refreshNotification() {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification())
        } catch (_: Exception) {}
    }

    private fun appendLog(who: String, text: String) {
        val current = logText?.text?.toString().orEmpty()
        logText?.text = (current + "\n$who: $text").trim()
        mainHandler.postDelayed({ logScroll?.fullScroll(View.FOCUS_DOWN) }, 60)
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val res = tts?.setLanguage(Locale("fa", "IR"))
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale("fa"))
                    tts?.setSpeechRate(0.95f); tts?.setPitch(1.0f)
                } catch (_: Exception) {}
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { mainHandler.post { onUtteranceFinished() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { mainHandler.post { onUtteranceFinished() } }
                })
            }
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { setPhase(AgentPhase.LISTENING, "در حال شنیدن...") }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { setPhase(AgentPhase.THINKING, "در حال تشخیص...") }
                    override fun onError(error: Int) {
                        setPhase(AgentPhase.IDLE)
                        if (conversationMode && !conversationPaused) {
                            val silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            if (silent) silentErrors++ else silentErrors += 2
                            if (silentErrors >= MAX_SILENT_RETRIES) {
                                appendLog("فوکو", "صدایی نشنیدم؛ مکالمه مکث شد. از اعلان «ادامه» را بزن.")
                                pauseConversation()
                            } else {
                                continueConversationIfActive(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900 else 400)
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) { silentErrors = 0; sendMessage(text) }
                        else { setPhase(AgentPhase.IDLE); continueConversationIfActive() }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (_: Exception) {}
    }

    private fun startListening() {
        if (busy) { Toast.makeText(this, "فوکو هنوز مشغول است...", Toast.LENGTH_SHORT).show(); return }
        stopSpeaking()
        VoskEngine.stop()
        if (voicePrefs.offlineSttEnabled && VoskEngine.isModelReady(applicationContext)) {
            startOfflineListening(); return
        }
        val r = recognizer ?: run {
            if (VoskEngine.isModelReady(applicationContext)) { startOfflineListening(); return }
            Toast.makeText(this, "تشخیص گفتار در دسترس نیست", Toast.LENGTH_SHORT).show(); return
        }
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            setPhase(AgentPhase.LISTENING, "در حال شنیدن..."); r.startListening(intent)
        } catch (_: Exception) {
            setPhase(AgentPhase.IDLE); Toast.makeText(this, "شروع شنیدن ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopListening() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        if (listeningOffline) { listeningOffline = false; VoskEngine.stop() }
    }

    /** تشخیص آفلاین فارسی با Vosk (بدون گوگل و بدون اینترنت). */
    private fun startOfflineListening() {
        listeningOffline = true
        setPhase(AgentPhase.LISTENING, "در حال شنیدن (آفلاین)...")
        val ok = VoskEngine.recognizeOnce(
            applicationContext,
            onPartial = { partial -> statusText?.text = partial.takeLast(40) },
            onResult = { text ->
                listeningOffline = false
                if (text.isNotBlank()) { silentErrors = 0; sendMessage(text) }
                else {
                    setPhase(AgentPhase.IDLE)
                    if (conversationMode && !conversationPaused) {
                        silentErrors++
                        if (silentErrors >= MAX_SILENT_RETRIES) {
                            appendLog("فوکو", "صدایی نشنیدم؛ مکالمه مکث شد. از اعلان «ادامه» را بزن.")
                            pauseConversation()
                        } else continueConversationIfActive()
                    } else startWakeWordIfIdle()
                }
            },
            onError = { msg ->
                listeningOffline = false
                setPhase(AgentPhase.ERROR, msg)
                if (conversationMode && !conversationPaused) { silentErrors += 2; continueConversationIfActive(1000) }
            }
        )
        if (!ok) listeningOffline = false
    }

    // ───────── گفتار: صدای گوشی یا صدای طبیعی، جمله به جمله، با قابلیت قطع کردن ─────────

    /** آماده‌سازی برای یک پاسخ تازه. */
    private fun beginReplySpeech() {
        stopSpeaking()
        pendingUtterances = 0
        finalQueued = false
        sentenceBuffer.setLength(0)
        streamedText = StringBuilder()
    }

    private fun stopSpeaking() {
        try { tts?.stop() } catch (_: Exception) {}
        NaturalTts.stop()
        bargeIn?.stop()
        pendingUtterances = 0
        finalQueued = false
    }

    /** تکه‌های استریم را جمع می‌کند و هر جمله کامل را همان لحظه می‌خواند. */
    private fun onStreamDelta(piece: String) {
        if (voicePrefs.naturalVoiceEnabled) return // صدای طبیعی کل جواب را یکجا می‌سازد
        streamedText.append(piece)
        sentenceBuffer.append(piece)
        val text = sentenceBuffer.toString()
        val cut = text.indexOfLast { it == '.' || it == '!' || it == '?' || it == '؟' || it == '\n' || it == '؛' }
        if (cut >= 0 && cut >= 12) {
            val sentence = text.substring(0, cut + 1)
            sentenceBuffer.delete(0, cut + 1)
            speakChunk(sentence, isFinal = false)
        }
    }

    /** خواندن کل پاسخ نهایی (یا باقی‌مانده‌ای که هنوز خوانده نشده). */
    private fun speak(text: String) {
        val alreadyStreamed = streamedText.toString()
        // اگر جواب استریم شده، فقط بخشی که هنوز خوانده نشده را بخوان (بدون تکرار)
        val remaining = if (alreadyStreamed.isBlank()) text else sentenceBuffer.toString()
        sentenceBuffer.setLength(0)
        if (voicePrefs.naturalVoiceEnabled && NaturalTts.isAvailable(ApiKeyStore(applicationContext))) {
            speakNatural(text)
        } else {
            speakChunk(remaining, isFinal = true)
        }
    }

    private fun speakChunk(text: String, isFinal: Boolean) {
        val engine = tts
        val cleaned = SpeechTextCleaner.forSpeech(text)
        if (engine == null || !ttsReady || cleaned.isBlank()) {
            if (isFinal) {
                finalQueued = true
                if (pendingUtterances <= 0) { finalQueued = false; afterSpeech() }
            }
            return
        }
        try { engine.language = SpeechTextCleaner.preferredLocale(cleaned) } catch (_: Exception) {}
        if (phase != AgentPhase.SPEAKING) {
            setPhase(AgentPhase.SPEAKING, "در حال پاسخ صوتی...")
            startBargeIn()
        }
        pendingUtterances++
        if (isFinal) finalQueued = true
        engine.speak(cleaned, TextToSpeech.QUEUE_ADD, null, "foko_${utteranceCounter++}")
    }

    private fun onUtteranceFinished() {
        pendingUtterances = maxOf(0, pendingUtterances - 1)
        if (pendingUtterances == 0 && finalQueued) {
            finalQueued = false
            afterSpeech()
        }
    }

    private fun speakNatural(text: String) {
        val cleaned = SpeechTextCleaner.forSpeech(text)
        if (cleaned.isBlank()) { afterSpeech(); return }
        setPhase(AgentPhase.SPEAKING, "در حال ساخت صدا...")
        scope.launch {
            val file = NaturalTts.synthesize(applicationContext, cleaned)
            if (file == null) {
                // اگر صدای طبیعی نشد، با صدای گوشی بخوان
                speakChunk(cleaned, isFinal = true)
                return@launch
            }
            setPhase(AgentPhase.SPEAKING, "در حال پاسخ صوتی...")
            startBargeIn()
            NaturalTts.play(file) { mainHandler.post { afterSpeech() } }
        }
    }

    private fun startBargeIn() {
        if (!voicePrefs.bargeInEnabled || !conversationMode || conversationPaused) return
        bargeIn?.start { interruptAndListen() }
    }

    /** کاربر وسط حرف فوکو صحبت کرد یا روی دایره زد: ساکت شو و گوش بده. */
    private fun interruptAndListen() {
        stopSpeaking()
        setPhase(AgentPhase.LISTENING, "گوش می‌دهم...")
        mainHandler.postDelayed({ if (!busy) startListening() }, 350)
    }

    /** بعد از تمام شدن گفتار. */
    private fun afterSpeech() {
        bargeIn?.stop()
        when {
            conversationMode && !conversationPaused -> {
                setPhase(AgentPhase.LISTENING, "دوباره گوش می‌دهم...")
                continueConversationIfActive(350)
            }
            ConnectorStore(applicationContext).isContinuousVoiceEnabled() && expanded -> {
                setPhase(AgentPhase.LISTENING, "دوباره گوش می‌دهم...")
                mainHandler.postDelayed({ startListening() }, 350)
            }
            else -> { setPhase(AgentPhase.IDLE); startWakeWordIfIdle() }
        }
    }

    private fun submitInput() {
        val text = inputField?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        inputField?.setText(""); sendMessage(text)
    }

    private fun sendMessage(text: String, showInLog: Boolean = true) {
        if (busy) { Toast.makeText(this, "فوکو هنوز در حال پردازش است...", Toast.LENGTH_SHORT).show(); return }
        busy = true
        VoskEngine.stop()
        beginReplySpeech()
        if (showInLog) appendLog("شما", text)
        transcript.add(ChatMessage(sender = MessageSender.USER, text = text))
        setPhase(AgentPhase.THINKING, "در حال تفکر..."); hideKeyboard()
        scope.launch {
            try {
                val keyStore = ApiKeyStore(applicationContext)
                val catalog = AiRouter.pickDefault(keyStore)
                val reply = AgentEngine.run(
                    context = applicationContext,
                    userText = text,
                    history = transcript.dropLast(1),
                    catalogModel = catalog,
                    keyStore = keyStore,
                    personaPrompt = SystemPersona.GENERAL.systemPrompt,
                    onDelta = { piece -> mainHandler.post { onStreamDelta(piece) } }
                ) { event ->
                    when (event) {
                        is AgentEvent.Status -> mainHandler.post { setPhase(event.phase, event.message) }
                        is AgentEvent.ToolUsed -> mainHandler.post { setPhase(AgentPhase.THINKING, "ابزار: ${event.name}") }
                        is AgentEvent.Failure -> mainHandler.post { setPhase(AgentPhase.ERROR, event.message) }
                        // اگر وسط کار ابزار اجرا شد، هر چه استریم شده متعلق به یک مرحله میانی بود

                        is AgentEvent.Reply -> Unit
                    }
                }
                transcript.add(ChatMessage(sender = MessageSender.ASSISTANT, text = reply))
                mainHandler.post { appendLog("فوکو", reply); busy = false; speak(reply) }
            } catch (e: Exception) {
                mainHandler.post {
                    appendLog("فوکو", "خطا: ${e.localizedMessage}")
                    setPhase(AgentPhase.ERROR, e.localizedMessage); busy = false
                    continueConversationIfActive(1200)
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "foko_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "FOKO Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(if (conversationMode) "فوکو — مکالمه دوطرفه" else "FOKO AI")
            .setContentText(notificationText())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (conversationMode) {
            if (conversationPaused) builder.addAction(0, "ادامه", actionIntent(ACTION_RESUME, 1))
            else builder.addAction(0, "مکث", actionIntent(ACTION_PAUSE, 2))
            builder.addAction(0, "پایان", actionIntent(ACTION_END, 3))
        } else {
            builder.addAction(0, "شروع مکالمه", actionIntent(ACTION_CONVERSATION, 4))
        }
        return builder.build()
    }

    private fun notificationText(): String = when {
        !conversationMode -> "دستیار شناور فعال است"
        conversationPaused -> "مکث شده"
        phase == AgentPhase.LISTENING -> "در حال شنیدن..."
        phase == AgentPhase.THINKING -> "در حال فکر کردن..."
        phase == AgentPhase.SPEAKING -> "در حال جواب دادن..."
        else -> "آماده"
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, FokoFloatingOverlayService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    override fun onDestroy() {
        FokoAccessibilityService.screenChangeListener = null
        FokoNotificationListener.listener = null
        mainHandler.removeCallbacks(liveExplainRunnable)
        VoskEngine.stop()
        stopSpeaking()
        try { recognizer?.destroy() } catch (_: Exception) {}
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        try { root?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        scope.cancel(); super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val ORB_SIZE_DP = 56
        private const val HIDDEN_SIZE_DP = 22
        private const val MAX_SILENT_RETRIES = 4

        const val ACTION_CONVERSATION = "com.example.foco.action.CONVERSATION"
        const val ACTION_PAUSE = "com.example.foco.action.PAUSE"
        const val ACTION_RESUME = "com.example.foco.action.RESUME"
        const val ACTION_END = "com.example.foco.action.END"
        const val ACTION_REFRESH_SETTINGS = "com.example.foco.action.REFRESH_SETTINGS"

        /** بعد از تغییر تنظیمات صوتی، اگر دستیار در حال اجراست آن‌ها را اعمال کن. */
        fun refreshSettings(context: Context) {
            try {
                context.startService(Intent(context, FokoFloatingOverlayService::class.java).setAction(ACTION_REFRESH_SETTINGS))
            } catch (_: Exception) {}
        }

        /** شروع مکالمه دوطرفه در دستیار شناور (بیرون از اپ هم ادامه دارد). */
        fun startConversation(context: Context) {
            val intent = Intent(context, FokoFloatingOverlayService::class.java).setAction(ACTION_CONVERSATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun start(context: Context) {
            val intent = Intent(context, FokoFloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FokoFloatingOverlayService::class.java))
        }
    }

    private class OrbView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.parseColor("#0F766E"); style = Paint.Style.FILL
        }
        private var scale = 1f
        private var animator: ValueAnimator? = null

        fun setStateColor(color: Int, animate: Boolean) {
            paint.color = color
            animator?.cancel()
            if (animate) {
                animator = ValueAnimator.ofFloat(0.92f, 1.08f).apply {
                    duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { scale = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else { scale = 1f; invalidate() }
        }

        override fun onDraw(canvas: Canvas) {
            val r = (minOf(width, height) / 2f) * 0.86f * scale
            canvas.drawCircle(width / 2f, height / 2f, r, paint)
        }
    }
}
