package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import com.github.sidedev.sidekick.api.SidekickService

class FlowActionComponent(
    internal var flowAction: FlowAction
) : JBPanel<FlowActionComponent>(BorderLayout()), IUpdatableFlowActionPanel {

    private var displayedChild: JComponent? = null
    private lateinit var defaultViewPanel: JBPanel<*>

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
        layout = BorderLayout() // Ensure BorderLayout for FlowActionComponent
        showChildFor(this.flowAction)
    }

    private fun showChildFor(action: FlowAction) {
        // Remove current child if any
        displayedChild?.let { remove(it) }
        displayedChild = null

        if (action.actionType == "user_request" || action.actionType.startsWith("user_request.")) {
            val service = SidekickService()
            val userRequestComp = UserRequestComponent(action, service)
            displayedChild = userRequestComp
        } else {
            // Default view: reuse or create defaultViewPanel
            if (!::defaultViewPanel.isInitialized) {
                defaultViewPanel = JBPanel<JBPanel<*>>(BorderLayout()) // As per plan
                actionTypeLabel = JBLabel().apply {
                    font = font.deriveFont(font.style or java.awt.Font.BOLD)
                }
                actionResultLabel = JBLabel().apply {
                    border = JBUI.Borders.empty(4, 0, 0, 0)
                }
                defaultViewPanel.add(actionTypeLabel, BorderLayout.NORTH)
                defaultViewPanel.add(actionResultLabel, BorderLayout.CENTER)
            }
            actionTypeLabel.text = action.actionType
            actionResultLabel.text = action.actionResult
            displayedChild = defaultViewPanel
        }

        // Add the new child and refresh UI
        add(displayedChild!!, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /**
     * Updates the component to display the data from the new FlowAction.
     */
    override fun update(newFlowAction: FlowAction) {
        this.flowAction = newFlowAction
        showChildFor(this.flowAction)
    }
}