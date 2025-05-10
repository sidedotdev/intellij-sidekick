package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A UI component that displays a summary status for a Subflow, particularly
 * useful for long-running or complex subflows like "code_context".
 * It shows a primary status line and an optional secondary line with a
 * loading indicator and specific action details when the subflow is running.
 */
class SubflowSummaryComponent : JBPanel<SubflowSummaryComponent>(BorderLayout()) {

    internal val primaryLabel = JBLabel()
    internal val secondaryLabel = JBLabel()
    internal val loadingIcon = AnimatedIcon.Default() // The animated icon itself
    internal val loadingIconContainer = JBLabel(loadingIcon)    // JBLabel to
    // host the
    // icon

    // Panel to hold the icon and secondary text horizontally
    internal val secondaryContentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false // Inherit background from parent
        border = JBUI.Borders.emptyTop(2) // Add slight space above secondary line
        add(loadingIconContainer) // Add the label hosting the icon
        add(JBUI.Borders.emptyLeft(4).wrap(secondaryLabel)) // Space between icon and text
    }

    // Main content panel using BoxLayout for vertical arrangement
    internal val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false // Inherit background from parent
        // Consistent padding with other components if necessary, adjust as needed
        border = JBUI.Borders.empty(5, 10)
        add(primaryLabel)
        add(secondaryContentPanel)
    }

    init {
        add(contentPanel, BorderLayout.CENTER)
        // Set an initial state assuming the subflow has just started.
        // The first call to update() will set the correct state.
        primaryLabel.text = "Finding Relevant Code"
        secondaryLabel.text = "Thinking..."
        secondaryContentPanel.isVisible = true
        loadingIconContainer.isVisible = true // Use loadingIconContainer for visibility
    }

    /**
     * Updates the component's display based on the latest action and the overall subflow status.
     *
     * @param latestNonTerminalAction The most recent action whose status is not terminal (e.g., not COMPLETE or FAILED). Can be null.
     * @param subflow The current state of the Subflow being summarized.
     */
    fun update(latestNonTerminalAction: FlowAction?, subflow: Subflow) {
        // 1. Update Primary Label based on Subflow Status
        primaryLabel.text = when (subflow.status) {
            SubflowStatus.STARTED -> "Finding Relevant Code"
            SubflowStatus.COMPLETE -> "Found Relevant Code"
            SubflowStatus.FAILED -> "Failed to Find Code" // Handle failure case
        }

        // 2. Update Secondary Line (Visibility and Content) based on Subflow Status
        if (subflow.status == SubflowStatus.STARTED) {
            secondaryContentPanel.isVisible = true
            loadingIconContainer.isVisible = true // Ensure icon is visible when panel is

            // Determine secondary text based on the latest non-terminal action
            val actionType = latestNonTerminalAction?.actionType
            val toolCallPrefix = "tool_call."

            val textForSecondaryLabel = if (actionType != null && actionType.startsWith(toolCallPrefix)) {
                val toolName = actionType.removePrefix(toolCallPrefix)
                                  .replace('_', ' ') // Replace underscores with spaces
                                  .trim()          // Trim leading/trailing whitespace

                if (toolName.isEmpty()) {
                    // If the processed tool name is empty, default to "Thinking..."
                    "Thinking..."
                } else {
                    // Capitalize the first character of the processed tool name
                    toolName.replaceFirstChar { 
                        if (it.isLowerCase()) it.titlecase() else it.toString() 
                    }
                }
            } else {
                // Default text when no specific tool action is active or available, or if not a tool_call
                "Thinking..."
            }
            secondaryLabel.text = textForSecondaryLabel
        } else {
            // Hide secondary line when subflow is not in a running state
            secondaryContentPanel.isVisible = false
            loadingIconContainer.isVisible = false // Use loadingIconContainer for visibility
        }

        // Ensure UI updates are reflected
        revalidate()
        repaint()
    }
}