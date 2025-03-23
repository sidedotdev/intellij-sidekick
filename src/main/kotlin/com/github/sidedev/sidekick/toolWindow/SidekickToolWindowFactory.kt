package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
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
import com.intellij.ui.ToolbarDecorator
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class SidekickToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        @NotNull project: Project,
        @NotNull toolWindow: ToolWindow,
    ) {
        createSidekickToolWindow(project, toolWindow, SidekickService())
    }

    internal fun createSidekickToolWindow(
        @NotNull project: Project,
        @NotNull toolWindow: ToolWindow,
        service: SidekickService,
    ): SidekickToolWindow {
        val myToolWindow = SidekickToolWindow(toolWindow, project, service)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        return myToolWindow
    }

    override fun shouldBeAvailable(project: Project) = true

    class SidekickToolWindow(
        private val toolWindow: ToolWindow,
        private val project: Project,
        private val sidekickService: SidekickService = SidekickService(),
    ) {
        private val taskListModel = TaskListModel()
        internal lateinit var statusLabel: JLabel
        private lateinit var cardLayout: CardLayout
        private lateinit var contentPanel: JPanel
        //private lateinit var taskCreationPanel: TaskCreationPanel

        companion object {
            private const val TASK_LIST_CARD = "TASK_LIST"
            private const val TASK_CREATION_CARD = "TASK_CREATION"
        }

        fun getContent(): JBPanel<JBPanel<*>> {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
            }

            // Status label at the top
            // TODO move to separate "loading" panel
            statusLabel = JLabel(MyBundle.message("statusLabel", "?")).apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            mainPanel.add(statusLabel, BorderLayout.NORTH)

            // Card layout panel for switching between views
            cardLayout = CardLayout()
            contentPanel = JPanel(cardLayout)

            val taskListPanel = JBPanel<JBPanel<*>>().apply {
                layout = BorderLayout()
            }
            
            // Task list in a scroll pane
            val taskList = JBList(taskListModel).apply {
                cellRenderer = TaskCellRenderer()
            }

            // Add toolbar with create task button
            val taskListWithToolbar = ToolbarDecorator.createDecorator(taskList)
                .setAddAction { _ -> showTaskCreation() }
                .createPanel()

            taskListPanel.add(taskListWithToolbar, BorderLayout.CENTER)

            // Add task list panel to card layout
            contentPanel.add(taskListPanel, TASK_LIST_CARD)
            mainPanel.add(contentPanel, BorderLayout.CENTER)

            statusLabel.text = "Loading..."

            // Check workspace status
            ApplicationManager.getApplication().executeOnPooledThread {
                CoroutineScope(Dispatchers.IO).launch {
                    updateWorkspaceContent()
                }
            }

            return mainPanel
        }

        private fun showTaskCreation() {
            cardLayout.show(contentPanel, TASK_CREATION_CARD)
        }

        fun showTaskList() {
            cardLayout.show(contentPanel, TASK_LIST_CARD)
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
                                            val taskCreationPanel = TaskCreationPanel(
                                                sidekickService = sidekickService,
                                                workspaceId = workspace.id,
                                                onTaskCreated = {
                                                    showTaskList()
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        updateWorkspaceContent()
                                                    }
                                                }
                                            )
                                            contentPanel.add(taskCreationPanel, TASK_CREATION_CARD)
                                            showTaskCreation()

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

        internal fun getTaskListModel(): TaskListModel = taskListModel
    }
}
