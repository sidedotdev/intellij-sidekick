package com.github.sidedev.sidekick.actions

import com.github.sidedev.sidekick.toolWindow.SidekickToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware

class NewTaskAction : AnAction(
    "New Task",
    "Create a new task in Sidekick",
    AllIcons.General.Add
), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectBasePath = project.basePath ?: return
        
        val window = SidekickToolWindowManager.getWindow(projectBasePath)
        window?.showTaskCreation()
    }

    override fun update(e: AnActionEvent) {
        // Action is always enabled when there's a project
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}