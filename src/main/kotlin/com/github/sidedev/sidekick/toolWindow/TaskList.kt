package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.Task
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.AbstractListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class TaskListModel : AbstractListModel<Task>() {
    private var tasks: List<Task> = emptyList()

    override fun getSize(): Int = tasks.size

    override fun getElementAt(index: Int): Task = tasks[index]

    fun updateTasks(newTasks: List<Task>) {
        // Sort by updatedAt in descending order
        tasks = newTasks.sortedByDescending { it.updated }
        fireContentsChanged(this, 0, tasks.size)
    }
}

class TaskCellRenderer : ListCellRenderer<Task> {
    override fun getListCellRendererComponent(
        list: JList<out Task>,
        value: Task,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return JPanel(BorderLayout(10, 0)).apply {
            val descLabel = JLabel(value.description)
            val statusLabel = JLabel(value.status)
            
            add(descLabel, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.EAST)

            if (isSelected) {
                background = list.selectionBackground
                foreground = list.selectionForeground
                descLabel.foreground = list.selectionForeground
                statusLabel.foreground = list.selectionForeground
            } else {
                background = list.background
                foreground = list.foreground
                descLabel.foreground = list.foreground
                statusLabel.foreground = list.foreground
            }
            isOpaque = true
        }
    }
}