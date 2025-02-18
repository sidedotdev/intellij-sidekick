package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Workspace
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
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
        
        private val taskListModel = TaskListModel()
        
        fun getContent(): JBPanel<JBPanel<*>> {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
            }
            
            // Status label at the top
            val statusLabel = JLabel(MyBundle.message("statusLabel", "?")).apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            mainPanel.add(statusLabel, BorderLayout.NORTH)
            
            // Task list in a scroll pane in the center
            val taskList = JBList(taskListModel).apply {
                cellRenderer = TaskCellRenderer()
            }
            mainPanel.add(JBScrollPane(taskList), BorderLayout.CENTER)
            
            statusLabel.text = "Loading..."

            // Check workspace status
            ApplicationManager.getApplication().executeOnPooledThread {
                kotlinx.coroutines.runBlocking {
                    val message = determineWorkspaceStatus()
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = MyBundle.message("statusLabel", message)
                    }
                }
            }
            
            return mainPanel
        }

        private suspend fun determineWorkspaceStatus(): String {
            return sidekickService.getWorkspaces().mapOrElse(
                { workspaces: List<Workspace> ->
                    val projectPath = project.basePath
                    if (projectPath != null) {
                        for (workspace in workspaces) {
                            if (projectPath == workspace.localRepoDir) {
                                return@mapOrElse "Found workspace ${workspace.id}"
                            }
                        }
                    }

                    "No workspace set up yet"
                },
                { error: ApiError ->
                    "Side is not running. Please run `side start`. Error: " + error.error
                }
            )
        }
    }
}