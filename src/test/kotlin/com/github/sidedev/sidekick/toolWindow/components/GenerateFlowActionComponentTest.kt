package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class GenerateFlowActionComponentTest : BasePlatformTestCase() {

    private lateinit var component: GenerateFlowActionComponent
    private val now: Instant = Clock.System.now()

    private fun createTestAction(
        actionType: String,
        actionStatus: ActionStatus,
        actionResult: String,
        id: String = "test-action",
    ): FlowAction {
        return FlowAction(
            id = id,
            flowId = "test-flow",
            subflowId = "test-subflow",
            workspaceId = "test-ws",
            created = now,
            updated = now,
            actionType = actionType,
            actionParams = emptyMap(),
            actionStatus = actionStatus,
            actionResult = actionResult,
            isHumanAction = false
        )
    }

    override fun setUp() {
        super.setUp()
        // Initial action for component creation, specific test actions will be passed via update()
        val initialAction = createTestAction("initial.type", ActionStatus.PENDING, "Initial result")
        component = GenerateFlowActionComponent(initialAction)
    }

    fun `test update with generate type, complete status, valid JSON with content`() {
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = """{"content":"Generated Content"}"""
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals("Generated Content", component.actionResultLabel.text)
    }

    fun `test update with generate type, complete status, valid JSON with null content`() {
        val actionResultJson = """{"content":null}"""
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = actionResultJson
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals(actionResultJson, component.actionResultLabel.text)
    }

    fun `test update with generate type, complete status, valid JSON without content field`() {
        val actionResultJson = """{"other_field":"Some Value"}"""
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = actionResultJson
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals(actionResultJson, component.actionResultLabel.text)
    }

    fun `test update with generate type, complete status, invalid JSON`() {
        val invalidJson = "not a valid json"
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = invalidJson
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals(invalidJson, component.actionResultLabel.text)
    }

    fun `test update with generate type, complete status, blank actionResult`() {
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = ""
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals("", component.actionResultLabel.text)
    }

    fun `test update with generate type, incomplete status`() {
        val actionResultJson = """{"content":"Content For InProgress"}"""
        val action = createTestAction(
            actionType = "generate.code",
            actionStatus = ActionStatus.STARTED,
            actionResult = actionResultJson
        )
        component.update(action)
        assertEquals("generate.code", component.actionTypeLabel.text)
        assertEquals(actionResultJson, component.actionResultLabel.text)
    }

    fun `test update with non-generate type, complete status`() {
        val actionResultJson = """{"content":"Content For NonGenerate"}"""
        val action = createTestAction(
            actionType = "other.type",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = actionResultJson
        )
        component.update(action)
        assertEquals("other.type", component.actionTypeLabel.text)
        assertEquals(actionResultJson, component.actionResultLabel.text)
    }

    fun `test update sets flowAction property`() {
        val action = createTestAction(
            actionType = "generate.test",
            actionStatus = ActionStatus.COMPLETE,
            actionResult = """{"content":"Test"}"""
        )
        component.update(action)
        assertSame(action, component.flowAction)
    }
}
