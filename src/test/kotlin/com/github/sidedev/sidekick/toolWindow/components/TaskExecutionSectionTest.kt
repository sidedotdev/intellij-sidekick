package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.UsefulTestCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertNotEquals
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import javax.swing.JComponent

@OptIn(ExperimentalCoroutinesApi::class)
class TaskExecutionSectionTest : UsefulTestCase() {

    private lateinit var taskExecutionSection: TaskExecutionSection
    private lateinit var contentPanel: JPanel

    // Use UnconfinedTestDispatcher for consistency with other tests
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createFlowAction(
        id: String,
        subflowId: String?,
        actionType: String = "DEFAULT_TYPE",
        actionStatus: ActionStatus = ActionStatus.COMPLETE,
        updated: Instant = Clock.System.now()
    ): FlowAction {
        return FlowAction(
            id = id,
            flowId = "flow-1",
            subflowId = subflowId,
            workspaceId = "ws-1",
            created = Clock.System.now(),
            updated = updated,
            actionType = actionType,
            actionParams = emptyMap(),
            actionStatus = actionStatus,
            actionResult = "Result for $id",
            isHumanAction = false
        )
    }

    private fun createSubflow(
        id: String,
        type: String?,
        status: SubflowStatus = SubflowStatus.STARTED
    ): Subflow {
        return Subflow(
            id = id,
            flowId = "flow-1",
            workspaceId = "ws-1",
            name = "Subflow $id",
            type = type,
            status = status
        )
    }

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
        subflowId = "subflow-2", // Associated with sampleSubflow2
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
        type = "another_type", // Not "code_context"
        status = SubflowStatus.STARTED
    )

    // Specific subflow for code_context tests
    private val codeContextSubflow1 = Subflow(
        id = "cc-subflow-1",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Code Context Subflow One",
        type = "code_context",
        status = SubflowStatus.STARTED
    )

    private val codeContextSubflow2 = Subflow(
        id = "cc-subflow-2",
        flowId = "flow-1",
        workspaceId = "ws-1",
        name = "Code Context Subflow Two",
        type = "code_context",
        status = SubflowStatus.STARTED
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

    fun `test processAction creates SubflowSummaryComponent for code_context subflow`() = runTest(testDispatcher) {
        // Given an empty section
        assertEquals(0, contentPanel.componentCount)
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty())
        assertTrue(taskExecutionSection.codeContextSubflowStates.isEmpty())

        // When processing an action associated with a 'code_context' subflow
        val action = createFlowAction("cc-action-1", codeContextSubflow1.id, actionStatus = ActionStatus.STARTED)
        taskExecutionSection.processAction(action, codeContextSubflow1)

        // Then a SubflowSummaryComponent should be added
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is SubflowSummaryComponent)
        assertEquals(1, taskExecutionSection.subflowSummaries.size)
        assertNotNull(taskExecutionSection.subflowSummaries[codeContextSubflow1.id])
        assertSame(contentPanel.getComponent(0), taskExecutionSection.subflowSummaries[codeContextSubflow1.id])

        // And no FlowActionComponent should be created
        assertTrue(taskExecutionSection.flowActionComponents.isEmpty())

        // And CodeContextSubflowState should be created and updated
        assertEquals(1, taskExecutionSection.codeContextSubflowStates.size)
        val state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1, state.subflow)
    }

    fun `test processAction updates subflow state for code_context subflow`() = runTest(testDispatcher) {
        val time1 = Clock.System.now()
        val time2 = time1 + 1.seconds
        val time3 = time2 + 1.seconds

        val action1 = createFlowAction("cc-action-1", codeContextSubflow1.id, actionStatus = ActionStatus.STARTED, updated = time1)
        val action2 = createFlowAction("cc-action-2", codeContextSubflow1.id, actionType = "tool_call.search", actionStatus = ActionStatus.STARTED, updated = time2)
        val action3Terminal = createFlowAction("cc-action-3", codeContextSubflow1.id, actionStatus = ActionStatus.COMPLETE, updated = time3)

        // Process first STARTED action
        taskExecutionSection.processAction(action1, codeContextSubflow1)
        var state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1, state.subflow)
        val summaryComponent = taskExecutionSection.subflowSummaries[codeContextSubflow1.id]
        assertNotNull(summaryComponent)

        // Process second action
        taskExecutionSection.processAction(action2, codeContextSubflow1)
        state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1, state.subflow)

        // Process a COMPLETE action
        val action2Completed = action2.copy(actionStatus = ActionStatus.COMPLETE, updated = time3)
        taskExecutionSection.processAction(action2Completed, codeContextSubflow1.copy(status = SubflowStatus.STARTED))
        state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1.copy(status = SubflowStatus.STARTED), state.subflow)

        // Process another STARTED action
        val action4 = createFlowAction("cc-action-4", codeContextSubflow1
            .id, actionStatus = ActionStatus.STARTED, updated = time3 + 1.seconds)
        taskExecutionSection.processAction(action4, codeContextSubflow1.copy(status = SubflowStatus.STARTED))
        state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1.copy(status = SubflowStatus.STARTED), state.subflow)

        // Process a COMPLETE action for a previous action
        taskExecutionSection.processAction(action1.copy(actionStatus = ActionStatus.COMPLETE), codeContextSubflow1.copy(status = SubflowStatus.STARTED))
        state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(codeContextSubflow1.copy(status = SubflowStatus.STARTED), state.subflow)
    }

    fun `test processAction handles multiple distinct code_context subflows`() = runTest(testDispatcher) {
        val actionCc1 = createFlowAction("cc-action1-s1", codeContextSubflow1.id, actionStatus = ActionStatus.STARTED)
        val actionCc2 = createFlowAction("cc-action1-s2", codeContextSubflow2.id, actionStatus = ActionStatus.STARTED)

        taskExecutionSection.processAction(actionCc1, codeContextSubflow1)
        taskExecutionSection.processAction(actionCc2, codeContextSubflow2)

        assertEquals(2, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is SubflowSummaryComponent)
        assertTrue(contentPanel.getComponent(1) is SubflowSummaryComponent)

        assertEquals(2, taskExecutionSection.subflowSummaries.size)
        assertNotNull(taskExecutionSection.subflowSummaries[codeContextSubflow1.id])
        assertNotNull(taskExecutionSection.subflowSummaries[codeContextSubflow2.id])
        assertNotEquals(taskExecutionSection.subflowSummaries[codeContextSubflow1.id], taskExecutionSection.subflowSummaries[codeContextSubflow2.id])


        assertEquals(2, taskExecutionSection.codeContextSubflowStates.size)
        assertNotNull(taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id])
        assertNotNull(taskExecutionSection.codeContextSubflowStates[codeContextSubflow2.id])
        assertEquals(codeContextSubflow1, taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]?.subflow)
        assertEquals(codeContextSubflow2, taskExecutionSection.codeContextSubflowStates[codeContextSubflow2.id]?.subflow)
    }


    fun `test processAction for non-code_context subflow with existing logic`() = runTest(testDispatcher) {
        taskExecutionSection.processAction(sampleFlowAction1, sampleSubflow1) // type="generic"
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is FlowActionComponent)
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty())
        assertTrue(taskExecutionSection.codeContextSubflowStates.isEmpty())
    }


    fun `test updateSubflow for code_context subflow`() = runTest(testDispatcher) {
        // First, process an action to create the summary and state
        val initialAction = createFlowAction("cc-action-initial", codeContextSubflow1.id, actionStatus = ActionStatus.STARTED)
        taskExecutionSection.processAction(initialAction, codeContextSubflow1) // Subflow1 is STARTED

        val summaryComponentSpy = spyk(taskExecutionSection.subflowSummaries[codeContextSubflow1.id]!!)
        taskExecutionSection.subflowSummaries[codeContextSubflow1.id] = summaryComponentSpy

        // When subflow status updates to COMPLETE
        val updatedSubflowComplete = codeContextSubflow1.copy(status = SubflowStatus.COMPLETE)
        taskExecutionSection.updateSubflow(updatedSubflowComplete)

        val state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(updatedSubflowComplete, state.subflow) // Subflow in state is updated

        verify(exactly = 1) { summaryComponentSpy.update(null, updatedSubflowComplete) }

        // When subflow status updates to FAILED
        val updatedSubflowFailed = codeContextSubflow1.copy(status = SubflowStatus.FAILED)
        taskExecutionSection.updateSubflow(updatedSubflowFailed)
        assertEquals(updatedSubflowFailed, state.subflow)
        verify(exactly = 1) { summaryComponentSpy.update(null, updatedSubflowFailed) }
    }

    fun `test updateSubflow for code_context subflow creates state if not existing`() = runTest(testDispatcher) {
        assertTrue(taskExecutionSection.codeContextSubflowStates.isEmpty())
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty())

        val updatedSubflow = codeContextSubflow1.copy(status = SubflowStatus.STARTED)
        taskExecutionSection.updateSubflow(updatedSubflow) // Subflow1 is STARTED

        val state = taskExecutionSection.codeContextSubflowStates[codeContextSubflow1.id]!!
        assertNotNull(state)
        assertEquals(updatedSubflow, state.subflow)

        // No summary component should be created or updated by updateSubflow if it doesn't exist
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty())
        assertEquals(0, contentPanel.componentCount)
    }


    fun `test updateSubflow for non-code_context subflow does nothing`() = runTest(testDispatcher) {
        val nonCcSubflow = createSubflow("non-cc-subflow", "other_type", SubflowStatus.STARTED)
        taskExecutionSection.updateSubflow(nonCcSubflow)

        assertTrue(taskExecutionSection.codeContextSubflowStates.isEmpty())
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty())
    }

    fun `test chronological addition of SubflowSummaryComponent instances`() = runTest(testDispatcher) {
        val actionCc1 = createFlowAction("action-cc1", codeContextSubflow1.id, actionStatus = ActionStatus.STARTED)
        val actionNonCc = createFlowAction("action-non-cc", sampleSubflow1.id) // Generic, creates FlowActionComponent
        val actionCc2 = createFlowAction("action-cc2", codeContextSubflow2.id, actionStatus = ActionStatus.STARTED)

        // Process in order: CC1, NonCC, CC2
        taskExecutionSection.processAction(actionCc1, codeContextSubflow1)
        val summary1 = taskExecutionSection.subflowSummaries[codeContextSubflow1.id]

        taskExecutionSection.processAction(actionNonCc, sampleSubflow1)
        val flowActionComp = taskExecutionSection.flowActionComponents[actionNonCc.id]

        taskExecutionSection.processAction(actionCc2, codeContextSubflow2)
        val summary2 = taskExecutionSection.subflowSummaries[codeContextSubflow2.id]

        assertEquals(3, contentPanel.componentCount)
        assertSame("First component should be summary for cc-subflow-1", summary1, contentPanel.getComponent(0))
        assertSame("Second component should be FlowActionComponent", flowActionComp, contentPanel.getComponent(1) )
        assertSame("Third component should be summary for cc-subflow-2", summary2, contentPanel.getComponent(2))
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

        // When processing a new action with a null subflow (should not be ignored by code_context logic)
        val actionWithNullSubflow = createFlowAction("action-null-subflow", null)
        taskExecutionSection.processAction(actionWithNullSubflow, null)

        // Then a new FlowActionComponent should be added
        assertEquals(1, contentPanel.componentCount)
        assertTrue(contentPanel.getComponent(0) is FlowActionComponent)
        assertEquals(1, taskExecutionSection.flowActionComponents.size)
        assertNotNull(taskExecutionSection.flowActionComponents[actionWithNullSubflow.id])
        assertTrue(taskExecutionSection.subflowSummaries.isEmpty()) // No summary component
    }
}