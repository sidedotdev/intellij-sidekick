package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
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

    // note: the task list is always visible, even if empty (when it's empty, it's effectively invisible
    // since nothing is rendered)
    internal val taskList = JBList(taskListModel).apply {
        cellRenderer = TaskCellRenderer()
        addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) {
                selectedValue?.let(onTaskSelected)
            }
        }
    }

    init {
        border = EmptyBorder(JBUI.insets(10))
        
        // Add all components with their initial layout
        add(statusLabel, BorderLayout.NORTH)
        add(taskList, BorderLayout.CENTER)
        add(noTasksLabel, BorderLayout.CENTER)
        add(newTaskButton, BorderLayout.SOUTH)
        
        // Set initial state
        updateEmptyState()
    }

    internal fun replaceTasks(newTasks: List<Task>) {
        taskListModel.updateTasks(newTasks)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (taskListModel.getSize() == 0) {
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