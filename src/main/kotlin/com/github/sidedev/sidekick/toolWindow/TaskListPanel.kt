package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.ListSelectionEvent
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskListPanel(
    private val workspaceId: String,
    private val taskListModel: TaskListModel,
    private val sidekickService: SidekickService,
    private val onTaskSelected: (Task) -> Unit,
    private val onNewTask: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JBPanel<TaskListPanel>(BorderLayout()) {

    internal val statusLabel = JBLabel("", SwingConstants.CENTER).apply {
        isAllowAutoWrapping = true
    }
    internal val newTaskButton = JButton("Start New Task").apply {
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
        emptyText.text = "No active tasks"
    }

    init {
        border = EmptyBorder(JBUI.insets(10))
        
        // Add all components with their initial layout
        add(statusLabel, BorderLayout.NORTH)
        add(taskList, BorderLayout.CENTER)
        add(newTaskButton, BorderLayout.SOUTH)
        
        // Set initial state
        statusLabel.isVisible = false
        updateEmptyState()

        // load task list
        CoroutineScope(dispatcher).launch {
            refreshTaskList()
        }
    }

    internal fun replaceTasks(newTasks: List<Task>) {
        taskListModel.updateTasks(newTasks)
        updateEmptyState()
    }

    suspend fun refreshTaskList() {
        when (val response = sidekickService.getTasks(workspaceId)) {
            is ApiResponse.Success -> {
                ApplicationManager.getApplication().invokeLater {
                    replaceTasks(response.data)
                }
            }
            is ApiResponse.Error -> {
                statusLabel.text = "<html>${ response.error.error }</html>"
                statusLabel.isVisible = true
            }
        }
    }

    private fun updateEmptyState() {
        if (taskListModel.getSize() == 0) {
            newTaskButton.isVisible = true
        } else {
            newTaskButton.isVisible = false
        }
        
        revalidate()
        repaint()
    }

    companion object {
        const val NAME = "TASK_LIST"
    }
}