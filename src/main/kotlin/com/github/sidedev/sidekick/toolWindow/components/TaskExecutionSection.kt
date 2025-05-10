package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.models.ActionStatus
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JPanel

class TaskExecutionSection(
    name: String,
    // Use the content panel provided by the AccordionSection superclass
    // It's initialized here and passed to the super constructor
    initialContent: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty()
    },
) : AccordionSection(title = name, content = initialContent, initiallyExpanded = false) {

    internal data class CodeContextSubflowState(var subflow: Subflow, var latestNonTerminalAction: FlowAction? = null)

    // Map to store SubflowSummaryComponent instances by Subflow ID for "code_context" subflows
    internal val subflowSummaries = mutableMapOf<String, SubflowSummaryComponent>()
    // Map to store state for "code_context" subflows
    internal val codeContextSubflowStates = mutableMapOf<String, CodeContextSubflowState>()

    // Map to store FlowActionComponents by FlowAction ID
    // Make internal for testing
    internal val flowActionComponents = mutableMapOf<String, FlowActionComponent>()

    /**
     * Processes a FlowAction.
     * For "code_context" subflows, it updates or creates a single SubflowSummaryComponent.
     * For other subflows, it updates or creates a FlowActionComponent for the specific action.
     *
     * @param flowAction The FlowAction data.
     * @param subflow The Subflow associated with the FlowAction.
     */
    fun processAction(flowAction: FlowAction, subflow: Subflow?) {
        if (subflow?.type == "code_context") {
            val subflowId = subflow.id // subflow is non-null here due to the type check

            val state = codeContextSubflowStates.getOrPut(subflowId) {
                CodeContextSubflowState(subflow)
            }
            // Ensure state has the latest subflow object passed with this action
            state.subflow = subflow

            // Update latestNonTerminalAction based on the current flowAction
            val currentLatestAction = state.latestNonTerminalAction
            if (flowAction.actionStatus == ActionStatus.PENDING || flowAction.actionStatus == ActionStatus.STARTED) {
                if (currentLatestAction == null || flowAction.updated > currentLatestAction.updated) {
                    state.latestNonTerminalAction = flowAction
                }
            } else if (flowAction.actionStatus == ActionStatus.COMPLETE || flowAction.actionStatus == ActionStatus.FAILED) {
                // If the completed/failed action was the latest non-terminal one, clear it
                if (currentLatestAction?.id == flowAction.id) {
                    state.latestNonTerminalAction = null
                }
            }

            val summaryComponent = subflowSummaries.getOrPut(subflowId) {
                SubflowSummaryComponent().also { newComponent ->
                    content.add(newComponent)
                    content.revalidate()
                    content.repaint()
                }
            }
            summaryComponent.update(state.latestNonTerminalAction, state.subflow)
            // Individual FlowActionComponents are not created for "code_context" subflows
        } else {
            // Existing logic for non-"code_context" subflows or when subflow is null
            val existingComponent = flowActionComponents[flowAction.id]
            if (existingComponent != null) {
                // Update existing component
                existingComponent.update(flowAction)
                // The component's update method handles its repaint.
            } else {
                // Create and add new component
                val newComponent = FlowActionComponent(flowAction)
                flowActionComponents[flowAction.id] = newComponent
                content.add(newComponent)
                content.revalidate()
                content.repaint()
            }
        }
    }

    /**
     * Updates the state and summary component for a "code_context" subflow, typically when its status changes.
     * This method is primarily for reacting to Subflow status updates that don't come via a FlowAction.
     *
     * @param updatedSubflow The Subflow data with updated information (e.g., status).
     */
    fun updateSubflow(updatedSubflow: Subflow) {
        if (updatedSubflow.type == "code_context") {
            val subflowId = updatedSubflow.id

            val state = codeContextSubflowStates.getOrPut(subflowId) {
                // If state doesn't exist when subflow update comes, create it.
                // latestNonTerminalAction will be null initially if state is created here.
                CodeContextSubflowState(updatedSubflow)
            }
            // Always update the subflow in the state with the new information
            state.subflow = updatedSubflow

            // Update the summary component if it exists.
            // The component's update method will handle its repaint.
            subflowSummaries[subflowId]?.update(state.latestNonTerminalAction, state.subflow)
        }
        // Non-"code_context" subflows are not handled by this method for summary components,
        // as their display is typically action-specific via FlowActionComponent.
    }

    // TODO: remove in favor of setTitle
    fun updateName(newName: String) {
        setTitle(newName)
    }
}