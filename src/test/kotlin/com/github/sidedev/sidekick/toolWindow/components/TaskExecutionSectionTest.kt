package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.FlowActionComponent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.*
import javax.swing.JPanel

@OptIn(ExperimentalCoroutinesApi::class)
class TaskExecutionSectionTest : BasePlatformTestCase() {

    private lateinit var taskExecutionSection: TaskExecutionSection
    private lateinit var contentPanel: JPanel

    // Use UnconfinedTestDispatcher for consistency with other tests, though not strictly necessary here
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleFlowAction1 = FlowAction(
        id = "action-1",
        flowId = "flow-1",
        subflowId = "subflow-1",
        workspaceId = "ws-1",
        created = Clock.System.now(),
        updated = Clock.System.now(),
        actionType = "TYPE_A",
        actionParams = emptyMap(),
        actionStatus = ActionStatus.COMPLETE,
        actionResult = "Result A",
        isHumanAction = false
    )

    private val sampleSubflow1 = Subflow(
        id = "subflow-1",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Subflow 1",
        type = "generic",
        status = SubflowStatus.COMPLETE
    )

    private val sampleFlowAction2 = FlowAction(
        id = "action-2",
        flowId = "flow-1",
        subflowId = "subflow-2",
        workspaceId = "ws-1",
        created = Clock.System.now(),
        updated = Clock.System.now(),
        actionType = "TYPE_B",
        actionParams = mapOf("param" to JsonNull),
        actionStatus = ActionStatus.COMPLETE,
        actionResult = "Result B",
        isHumanAction = true
    )

    private val sampleSubflow2 = Subflow(
        id = "subflow-2",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Subflow 2",
        type = "another_type",
        status = SubflowStatus.STARTED
    )

    private val codeContextSubflow = Subflow(
        id = "subflow-cc",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Code Context Subflow",
        type = "code_context", // The type to ignore
        status = SubflowStatus.COMPLETE
    )

    override fun setUp() {
        super.setUp()
        // Use a real JPanel for content to allow adding components
        contentPanel = JPanel()
        // Pass the real JPanel to the constructor
        taskExecutionSection = TaskExecutionSection(name = "Test Section", initialContent = contentPanel)
        // Clear mocks before each test if using instance-level mocks/spies
        clearAllMocks()
    }

    fun `test processAction adds new component`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // When processing a new action
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1)

        // Then a new component should be added to the content panel
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is FlowActionComponent)

        // And the component should be tracked internally
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
        assertSame(contentPanel.getComponent(0), taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
    }

    fun `test processAction updates existing component`() = runTest(testDispatcher) {
        // Given an action has already been processed and its component added
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1)
        assertEquals(1, contentPanel.componentCount)
        val initialComponent = taskExecutionSection.flowActionComponents[sampleFlowAction1.id]
        assertNotNull(initialComponent)

        // Spy on the existing component to verify update call
        val componentSpy = spyk(initialComponent!!, recordPrivateCalls = true)
        taskExecutionSection.flowActionComponents[sampleFlowAction1.id] = componentSpy // Replace original with spy

        // When processing an action with the same ID but updated data
        val updatedAction = sampleFlowAction1.copy(actionResult = "Updated Result A", updated = Clock.System.now())
        taskExecutionSection.processAction(updatedAction, sampleSubflow1)

        // Then the existing component's update method should be called
        verify(exactly = 1) { componentSpy.update(updatedAction) }

        // And no new component should be added
        assertEquals(1, contentPanel.componentCount)
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertSame(componentSpy, taskExecutionSection.flowActionComponents[sampleFlowAction1.id]) // Ensure spy is still tracked
    }

    fun `test processAction ignores code_context subflow`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // When processing an action associated with a 'code_context' subflow
        taskExecutionSection.processAction(sampleFlowAction1, codeContextSubflow)

        // Then no component should be added
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())
    }

     fun `test processAction handles multiple distinct actions`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // When processing two distinct actions
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1)
        taskExecutionSection.processAction(sampleFlowAction2, sampleSubflow2)

        // Then two components should be added to the content panel
        assertEquals(2, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is FlowActionComponent)
        assertTrue(contentPanel.getComponent(1) is FlowActionComponent)

        // And both components should be tracked internally
        assertEquals(2, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction2.id])
        assertSame(contentPanel.getComponent(0), taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
        assertSame(contentPanel.getComponent(1), taskExecutionSection.flowActionComponents[sampleFlowAction2.id])
    }

    fun `test processAction handles null subflow gracefully`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // When processing a new action with a null subflow (should not be ignored)
        taskExecutionSection.processAction(sampleFlowAction1, null)

        // Then a new component should be added
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is FlowActionComponent)
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
    }
}