package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.Workspace
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.swing.JLabel

class SidekickToolWindowTest : BasePlatformTestCase() {
    private lateinit var toolWindowFactory: SidekickToolWindowFactory
    private lateinit var mockSidekickService: SidekickService
    
    override fun setUp() {
        super.setUp()
        
        // Create a mock SidekickService
        mockSidekickService = mockk()
        
        // Create the factory
        toolWindowFactory = SidekickToolWindowFactory()
        
        // Mock the ApplicationManager for UI updates
        mockkStatic(ApplicationManager::class)
        val mockApplication = mockk<com.intellij.openapi.application.Application>()
        every { ApplicationManager.getApplication() } returns mockApplication
        
        // Mock executeOnPooledThread to execute the runnable immediately
        every { 
            mockApplication.executeOnPooledThread(any()) 
        } answers { 
            firstArg<Runnable>().run()
            mockk()
        }
        
        // Mock invokeLater to execute the runnable immediately
        every { 
            mockApplication.invokeLater(any()) 
        } answers { 
            firstArg<Runnable>().run()
        }
    }
    
    override fun tearDown() {
        super.tearDown()
    }
    
    /**
     * Helper method to create a SidekickToolWindow instance with our mock service
     */
    private fun createToolWindowWithMockService(): Any {
        // Create a mock ToolWindow
        val mockToolWindow = mockk<com.intellij.openapi.wm.ToolWindow>()
        
        // Create the SidekickToolWindow instance using reflection
        val constructor = SidekickToolWindowFactory::class.java.getDeclaredClasses()[0].getDeclaredConstructor(
            SidekickToolWindowFactory::class.java,
            com.intellij.openapi.wm.ToolWindow::class.java,
            com.intellij.openapi.project.Project::class.java
        )
        constructor.isAccessible = true
        
        val toolWindow = constructor.newInstance(toolWindowFactory, mockToolWindow, project)
        
        // Replace the SidekickService with our mock
        val serviceField = toolWindow.javaClass.getDeclaredField("sidekickService")
        serviceField.isAccessible = true
        serviceField.set(toolWindow, mockSidekickService)
        
        return toolWindow
    }
    
    /**
     * Helper method to invoke the updateWorkspaceContent method using reflection
     */
    private fun invokeUpdateWorkspaceContent(toolWindow: Any) {
        val method = toolWindow.javaClass.getDeclaredMethod("updateWorkspaceContent")
        method.isAccessible = true
        runBlocking {
            method.invoke(toolWindow)
        }
    }
    
    /**
     * Helper method to get the status label from the tool window
     */
    private fun getStatusLabel(toolWindow: Any): JLabel {
        val field = toolWindow.javaClass.getDeclaredField("statusLabel")
        field.isAccessible = true
        return field.get(toolWindow) as JLabel
    }
    
    /**
     * Helper method to get the task list model from the tool window
     */
    private fun getTaskListModel(toolWindow: Any): TaskListModel {
        val field = toolWindow.javaClass.getDeclaredField("taskListModel")
        field.isAccessible = true
        return field.get(toolWindow) as TaskListModel
    }
    
    fun testNoMatchingWorkspace() {
        // Mock workspaces response with no matching workspace
        val workspaces = listOf(
            Workspace(id = "workspace1", name = "Workspace 1", localRepoDir = "/some/other/path")
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)
        
        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)
        
        // Verify the status label shows the correct message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "No workspace set up yet"),
            statusLabel.text
        )
        
        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
    
    fun testMatchingWorkspaceWithTasks() {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)
        
        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(id = "workspace1", name = "Workspace 1", localRepoDir = projectPath!!)
        )
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Success(workspaces)
        
        // Mock tasks response
        val tasks = listOf(
            Task(
                id = "task1",
                workspaceId = "workspace1",
                description = "Task 1",
                status = "In Progress",
                created = java.time.Instant.now(),
                updated = java.time.Instant.now()
            ),
            Task(
                id = "task2",
                workspaceId = "workspace1",
                description = "Task 2",
                status = "Done",
                created = java.time.Instant.now(),
                updated = java.time.Instant.now()
            )
        )
        coEvery { mockSidekickService.getTasks("workspace1") } returns ApiResponse.Success(tasks)
        
        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)
        
        // Verify the status label shows the correct message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "Tasks for workspace workspace1"),
            statusLabel.text
        )
        
        // Verify the task list contains the expected tasks
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(2, taskListModel.size)
    }
    
    fun testMatchingWorkspaceWithNoTasks() {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)
        
        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(id = "workspace1", name = "Workspace 1", localRepoDir = projectPath!!)
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
            statusLabel.text
        )
        
        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
    
    fun testErrorFetchingWorkspaces() {
        // Mock error response for workspaces
        coEvery { mockSidekickService.getWorkspaces() } returns ApiResponse.Error(ApiError("Connection error"))
        
        // Create tool window and execute the method under test
        val toolWindow = createToolWindowWithMockService()
        invokeUpdateWorkspaceContent(toolWindow)
        
        // Verify the status label shows the error message
        val statusLabel = getStatusLabel(toolWindow)
        assertEquals(
            MyBundle.message("statusLabel", "Side is not running. Please run `side start`. Error: Connection error"),
            statusLabel.text
        )
        
        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
    
    fun testErrorFetchingTasks() {
        // Set up the project path
        val projectPath = project.basePath
        assertNotNull("Project path should not be null", projectPath)
        
        // Mock workspaces response with a matching workspace
        val workspaces = listOf(
            Workspace(id = "workspace1", name = "Workspace 1", localRepoDir = projectPath!!)
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
            statusLabel.text
        )
        
        // Verify the task list is empty
        val taskListModel = getTaskListModel(toolWindow)
        assertEquals(0, taskListModel.size)
    }
}