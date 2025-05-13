package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
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

    // container required to control loading icon visibility
    internal val loadingIcon = AnimatedIcon.Default()
    internal val loadingIconContainer = JBLabel(loadingIcon)

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
        return try {
            val toolParams = Json.decodeFromJsonElement<RetrieveCodeContextParams>(params.values.first())
            val requests = toolParams.codeContextRequests
            
            when {
                requests.isEmpty() -> "Looking up code..."
                else -> buildString {
                    append("Looking up: ")
                    append(requests.joinToString(", ") { request ->
                        buildString {
                            append(request.filePath)
                            if (!request.symbolNames.isNullOrEmpty()) {
                                append(" (${request.symbolNames.joinToString(", ")})")
                            }
                        }
                    })
                }
            }
        } catch (e: Exception) {
            "Looking up code..."
        }
    }

    private fun getBulkSearchRepositoryStatus(params: Map<String, JsonElement>): String {
        return try {
            val toolParams = Json.decodeFromJsonElement<BulkSearchRepositoryParams>(params.values.first())
            val searches = toolParams.searches
            
            when {
                searches.isEmpty() -> "Searching..."
                else -> buildString {
                    append("Searching for: ")
                    append(searches.joinToString(", ") { search ->
                        if (search.pathGlob.isNotEmpty()) {
                            "'${search.searchTerm}' in ${search.pathGlob}"
                        } else {
                            "'${search.searchTerm}'"
                        }
                    })
                }
            }
        } catch (e: Exception) {
            "Searching..."
        }
    }

    private fun getReadFileLinesStatus(params: Map<String, JsonElement>): String {
        return try {
            val toolParams = Json.decodeFromJsonElement<ReadFileLinesParams>(params.values.first())
            val fileLines = toolParams.fileLines
            
            when {
                fileLines.isEmpty() -> "Reading files..."
                else -> "Reading: ${fileLines.joinToString(", ") { it.filePath }}"
            }
        } catch (e: Exception) {
            "Reading files..."
        }
    }
}