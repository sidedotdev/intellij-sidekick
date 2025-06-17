package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.UserResponse
import com.github.sidedev.sidekick.api.UserResponsePayload
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.components.JBLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import com.github.sidedev.sidekick.api.UserRequestActionResult // Added import
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlinx.serialization.encodeToString // Added import
import kotlinx.serialization.json.Json // Added import


@OptIn(ExperimentalCoroutinesApi::class)
class UserRequestComponentTest : UsefulTestCase() {
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
        requestContent: String?,
        params: Map<String, JsonElement> = mutableMapOf<String, JsonElement>().apply {
            this["requestKind"] = JsonPrimitive(kind)
            if (requestContent != null) {
                this["requestContent"] = JsonPrimitive(requestContent)
            }
        },
        actionResult: String = ""
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
            actionResult = actionResult,
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

        assertFalse(userRequestComponent.submitButton.isEnabled) // initially disabled

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
        assertTrue(userRequestComponent.inputTextArea.isEditable)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.submitButton.doClick()
        
        coVerify { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) }
        // Check UI changes on success (e.g., disabled fields)
        assertFalse(userRequestComponent.inputTextArea.isEditable)
        assertFalse(userRequestComponent.submitButton.isVisible)
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
        assertTrue(userRequestComponent.inputTextArea.isVisible) // Should still be enabled to allow retry
        assertTrue(userRequestComponent.submitButton.isVisible)


        // Setup for successful retry
        coEvery {
            sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload)
        } returns ApiResponse.Success(Unit)

        userRequestComponent.submitButton.doClick() // Retry

        assertFalse(userRequestComponent.errorLabel.isVisible)
        assertEquals("", userRequestComponent.errorLabel.text) // Text should be cleared
        // Check UI changes on success
        assertFalse(userRequestComponent.inputTextArea.isEditable)
        assertFalse(userRequestComponent.submitButton.isVisible)
    }
    
    @Test
    fun `test component shows placeholder for non-freeform PENDING action`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "some_other_kind", "Do this?")
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        assertEquals("Unsupported request kind: some_other_kind", userRequestComponent.unsupportedLabel.text)
    }
    
    @Test
    fun `test approval PENDING state renders correctly with default button texts`() = runTest(testDispatcher) {
        val requestText = "Please approve this action."
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", requestText)
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        assertNotNull(userRequestComponent.requestContentLabel)
        assertEquals("<html>${requestText.replace("\n", "<br>")}</html>", userRequestComponent.requestContentLabel.text)
        assertNotNull(userRequestComponent.inputTextArea) // Assumed to be present for optional comments
        assertTrue(userRequestComponent.inputTextArea.isEditable)
        assertNotNull(userRequestComponent.approveButton)
        assertEquals("Approve", userRequestComponent.approveButton.text)
        assertTrue(userRequestComponent.approveButton.isEnabled)
        assertTrue(userRequestComponent.approveButton.isVisible)
        assertNotNull(userRequestComponent.rejectButton)
        assertEquals("Reject", userRequestComponent.rejectButton.text)
        assertTrue(userRequestComponent.rejectButton.isEnabled)
        assertTrue(userRequestComponent.rejectButton.isVisible)
        assertNotNull(userRequestComponent.errorLabel)
        assertFalse(userRequestComponent.errorLabel.isVisible)
    }

    @Test
    fun `test approval PENDING state renders correctly with custom button texts`() = runTest(testDispatcher) {
        val requestText = "Confirm action?"
        val approveText = "Yes, Proceed!"
        val rejectText = "No, Cancel."
        val params = mapOf(
            "requestKind" to JsonPrimitive("approval"),
            "requestContent" to JsonPrimitive(requestText),
            "approveButtonText" to JsonPrimitive(approveText),
            "rejectButtonText" to JsonPrimitive(rejectText)
        )
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", requestText, params)
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)

        assertEquals(approveText, userRequestComponent.approveButton.text)
        assertEquals(rejectText, userRequestComponent.rejectButton.text)
    }
    
    @Test
    fun `test approve action calls service with correct params for approval PENDING`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", "Approve it.")
        val userInput = "User comment for approval"
        val expectedPayload = UserResponsePayload(UserResponse(content = userInput, approved = true))

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Success(Unit)

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.approveButton.doClick()

        coVerify { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) }
        assertFalse(userRequestComponent.inputTextArea.isEditable)
        assertTrue(userRequestComponent.inputTextArea.isVisible)
        assertFalse(userRequestComponent.approveButton.isVisible)
        assertFalse(userRequestComponent.rejectButton.isVisible)
    }

    @Test
    fun `test reject action calls service with correct params for approval PENDING`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", "Reject it.")
        val userInput = "User comment for rejection"
        val expectedPayload = UserResponsePayload(UserResponse(content = userInput, approved = false))

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Success(Unit)

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.rejectButton.doClick()

        coVerify { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) }
        assertFalse(userRequestComponent.inputTextArea.isEditable)
        assertTrue(userRequestComponent.inputTextArea.isVisible)
        assertFalse(userRequestComponent.approveButton.isVisible)
        assertFalse(userRequestComponent.rejectButton.isVisible)
    }
    
    @Test
    fun `test API error displays and clears for approval PENDING on approve`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", "Approve with error.")
        val userInput = "Error test input"
        val errorMessage = "Approval failed!"
        val expectedPayload = UserResponsePayload(UserResponse(content = userInput, approved = true))

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Error(ApiError(errorMessage))

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = userInput
        userRequestComponent.approveButton.doClick()

        assertTrue(userRequestComponent.errorLabel.isVisible)
        assertEquals("<html>${errorMessage}</html>", userRequestComponent.errorLabel.text)
        assertTrue(userRequestComponent.inputTextArea.isEditable)
        assertTrue(userRequestComponent.inputTextArea.isVisible)
        assertTrue(userRequestComponent.approveButton.isVisible)
        assertTrue(userRequestComponent.rejectButton.isVisible)

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Success(Unit)
        userRequestComponent.approveButton.doClick() // Retry

        assertFalse(userRequestComponent.errorLabel.isVisible)
        assertEquals("", userRequestComponent.errorLabel.text)
        assertFalse(userRequestComponent.inputTextArea.isEditable)
        assertFalse(userRequestComponent.approveButton.isVisible)
        assertFalse(userRequestComponent.rejectButton.isVisible)
    }
    
    @Test
    fun `test approve action with blank input sends null content`() = runTest(testDispatcher) {
        val flowAction = createTestFlowAction(ActionStatus.PENDING, "approval", "Approve it.")
        val expectedPayload = UserResponsePayload(UserResponse(content = null, approved = true))

        coEvery { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) } returns ApiResponse.Success(Unit)

        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        userRequestComponent.inputTextArea.text = "   " // Blank input
        userRequestComponent.approveButton.doClick()

        coVerify { sidekickServiceMock.completeFlowAction(testWorkspaceId, testFlowActionId, expectedPayload) }
    }


    @Test
    fun `test COMPLETE free_form action with valid actionResult`() = runTest(testDispatcher) {
        val requestContent = "Please provide your analysis"
        val resultContent = "Here is my detailed analysis of the situation"
        val actionResult = UserRequestActionResult(content = resultContent, approved = null)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "free_form", 
            requestContent,
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please provide your analysis</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have result label
        assertEquals("<html><b>Result:</b><br>Here is my detailed analysis of the situation</html>", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE approval action with approved true`() = runTest(testDispatcher) {
        val requestContent = "Please approve this change"
        val resultContent = "Looks good to me"
        val actionResult = UserRequestActionResult(content = resultContent, approved = true)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "approval", 
            requestContent,
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please approve this change</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have status label showing approved
        assertEquals("<html><b>Status:</b> ✅ Approved</html>", userRequestComponent.statusLabel.text)
        
        // Should have result label with content
        assertEquals("<html><b>Result:</b><br>Looks good to me</html>", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE approval action with approved false`() = runTest(testDispatcher) {
        val requestContent = "Please approve this change"
        val resultContent = "I have concerns about this approach"
        val actionResult = UserRequestActionResult(content = resultContent, approved = false)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "approval", 
            requestContent,
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please approve this change</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have status label showing rejected
        assertEquals("<html><b>Status:</b> ❌ Rejected</html>", userRequestComponent.statusLabel.text)
        
        // Should have result label with content
        assertEquals("<html><b>Result:</b><br>I have concerns about this approach</html>", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE action with missing actionResult`() = runTest(testDispatcher) {
        val requestContent = "Please provide feedback"
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "free_form", 
            requestContent,
            actionResult = "",
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please provide feedback</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have error label for missing result
        assertEquals("Result: No action result available.", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE action with invalid JSON in actionResult`() = runTest(testDispatcher) {
        val requestContent = "Please provide feedback"
        val invalidJson = "{ invalid json content"
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "free_form", 
            requestContent,
            actionResult = invalidJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please provide feedback</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have error label for JSON parsing error
        assertTrue("Error label should contain parsing error message", 
            userRequestComponent.resultLabel.text.contains("Error parsing action result:"))
    }

    @Test
    fun `test COMPLETE action with missing requestContent`() = runTest(testDispatcher) {
        val resultContent = "Task completed successfully"
        val actionResult = UserRequestActionResult(content = resultContent, approved = null)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "free_form", 
            null, // No request content
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label with default message
        assertEquals("<html><b>Original Request:</b><br>No original request content available.</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have result label
        assertEquals("<html><b>Result:</b><br>Task completed successfully</html>", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE free_form action with empty content shows appropriate message`() = runTest(testDispatcher) {
        val requestContent = "Please provide feedback"
        val actionResult = UserRequestActionResult(content = "", approved = null)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "free_form", 
            requestContent,
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please provide feedback</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have message for no content submitted
        assertEquals("Result: No content submitted.", userRequestComponent.resultLabel.text)
    }

    @Test
    fun `test COMPLETE approval action with empty content shows appropriate message`() = runTest(testDispatcher) {
        val requestContent = "Please approve this change"
        val actionResult = UserRequestActionResult(content = "", approved = true)
        val actionResultJson = Json.encodeToString(actionResult)
        
        val flowAction = createTestFlowAction(
            ActionStatus.COMPLETE, 
            "approval", 
            requestContent,
            actionResult = actionResultJson
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        
        // Should have original request label
        assertEquals("<html><b>Original Request:</b><br>Please approve this change</html>", userRequestComponent.originalRequestLabel.text)
        
        // Should have status label showing approved
        assertEquals("<html><b>Status:</b> ✅ Approved</html>", userRequestComponent.statusLabel.text)
        
        // Should have message for no comments provided
        assertEquals("Result: No comments provided.", userRequestComponent.resultLabel.text)
    }
    
    @Test
    fun `test component handles missing requestKind or requestContent`() = runTest(testDispatcher) {
        // Missing requestKind
        var flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form", // This kind is in the helper, but we override params
            "Content", // This content is in the helper, but we override params
            params = mapOf("requestContent" to JsonPrimitive("Some content")) // requestKind is missing
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        var label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for missing kind", label)
        assertEquals("Missing or invalid request kind.", label!!.text)

        // Missing requestContent (but kind is free_form)
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form",
            null, // Explicitly pass null for requestContent
            params = mapOf("requestKind" to JsonPrimitive("free_form")) // requestContent is missing from params
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        // This will proceed to setupFreeFormPendingUI but requestContentLabel will show default message
        assertNotNull(userRequestComponent.requestContentLabel)
        assertEquals("<html>No request content provided or content is not a string.</html>", userRequestComponent.requestContentLabel.text)
        
        // Missing requestContent (but kind is approval)
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "approval",
            null, // Explicitly pass null for requestContent
            params = mapOf("requestKind" to JsonPrimitive("approval")) // requestContent is missing from params
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        // This will proceed to setupApprovalPendingUI but requestContentLabel will show default message
        assertNotNull(userRequestComponent.requestContentLabel)
        assertEquals("<html>No request content provided or content is not a string.</html>", userRequestComponent.requestContentLabel.text)


        // Both missing
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form", // This kind is in the helper, but we override params
            null, // This content is in the helper, but we override params
            params = mapOf("someOtherParam" to JsonPrimitive("value")) // Both kind and content missing
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for both missing", label)
        assertEquals("Missing or invalid request kind.", label!!.text)

        // requestKind is JsonNull
        flowAction = createTestFlowAction(
            ActionStatus.PENDING,
            "free_form", // This kind is in the helper, but we override params
            "Content", // This content is in the helper, but we override params
            params = mapOf("requestKind" to JsonNull, "requestContent" to JsonPrimitive("Some content"))
        )
        userRequestComponent = UserRequestComponent(flowAction, sidekickServiceMock, testDispatcher)
        label = userRequestComponent.getComponent(0) as? JBLabel
        assertNotNull("Component should have a label for null kind", label)
        assertEquals("Missing or invalid request kind.", label!!.text)

    }
}
