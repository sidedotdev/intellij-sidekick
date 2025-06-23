package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JComponent
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

    private val logger: Logger = logger<TaskExecutionSection>()
    internal data class CodeContextSubflowState(var subflow: Subflow)

    // Map to store SubflowSummaryComponent instances by Subflow ID for "code_context" subflows
    internal val subflowSummaries = mutableMapOf<String, SubflowSummaryComponent>()
    // Map to store state for "code_context" subflows
    internal val codeContextSubflowStates = mutableMapOf<String, CodeContextSubflowState>()

    // Map to store FlowActionComponents by FlowAction ID
    // Make internal for testing
    internal val flowActionComponents = mutableMapOf<String, IUpdatableFlowActionPanel>()

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

            val summaryComponent = subflowSummaries.getOrPut(subflowId) {
                SubflowSummaryComponent().also { newComponent ->
                    content.add(newComponent)
                    content.revalidate()
                    content.repaint()
                }
            }
            summaryComponent.update(flowAction, state.subflow)
            // Individual FlowActionComponents are not created for "code_context" subflows
        } else {
            val existingComponent = flowActionComponents[flowAction.id]

            if (existingComponent != null) {
                logger.info("updating existing component for flow action ${ flowAction.id }")
                existingComponent.update(flowAction)
            } else {
                logger.info("Adding new component for flow action ${ flowAction.id }: ${flowAction.actionType}")
                // No existing component, create and add a new one
                val isGenerateActionType = flowAction.actionType.startsWith("generate.")
                val newComponentToAdd: IUpdatableFlowActionPanel = if (isGenerateActionType) {
                    GenerateFlowActionComponent(flowAction)
                } else {
                    FlowActionComponent(flowAction)
                }
                flowActionComponents[flowAction.id] = newComponentToAdd
                // Ensure the new component (which is a JPanel) is added as a JComponent.
                content.add(newComponentToAdd as JComponent)
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
                CodeContextSubflowState(updatedSubflow)
            }
            // Always update the subflow in the state with the new information
            state.subflow = updatedSubflow

            // Update the summary component if it exists.
            // The component's update method will handle its repaint.
            subflowSummaries[subflowId]?.update(null, state.subflow)
        }
        // Non-"code_context" subflows are not handled by this method for summary components,
        // as their display is typically action-specific via FlowActionComponent.
    }

    // TODO: remove in favor of setTitle
    fun updateName(newName: String) {
        setTitle(newName)
    }
}