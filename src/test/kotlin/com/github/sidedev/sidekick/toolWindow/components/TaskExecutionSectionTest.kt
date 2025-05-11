package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.*
import junit.framework.Assert.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertNotEquals
import javax.swing.JPanel
import javax.swing.JComponent

@OptIn(ExperimentalCoroutinesApi::class)
class TaskExecutionSectionTest : BasePlatformTestCase() {

    private lateinit var taskExecutionSection: TaskExecutionSection
    private lateinit var contentPanel: JPanel

    // Use UnconfinedTestDispatcher for consistency with other tests, though not strictly necessary here
    private val testDispatcher = UnconfinedTestDispatcher()

    private val now = Clock.System.now()

    private val sampleFlowAction1 = FlowAction(
        id = "action-1",
        flowId = "flow-1",
        subflowId = "subflow-1",
        workspaceId = "ws-1",
        created = now,
        updated = now,
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
        created = now,
        updated = now,
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

    private val sampleGenerateActionComplete = FlowAction(
        id = "gen-action-complete-1",
        flowId = "flow-1",
        subflowId = "subflow-gen-1",
        workspaceId = "ws-1",
        created = now,
        updated = now,
        actionType = "generate.code",
        actionParams = emptyMap(),
        actionStatus = ActionStatus.COMPLETE,
        actionResult = """{"content":"Generated Code Content"}""",
        isHumanAction = false
    )

    private val sampleGenerateSubflow = Subflow(
        id = "subflow-gen-1",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Generate Subflow",
        type = "generic_gen",
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

    fun `test processAction adds new GenerateFlowActionComponent`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // When processing a new generate action
        taskExecutionSection.processAction(sampleGenerateActionComplete, sampleGenerateSubflow)

        // Then a new GenerateFlowActionComponent should be added
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is GenerateFlowActionComponent)

        // And the component should be tracked internally
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
        assertSame(contentPanel.getComponent(0), taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
    }

    fun `test processAction updates existing GenerateFlowActionComponent`() = runTest(testDispatcher) {
        // Given a generate action has been processed
        taskExecutionSection.processAction(sampleGenerateActionComplete, sampleGenerateSubflow)
        assertEquals(1, contentPanel.componentCount)
        val initialComponent = taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id]
        assertNotNull(initialComponent)
        assertTrue(initialComponent is GenerateFlowActionComponent)

        // Spy on the existing component
        val componentSpy = spyk(initialComponent!!, recordPrivateCalls = true)
        taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id] = componentSpy

        // When processing an update for the same generate action
        val updatedGenerateAction = sampleGenerateActionComplete.copy(
            actionResult = """{"content":"Updated Generated Content"}""",
            updated = Clock.System.now()
        )
        taskExecutionSection.processAction(updatedGenerateAction, sampleGenerateSubflow)

        // Then the existing component's update method should be called
        verify(exactly = 1) { componentSpy.update(updatedGenerateAction) }

        // And no new component should be added, spy is still tracked
        assertEquals(1, contentPanel.componentCount)
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertSame(componentSpy, taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
    }

    fun `test processAction replaces FlowActionComponent with GenerateFlowActionComponent on type change`() = runTest(testDispatcher) {
        // Given a non-generate action has been processed
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1)
        assertEquals(1, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents[sampleFlowAction1.id] is FlowActionComponent)
        val oldComponent = contentPanel.getComponent(0)

        // When the action is updated to be a generate type
        val updatedToAction = sampleFlowAction1.copy(
            actionType = "generate.switchToThis",
            actionResult = """{"content":"Switched to Generate"}""",
            updated = Clock.System.now()
        )
        taskExecutionSection.processAction(updatedToAction, sampleSubflow1)

        // Then the component should be replaced
        assertEquals(1, contentPanel.componentCount) // Still one component overall
        val newComponent = contentPanel.getComponent(0)
        assertTrue(newComponent is GenerateFlowActionComponent)
        assertFalse(oldComponent === newComponent) // Ensure it's a new instance

        // And the new component should be tracked
        assertNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id]?.let { it as? FlowActionComponent })
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
        assertSame(newComponent, taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
    }

    fun `test processAction replaces GenerateFlowActionComponent with FlowActionComponent on type change`() = runTest(testDispatcher) {
        // Given a generate action has been processed
        taskExecutionSection.processAction(sampleGenerateActionComplete, sampleGenerateSubflow)
        assertEquals(1, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id] is GenerateFlowActionComponent)
        val oldComponent = contentPanel.getComponent(0)

        // When the action is updated to be a non-generate type
        val updatedToNonGenerateAction = sampleGenerateActionComplete.copy(
            actionType = "non.generate.type",
            actionResult = "Switched to Non-Generate",
            updated = Clock.System.now()
        )
        taskExecutionSection.processAction(updatedToNonGenerateAction, sampleGenerateSubflow)

        // Then the component should be replaced
        assertEquals(1, contentPanel.componentCount) // Still one component overall
        val newComponent = contentPanel.getComponent(0)
        assertTrue(newComponent is FlowActionComponent)
        assertFalse(oldComponent === newComponent) // Ensure it's a new instance

        // And the new component should be tracked
        assertNull(taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id]?.let { it as? GenerateFlowActionComponent })
        assertNotNull(taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
        assertSame(newComponent, taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
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

        // When processing two distinct actions, one non-generate and one generate
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1) // Non-generate
        taskExecutionSection.processAction(sampleGenerateActionComplete, sampleGenerateSubflow) // Generate

        // Then two components should be added to the content panel
        assertEquals(2, contentPanel.componentCount)
        val component1 = contentPanel.getComponent(0) as JComponent
        val component2 = contentPanel.getComponent(1) as JComponent

        // Check types (order depends on internal Swing container behavior, but both types must be present)
        val hasFlowActionComponent = component1 is FlowActionComponent || component2 is FlowActionComponent
        val hasGenerateFlowActionComponent = component1 is GenerateFlowActionComponent || component2 is GenerateFlowActionComponent
        assertTrue("Should have a FlowActionComponent", hasFlowActionComponent)
        assertTrue("Should have a GenerateFlowActionComponent", hasGenerateFlowActionComponent)
        assertNotEquals("Components should be of different types if test is set up correctly", component1::class, component2::class)


        // And both components should be tracked internally
        assertEquals(2, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
        assertNotNull(taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])

        // Verify the tracked components match those in the panel
        if (component1 is FlowActionComponent) {
            assertSame(component1, taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
            assertSame(component2, taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
        } else {
            assertSame(component2, taskExecutionSection.flowActionComponents[sampleFlowAction1.id])
            assertSame(component1, taskExecutionSection.flowActionComponents[sampleGenerateActionComplete.id])
        }
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