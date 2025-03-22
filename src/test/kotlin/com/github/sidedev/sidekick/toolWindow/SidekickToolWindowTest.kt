package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.Workspace
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import javax.swing.JLabel

class SidekickToolWindowTest : LightPlatformTestCase() {
    private lateinit var toolWindowFactory: SidekickToolWindowFactory
    private lateinit var mockSidekickService: SidekickService

    override fun setUp() {
        super.setUp()

        // Create a mock SidekickService
        mockSidekickService = mockk()

        // Create the factory
        toolWindowFactory = SidekickToolWindowFactory()
    }

    override fun tearDown() {
        super.tearDown()
    }

    /**
     * Helper method to create a SidekickToolWindow instance with our mock service
     */
    private fun createToolWindowWithMockService(): SidekickToolWindowFactory.SidekickToolWindow {
        // Create a new instance of SidekickToolWindow using our factory and mock service
        val sidekickToolWindow = SidekickToolWindowFactory().createSidekickToolWindow(
            project,
            mockk<ToolWindow>(relaxed = true),
            mockSidekickService,
        )

        // Dispatch all events to ensure UI updates are processed
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        return sidekickToolWindow
    }

    /**
     * Helper method to invoke the updateWorkspaceContent method
     */
    private suspend fun invokeUpdateWorkspaceContent(toolWindow: SidekickToolWindowFactory.SidekickToolWindow) {
        toolWindow.updateWorkspaceContent()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /**
     * Helper method to get the status label from the tool window
     */
    private fun getStatusLabel(toolWindow: SidekickToolWindowFactory.SidekickToolWindow): JLabel = toolWindow.statusLabel

    /**
     * Helper method to get the task list model from the tool window
     */
    private fun getTaskListModel(toolWindow: SidekickToolWindowFactory.SidekickToolWindow): TaskListModel = toolWindow.getTaskListModel()

    fun testNoMatchingWorkspace() = runBlocking {
        // Mock workspaces response with no matching workspace
        val workspaces = listOf(
            Workspace(
                id = "workspace1",
                name = "Workspace 1",
                localRepoDir = "/some/other/path",
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the status label shows the correct message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "No workspace set up yet"),
            statusLabel.text,
        )

        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)

        // Verify that getTasks was not called
        runBlocking {
            coVerify(exactly = 0) { mockSidekickService.getTasks(any()) }
        }
    }

    fun testMatchingWorkspaceWithTasks() = runBlocking {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)

        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(
                id = "workspace1",
                name = "Workspace 1",
                localRepoDir = projectPath!!,
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)

        // Mock tasks response
        val tasks = listOf(
            Task(
                id = "task1",
                workspaceId = "workspace1",
                description = "Task 1",
                status = "In Progress",
                agentType = "agent1",
                flowType = "flow1",
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
            Task(
                id = "task2",
                workspaceId = "workspace1",
                description = "Task 2",
                status = "Done",
                agentType = "agent2",
                flowType = "flow2",
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
        )
        coEvery { mockSidekickService.getTasks("workspace1") } returns ApiResponse.Success(tasks)

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the status label shows the correct message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "Tasks for workspace workspace1"),
            statusLabel.text,
        )

        // Verify the task list contains the expected tasks
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(2, taskListModel.size)
        assertEquals("task1", taskListModel.getElementAt(0).id)
        assertEquals("task2", taskListModel.getElementAt(1).id)
        assertEquals("In Progress", taskListModel.getElementAt(0).status)
        assertEquals("Done", taskListModel.getElementAt(1).status)
    }

    fun testMatchingWorkspaceWithNoTasks() = runBlocking {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)

        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(
                id = "workspace1",
                name = "Workspace 1",
                localRepoDir = projectPath!!,
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)

        // Mock empty tasks response
        coEvery { mockSidekickService.getTasks("workspace1") } returns ApiResponse.Success(emptyList())

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the status label shows the correct message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "No tasks found"),
            statusLabel.text,
        )

        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }

    fun testErrorFetchingWorkspaces() = runBlocking {
        // Mock error response for workspaces
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Error(ApiError("Connection error"))

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the status label shows the error message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "Side is not running. Please run `side start`. Error: Connection error"),
            statusLabel.text,
        )

        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)

        // Verify that getTasks was not called
        coVerify(exactly = 0) { mockSidekickService.getTasks(any()) }
    }

    fun testErrorFetchingTasks() = runBlocking {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)

        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(
                id = "workspace1",
                name = "Workspace 1",
                localRepoDir = projectPath!!,
                created = "2023-01-01T00:00:00Z",
                updated = "2023-01-01T00:00:00Z",
            ),
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)

        // Mock error response for tasks
        coEvery { mockSidekickService.getTasks("workspace1") } returns ApiResponse.Error(ApiError("Task fetch error"))

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the status label shows the error message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "Error fetching tasks: Task fetch error"),
            statusLabel.text,
        )

        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
}
