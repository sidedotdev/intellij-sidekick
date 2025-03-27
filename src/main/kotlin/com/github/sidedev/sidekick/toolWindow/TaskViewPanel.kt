package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.Task
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import javax.swing.JComponent

class TaskViewPanel(
    private val task: Task,
    private val onAllTasksClick: () -> Unit
) : JBPanel<TaskViewPanel>(BorderLayout()) {

    companion object {
        const val NAME = "TaskView"
    }

    init {
        // Create and configure the "All Tasks" link
        val allTasksLink = JBLabel("<html><u>All Tasks</u></html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(8)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    onAllTasksClick()
                }
            })
        }

        // Create and configure the description text area
        val descriptionArea = JBTextArea().apply {
            text = task.description
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            border = JBUI.Borders.empty(8)
        }

        // Add components to the panel
        add(allTasksLink, BorderLayout.NORTH)
        add(JBScrollPane(descriptionArea), BorderLayout.CENTER)
    }
}