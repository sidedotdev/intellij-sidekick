package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.UserResponse
import com.github.sidedev.sidekick.api.UserResponsePayload
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4


@OptIn(ExperimentalCoroutinesApi::class)
@RunsInEdt // Ensure Swing components are accessed on the EDT
@RunWith(JUnit4::class) // Required for @Rule and @Test to work with BasePlatformTestCase
class UserRequestComponentTest : BasePlatformTestCase() {

    @get:Rule val edtRule = EdtRule() // Ensures tests run on EDT

    private lateinit var sidekickServiceMock: SidekickService
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var userRequestComponent: UserRequestComponent

    private val testWorkspaceId = "ws_test_123"
    private val testFlowActionId = "fa_test_456"

    override fun setUp() {
        super.setUp()
        sidekickServiceMock = mockk()
        testDispatcher = UnconfinedTestDispatcher()
    }

    private fun createTestFlowAction(
        status: ActionStatus,
        kind: String,
        requestContent: String,
        params: Map<String, JsonElement> = mapOf(
            "requestKind" to JsonPrimitive(kind),
            "requestContent" to JsonPrimitive(requestContent)
        )
    ): FlowAction {
        return FlowAction(
            id = testFlowActionId,
            flowId = "flow_id_abc",
            subflowId = null,
            workspaceId = testWorkspaceId,
            created = Clock.System.now(),
            updated = Clock.System.now(),
            actionType = "USER_REQUEST",
            actionParams = params,
            actionStatus = status,
            actionResult = "", // Not used in PENDING state
            isHumanAction = true
        )
    }

    @Test
    fun `test free_form PENDING state renders correctly`() = runTest(testDispatcher) {
        val requestText = "Please provide your input:"
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "free_form", requestText)
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        assertNotNull(userRequestComponent.requestContentLabel)
        assertEquals("<html>${requestText.replace("\n", "<br>")}</html>", userRequestComponent.requestContentLabel.text)
        assertNotNull(userRequestComponent.inputTextArea)
        assertTrue(userRequestComponent.inputTextArea.isEditable)
        assertNotNull(userRequestComponent.submitButton)
        assertEquals("Submit", userRequestComponent.submitButton.text)
        assertFalse(userRequestComponent.submitButton.isEnabled) // Initially disabled
        assertNotNull(userRequestComponent.errorLabel)
        assertFalse(userRequestComponent.errorLabel.isVisible)
    }

    @Test
    fun `test submit button enablement for free_form PENDING`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "free_form", "Enter details")
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        assertFalse(userRequestComponent.submitButton.isEnabled)

        userRequestComponent.inputTextArea.text = "Some input"
        assertTrue(userRequestComponent.submitButton.isEnabled)

        userRequestComponent.inputTextArea.text = "  " // Whitespace only
        assertFalse(userRequestComponent.submitButton.isEnabled)


        userRequestComponent.inputTextArea.text = ""
        assertFalse(userRequestComponent.submitButton.isEnabled)
    }

    @Test
    fun `test submit action calls service with correct params for free_form PENDING`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "free_form", "Your input?")
        val userInput = "This is my detailed response."
        val expectedPayload = UserResponsePayload(UserResponse(content = userInput, approved = null))

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Success(Unit)

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.submitButton.doClick()
        
        coVerify { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) }
        // Check UI changes on success (e.g., disabled fields)
        assertFalse(userRequestComponent.inputTextArea.isEnabled)
        assertFalse(userRequestComponent.submitButton.isEnabled)
    }

    @Test
    fun `test API error displays in errorLabel and clears on retry for free_form PENDING`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "free_form", "Input required.")
        val userInput = "Some text"
        val errorMessage = "Network failure"
        val expectedPayload = UserResponsePayload(UserResponse(content = userInput, approved = null))

        coEvery {
            sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload)
        } returns ApiResponse.Error(ApiError(errorMessage))

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.submitButton.doClick()

        assertTrue(userRequestComponent.errorLabel.isVisible)
        assertEquals("<html>${errorMessage}</html>", userRequestComponent.errorLabel.text)
        assertTrue(userRequestComponent.inputTextArea.isEnabled) // Should still be enabled to allow retry
        assertTrue(userRequestComponent.submitButton.isEnabled)


        // Setup for successful retry
        coEvery {
            sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload)
        } returns ApiResponse.Success(Unit)

        userRequestComponent.submitButton.doClick() // Retry

        assertFalse(userRequestComponent.errorLabel.isVisible)
        assertEquals("", userRequestComponent.errorLabel.text) // Text should be cleared
        // Check UI changes on success
        assertFalse(userRequestComponent.inputTextArea.isEnabled)
        assertFalse(userRequestComponent.submitButton.isEnabled)
    }
    
    @Test
    fun `test component shows placeholder for non-freeform PENDING action`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", "Approve this?")
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        val label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull(label)
        assertEquals("This action type or status is not currently supported or parameters are missing.", label!!.text)
    }

    @Test
    fun `test component shows placeholder for COMPLETED free_form action`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.COMPLETE, "free_form", "Done.")
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        val label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull(label)
        assertEquals("This action type or status is not currently supported or parameters are missing.", label!!.text)
    }
    
    @Test
    fun `test component handles missing requestKind or requestContent`() = runTest(testDispatcher) {
        // Missing requestKind
        var flowAction = createTestFlowAction(
            ActionStatus.PENDING, 
            "free_form", // This kind is in the helper, but we override params
            "Content",
            params = mapOf("requestContent" to JsonPrimitive("Some content")) // requestKind is missing
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        var label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for missing kind", label)
        assertEquals("This action type or status is not currently supported or parameters are missing.", label!!.text)

        // Missing requestContent (but kind is free_form)
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form", 
            "", // This content is in the helper, but we override params
            params = mapOf("requestKind" to JsonPrimitive("free_form")) // requestContent is missing
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        // This will proceed to setupFreeFormPendingUI but requestContentLabel will show default message
        assertNotNull(userRequestComponent.requestContentLabel)
        assertEquals("<html>No request content provided or content is not a string.</html>", userRequestComponent.requestContentLabel.text)

        // Both missing
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form",
            "",
            params = mapOf("someOtherParam" to JsonPrimitive("value")) // Both kind and content missing
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for both missing", label)
        assertEquals("This action type or status is not currently supported or parameters are missing.", label!!.text)
        
        // requestKind is JsonNull
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form",
            "Content",
            params = mapOf("requestKind" to JsonNull, "requestContent" to JsonPrimitive("Some content"))
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for null kind", label)
        assertEquals("This action type or status is not currently supported or parameters are missing.", label!!.text)

    }


    // BasePlatformTestCase doesn't require getTestDataPath if not used,
    // but if it were needed:
    // override fun getTestDataPath(): String = "src/test/testData"
}
