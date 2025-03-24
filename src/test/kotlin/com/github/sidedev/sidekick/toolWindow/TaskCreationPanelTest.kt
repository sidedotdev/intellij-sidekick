package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCreationPanelTest : BasePlatformTestCase() {
    private lateinit var sidekickService: SidekickService
    private lateinit var taskCreationPanel: TaskCreationPanel
    private var taskCreatedCallbackInvoked = false
    private val testDispatcher = UnconfinedTestDispatcher()

    override fun setUp() {
        super.setUp()
        Dispatchers.setMain(testDispatcher)

        sidekickService = mockk()
        taskCreatedCallbackInvoked = false

        taskCreationPanel = TaskCreationPanel(
            sidekickService = sidekickService,
            workspaceId = "ws_123",
            onTaskCreated = { taskCreatedCallbackInvoked = true },
            dispatcher = testDispatcher,
        )
    }

    override fun tearDown() {
        super.tearDown()
        Dispatchers.resetMain()
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
                any(),
            )
        } returns ApiResponse.Success(task)

        // When: Fill the form
        val description = "Test task description"
        taskCreationPanel.descriptionTextArea.text = description

        // And: Click create button
        taskCreationPanel.createButton.doClick()

        // Then: Form is cleared
        assertEquals("", taskCreationPanel.descriptionTextArea.text)
        assertTrue(taskCreationPanel.determineRequirementsCheckbox.isSelected)

        // And: API was called
        coVerify(exactly = 1) {
            sidekickService.createTask(
                workspaceId = "ws_123",
                any(),
            )
        }

        // And callback was invoked
        assertTrue(taskCreatedCallbackInvoked)
    }
}
