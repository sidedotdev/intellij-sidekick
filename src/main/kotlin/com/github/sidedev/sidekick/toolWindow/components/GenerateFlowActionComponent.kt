package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.models.ActionStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import javax.swing.BoxLayout

@Serializable
internal data class ActionResultPayload(val content: String? = null)

class GenerateFlowActionComponent(initialFlowAction: FlowAction) :
    JBPanel<GenerateFlowActionComponent>(),
    IUpdatableFlowActionPanel {

    internal var flowAction: FlowAction = initialFlowAction
        private set // Keep the public setter for flowAction as per plan, but allow internal update via method

    internal val actionTypeLabel: JBLabel
    internal val actionResultLabel: JBLabel

    init {
        layout = BoxLayout(this, BoxLayout.PAGE_AXIS)

        actionTypeLabel = JBLabel()
        actionResultLabel = JBLabel()

        add(actionTypeLabel)
        add(actionResultLabel)

        update(initialFlowAction)
    }

    override fun update(newFlowAction: FlowAction) {
        this.flowAction = newFlowAction

        actionTypeLabel.text = newFlowAction.actionType

        var displayResult = newFlowAction.actionResult
        if (newFlowAction.actionType.startsWith("generate.") &&
            newFlowAction.actionStatus == ActionStatus.COMPLETE) {
            if (newFlowAction.actionResult.isNotBlank()) {
                try {
                    val payload = Json.decodeFromString<ActionResultPayload>(newFlowAction.actionResult)
                    payload.content?.let {
                        displayResult = it
                    }
                } catch (e: SerializationException) {
                    // Fallback to raw actionResult is implicit as displayResult is already set.
                    // Optionally log: System.err.println("Failed to parse ActionResultPayload for action ${newFlowAction.id}: ${e.message}")
                }
            }
        }
        actionResultLabel.text = displayResult
    }
}