package com.github.sidedev.sidekick.models.flowEvent

import com.github.sidedev.sidekick.models.ChatMessageDelta
import com.github.sidedev.sidekick.models.ChatRole
import com.github.sidedev.sidekick.models.Usage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowEventTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testProgressTextEventSerialization() {
        val event = ProgressTextEvent(
            text = "Processing...",
            parentId = "parent123"
        )
        val jsonString = json.encodeToString(event as FlowEvent)
        assertEquals("""{"eventType":"progress_text","text":"Processing...","parentId":"parent123"}""", jsonString)
        
        val decoded = json.decodeFromString<FlowEvent>(jsonString)
        assertEquals(event, decoded)
    }

    @Test
    fun testStatusChangeEventSerialization() {
        val event = StatusChangeEvent(
            status = "completed",
            parentId = "parent123"
        )
        val jsonString = json.encodeToString(event as FlowEvent)
        assertEquals("""{"eventType":"status_change","status":"completed","parentId":"parent123"}""".trimMargin(), jsonString)
        
        val decoded = json.decodeFromString<FlowEvent>(jsonString)
        assertEquals(event, decoded)
    }

    @Test
    fun testEndStreamEventSerialization() {
        val event = EndStreamEvent(
            parentId = "parent123"
        )
        val jsonString = json.encodeToString(event as FlowEvent)
        assertEquals("""{"eventType":"end_stream","parentId":"parent123"}""", jsonString)
        
        val decoded = json.decodeFromString<FlowEvent>(jsonString)
        assertEquals(event, decoded)
    }

    @Test
    fun testChatMessageDeltaEventSerialization() {
        val chatMessageDelta = ChatMessageDelta(
            role = ChatRole.ASSISTANT,
            content = "Hello",
            toolCalls = emptyList(),
            usage = Usage(inputTokens = null, outputTokens = null)
        )
        val event = ChatMessageDeltaEvent(
            flowActionId = "action123",
            chatMessageDelta = chatMessageDelta
        )
        val jsonString = json.encodeToString(event as FlowEvent)
        assertEquals("""{"eventType":"chat_message_delta","flowActionId":"action123","chatMessageDelta":{"role":"assistant","content":"Hello","toolCalls":[],"usage":{"inputTokens":null,"outputTokens":null}}}""", jsonString)
        
        val decoded = json.decodeFromString<FlowEvent>(jsonString)
        assertEquals(event, decoded)
    }

    @Test
    fun testCodeDiffEventSerialization() {
        val event = CodeDiffEvent(
            subflowId = "subflow123",
            diff = "- old\n+ new"
        )
        val jsonString = json.encodeToString(event as FlowEvent)
        assertEquals("""{"eventType":"code_diff","subflowId":"subflow123","diff":"- old\n+ new"}""", jsonString)
        
        val decoded = json.decodeFromString<FlowEvent>(jsonString)
        assertEquals(event, decoded)
    }
}