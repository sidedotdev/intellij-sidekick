package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.models.FlowAction
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

    // Map to store FlowActionComponents by FlowAction ID
    // Make internal for testing
    internal val flowActionComponents = mutableMapOf<String, FlowActionComponent>()

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
        if (existingComponent != null) {
            // Update existing component
            existingComponent.update(flowAction)
            // The component's update method handles its repaint.
            // Container repaint might be needed if component size changes, but deferring for now.
        } else {
            // Create and add new component
            val newComponent = FlowActionComponent(flowAction)
            flowActionComponents[flowAction.id] = newComponent
            content.add(newComponent)
            content.revalidate()
            content.repaint()
        }
    }

    // TODO: remove in favor of setTitle
    fun updateName(newName: String) {
        setTitle(newName)
    }
}