package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.components.GenerateFlowActionComponent
import com.github.sidedev.sidekick.toolWindow.components.IUpdatableFlowActionPanel
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

    // Map to store FlowActionComponents by FlowAction ID
    // Make internal for testing
    internal val flowActionComponents = mutableMapOf<String, IUpdatableFlowActionPanel>()

    /**
     * Processes a FlowAction, either updating an existing component or adding a new one.
     * Ignores actions associated with "code_context" subflows.
     *
     * @param flowAction The FlowAction data.
     * @param subflow The Subflow associated with the FlowAction.
     */
    fun processAction(flowAction: FlowAction, subflow: Subflow?) {
        // Ignore actions related to code context subflows
        if (subflow?.type == "code_context") {
            return
        }

        val existingComponent = flowActionComponents[flowAction.id]
        val isNewActionGenerateType = flowAction.actionType.startsWith("generate.")

        if (existingComponent != null) {
            // Both FlowActionComponent and GenerateFlowActionComponent are JPanels (JComponents).
            val existingComponentAsJComponent = existingComponent as JComponent
            val isExistingComponentGenerateType = existingComponent is GenerateFlowActionComponent

            if (isNewActionGenerateType == isExistingComponentGenerateType) {
                // Type matches, just update the existing component
                existingComponent.update(flowAction)
                // The component's update method should handle its own repaint if necessary.
                // Container revalidation/repaint might be needed if component size changes significantly,
                // but this is handled by add/remove operations. For in-place updates,
                // we rely on the component or assume minor visual changes.
            } else {
                // Type mismatch, remove old and add new
                content.remove(existingComponentAsJComponent)
                flowActionComponents.remove(flowAction.id) // Remove from map

                val newComponentToAdd: IUpdatableFlowActionPanel = if (isNewActionGenerateType) {
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
        } else {
            // No existing component, create and add a new one
            val newComponentToAdd: IUpdatableFlowActionPanel = if (isNewActionGenerateType) {
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

    // TODO: remove in favor of setTitle
    fun updateName(newName: String) {
        setTitle(newName)
    }
}