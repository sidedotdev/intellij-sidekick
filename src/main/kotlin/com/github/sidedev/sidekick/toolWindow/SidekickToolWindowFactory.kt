package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.response.ApiError
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    class SidekickToolWindow(
        private val toolWindow: ToolWindow,
        private val project: Project,
        private val sidekickService: SidekickService = SidekickService()
    ) {
        private val taskListModel = TaskListModel()
        internal lateinit var statusLabel: JLabel
        
        fun getContent(): JBPanel<JBPanel<*>> {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
            }
            
            // Status label at the top
            statusLabel = JLabel(MyBundle.message("statusLabel", "?")).apply {
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
                CoroutineScope(Dispatchers.IO).launch {
                    updateWorkspaceContent()
                }
            }
            
            return mainPanel
        }

        internal suspend fun updateWorkspaceContent() {
            when (val workspacesResult = sidekickService.getWorkspaces()) {
                is ApiResponse.Success -> {
                    val workspaces = workspacesResult.data
                    val projectPath = project.basePath
                    if (projectPath != null) {
                        for (workspace in workspaces) {
                            if (projectPath == workspace.localRepoDir) {
                                // Handle found workspace
                                when (val tasksResult = sidekickService.getTasks(workspace.id)) {
                                    is ApiResponse.Success -> {
                                        val tasks = tasksResult.data
                                        ApplicationManager.getApplication().invokeLater {
                                            if (tasks.isEmpty()) {
                                                statusLabel.text = MyBundle.message("statusLabel", "No tasks found")
                                            } else {
                                                statusLabel.text = MyBundle.message("statusLabel", "Tasks for workspace ${workspace.id}")
                                            }
                                            taskListModel.updateTasks(tasks)
                                        }
                                    }
                                    is ApiResponse.Error -> {
                                        val message = "Error fetching tasks: ${tasksResult.error.error}"
                                        ApplicationManager.getApplication().invokeLater {
                                            statusLabel.text = MyBundle.message("statusLabel", message)
                                            taskListModel.updateTasks(emptyList())
                                        }
                                    }
                                }
                                return
                            }
                        }
                    }

                    // No matching workspace found
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = MyBundle.message("statusLabel", "No workspace set up yet")
                        taskListModel.updateTasks(emptyList())
                    }
                }
                is ApiResponse.Error -> {
                    val message = "Side is not running. Please run `side start`. Error: ${workspacesResult.error.error}"
                    ApplicationManager.getApplication().invokeLater {
                        statusLabel.text = MyBundle.message("statusLabel", message)
                        taskListModel.updateTasks(emptyList())
                    }
                }
            }
        }
        
        internal fun getTaskListModel(): TaskListModel {
            return taskListModel
        }
    }
}