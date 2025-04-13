package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListPanelTest : BasePlatformTestCase() {
    private lateinit var sidekickService: SidekickService
    private lateinit var taskListModel: TaskListModel
    private lateinit var taskListPanel: TaskListPanel
    private var taskSelectedCallbackInvoked = false
    private var newTaskCallbackInvoked = false
    
    private val testDispatcher = UnconfinedTestDispatcher()

    override fun setUp() {
        super.setUp()
        sidekickService = mockk()
        taskListModel = TaskListModel()
        taskListPanel = TaskListPanel(
            workspaceId = "test-workspace",
            taskListModel = taskListModel,
            sidekickService = sidekickService,
            onTaskSelected = { taskSelectedCallbackInvoked = true },
            onNewTask = { newTaskCallbackInvoked = true }
        )
    }

    fun testEmptyStateShowsNoTasksMessageAndButton() {
        // Given an empty task list
        taskListModel.updateTasks(emptyList())

        // Then the panel should show "No tasks" message
        assertTrue("No tasks label should be visible", taskListPanel.noTasksLabel.isVisible)
        assertEquals("No tasks label should have correct text", "No tasks", taskListPanel.noTasksLabel.text)

        // And the New Task button should be present and visible
        assertTrue("New Task button should be visible", taskListPanel.newTaskButton.isVisible)
        assertEquals("New Task button should have correct text", "New Task", taskListPanel.newTaskButton.text)
    }

    fun testNewTaskButtonTriggersCallback() {
        // Given an empty task list
        taskListModel.updateTasks(emptyList())

        // When clicking the New Task button
        assertTrue("New Task button should be visible", taskListPanel.newTaskButton.isVisible)
        taskListPanel.newTaskButton.doClick()

        // Then the callback should be invoked
        assertTrue("New Task callback should be invoked", newTaskCallbackInvoked)
    }
}