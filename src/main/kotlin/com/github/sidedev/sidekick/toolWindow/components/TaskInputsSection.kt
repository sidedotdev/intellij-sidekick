package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.TaskRequest
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.border.EmptyBorder

class TaskInputsSection(taskRequest: TaskRequest) : AccordionSection(
    title = createTruncatedTitle(taskRequest.description),
    content = createContentPanel(taskRequest),
    initiallyExpanded = false
) {
    private val fullTitle: String = "Task: ${taskRequest.description}"
    private val truncatedTitle: String = createTruncatedTitle(taskRequest.description)

    companion object {
        private const val MAX_DESC_LENGTH = 50

        private fun createTruncatedTitle(description: String): String {
            val truncatedDesc = if (description.length > MAX_DESC_LENGTH) {
                description.substring(0, MAX_DESC_LENGTH) + "..."
            } else {
                description
            }
            return "Task: $truncatedDesc"
        }

        private fun createContentPanel(taskRequest: TaskRequest): JComponent {
            val contentPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                border = EmptyBorder(JBUI.insets(8))
            }

            val gbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridx = 0
                gridy = 0
                insets = JBUI.insets(4)
            }

            fun addLabeledValue(label: String, value: String?) {
                contentPanel.add(JBLabel("<html><b>$label:</b></html>"), gbc)
                gbc.gridy++
                contentPanel.add(JBLabel(value ?: "Not specified"), gbc)
                gbc.gridy++
            }

            // Add Description Label
            contentPanel.add(JBLabel("<html><b>Description:</b></html>"), gbc)
            gbc.gridy++

            // Add Description Value (with wrapping)
            val descriptionText = taskRequest.description?.replace("\n", "<br>") ?: "Not specified"
            val descriptionValueLabel = JBLabel("<html>$descriptionText</html>")
            contentPanel.add(descriptionValueLabel, gbc)
            gbc.gridy++

            addLabeledValue("Status", taskRequest.status)
            addLabeledValue("Agent Type", taskRequest.agentType)
            addLabeledValue("Flow Type", taskRequest.flowType)

            // Add flow options
            contentPanel.add(JBLabel("<html><b>Flow Options:</b></html>"), gbc)
            gbc.gridy++

            val flowOptionsPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
                border = EmptyBorder(JBUI.insets(0, 16, 0, 0)) // Indent flow options
            }

            val flowOptionsGbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridx = 0
                gridy = 0
                insets = JBUI.insets(2)
            }

            with(taskRequest.flowOptions) {
                flowOptionsPanel.add(JBLabel("Determine Requirements: $determineRequirements"), flowOptionsGbc)
                flowOptionsGbc.gridy++

                planningPrompt?.let {
                    flowOptionsPanel.add(JBLabel("Planning Prompt: $it"), flowOptionsGbc)
                    flowOptionsGbc.gridy++
                }

                envType?.let {
                    flowOptionsPanel.add(JBLabel("Environment Type: $it"), flowOptionsGbc)
                    flowOptionsGbc.gridy++
                }
            }
            contentPanel.add(flowOptionsPanel, gbc)
            return contentPanel
        }
    }

    override fun setExpanded(expand: Boolean) {
        super.setExpanded(expand)
        setTitle(if (expand) fullTitle else truncatedTitle)
    }
}