package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.AgentType
import com.github.sidedev.sidekick.api.TaskStatus
import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBLabel
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
        val expectedHtmlDescription = "<html>${testTask.description?.replace("\n", "<br>")}</html>"
        val descriptionValueLabel = findComponentsOfType(taskViewPanel, JBLabel::class.java)
            .find { it.text == expectedHtmlDescription }

        assertNotNull("Description value label should exist with HTML content", descriptionValueLabel)
        assertEquals(
            "Label should display task description (HTML formatted)",
            expectedHtmlDescription,
            descriptionValueLabel!!.text
        )
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

    fun testDetermineSectionId() = runTest {
        val requirementsSubflow = getTestSubflow(id = "test", type = "dev_requirements")
        assertEquals(
            TaskViewPanel.SECTION_REQUIREMENTS_PLANNING,
            taskViewPanel.determineSectionId(requirementsSubflow)
        )

        val planSubflow = getTestSubflow(id = "test", type = "dev_plan")
        assertEquals(
            TaskViewPanel.SECTION_REQUIREMENTS_PLANNING,
            taskViewPanel.determineSectionId(planSubflow)
        )

        val llmStepSubflow = getTestSubflow(id = "test-id", type =
            "llm_step", name = "do the thing (testing llmStepSubflow)")
        assertEquals(
            "do the thing (testing llmStepSubflow)",
            taskViewPanel.determineSectionId(llmStepSubflow)
        )

        val unknownSubflow = getTestSubflow(id = "test", type = "unknown")
        assertEquals(
            TaskViewPanel.SECTION_UNKNOWN,
            taskViewPanel.determineSectionId(unknownSubflow)
        )
    }

    fun testFindRelevantSubflowType() = runTest {
        val child = getTestSubflow(id = "child", type = "whatever", parentSubflowId = "parent")
        val parent = getTestSubflow(id = "parent", type = "dev_requirements")
        
        coEvery { 
            sidekickService.getSubflow("ws_123", "parent")
        } returns ApiResponse.Success(parent)
        
        assertEquals(
            "dev_requirements",
            taskViewPanel.findPrimarySubflow(child)!!.type
        )
    }

    fun testGetSectionName() {
        taskViewPanel.apply {
            hasRequirementsSubflow = true
            assertEquals("Requirements", getSectionName(TaskViewPanel.SECTION_REQUIREMENTS_PLANNING))
            
            hasPlanningSubflow = true
            assertEquals("Requirements and Planning", getSectionName(TaskViewPanel.SECTION_REQUIREMENTS_PLANNING))

            hasRequirementsSubflow = false
            assertEquals("Planning", getSectionName(TaskViewPanel.SECTION_REQUIREMENTS_PLANNING))
        }

        assertEquals(
            "Coding",
            taskViewPanel.getSectionName(TaskViewPanel.SECTION_CODING)
        )

        assertEquals(
            "Unknown",
            taskViewPanel.getSectionName(TaskViewPanel.SECTION_UNKNOWN)
        )

        assertEquals(
            "some section id",
            taskViewPanel.getSectionName("some section id")
        )
    }

    private fun getTestSubflow(
        id: String = "test",
        name: String = "test subflow name",
        status: SubflowStatus = SubflowStatus.COMPLETE,
        parentSubflowId: String? = null,
        type: String,
    ): Subflow {
        return Subflow(
            name = name,
            workspaceId = "ws_123",
            status = status,
            id = id,
            flowId = "flow_123",
            parentSubflowId = parentSubflowId,
            type = type,
        )
    }
}