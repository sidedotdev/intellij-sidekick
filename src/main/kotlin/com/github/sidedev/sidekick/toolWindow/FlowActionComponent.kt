package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class FlowActionComponent(
    private val flowAction: FlowAction
) : JBPanel<FlowActionComponent>(BorderLayout()) {
    init {
        alignmentY = JComponent.TOP_ALIGNMENT
        // Create a compound border with a line on top and padding
        border = CompoundBorder(
            MatteBorder(1, 0, 0, 0, JBUI.CurrentTheme.DefaultTabs.borderColor()),
            EmptyBorder(8, 8, 8, 8)
        )

        // Add action type label
        add(JBLabel(flowAction.actionType).apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
        }, BorderLayout.NORTH)

        // Add action result
        add(JBLabel(flowAction.actionResult).apply {
            border = JBUI.Borders.empty(4, 0, 0, 0)
        }, BorderLayout.CENTER)
    }
}