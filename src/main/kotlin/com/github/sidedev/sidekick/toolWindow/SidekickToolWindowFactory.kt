package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JLabel
import javax.swing.SwingConstants

class SidekickToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
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
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SidekickToolWindow {
        val myToolWindow = SidekickToolWindow(toolWindow, project, service, dispatcher)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        project.basePath?.let { basePath ->
            SidekickToolWindowManager.storeWindow(basePath, myToolWindow)
        }
        return myToolWindow
    }

    override fun shouldBeAvailable(project: Project) = true
}

class SidekickToolWindow(
    private val toolWindow: ToolWindow,
    private val project: Project,
    private val sidekickService: SidekickService = SidekickService(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val taskListModel = TaskListModel()
    private lateinit var cardLayout: CardLayout
    internal lateinit var contentPanel: JBPanel<JBPanel<*>>
    internal lateinit var loadingPanel: LoadingPanel
    private lateinit var taskListStatusLabel: JLabel

    companion object {
        // TODO move to TaskListPanel.NAME constant
        private const val TASK_LIST_CARD = "TASK_LIST"
        // TODO move to TaskCreationPanel.NAME constant
        private const val TASK_CREATION_CARD = "TASK_CREATION"
        internal const val WORKSPACE_ID_KEY_PREFIX = "sidekick.workspace.id."
    }

    fun getContent(): JBPanel<JBPanel<*>> {
        // Card layout panel for switching between views
        cardLayout = CardLayout()
        contentPanel = JBPanel<JBPanel<*>>().apply {
            layout = cardLayout
        }

        // Create and add loading panel
        loadingPanel = LoadingPanel(
            project = project,
            sidekickService = sidekickService,
            onWorkspaceLoaded = { workspaceId ->
                ApplicationManager.getApplication().invokeLater {
                    CoroutineScope(dispatcher).launch {
                        refreshTaskList()
                    }
                    setupTaskPanels(workspaceId)
                    // TODO if there is a single active task, show the TaskViewPanel.NAME, if multiple active, show TaskListPanel.NAME, otherwise show TaskCreationPanel.NAME
                    //cardLayout.show(contentPanel, TASK_LIST_CARD)
                    cardLayout.show(contentPanel, TASK_CREATION_CARD)
                }
            }
        )
        contentPanel.add(loadingPanel, LoadingPanel.NAME)
        cardLayout.show(contentPanel, LoadingPanel.NAME)

        // Create task list panel
        val taskListPanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            name = "TASK_LIST"
        }

        // Add status label at the top
        taskListStatusLabel = JLabel().apply {
            name = "taskListStatusLabel"
            horizontalAlignment = SwingConstants.CENTER
        }
        taskListPanel.add(taskListStatusLabel, BorderLayout.NORTH)

        // Task list in a scroll pane
        val taskList = JBList(taskListModel).apply {
            cellRenderer = TaskCellRenderer()
        }

        // Add toolbar with create task button
        val taskListWithToolbar = ToolbarDecorator
            .createDecorator(taskList)
            .setAddAction { _ -> showTaskCreation() }
            .createPanel()

        taskListPanel.add(taskListWithToolbar, BorderLayout.CENTER)

        // Add task list panel to card layout
        contentPanel.add(taskListPanel, TASK_LIST_CARD)

        return contentPanel
    }

    private fun setupTaskPanels(workspaceId: String) {
        val taskCreationPanel = TaskCreationPanel(
            sidekickService = sidekickService,
            workspaceId = workspaceId,
            onTaskCreated = {
                showTaskList()
                CoroutineScope(dispatcher).launch {
                    refreshTaskList()
                }
            },
        )
        contentPanel.add(taskCreationPanel, TASK_CREATION_CARD)
    }

    private fun getCachedWorkspaceId(): String? {
        return PropertiesComponent.getInstance(project).getValue(WORKSPACE_ID_KEY_PREFIX + (project.basePath ?: ""))
    }

    // FIXME this should take in a workspaceId parameter
    internal suspend fun refreshTaskList() {
        val workspaceId = getCachedWorkspaceId() ?: return
        when (val tasksResult = sidekickService.getTasks(workspaceId)) {
            is ApiResponse.Success -> {
                ApplicationManager.getApplication().invokeLater {
                    taskListModel.updateTasks(tasksResult.data)
                    taskListStatusLabel.text = if (tasksResult.data.isEmpty()) {
                        MyBundle.message("statusLabel", "No tasks found")
                    } else {
                        MyBundle.message("statusLabel", "Tasks for workspace $workspaceId")
                    }
                }
            }
            is ApiResponse.Error -> {
                ApplicationManager.getApplication().invokeLater {
                    // we keep the list of tasks previously retrieved, if any
                    taskListStatusLabel.text = MyBundle.message("statusLabel", "Error fetching tasks: ${tasksResult.error.error}")
                }
            }
        }
    }

    fun showTaskCreation() {
        cardLayout.show(contentPanel, TASK_CREATION_CARD)
    }

    fun showTaskList() {
        cardLayout.show(contentPanel, TASK_LIST_CARD)
    }

    internal fun getTaskListModel(): TaskListModel = taskListModel

    internal suspend fun updateWorkspaceContent() {
        val workspaceId = getCachedWorkspaceId()
        if (workspaceId == null) {
            loadingPanel?.setupWorkspace()
            return
        }
        refreshTaskList()
    }
}
