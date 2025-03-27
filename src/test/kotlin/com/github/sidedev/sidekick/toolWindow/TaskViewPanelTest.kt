package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.Task
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.awt.Component
import java.awt.event.MouseEvent

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewPanelTest : BasePlatformTestCase() {
    private lateinit var taskViewPanel: TaskViewPanel
    private lateinit var testTask: Task
    private var allTasksClicked = false

    override fun setUp() {
        super.setUp()
        testTask = Task(
            id = "task_123",
            workspaceId = "ws_123",
            status = "PENDING",
            agentType = "TEST",
            flowType = "TEST",
            description = "Test task description",
            created = "2024-01-01T00:00:00Z",
            updated = "2024-01-01T00:00:00Z"
        )
        allTasksClicked = false
        taskViewPanel = TaskViewPanel(
            task = testTask,
            onAllTasksClick = { allTasksClicked = true }
        )
    }

    fun testPanelDisplaysTaskDescription() {
        // Find the JBTextArea component that should display the description
        val textArea = findComponentOfType(taskViewPanel, JBTextArea::class.java)
        assertNotNull("Description text area should exist", textArea)
        assertEquals("Text area should display task description", testTask.description, textArea!!.text)
        assertFalse("Text area should be read-only", textArea.isEditable)
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
}