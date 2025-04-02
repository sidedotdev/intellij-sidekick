package com.github.sidedev.sidekick.actions

import com.github.sidedev.sidekick.toolWindow.SidekickToolWindowManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.icons.AllIcons

class ShowTasksAction : AnAction(
    "Active Tasks",
    "Show active tasks",
    AllIcons.Actions.ListFiles
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val window = SidekickToolWindowManager.getWindow(project.basePath ?: return)
        window?.showTaskList()
    }

    override fun update(e: AnActionEvent) {
        // Action is enabled when there's a project
        e.presentation.isEnabled = e.project != null
    }
}