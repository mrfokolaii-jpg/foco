package com.example

import com.example.data.agent.AgentEngine
import com.example.data.remote.PageReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for the agent tool protocol and the web page reader. */
class AgentEngineTest {

    @Test
    fun parsesTaggedToolCall() {
        val reply = "باشه، الان جستجو می‌کنم.\n" +
            "<foko_tool>{\"name\":\"web_search\",\"args\":{\"query\":\"هوش مصنوعی\"}}</foko_tool>"
        val call = AgentEngine.parseToolCall(reply)
        assertNotNull(call)
        assertEquals("web_search", call!!.optString("name"))
        assertEquals("هوش مصنوعی", call.optJSONObject("args")!!.optString("query"))
    }

    @Test
    fun parsesBareJsonToolCall() {
        val reply = "{\"name\":\"read_screen\",\"args\":{}}"
        val call = AgentEngine.parseToolCall(reply)
        assertNotNull(call)
        assertEquals("read_screen", call!!.optString("name"))
    }

    @Test
    fun plainReplyIsNotAToolCall() {
        assertNull(AgentEngine.parseToolCall("سلام! چطور می‌توانم کمک کنم؟"))
    }

    @Test
    fun htmlToTextStripsMarkupAndDecodesEntities() {
        val html = "<html><head><style>body{color:red}</style></head>" +
            "<body><h1>عنوان</h1><p>سلام &amp; درود</p><script>alert(1)</script></body></html>"
        val text = PageReader.htmlToText(html)
        assertEquals(true, text.contains("عنوان"))
        assertEquals(true, text.contains("سلام & درود"))
        assertEquals(false, text.contains("alert"))
        assertEquals(false, text.contains("color:red"))
    }

    @Test
    fun normalizeUrlAddsScheme() {
        assertEquals("https://example.com", PageReader.normalizeUrl("example.com"))
        assertEquals("https://example.com/x", PageReader.normalizeUrl("https://example.com/x"))
    }
}
