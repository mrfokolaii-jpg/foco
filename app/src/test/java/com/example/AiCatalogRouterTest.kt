package com.example

import com.example.data.model.AiModelCatalog
import com.example.data.model.AiModelOption
import com.example.data.model.AiProviderKind
import com.example.data.model.ChatMessage
import com.example.data.model.ConversationExport
import com.example.data.model.MessageSender
import com.example.data.model.ModelCapability
import com.example.data.remote.ProviderHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCatalogRouterTest {
    @Test
    fun defaultModelIsTheNotConnectedPlaceholder() {
        // هوش مصنوعی داخلی و بی‌کلید فوکو حذف شده است؛ تا وقتی کاربر کلید نگذارد
        // فقط حالت «وصل نشده» نشان داده می‌شود.
        val model = AiModelCatalog.defaultModel()
        assertEquals("not-connected", model.id)
        assertEquals(AiProviderKind.NONE, model.provider)
    }

    @Test
    fun builtinFreeProviderIsGone() {
        assertNull(AiModelCatalog.findById("foko-free"))
        assertTrue(AiModelCatalog.all.none { it.id == "foko-free" })
        assertTrue(AiModelCatalog.all.none { it.provider == AiProviderKind.NONE })
    }

    @Test
    fun onlyGeminiHuggingFaceOfflineAndNoneExist() {
        // کلیدهای Groq / OpenRouter / xAI / 9Router و سرویس‌های دستی حذف شده‌اند.
        assertEquals(
            listOf("NONE", "GEMINI", "HUGGINGFACE", "OFFLINE"),
            AiProviderKind.entries.map { it.name }
        )
    }

    @Test
    fun findByIdReturnsKnownModels() {
        assertNotNull(AiModelCatalog.findById("hf-qwen25"))
        assertNotNull(AiModelCatalog.findById("gemini-flash"))
        assertNull(AiModelCatalog.findById("does-not-exist"))
    }

    @Test
    fun textModelsExcludeImageCapability() {
        val texts = AiModelCatalog.textModels()
        assertTrue(texts.none { it.capability == ModelCapability.IMAGE })
        assertTrue(texts.any { it.provider == AiProviderKind.HUGGINGFACE })
        assertTrue(texts.any { it.provider == AiProviderKind.GEMINI })
    }

    @Test
    fun activeModelsRespectConfiguredKeys() {
        val snapshot = com.example.data.local.ApiKeySnapshot(
            geminiConfigured = true,
            huggingFaceConfigured = false,
            offlineConfigured = false
        )
        val active = AiModelCatalog.activeModels(snapshot)
        assertTrue(active.any { it.provider == AiProviderKind.GEMINI })
        assertFalse(active.any { it.provider == AiProviderKind.HUGGINGFACE })
    }

    @Test
    fun geminiOptionRoundTrip() {
        val flash = AiModelCatalog.fromGeminiOption(AiModelOption.FLASH)
        assertEquals("gemini-flash", flash.id)
        assertEquals(
            AiModelOption.PRO,
            AiModelCatalog.toGeminiOption(AiModelCatalog.findById("gemini-pro")!!)
        )
    }

    @Test
    fun exportPlainTextContainsRoles() {
        val text = ConversationExport.toPlainText(
            title = "آزمایش",
            messages = listOf(
                ChatMessage(sender = MessageSender.USER, text = "سلام"),
                ChatMessage(sender = MessageSender.ASSISTANT, text = "درود")
            )
        )
        assertTrue(text.contains("عنوان گفتگو: آزمایش"))
        assertTrue(text.contains("کاربر"))
        assertTrue(text.contains("فوکو"))
        assertTrue(text.contains("سلام"))
        assertTrue(text.contains("درود"))
    }

    @Test
    fun httpStatusMapsToPersian() {
        assertTrue(ProviderHttp.mapStatus(401, "").contains("کلید نامعتبر"))
        assertTrue(ProviderHttp.mapStatus(404, "").contains("مدل"))
        assertTrue(ProviderHttp.mapStatus(429, "").contains("سهمیه"))
        assertFalse(ProviderHttp.mapStatus(200, "").contains("کلید"))
    }
}
