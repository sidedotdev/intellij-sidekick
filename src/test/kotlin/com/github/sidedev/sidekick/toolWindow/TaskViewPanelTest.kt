package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.*
import com.github.sidedev.sidekick.models.Subflow
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import java.awt.Component
import java.awt.event.MouseEvent

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewPanelTest : BasePlatformTestCase() {
    private lateinit var taskViewPanel: TaskViewPanel
    private lateinit var testTask: Task
    private var allTasksClicked = false
    private lateinit var sidekickService: SidekickService

    override fun setUp() {
        super.setUp()
        testTask = Task(
            id = "task_123",
            workspaceId = "ws_123",
            status = TaskStatus.TO_DO,
            agentType = AgentType.LLM,
            flowType = "TEST",
            description = "Test task description",
            created = Clock.System.now(),
            updated = Clock.System.now(),
        )
        allTasksClicked = false
        sidekickService = mockk()
        taskViewPanel = TaskViewPanel(
            task = testTask,
            onAllTasksClick = { allTasksClicked = true },
            sidekickService = sidekickService
        )
    }

    fun testPanelDisplaysTaskDescription() {
        // Find the JBLabel component that displays the description
        val descriptionLabel = findComponentsOfType(taskViewPanel, JBLabel::class.java)
            .find { it.text == testTask.description }
        assertNotNull("Description label should exist", descriptionLabel)
        assertEquals("Label should display task description", testTask.description, descriptionLabel!!.text)
    }

    private fun <T : Component> findComponentsOfType(container: Component, type: Class<T>): List<T> {
        val results = mutableListOf<T>()
        
        if (type.isInstance(container)) {
            @Suppress("UNCHECKED_CAST")
            results.add(container as T)
        }
        
        if (container is java.awt.Container) {
            for (component in container.components) {
                results.addAll(findComponentsOfType(component, type))
            }
        }
        return results
    }

    fun testPanelHasScrollPane() {
        val scrollPane = findComponentOfType(taskViewPanel, JBScrollPane::class.java)
        assertNotNull("Panel should have a scroll pane", scrollPane)
    }

    fun testAllTasksLinkExists() {
        // Initially not clicked
        assertFalse("All Tasks link should not be clicked initially", allTasksClicked)
        
        // Find and simulate click on the link
        val allTasksLink = findComponentOfType(taskViewPanel, JBLabel::class.java)
        assertNotNull("All Tasks link should exist", allTasksLink)
        assertTrue("All Tasks link should be visible", allTasksLink!!.isVisible)
        assertEquals("Link should show 'All Tasks'", "<html><u>All Tasks</u></html>", allTasksLink.text)
        
        // Simulate click and verify callback was invoked
        allTasksLink.dispatchEvent(MouseEvent(
            allTasksLink,
            MouseEvent.MOUSE_CLICKED,
            System.currentTimeMillis(),
            0,
            0,
            0,
            1,
            false
        ))
        assertTrue("All Tasks click callback should be invoked", allTasksClicked)
    }

    private fun <T : Component> findComponentOfType(container: Component, type: Class<T>): T? {
        if (type.isInstance(container)) {
            @Suppress("UNCHECKED_CAST")
            return container as T
        }
        
        if (container is java.awt.Container) {
            for (component in container.components) {
                val found = findComponentOfType(component, type)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    fun testDetermineSubflowCategory() = runTest {
        val requirementsSubflow = Subflow(id = "test", type = "dev_requirements")
        assertEquals(
            TaskViewPanel.CATEGORY_REQUIREMENTS_PLANNING,
            taskViewPanel.determineSubflowCategory(requirementsSubflow)
        )

        val planSubflow = Subflow(id = "test", type = "dev_plan")
        assertEquals(
            TaskViewPanel.CATEGORY_REQUIREMENTS_PLANNING,
            taskViewPanel.determineSubflowCategory(planSubflow)
        )

        val llmStepSubflow = Subflow(id = "test-id", type = "llm_step")
        assertEquals(
            "llm_step:test-id",
            taskViewPanel.determineSubflowCategory(llmStepSubflow)
        )

        val unknownSubflow = Subflow(id = "test", type = "unknown")
        assertEquals(
            TaskViewPanel.CATEGORY_CODING,
            taskViewPanel.determineSubflowCategory(unknownSubflow)
        )
    }

    fun testFindRelevantSubflowType() = runTest {
        val child = Subflow(id = "child", parentSubflowId = "parent")
        val parent = Subflow(id = "parent", type = "dev_requirements")
        
        coEvery { 
            sidekickService.getSubflow("ws_123", "parent")
        } returns ApiResponse.Success(parent)
        
        assertEquals(
            "dev_requirements",
            taskViewPanel.findRelevantSubflowType(child)
        )
    }

    fun testGetSectionName() {
        assertEquals(
            "Requirements and Planning",
            taskViewPanel.getSectionName(TaskViewPanel.CATEGORY_REQUIREMENTS_PLANNING)
        )
        
        taskViewPanel.apply { 
            hasRequirementsSubflow = true
            assertEquals("Requirements", getSectionName(TaskViewPanel.CATEGORY_REQUIREMENTS_PLANNING))
            
            hasPlanningSubflow = true
            assertEquals("Requirements and Planning", getSectionName(TaskViewPanel.CATEGORY_REQUIREMENTS_PLANNING))
        }

        assertEquals(
            "Coding",
            taskViewPanel.getSectionName(TaskViewPanel.CATEGORY_CODING)
        )

        assertEquals(
            "Step test-id",
            taskViewPanel.getSectionName("llm_step:test-id")
        )
    }
}