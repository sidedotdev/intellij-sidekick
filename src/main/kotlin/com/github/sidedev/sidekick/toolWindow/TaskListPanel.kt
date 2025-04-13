package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import com.intellij.util.ui.JBUI

class TaskListPanel(
    private val workspaceId: String,
    private val taskListModel: TaskListModel,
    private val sidekickService: SidekickService,
    private val onTaskSelected: (Task) -> Unit,
    private val onNewTask: () -> Unit
) : JBPanel<TaskListPanel>(BorderLayout()) {

    internal val statusLabel = JBLabel("", SwingConstants.CENTER)
    internal val noTasksLabel = JBLabel("No tasks", SwingConstants.CENTER)
    internal val newTaskButton = JButton("New Task").apply {
        addActionListener { onNewTask() }
    }

    init {
        border = EmptyBorder(JBUI.insets(10))
        add(statusLabel, BorderLayout.NORTH)
        updateEmptyState(taskListModel.getSize() == 0)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        removeAll()
        add(statusLabel, BorderLayout.NORTH)
        
        if (isEmpty) {
            add(noTasksLabel, BorderLayout.CENTER)
            add(newTaskButton, BorderLayout.SOUTH)
            noTasksLabel.isVisible = true
            newTaskButton.isVisible = true
        } else {
            noTasksLabel.isVisible = false
            newTaskButton.isVisible = false
        }
        
        revalidate()
        repaint()
    }

    companion object {
        const val NAME = "TASK_LIST"
    }
}