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
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The floating FOKO assistant.
 *
 * - Collapsed: a draggable orb whose colour tells the user what FOKO is doing
 *   (green = listening to you, red = thinking, blue = answering, teal = idle).
 * - Expanded: a small chat panel with voice and text, plus one-tap tools.
 * - Long press the orb to hide it into a tiny sliver; tap to bring it back.
 *
 * The widget runs the exact same [AgentEngine] as the in-app chat, so every tool
 * (web search, browser reading, screen vision, GitHub, Hugging Face, Drive, saving
 * files, self-upgrade) works while the user is inside other apps.
 */
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildUi()
        initTts()
        initRecognizer()
        appendLog("فوکو", "سلام! من فوکو هستم. میکروفون را بزن و صحبت کن یا بنویس. هر کاری بخواهی انجام می‌دهم.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ---------------------------------------------------------------- UI ----

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
        try {
            windowManager?.addView(container, p)
        } catch (_: Exception) {
        }
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

        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var moved = false
        var downTime = 0L

        wrapper.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    moved = false
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                        moved = true
                        isDragging = true
                    }
                    if (moved) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        try {
                            root?.let { windowManager?.updateViewLayout(it, lp) }
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val held = System.currentTimeMillis() - downTime
                    if (!moved) {
                        if (held > 550) toggleHidden() else togglePanel()
                    } else {
                        snapToEdge()
                    }
                    mainHandler.postDelayed({ isDragging = false }, 80)
                    true
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
                leftMargin = dp(8)
                gravity = Gravity.BOTTOM
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "FOKO AI"
            setTextColor(AColor.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusText = TextView(this).apply {
            text = "آماده"
            setTextColor(AColor.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        }
        val hideBtn = miniButton("\u2013") { toggleHidden() }
        val closeBtn = miniButton("\u2715") { stopSelf() }

        header.addView(title)
        header.addView(statusText)
        header.addView(hideBtn)
        header.addView(closeBtn)
        p.addView(header)

        logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
            isFillViewport = true
        }
        logText = TextView(this).apply {
            setTextColor(AColor.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        logScroll?.addView(logText)
        p.addView(logScroll)

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        quick.addView(chip("صفحه") { sendMessage("محتوای همین صفحه را بخوان و تحلیل کن") })
        quick.addView(chip("مرورگر") { sendMessage("آخرین خبرهای مهم امروز را از وب جستجو کن") })
        quick.addView(chip("گیت‌هاب") { sendMessage("وضعیت اتصال گیت‌هاب من را بررسی کن") })
        quick.addView(chip("ذخیره") { sendMessage("این گفتگو را در حافظه و گیت‌هاب ذخیره کن") })
        p.addView(quick)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        inputField = EditText(this).apply {
            hint = "پیام یا دستور خود را بنویسید..."
            setHintTextColor(AColor.parseColor("#64748B"))
            setTextColor(AColor.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitInput()
                    true
                } else false
            }
        }
        val micBtn = miniButton("\uD83C\uDFA4") { startListening() }
        val sendBtn = miniButton("\u27A4") { submitInput() }
        inputRow.addView(inputField)
        inputRow.addView(micBtn)
        inputRow.addView(sendBtn)
        p.addView(inputRow)

        panel = p
        return p
    }

    private fun miniButton(label: String, onClick: () -> Unit): View {
        val b = TextView(this).apply {
            text = label
            setTextColor(AColor.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
        }
        return b
    }

    private fun chip(label: String, onClick: () -> Unit): View {
        val b = TextView(this).apply {
            text = label
            setTextColor(AColor.parseColor("#7DD3FC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setBackgroundColor(AColor.parseColor("#1E293B"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(6)
            }
        }
        return b
    }

    private fun togglePanel() {
        expanded = !expanded
        if (expanded) {
            if (hidden) setHidden(false)
            panel?.visibility = View.VISIBLE
            params?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            clampIntoView()
            inputField?.requestFocus()
        } else {
            panel?.visibility = View.GONE
            params?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            hideKeyboard()
        }
        updateLayout()
    }

    private fun toggleHidden() {
        if (expanded) togglePanel()
        setHidden(!hidden)
    }

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
        lp.x = if (lp.x + dp(ORB_SIZE_DP) / 2 < metrics.widthPixels / 2) dp(8)
        else metrics.widthPixels - dp(ORB_SIZE_DP) - dp(8)
        lp.y = lp.y.coerceIn(0, metrics.heightPixels - dp(ORB_SIZE_DP))
        updateLayout()
    }

    private fun updateLayout() {
        try {
            val lp = params ?: return
            root?.let { windowManager?.updateViewLayout(it, lp) }
        } catch (_: Exception) {
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(root?.windowToken, 0)
    }

    // ------------------------------------------------------------- State ----

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
        val label = message ?: when (newPhase) {
            AgentPhase.LISTENING -> "در حال شنیدن..."
            AgentPhase.THINKING -> "در حال تفکر..."
            AgentPhase.SPEAKING -> "در حال پاسخ..."
            AgentPhase.ERROR -> "خطا"
            AgentPhase.IDLE -> "آماده"
        }
        statusText?.text = label
    }

    private fun appendLog(who: String, text: String) {
        val current = logText?.text?.toString().orEmpty()
        val entry = "\n$who: $text"
        logText?.text = (current + entry).trim()
        mainHandler.postDelayed({
            logScroll?.fullScroll(View.FOCUS_DOWN)
        }, 60)
    }

    // ------------------------------------------------------------- Voice ----

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val res = tts?.setLanguage(Locale("fa", "IR"))
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("fa"))
                    }
                    tts?.setSpeechRate(1.0f)
                } catch (_: Exception) {
                }
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (ConnectorStore(applicationContext).isContinuousVoiceEnabled() && expanded) {
                                setPhase(AgentPhase.LISTENING, "دوباره گوش می‌دهم...")
                                startListening()
                            } else {
                                setPhase(AgentPhase.IDLE)
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        mainHandler.post { setPhase(AgentPhase.IDLE) }
                    }
                })
            }
        }
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startListening() {
        if (!hasAudioPermission()) {
            Toast.makeText(this, "برای مکالمه صوتی، مجوز میکروفون را در برنامه فوکو فعال کنید.", Toast.LENGTH_LONG).show()
            return
        }
        if (recognizer == null && SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val sr = recognizer ?: run {
            Toast.makeText(this, "سرویس تشخیص گفتار در این دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                setPhase(AgentPhase.LISTENING, "در حال شنیدن صدای شما...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                setPhase(AgentPhase.THINKING, "در حال پردازش...")
            }

            override fun onError(error: Int) {
                if (busy) return
                setPhase(AgentPhase.IDLE)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty().trim()
                if (spoken.isNotBlank()) sendMessage(spoken) else setPhase(AgentPhase.IDLE)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { statusText?.text = "«$it»" }
            }

            override fun onEvent(eventType: Int, p: Bundle?) {}
        })
        try {
            sr.startListening(intent)
        } catch (_: Exception) {
            setPhase(AgentPhase.IDLE)
        }
    }

    private fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            setPhase(AgentPhase.IDLE)
            return
        }
        setPhase(AgentPhase.SPEAKING, "در حال پاسخ صوتی...")
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "foko_reply")
    }

    // ------------------------------------------------------------ Message ----

    private fun submitInput() {
        val text = inputField?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        inputField?.setText("")
        sendMessage(text)
    }

    private fun sendMessage(text: String) {
        if (busy) {
            Toast.makeText(this, "فوکو هنوز در حال پردازش است...", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        appendLog("شما", text)
        val userMsg = ChatMessage(sender = MessageSender.USER, text = text)
        transcript.add(userMsg)
        setPhase(AgentPhase.THINKING, "در حال تفکر و اجرای ابزارها...")
        hideKeyboard()

        scope.launch(Dispatchers.IO) {
            val keyStore = ApiKeyStore(applicationContext)
            val model = AiRouter.pickDefault(keyStore)
            val persona = SystemPersona.GENERAL.systemPrompt
            val history = transcript.dropLast(1).takeLast(12)
            val reply = AgentEngine.run(
                context = applicationContext,
                userText = text,
                history = history,
                catalogModel = model,
                keyStore = keyStore,
                personaPrompt = persona
            ) { event ->
                when (event) {
                    is AgentEvent.Status -> mainHandler.post { setPhase(event.phase, event.message) }
                    is AgentEvent.ToolUsed -> mainHandler.post {
                        appendLog("ابزار ${event.name}", event.summary)
                    }
                    is AgentEvent.Failure -> mainHandler.post { setPhase(AgentPhase.ERROR, event.message) }
                    is AgentEvent.Reply -> Unit
                }
            }
            transcript.add(ChatMessage(sender = MessageSender.ASSISTANT, text = reply))
            mainHandler.post {
                appendLog("فوکو", reply)
                busy = false
                speak(reply)
            }
        }
    }

    // ------------------------------------------------------------ Lifecycle --

    private fun createNotification(): Notification {
        val channelId = "foko_floating_service"
        val channelName = "ویجت شناور فوکو"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("دستیار شناور فوکو (FOKO AI)")
            .setContentText("مکالمه زنده، بینایی صفحه و ابزارهای هوشمند فعال است.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        scope.cancel()
        if (root != null) {
            try {
                root?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            }
        }
        ConnectorStore(this).setOverlayEnabled(false)
    }

    /** Custom orb that pulses while FOKO is listening or speaking. */
    class OrbView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var color: Int = AColor.parseColor("#0F766E")
        private var pulse = 0f
        private var animator: ValueAnimator? = null

        init {
            paint.style = Paint.Style.FILL
        }

        fun setStateColor(newColor: Int, animate: Boolean) {
            color = newColor
            if (animate) startPulse() else stopPulse()
            invalidate()
        }

        private fun startPulse() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun stopPulse() {
            animator?.cancel()
            animator = null
            pulse = 0f
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val base = minOf(width, height) / 2f - 4f
            val glowRadius = base * (1f + 0.18f * pulse)
            paint.color = color
            paint.alpha = (60 + 80 * pulse).toInt()
            canvas.drawCircle(cx, cy, glowRadius, paint)
            paint.alpha = 255
            canvas.drawCircle(cx, cy, base, paint)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val ORB_SIZE_DP = 58
        private const val HIDDEN_SIZE_DP = 26

        fun start(context: Context) {
            val intent = Intent(context, FokoFloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            ConnectorStore(context).setOverlayEnabled(true)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FokoFloatingOverlayService::class.java))
            ConnectorStore(context).setOverlayEnabled(false)
        }
    }
}
