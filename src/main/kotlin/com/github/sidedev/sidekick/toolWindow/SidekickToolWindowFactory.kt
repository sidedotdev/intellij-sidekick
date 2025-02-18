package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.response.ErrorResponse
import com.github.sidedev.sidekick.api.SidekickService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.NotNull
import javax.swing.JLabel
import javax.swing.SwingConstants
import java.awt.BorderLayout

class SidekickToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        val myToolWindow = SidekickToolWindow(toolWindow, project)
        val content = ContentFactory.getInstance().createContent(
            myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    private class SidekickToolWindow(
        private val toolWindow: ToolWindow,
        private val project: Project
    ) {
        private val sidekickService = SidekickService()
        
        fun getContent(): JBPanel<JBPanel<*>> {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
            }
            
            val statusLabel = JLabel(MyBundle.message("statusLabel", "?")).apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            mainPanel.add(statusLabel, BorderLayout.CENTER)
            
            // Check workspace status
            ApplicationManager.getApplication().executeOnPooledThread {
                val message = determineWorkspaceStatus()
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = MyBundle.message("statusLabel", message)
                }
            }
            
            return mainPanel
        }

        private fun determineWorkspaceStatus(): String {
            val response = sidekickService.getWorkspaces()
            if (response.isError()) {
                return "Side is not running. Please run `side start`. Error: " + response.getErrorIfAny()
            }
            val workspaces = response.getDataOrThrow().workspaces

            val projectPath = project.basePath
            if (projectPath != null) {
                for (workspace in workspaces) {
                    if (projectPath == workspace.localRepoDir) {
                        return "Found workspace ${workspace.id}"
                    }
                }
            }
            
            return "No workspace set up yet"
        }
    }
}