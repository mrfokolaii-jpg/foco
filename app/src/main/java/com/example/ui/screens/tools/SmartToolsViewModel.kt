package com.example.ui.screens.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ApiKeyStore
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.remote.AiRouter
import com.example.data.remote.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ToolType(
    val id: String,
    val persianTitle: String,
    val persianDescription: String,
    val inputPlaceholder: String,
    val iconName: String,
    val systemPrompt: String
) {
    DEVICE_AUTOMATION(
        id = "device_automation",
        persianTitle = "اتوماسیون دستگاه (JARVIS-1)",
        persianDescription = "تولید دستورات کنترلی دستگاه، لمس صفحه، پیام و مدیریت اپلیکیشن‌ها",
        inputPlaceholder = "دستور خود را بنویسید (مثلاً: پیامک به علی ارسال کن، صفحه را اسکرول کن)...",
        iconName = "SmartToy",
        systemPrompt = "شما موتور کنترل دستگاه JARVIS-1 فوکو هستید. درخواست کاربر را تحلیل کنید و گام‌های دقیق اجرای آن روی سیستم اندروید و دستورات مناسب را با زبان فارسی دقیق و واضح توضیح و ارائه دهید."
    ),
    OPENCODE_DEVELOPER(
        id = "opencode_dev",
        persianTitle = "توسعه‌دهنده OpenCode (JARVIS-3)",
        persianDescription = "تحلیل معماری، کدنویسی، دیباگ عمیق و ریفکتورینگ پروژه‌ها",
        inputPlaceholder = "کد یا سوال تخصصی توسعه نرم‌افزار را اینجا وارد کنید...",
        iconName = "Code",
        systemPrompt = "شما موتور تخصصی OpenCode در JARVIS-3 فوکو هستید. کدهای بسیار تمیز، مدرن، کارآمد و مستندسازی‌شده به زبان فارسی با توضیحات خط‌به‌خط فنی ارائه دهید."
    ),
    MCP_DISPATCHER(
        id = "mcp_dispatch",
        persianTitle = "ارتباط با ابزارها و وب (JARVIS-2)",
        persianDescription = "اجرای ابزارهای MCP، پردازش دستورات شل، استخراج اطلاعات و جستجو",
        inputPlaceholder = "هدف یا ابزار مورد نظر را وارد نمایید...",
        iconName = "Hub",
        systemPrompt = "شما لایه MCP Dispatcher در JARVIS-2 فوکو هستید. راهکار اجرای ابزارهای خارجی، وب و اتوماسیون فرایندها را با دقت ساختاریافته تحلیل و مشخص کنید."
    ),
    EDITOR(
        id = "editor",
        persianTitle = "ویراستار متون فارسی",
        persianDescription = "اصلاح غلط‌های املایی، ویرایش نیم‌فاصله‌ها، علائم نگارشی و روان‌سازی جملات",
        inputPlaceholder = "متن فارسی خود را برای ویرایش و پیرایش اینجا بچسبانید...",
        iconName = "Edit",
        systemPrompt = "شما یک ویراستار ارشد و استاد دستور زبان فارسی هستید. متن ارسالی کاربر را از نظر املایی، نشانه‌گذاری، نیم‌فاصله‌ها و روانی ویرایش کنید. متن ویرایش‌شده نهایی را در ابتدا ارائه داده و سپس به صورت موردی نکات اصلاح شده را توضیح دهید."
    ),
    POET(
        id = "poet",
        persianTitle = "سرایش شعر و ادب پارسی",
        persianDescription = "سرودن غزل، دوبیتی، رباعی، مثنوی حماسی یا شعر نو با وزن و قافیه دقیق",
        inputPlaceholder = "موضوع، قالب یا حس شعر مورد نظر خود را بنویسید (مثلاً: یک غزل عاشقانه در وصف بهار و شکوفه‌ها)...",
        iconName = "HistoryEdu",
        systemPrompt = "شما یک شاعر برجسته پارسی‌سرا با تسلط بر اوزان عروضی، صنایع ادبی و قافیه هستید. شعری بسیار زیبا، موزون و پرمعنا با واژگان اصیل فارسی بسرایید و قالب شعری را در ابتدای آن ذکر کنید."
    ),
    SUMMARIZER(
        id = "summarizer",
        persianTitle = "خلاصه‌ساز مقالات و متون",
        persianDescription = "چکیده‌سازی سریع متون طولانی با استخراج نکات کلیدی و نتیجه‌گیری",
        inputPlaceholder = "متن طولانی یا مقاله خود را برای خلاصه‌سازی وارد کنید...",
        iconName = "Summarize",
        systemPrompt = "شما یک متخصص خلاصه‌سازی و تحلیل محتوا هستید. متن داده‌شده را با رعایت امانت به زبان فارسی شیوا در قالب: ۱) چکیده یک‌بندی ۲) مهم‌ترین نکات کلیدی به صورت بالت‌پوینت ۳) نتیجه‌گیری نهایی خلاصه کنید."
    ),
    TRANSLATOR(
        id = "translator",
        persianTitle = "مترجم طلایی به فارسی سلیس",
        persianDescription = "ترجمه حرفه‌ای و مفهومی متون انگلیسی یا دیگر زبان‌ها به فارسی فصیح",
        inputPlaceholder = "متن زبان خارجی را برای ترجمه به فارسی روان وارد کنید...",
        iconName = "Translate",
        systemPrompt = "شما یک مترجم فوق‌حرفه‌ای و زبردست هستید. متن ورودی را نه به صورت کلمه‌به‌کلمه، بلکه با درک کامل اصطلاحات و لحن، به زبان فارسی معیار، فصیح، سلیس و زیبا ترجمه کنید."
    )
}

data class SmartToolsUiState(
    val selectedTool: ToolType = ToolType.DEVICE_AUTOMATION,
    val inputText: String = "",
    val outputText: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SmartToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore = ApiKeyStore(application)
    private val _uiState = MutableStateFlow(SmartToolsUiState())
    val uiState: StateFlow<SmartToolsUiState> = _uiState.asStateFlow()

    fun selectTool(tool: ToolType) {
        _uiState.value = _uiState.value.copy(
            selectedTool = tool,
            outputText = null,
            errorMessage = null
        )
    }

    fun setInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun runTool() {
        val input = _uiState.value.inputText.trim()
        if (input.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "لطفاً متن ورودی را وارد نمایید.")
            return
        }

        val tool = _uiState.value.selectedTool
        val activeModel = AiRouter.pickDefault(keyStore)

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            outputText = null
        )

        viewModelScope.launch {
            val messages = listOf(
                ChatMessage(
                    sender = MessageSender.USER,
                    text = input
                )
            )

            val result = AiRouter.chat(
                catalogModel = activeModel,
                conversationHistory = messages,
                systemInstructionText = tool.systemPrompt,
                keyStore = keyStore
            )

            when (result) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        outputText = result.data.replyText
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }
}
