package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.AgentType
import com.github.sidedev.sidekick.api.TaskStatus
import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import java.awt.Component

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewPanelTest : UsefulTestCase() {
    private lateinit var taskViewPanel: TaskViewPanel
    private lateinit var testTask: Task
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
        sidekickService = mockk()
        taskViewPanel = TaskViewPanel(
            task = testTask,
            sidekickService = sidekickService
        )
    }

    fun testPanelDisplaysTaskDescription() {
        // Find the JBTextArea component that displays the description
        val descriptionArea = findComponentsOfType(taskViewPanel, JBTextArea::class.java)
            .find { it.text == testTask.description }

        assertNotNull("Description text area should exist", descriptionArea)
        assertEquals(
            "Text area should display task description",
            testTask.description,
            descriptionArea!!.text
        )
        assertFalse("Text area should not be editable", descriptionArea.isEditable)
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