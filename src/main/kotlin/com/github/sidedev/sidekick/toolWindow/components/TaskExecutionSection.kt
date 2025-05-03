package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.toolWindow.FlowActionComponent
import com.intellij.util.ui.JBUI
import javax.swing.BoxLayout
import javax.swing.JPanel

class TaskExecutionSection(
    name: String,
    content: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty()
    },
) : AccordionSection(title = name, content = content, initiallyExpanded = false) {
    fun addFlowAction(flowActionComponent: FlowActionComponent) {
        content.add(flowActionComponent)
        content.revalidate()
        content.repaint()
    }

    // TODO: remove in favor of setTitle
    fun updateName(newName: String) {
        setTitle(newName)
    }
}