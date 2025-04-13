package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.AgentType
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.TaskStatus
import com.github.sidedev.sidekick.api.Workspace
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Clock
import java.awt.Component
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SidekickToolWindowTest : LightPlatformTestCase() {
    private lateinit var toolWindowFactory: SidekickToolWindowFactory
    private lateinit var mockSidekickService: SidekickService

    // the unconfined test dispatcher makes tests much simpler, given we don't
    // care about coroutine ordering etc., but just want to run coroutines to
    // completion typically, then assert
    private val testDispatcher = UnconfinedTestDispatcher()

    override fun setUp() {
        super.setUp()

        // Create a mock SidekickService
        mockSidekickService = mockk()

        // Create the factory
        toolWindowFactory = SidekickToolWindowFactory()

        // Clear any cached workspace ID
        PropertiesComponent.getInstance(project).unsetValue(SidekickToolWindow.WORKSPACE_ID_KEY_PREFIX + project.basePath)
    }

    override fun tearDown() {
        super.tearDown()
    }

    /**
     * Helper method to create a SidekickToolWindow instance with our mock service
     */
    private fun createToolWindowWithMockService(): SidekickToolWindow {
        // Create a new instance of SidekickToolWindow using our factory and mock service
        val sidekickToolWindow = SidekickToolWindowFactory().createSidekickToolWindow(
            project,
            mockk<ToolWindow>(relaxed = true),
            mockSidekickService,
            dispatcher = testDispatcher,
        )

        // Dispatch all events to ensure UI updates are processed
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        return sidekickToolWindow
    }

    /**
     * Helper method to invoke the updateWorkspaceContent method
     */
    private suspend fun invokeUpdateWorkspaceContent(toolWindow: SidekickToolWindow) {
        toolWindow.updateWorkspaceContent()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    /**
     * Helper method to get the currently visible card name from the tool window
     */
    private fun getVisibleCard(toolWindow: SidekickToolWindow): Component {
        // Return the currently visible card name
        return toolWindow.contentPanel.components
            .first { it.isVisible }
    }

    /**
     * Helper method to get the task list model from the tool window
     */
    private fun getTaskListModel(toolWindow: SidekickToolWindow): TaskListModel = toolWindow.getTaskListModel()

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

        // Verify the loading panel is visible
        assertEquals(LoadingPanel.NAME, getVisibleCard(toolWindow).name)

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
                status = TaskStatus.IN_PROGRESS,
                agentType = AgentType.LLM,
                flowType = "flow1",
                created = Clock.System.now(),
                updated = Clock.System.now(),
            ),
            Task(
                id = "task2",
                workspaceId = "workspace1",
                description = "Task 2",
                status = TaskStatus.BLOCKED,
                agentType = AgentType.HUMAN,
                flowType = "flow2",
                created = Clock.System.now(),
                updated = Clock.System.now().plus(1.seconds),
            ),
        )
        coEvery { mockSidekickService.getTasks("workspace1") } returns ApiResponse.Success(tasks)

        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)

        // Verify the task creation panel is visible
        assertEquals("TASK_CREATE", getVisibleCard(toolWindow).name)

        // Verify the task list contains the expected tasks, most recently updated first
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(2, taskListModel.size)
        assertEquals("task1", taskListModel.getElementAt(1).id)
        assertEquals("task2", taskListModel.getElementAt(0).id)
        assertEquals(TaskStatus.IN_PROGRESS, taskListModel.getElementAt(1).status)
        assertEquals(TaskStatus.BLOCKED, taskListModel.getElementAt(0).status)
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

        // Verify the task list panel is visible
        assertEquals("TASK_CREATE", getVisibleCard(toolWindow).name)

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

        // Verify the loading panel is visible
        assertEquals("LOADING", getVisibleCard(toolWindow).name)

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

        // Verify the task list panel is visible
        assertEquals("TASK_CREATE", getVisibleCard(toolWindow).name)

        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
}
