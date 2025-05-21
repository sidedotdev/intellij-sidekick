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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class UserRequestComponent(
    private val flowAction: FlowAction,
    private val sidekickService: SidekickService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : JBPanel<UserRequestComponent>() {

    internal lateinit var requestContentLabel: JBLabel
    internal lateinit var inputTextArea: JBTextArea
    internal lateinit var inputScrollPane: JBScrollPane
    internal lateinit var submitButton: JButton
    internal lateinit var errorLabel: JBLabel

    init {
        border = JBUI.Borders.empty(10)
        layout = VerticalLayout(JBUI.scale(8))

        val actionStatus = flowAction.actionStatus
        // Ensure jsonPrimitive is accessed safely
        val requestKindElement = flowAction.actionParams["requestKind"]
        val requestKind = if (requestKindElement is JsonPrimitive && requestKindElement.isString) requestKindElement.content else null

        if (actionStatus == ActionStatus.PENDING && requestKind == "free_form") {
            setupFreeFormPendingUI()
        } else {
            // Placeholder for other states/kinds or if data is missing
            add(JBLabel("This action type or status is not currently supported or parameters are missing.").apply {
                horizontalAlignment = SwingConstants.CENTER
            })
        }
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
                handleSubmitAction()
            }
        }
        add(submitButton)

        errorLabel = JBLabel("").apply {
            foreground = JBColor.RED
            isVisible = false
            // Allow text to wrap if it's long
            // A common way is to use HTML, but for simple errors, direct text is fine.
            // If errors can be long, consider wrapping in a panel or using a JTextPane.
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
    }

    private fun updateButtonState() {
        submitButton.isEnabled = inputTextArea.text.isNotBlank()
    }

    private fun handleSubmitAction() {
        errorLabel.isVisible = false
        errorLabel.text = ""

        val inputText = inputTextArea.text
        // Button should be disabled if text is blank, but double check
        if (inputText.isBlank()) {
            return
        }

        CoroutineScope(dispatcher).launch {
            val payload = UserResponsePayload(
                userResponse = UserResponse(content = inputText, approved = null)
            )
            when (val response = sidekickService.completeFlowAction(flowAction.workspaceId, flowAction.id, payload)) {
                is ApiResponse.Success -> {
                    // TODO: Handle success, e.g., update UI to completed state (for a later step)
                    // For now, can disable input and button
                    inputTextArea.isEnabled = false
                    submitButton.isEnabled = false
                    // Potentially show a success message or transition UI
                }
                is ApiResponse.Error -> {
                    errorLabel.text = "<html>${response.error.error.replace("\n", "<br>")}</html>"
                    errorLabel.isVisible = true
                }
            }
        }
    }
}
