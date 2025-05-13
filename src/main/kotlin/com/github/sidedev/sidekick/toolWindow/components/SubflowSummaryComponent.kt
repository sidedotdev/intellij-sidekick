package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.FlowAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.JsonElement
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
     * Updates the component's display based on the current action and subflow status.
     *
     * @param flowAction The current action being processed, if any
     * @param subflow The current state of the Subflow being summarized
     */
    fun update(flowAction: FlowAction?, subflow: Subflow) {
        // Update primary label based on subflow status
        primaryLabel.text = when (subflow.status) {
            SubflowStatus.STARTED -> "Finding Relevant Code"
            SubflowStatus.COMPLETE -> "Found Relevant Code"
            SubflowStatus.FAILED -> "Failed to Find Code"
        }

        // Update secondary line visibility and content
        if (subflow.status == SubflowStatus.STARTED) {
            secondaryContentPanel.isVisible = true
            loadingIconContainer.isVisible = true

            secondaryLabel.text = when {
                flowAction == null -> "Thinking..."
                !flowAction.actionType.startsWith("tool_call.") -> "Thinking..."
                else -> getToolSpecificStatusText(flowAction)
            }
        } else {
            secondaryContentPanel.isVisible = false
            loadingIconContainer.isVisible = false
        }

        revalidate()
        repaint()
    }

    private fun getToolSpecificStatusText(flowAction: FlowAction): String {
        val toolName = flowAction.actionType.removePrefix("tool_call.")
                          .replace('_', ' ') // Replace underscores with spaces
                          .trim()          // Trim leading/trailing whitespace
                          .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        return when (toolName.lowercase()) {
            "retrieve code context" -> getRetrieveCodeContextStatus(flowAction.actionParams)
            "bulk search repository" -> getBulkSearchRepositoryStatus(flowAction.actionParams)
            "read file lines" -> getReadFileLinesStatus(flowAction.actionParams)
            "get help or input" -> "Waiting for input..."
            else -> toolName // For unknown tools, just show the formatted tool name
        }
    }

    private fun getRetrieveCodeContextStatus(params: Map<String, JsonElement>): String {
        val requests = params["code_context_requests"] ?: return "Looking up code..."
        val requestsArray = requests.toString()
        val fileCount = requestsArray.split("file_path").size - 1
        
        return if (fileCount > 1) {
            "Looking up: multiple files"
        } else if (fileCount == 1) {
            val fileName = requestsArray.substringAfter("file_path").substringAfter(":").substringBefore(",").trim('"')
            "Looking up: $fileName"
        } else {
            "Looking up code..."
        }
    }

    private fun getBulkSearchRepositoryStatus(params: Map<String, JsonElement>): String {
        val searches = params["searches"] ?: return "Searching..."
        val searchesStr = searches.toString()
        
        // Extract search terms
        val searchTerms = searchesStr.split("search_term")
            .drop(1) // Skip the part before first search_term
            .map { it.substringAfter(":").substringBefore(",").trim('"', ' ', '}', ']') }
            .filter { it.isNotEmpty() }

        return when {
            searchTerms.isEmpty() -> "Searching..."
            searchTerms.size == 1 -> "Searching: ${searchTerms[0]}"
            else -> "Searching: ${searchTerms[0]} (and ${searchTerms.size - 1} more)"
        }
    }

    private fun getReadFileLinesStatus(params: Map<String, JsonElement>): String {
        val fileLines = params["file_lines"] ?: return "Reading files..."
        val fileLinesStr = fileLines.toString()
        
        val fileCount = fileLinesStr.split("file_path").size - 1
        return when {
            fileCount == 0 -> "Reading files..."
            fileCount == 1 -> {
                val fileName = fileLinesStr.substringAfter("file_path").substringAfter(":").substringBefore(",").trim('"')
                "Reading: $fileName"
            }
            else -> "Reading: multiple files"
        }
    }
}