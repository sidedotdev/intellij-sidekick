package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SubflowSummaryComponentTest : BasePlatformTestCase() {

    private lateinit var component: SubflowSummaryComponent

    // Helper to create a FlowAction for tests
    private fun createFlowAction(
        actionType: String = "generic_action",
        actionStatus: ActionStatus = ActionStatus.STARTED,
        updated: Instant = Clock.System.now()
    ): FlowAction {
        return FlowAction(
            id = "action-${Clock.System.now().toEpochMilliseconds()}",
            flowId = "flow1",
            subflowId = "subflow1",
            workspaceId = "ws1",
            created = Clock.System.now(),
            updated = updated,
            actionType = actionType,
            actionParams = emptyMap(),
            actionStatus = actionStatus,
            actionResult = "",
            isHumanAction = false
        )
    }

    // Helper to create a Subflow for tests
    private fun createSubflow(
        status: SubflowStatus,
        type: String = "code_context"
    ): Subflow {
        return Subflow(
            workspaceId = "ws1",
            id = "subflow1",
            name = "Test Code Context Subflow",
            type = type,
            description = "Generating code context",
            status = status,
            flowId = "flow1"
        )
    }

    override fun setUp() {
        super.setUp()
        component = SubflowSummaryComponent() // Assumes default constructor or DI if any
    }

    fun `test initial state matches STARTED with no specific action`() {
        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
        // Assuming loadingIcon is part of secondaryContentPanel and visible when panel is visible
        assertTrue(component.loadingIconContainer.isVisible) // Assuming loadingIcon is in a JBLabel named loadingIconContainer
        assertEquals(AnimatedIcon.Default::class.java, component.loadingIconContainer.icon::class.java)
    }

    fun `test update with SubflowStatus STARTED and null action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        component.update(null, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
        assertTrue(component.loadingIconContainer.isVisible)
        assertEquals(AnimatedIcon.Default::class.java, component.loadingIconContainer.icon::class.java)
    }

    fun `test test update with SubflowStatus STARTED and non-tool_call action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "some_processing_step")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
    }

    fun `test update with SubflowStatus STARTED and tool_call action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "tool_call.bulk_search_repository")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Bulk search repository", component.secondaryLabel.text)
    }

    fun `test update with SubflowStatus STARTED and another tool_call action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "tool_call.another_tool_example")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Another tool example", component.secondaryLabel.text)
    }
    
    fun `test update with SubflowStatus STARTED and tool_call action with single word`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "tool_call.retrieve")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Retrieve", component.secondaryLabel.text)
    }


    fun `test update with SubflowStatus COMPLETE`() {
        val subflow = createSubflow(status = SubflowStatus.COMPLETE)
        component.update(null, subflow) // Action shouldn't matter for COMPLETE status visibility

        assertEquals("Found Relevant Code", component.primaryLabel.text)
        assertFalse(component.secondaryContentPanel.isVisible)
    }

    fun `test update with SubflowStatus FAILED`() {
        val subflow = createSubflow(status = SubflowStatus.FAILED)
        component.update(null, subflow) // Action shouldn't matter for FAILED status visibility

        assertEquals("Failed to Find Code", component.primaryLabel.text)
        assertFalse(component.secondaryContentPanel.isVisible)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : java.awt.Component> SubflowSummaryComponent.findComponentByName(name: String): T? {
        return this.components.find { it.name == name } as? T
    }
}