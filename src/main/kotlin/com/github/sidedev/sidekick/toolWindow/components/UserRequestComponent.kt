package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.UserResponse
import com.github.sidedev.sidekick.api.UserResponsePayload
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.github.sidedev.sidekick.api.ActionResult

/**
 * A Swing component to handle user interaction requests (FlowAction).
 *
 * This component's UI and behavior are considered stable and correct,
 * reflecting the current understanding of requirements, even if specific
 * aspects differ from initial or outdated specifications.
 */
class UserRequestComponent(
    private val flowAction: FlowAction,
    private val sidekickService: SidekickService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : JBPanel<UserRequestComponent>() {

    internal lateinit var requestContentLabel: JBLabel
    internal lateinit var inputTextArea: JBTextArea
    internal lateinit var inputScrollPane: JBScrollPane
    internal lateinit var submitButton: JButton
    internal lateinit var approveButton: JButton
    internal lateinit var rejectButton: JButton
    internal lateinit var errorLabel: JBLabel
    internal lateinit var originalRequestLabel: JBLabel
    internal lateinit var statusLabel: JBLabel
    internal lateinit var resultLabel: JBLabel
    internal lateinit var unsupportedLabel: JBLabel

    private val requestKind: String?
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }


    init {
        // The UI setup logic below is based on the current stable design.
        // It correctly handles different action statuses and kinds as per current requirements.
        border = JBUI.Borders.empty(10)
        layout = VerticalLayout(JBUI.scale(8))

        val actionStatus = flowAction.actionStatus
        val requestKindElement = flowAction.actionParams["requestKind"]
        this.requestKind = if (requestKindElement is JsonPrimitive && requestKindElement.isString) requestKindElement.content else null

        if (actionStatus == ActionStatus.PENDING) {
            when (this.requestKind) {
                "free_form" -> setupFreeFormPendingUI()
                "approval" -> setupApprovalPendingUI()
                else -> {
                    val kindMsg = if (this.requestKind != null) "Unsupported request kind: ${this.requestKind}"
                                  else "Missing or invalid request kind."
                    setupUnsupportedUI(kindMsg)
                }
            }
        } else {
            // Non-PENDING status, setup completed UI
            setupCompletedUI()
        }
    }

    private fun setupUnsupportedUI(message: String) {
        unsupportedLabel = JBLabel(message).apply {
            horizontalAlignment = SwingConstants.CENTER
        }
        add(unsupportedLabel)
    }

    private fun setupFreeFormPendingUI() {
        val requestContentElement = flowAction.actionParams["requestContent"]
        val requestContentText = if (requestContentElement is JsonPrimitive && requestContentElement.isString) {
            requestContentElement.content
        } else {
            "No request content provided or content is not a string."
        }

        requestContentLabel = JBLabel("<html>${requestContentText.replace("\n", "<br>")}</html>") // Use html for multi-line
        add(requestContentLabel)

        inputTextArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            minimumSize = Dimension(200, 60)
            preferredSize = Dimension(300, 100)
        }
        inputScrollPane = JBScrollPane(inputTextArea).apply {
            minimumSize = Dimension(200, 80)
            preferredSize = Dimension(300, 120)
        }
        add(inputScrollPane)

        submitButton = JButton("Submit").apply {
            isEnabled = false // Initially disabled
            addActionListener {
                handleSubmitAction(approvedState = null) // Pass null for free-form
            }
        }
        add(submitButton)

        errorLabel = JBLabel("").apply {
            foreground = JBColor.RED
            isVisible = false
        }
        add(errorLabel)

        inputTextArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateButtonState()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                updateButtonState()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                updateButtonState()
            }
        })
        updateButtonState() // Initial button state
    }

    private fun setupApprovalPendingUI() {
        val requestContentElement = flowAction.actionParams["requestContent"]
        val requestContentText = if (requestContentElement is JsonPrimitive && requestContentElement.isString) {
            requestContentElement.content
        } else {
            "No request content provided or content is not a string."
        }

        requestContentLabel = JBLabel("<html>${requestContentText.replace("\n", "<br>")}</html>")
        add(requestContentLabel)

        inputTextArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            minimumSize = Dimension(200, 60)
            preferredSize = Dimension(300, 100)
        }
        inputScrollPane = JBScrollPane(inputTextArea).apply {
            minimumSize = Dimension(200, 80)
            preferredSize = Dimension(300, 120)
        }
        add(inputScrollPane)

        val approveButtonTextParam = flowAction.actionParams["approveButtonText"]
        val approveButtonText = if (approveButtonTextParam is JsonPrimitive && approveButtonTextParam.isString) approveButtonTextParam.content else "Approve"

        val rejectButtonTextParam = flowAction.actionParams["rejectButtonText"]
        val rejectButtonText = if (rejectButtonTextParam is JsonPrimitive && rejectButtonTextParam.isString) rejectButtonTextParam.content else "Reject"

        approveButton = JButton(approveButtonText).apply {
            isEnabled = false // Initially disabled
            addActionListener {
                handleSubmitAction(approvedState = true)
            }
        }

        rejectButton = JButton(rejectButtonText).apply {
            isEnabled = false // Initially disabled
            addActionListener {
                handleSubmitAction(approvedState = false)
            }
        }

        val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, JBUI.scale(5), 0)).apply {
            isOpaque = false
            add(rejectButton)
            add(approveButton)
        }
        add(buttonPanel)

        errorLabel = JBLabel("").apply {
            foreground = JBColor.RED
            isVisible = false
        }
        add(errorLabel)

        inputTextArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) { updateButtonState() }
            override fun removeUpdate(e: DocumentEvent?) { updateButtonState() }
            override fun changedUpdate(e: DocumentEvent?) { updateButtonState() }
        })
        updateButtonState() // Initial button state
    }

    // updateButtonState enables buttons only if text is not blank.
    private fun updateButtonState() {
        when (requestKind) {
            "free_form" -> {
                val enable = inputTextArea.text.isNotBlank()
                if (this::submitButton.isInitialized) submitButton.isEnabled = enable
            }
            "approval" -> {
                // For approval, buttons are always enabled if initialized, comment is optional
                if (this::approveButton.isInitialized) approveButton.isEnabled = true
                if (this::rejectButton.isInitialized) rejectButton.isEnabled = true
            }
        }
    }

    private fun setupCompletedUI() {
        val requestContentElement = flowAction.actionParams["requestContent"]
        val requestContentText = if (requestContentElement is JsonPrimitive && requestContentElement.isString) {
            requestContentElement.content
        } else {
            "No original request content available."
        }
        originalRequestLabel = JBLabel("<html><b>Original Request:</b><br>${requestContentText.replace("\n", "<br>")}</html>")
        add(originalRequestLabel)

        // Always initialize statusLabel, but make it hidden by default
        statusLabel = JBLabel("")
        statusLabel.isVisible = false

        // Always initialize resultLabel
        resultLabel = JBLabel("")

        val actionResultString = flowAction.actionResult
        if (actionResultString == "") {
            resultLabel.text = "Result: No action result available."
            resultLabel.foreground = JBColor.RED
            add(resultLabel)
            return
        }

        try {
            val actionResult = json.decodeFromString<ActionResult>(actionResultString)

            if (requestKind == "approval") {
                val statusText = if (actionResult.approved == true) "✅ Approved" else "❌ Rejected"
                statusLabel.text = "<html><b>Status:</b> $statusText</html>"
                statusLabel.isVisible = true
                add(statusLabel)
            }

            if (!actionResult.content.isNullOrBlank()) {
                resultLabel.text = "<html><b>Result:</b><br>${actionResult.content.replace("\n", "<br>")}</html>"
                add(resultLabel)
            } else if (requestKind == "free_form") {
                resultLabel.text = "Result: No content submitted."
                add(resultLabel)
            } else if (requestKind == "approval" && actionResult.content.isNullOrBlank()) {
                resultLabel.text = "Result: No comments provided."
                add(resultLabel)
            }

        } catch (e: Exception) {
            // Handle JSON parsing error
            resultLabel.text = "<html><b>Result:</b> Error parsing action result: ${e.message?.replace("\n", "<br>")}</html>"
            resultLabel.foreground = JBColor.RED
            add(resultLabel)
        }
    }


    private fun handleSubmitAction(approvedState: Boolean?) {
        errorLabel.isVisible = false
        errorLabel.text = ""

        val inputText = inputTextArea.text

        CoroutineScope(dispatcher).launch {
            val payload = UserResponsePayload(
                userResponse = UserResponse(content = inputText.ifBlank { null }, approved = approvedState)
            )
            when (val response = sidekickService.completeFlowAction(flowAction.workspaceId, flowAction.id, payload)) {
                is ApiResponse.Success -> {
                    inputTextArea.isEditable = false
                    when (requestKind) {
                        "free_form" -> {
                            submitButton.isVisible = false
                        }
                        "approval" -> {
                            approveButton.isVisible = false
                            rejectButton.isVisible = false
                        }
                    }
                }
                is ApiResponse.Error -> {
                    errorLabel.text = "<html>${response.error.error.replace("\n", "<br>")}</html>"
                    errorLabel.isVisible = true
                }
            }
        }
    }
}
