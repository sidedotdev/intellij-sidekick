package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.FlowOptions
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.TaskRequest
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class TaskCreationPanelTest : BasePlatformTestCase() {
    private lateinit var sidekickService: SidekickService
    private lateinit var taskCreationPanel: TaskCreationPanel
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")
    private var taskCreatedCallbackInvoked = false

    override fun setUp() {
        super.setUp()
        Dispatchers.setMain(mainThreadSurrogate)
        
        sidekickService = mockk()
        taskCreatedCallbackInvoked = false
        
        taskCreationPanel = TaskCreationPanel(
            sidekickService = sidekickService,
            workspaceId = "ws_123",
            onTaskCreated = { taskCreatedCallbackInvoked = true }
        )
    }

    override fun tearDown() {
        super.tearDown()
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    fun testCreateTaskClearsFormWithoutApiCall() = runTest {
        val task = Task(
            id = "1",
            description = "test",
            status = "TODO",
            workspaceId = "ws_123",
            agentType = "llm",
            flowType = "basic_dev",
            created = "",
            updated = "",
        )

        // Given: Mock service that will return success
        coEvery { 
            sidekickService.createTask(
                any(),
                any()
            )
        } returns ApiResponse.Success(task)

        // When: Fill the form
        val description = "Test task description"
        taskCreationPanel.descriptionTextArea.text = description

        // And: Click create button
        taskCreationPanel.createButton.doClick()


        // FIXME: need a better way to wait for button event to be processed
        Thread.sleep(100)

        // Wait for all coroutines to complete
        advanceUntilIdle()

        // Then: Form is cleared
        assertEquals("", taskCreationPanel.descriptionTextArea.text)
        assertTrue(taskCreationPanel.determineRequirementsCheckbox.isSelected)

        // And: API was called
        coVerify(exactly = 1) {
            sidekickService.createTask(
                workspaceId = "ws_123",
                any()
            )
        }

        // And callback was invoked
        assertTrue(taskCreatedCallbackInvoked)
    }
}