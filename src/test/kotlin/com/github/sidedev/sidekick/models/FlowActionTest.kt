package com.github.sidedev.sidekick.models

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowActionTest {
    @Test
    fun testInstantSerialization() {
        // Given a FlowAction with specific timestamps
        val created = Instant.parse("2023-01-01T12:00:00Z")
        val updated = Instant.parse("2023-01-01T13:00:00Z")
        val action = FlowAction(
            id = "test-id",
            flowId = "test-flow",
            workspaceId = "test-workspace",
            created = created,
            updated = updated,
            actionType = "test-type",
            actionParams = mapOf("param1" to JsonPrimitive("value1")),
            actionStatus = ActionStatus.PENDING,
            actionResult = "test-result",
            isHumanAction = false
        )

        // When serializing to JSON and back
        val json = Json.encodeToString(action)
        val decoded = Json.decodeFromString<FlowAction>(json)

        // Then the timestamps should match
        assertEquals(created, decoded.created)
        assertEquals(updated, decoded.updated)
        
        // And verify the actual JSON contains ISO8601 strings
        assert(json.contains("\"2023-01-01T12:00:00Z\"")) { "JSON should contain ISO8601 created timestamp" }
        assert(json.contains("\"2023-01-01T13:00:00Z\"")) { "JSON should contain ISO8601 updated timestamp" }
    }
}