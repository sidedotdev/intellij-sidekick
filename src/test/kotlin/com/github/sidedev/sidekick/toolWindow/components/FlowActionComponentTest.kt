package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBPanel
import kotlinx.datetime.Instant
import java.util.UUID
import javax.swing.JComponent

class FlowActionComponentTest : UsefulTestCase() {

    private lateinit var flowActionUserRequest: FlowAction
    private lateinit var flowActionUserRequestSpecific: FlowAction
    private lateinit var flowActionOtherType: FlowAction
    private lateinit var flowActionOtherTypeUpdated: FlowAction
    private lateinit var flowActionUserRequestUpdated: FlowAction

    private val completedActionStatus = ActionStatus.COMPLETE

    override fun setUp() {
        super.setUp()

        val fixedInstant = Instant.parse("2023-01-01T00:00:00Z")

        flowActionUserRequest = FlowAction(
            id = UUID.randomUUID().toString(),
            flowId = "flow1",
            workspaceId = "ws1",
            created = fixedInstant,
            updated = fixedInstant,
            actionType = "user_request",
            actionParams = emptyMap(),
            actionStatus = completedActionStatus,
            actionResult = "User request details",
            isHumanAction = true
        )

        flowActionUserRequestSpecific = FlowAction(
            id = UUID.randomUUID().toString(),
            flowId = "flow2",
            workspaceId = "ws1",
            created = fixedInstant,
            updated = fixedInstant,
            actionType = "user_request.specific_action",
            actionParams = emptyMap(),
            actionStatus = completedActionStatus,
            actionResult = "Specific user request details",
            isHumanAction = true
        )

        flowActionOtherType = FlowAction(
            id = UUID.randomUUID().toString(),
            flowId = "flow3",
            workspaceId = "ws1",
            created = fixedInstant,
            updated = fixedInstant,
            actionType = "other_type",
            actionParams = emptyMap(),
            actionStatus = completedActionStatus,
            actionResult = "Result for other type",
            isHumanAction = false
        )

        flowActionOtherTypeUpdated = FlowAction(
            id = flowActionOtherType.id, // Same ID, different result
            flowId = "flow3",
            workspaceId = "ws1",
            created = fixedInstant,
            updated = Instant.parse("2023-01-01T01:00:00Z"),
            actionType = "other_type",
            actionParams = emptyMap(),
            actionStatus = completedActionStatus,
            actionResult = "UPDATED Result for other type",
            isHumanAction = false
        )

        flowActionUserRequestUpdated = flowActionUserRequest.copy(
            id = UUID.randomUUID().toString(), // New ID
            updated = Instant.parse("2023-01-01T02:00:00Z"),
            actionResult = "Updated user request details"
        )
    }

    fun testShowsUserRequestComponentForUserRequestType() {
        val component = FlowActionComponent(flowActionUserRequest)
        assertNotNull("Displayed child should not be null", component.displayedChild)
        assertTrue("Displayed child should be UserRequestComponent", component.displayedChild is UserRequestComponent)
        assertEquals("FlowActionComponent should have one child component", 1, component.componentCount)
    }

    fun testShowsUserRequestComponentForUserRequestPrefix() {
        val component = FlowActionComponent(flowActionUserRequestSpecific)
        assertNotNull("Displayed child should not be null", component.displayedChild)
        assertTrue("Displayed child should be UserRequestComponent for prefixed type", component.displayedChild is UserRequestComponent)
        assertEquals("FlowActionComponent should have one child component", 1, component.componentCount)
    }

    fun testShowsDefaultViewForOtherType() {
        val component = FlowActionComponent(flowActionOtherType)
        assertNotNull("Displayed child should not be null", component.displayedChild)
        assertTrue("Displayed child should be a JBPanel (defaultViewPanel)", component.displayedChild is JBPanel<*>)
        assertSame("Displayed child should be the defaultViewPanel instance", component.defaultViewPanel, component.displayedChild)
        assertEquals("FlowActionComponent should have one child component", 1, component.componentCount)

        assertEquals("Action type label mismatch", "other_type", component.actionTypeLabel.text)
        assertEquals("Action result label mismatch", "Result for other type", component.actionResultLabel.text)
    }

    fun testUpdateToUserRequestComponent() {
        val component = FlowActionComponent(flowActionOtherType) // Initial state: default view
        assertTrue("Initial child should be defaultViewPanel", component.displayedChild === component.defaultViewPanel)

        component.update(flowActionUserRequest) // Update to user request type

        assertNotNull("Displayed child should not be null after update", component.displayedChild)
        assertTrue("Displayed child should be UserRequestComponent after update", component.displayedChild is UserRequestComponent)
        assertNotSame("Displayed child should not be the old defaultViewPanel instance", component.defaultViewPanel, component.displayedChild)
    }

    fun testUpdateToDefaultView() {
        val component = FlowActionComponent(flowActionUserRequest) // Initial state: user request view
        val initialChild = component.displayedChild
        assertTrue("Initial child should be UserRequestComponent", initialChild is UserRequestComponent)

        component.update(flowActionOtherType) // Update to default view type

        assertNotNull("Displayed child should not be null after update", component.displayedChild)
        assertTrue("Displayed child should be a JBPanel (defaultViewPanel) after update", component.displayedChild is JBPanel<*>)
        assertSame("Displayed child should be the defaultViewPanel instance after update", component.defaultViewPanel, component.displayedChild)
        assertNotSame("Displayed child should have changed from the initial UserRequestComponent", initialChild, component.displayedChild)

        assertEquals("Action type label mismatch after update", "other_type", component.actionTypeLabel.text)
        assertEquals("Action result label mismatch after update", "Result for other type", component.actionResultLabel.text)
    }

    fun testUpdateUserRequestComponentData() {
        val component = FlowActionComponent(flowActionUserRequest)
        val initialUserRequestComponentInstance = component.displayedChild
        assertTrue("Initial child should be UserRequestComponent", initialUserRequestComponentInstance is UserRequestComponent)

        component.update(flowActionUserRequestUpdated)

        val updatedUserRequestComponentInstance = component.displayedChild
        assertNotNull("Displayed child should not be null after update", updatedUserRequestComponentInstance)
        assertTrue("Displayed child should still be UserRequestComponent after update", updatedUserRequestComponentInstance is UserRequestComponent)
        assertNotSame(
            "A new UserRequestComponent instance should be created and shown",
            initialUserRequestComponentInstance,
            updatedUserRequestComponentInstance
        )
    }

    fun testUpdateDefaultViewData() {
        val component = FlowActionComponent(flowActionOtherType)
        val initialDefaultViewPanelInstance = component.defaultViewPanel

        assertEquals("Initial action type label mismatch", "other_type", component.actionTypeLabel.text)
        assertEquals("Initial action result label mismatch", "Result for other type", component.actionResultLabel.text)

        component.update(flowActionOtherTypeUpdated)

        assertSame("DefaultViewPanel instance should be reused", initialDefaultViewPanelInstance, component.defaultViewPanel)
        assertSame("Displayed child should be the reused defaultViewPanel", component.defaultViewPanel, component.displayedChild)

        assertEquals("Action type label should remain the same", "other_type", component.actionTypeLabel.text)
        assertEquals("Action result label should be updated", "UPDATED Result for other type", component.actionResultLabel.text)
    }
}