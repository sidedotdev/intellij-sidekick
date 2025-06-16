package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.components.IUpdatableFlowActionPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class FlowActionComponent(
    internal var flowAction: FlowAction // Changed to var and internal
) : JBPanel<FlowActionComponent>(BorderLayout()), IUpdatableFlowActionPanel {

    // Properties to hold the labels for updating
    internal lateinit var actionTypeLabel: JBLabel
    internal lateinit var actionResultLabel: JBLabel

    init {
        alignmentY = JComponent.TOP_ALIGNMENT
        // Create a compound border with a line on top and padding
        border = CompoundBorder(
            MatteBorder(1, 0, 0, 0, JBUI.CurrentTheme.DefaultTabs.borderColor()),
            EmptyBorder(8, 8, 8, 8)
        )

        // Create and store action type label
        actionTypeLabel = JBLabel(flowAction.actionType).apply {
            font = font.deriveFont(font.style or java.awt.Font.BOLD)
        }
        add(actionTypeLabel, BorderLayout.NORTH)

        // Create and store action result label
        actionResultLabel = JBLabel(flowAction.actionResult).apply {
            border = JBUI.Borders.empty(4, 0, 0, 0)
        }
        add(actionResultLabel, BorderLayout.CENTER)
    }

    /**
     * Updates the component to display the data from the new FlowAction.
     */
    override fun update(newFlowAction: FlowAction) {
        this.flowAction = newFlowAction
        actionTypeLabel.text = newFlowAction.actionType
        actionResultLabel.text = newFlowAction.actionResult
        // Request layout and repaint updates
        revalidate()
        repaint()
    }
}