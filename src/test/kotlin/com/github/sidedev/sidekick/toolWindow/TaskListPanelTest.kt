package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.AgentType
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.TaskStatus
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListPanelTest : BasePlatformTestCase() {
    private lateinit var sidekickService: SidekickService
    private lateinit var taskListModel: TaskListModel
    private lateinit var taskListPanel: TaskListPanel
    private var taskSelectedCallbackInvoked = false
    private var newTaskCallbackInvoked = false
    
    private val testDispatcher = UnconfinedTestDispatcher()

    private val exampleTask = Task(
        id = "1",
        workspaceId = "test-workspace",
        status = TaskStatus.IN_PROGRESS,
        agentType = AgentType.LLM,
        flowType = "test",
        description = "Initial task",
        created = Clock.System.now(),
        updated = Clock.System.now(),
    )

    override fun setUp() {
        super.setUp()
        sidekickService = mockk()
        coEvery { sidekickService.getTasks("test-workspace") } returns ApiResponse.Success(emptyList())

        taskListModel = TaskListModel()
        taskListPanel = TaskListPanel(
            workspaceId = "test-workspace",
            taskListModel = taskListModel,
            sidekickService = sidekickService,
            onTaskSelected = { taskSelectedCallbackInvoked = true },
            onNewTask = { newTaskCallbackInvoked = true }
        )
    }

    fun testEmptyStateShowsNoTasksMessageAndButton() = runTest(testDispatcher) {
        // Given an empty task list
        taskListPanel.replaceTasks(emptyList())

        // And the New Task button should be present and visible
        assertTrue("New Task button should be visible", taskListPanel.newTaskButton.isVisible)
        assertEquals("New Task button should have correct text", "Start New Task", taskListPanel.newTaskButton.text)
    }

    fun testNewTaskButtonTriggersCallback() = runTest(testDispatcher) {
        // Given an empty task list
        taskListPanel.replaceTasks(emptyList())
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // When clicking the New Task button
        assertTrue("New Task button should be visible", taskListPanel.newTaskButton.isVisible)
        taskListPanel.newTaskButton.doClick()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // Then the callback should be invoked
        assertTrue("New Task callback should be invoked", newTaskCallbackInvoked)
    }

    fun testTaskListDisplayWithNonEmptyTasks() = runTest(testDispatcher) {
        // Given a list of tasks
        val tasks = listOf(exampleTask)

        // When updating the task list
        taskListPanel.replaceTasks(tasks)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // Then the task list should be visible and empty state hidden
        assertTrue("Task list should be visible", taskListPanel.taskList.isVisible)
        assertFalse("New Task button should be hidden", taskListPanel.newTaskButton.isVisible)
        assertEquals("Task list should have correct number of items", 1, taskListPanel.taskList.model.size)
    }

    fun testTaskSelectionTriggersCallback() = runTest(testDispatcher) {
        // Given a list of tasks
        taskListPanel.replaceTasks(listOf(exampleTask))

        // When selecting a task
        taskListPanel.taskList.selectedIndex = 0

        // Then the callback should be invoked
        assertTrue("Task selected callback should be invoked", taskSelectedCallbackInvoked)
    }

    fun testRefreshTaskListSuccess() = runTest(testDispatcher) {
        // Given a successful API response
        val tasks = listOf(exampleTask)
        coEvery { sidekickService.getTasks("test-workspace") } returns ApiResponse.Success(tasks)

        // When refreshing the task list
        taskListPanel.refreshTaskList()

        // Then the tasks should be updated and status label hidden
        assertEquals("Task list should have correct number of items", 1, taskListPanel.taskList.model.size)
        assertFalse("Status label should be hidden", taskListPanel.statusLabel.isVisible)
    }

    fun testRefreshTaskListError() = runTest(testDispatcher) {
        // Given an initial task list
        taskListPanel.replaceTasks(listOf(exampleTask))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        // pre-condition: if this fails, we aren't waiting for updates to get applied properly
        // FIXME this is slightly flaky, it sometimes fails saying:
        //      junit.framework.AssertionFailedError: Task list should be correct initialized before test actions:<1> but was:<0>
        assertEquals("Task list should be correct initialized before test actions", 1, taskListPanel.taskList.model.size)

        // And an API error response
        val errorMessage = "Failed to fetch tasks"
        coEvery { sidekickService.getTasks("test-workspace") } returns ApiResponse.Error(ApiError(errorMessage))

        // When refreshing the task list
        taskListPanel.refreshTaskList()

        // Then the error should be shown and existing tasks preserved
        assertEquals("Status label should show error message", "<html>$errorMessage</html>", taskListPanel.statusLabel.text)
        assertTrue("Status label should be visible", taskListPanel.statusLabel.isVisible)

        assertEquals("Task list should preserve existing tasks", 1, taskListPanel.taskList.model.size)

        assertEquals("Initial task should still be present", exampleTask, taskListPanel.taskList.model.getElementAt(0))
    }
}