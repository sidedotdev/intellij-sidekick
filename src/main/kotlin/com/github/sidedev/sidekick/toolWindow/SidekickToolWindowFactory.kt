package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.TaskStatus
import com.github.sidedev.sidekick.api.AgentType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NotNull
import java.awt.CardLayout
import com.github.sidedev.sidekick.api.Task

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

        val titleActions = mutableListOf<AnAction>()
        val action = ActionManager.getInstance().getAction("SidekickToolWindowActions")
        titleActions.add(action)
        toolWindow.setTitleActions(titleActions)

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
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val taskListModel = TaskListModel()
    private lateinit var cardLayout: CardLayout
    internal lateinit var contentPanel: JBPanel<JBPanel<*>>
    internal lateinit var loadingPanel: LoadingPanel
    private lateinit var taskListPanel: TaskListPanel
    private lateinit var taskViewPanel: TaskViewPanel

    companion object {
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
                    setupTaskPanels(workspaceId)
                    // TODO if there is a single active task, show the TaskViewPanel.NAME, if multiple active, show TaskListPanel.NAME, otherwise show TaskCreationPanel.NAME
                    //cardLayout.show(contentPanel, TaskListPanel.NAME)
                    cardLayout.show(contentPanel, TASK_CREATION_CARD)
                }
            }
        )
        contentPanel.add(loadingPanel, LoadingPanel.NAME)
        cardLayout.show(contentPanel, LoadingPanel.NAME)

        // Create initial task view panel with empty task
        taskViewPanel = TaskViewPanel(
            task = Task(
                id = "",
                workspaceId = "",
                status = TaskStatus.DRAFTING,
                agentType = AgentType.NONE,
                flowType = null,
                description = "",
                created = kotlinx.datetime.Clock.System.now(),
                updated = kotlinx.datetime.Clock.System.now()
            ),
            onAllTasksClick = { showTaskList() }
        )
        contentPanel.add(taskViewPanel, TaskViewPanel.NAME)

        return contentPanel
    }

    private fun setupTaskPanels(workspaceId: String) {
        val taskCreationPanel = TaskCreationPanel(
            sidekickService = sidekickService,
            workspaceId = workspaceId,
            onTaskCreated = { task ->
                showTaskView(task)
            },
        )
        contentPanel.add(taskCreationPanel, TASK_CREATION_CARD)

        taskListPanel = TaskListPanel(
            workspaceId = workspaceId,
            taskListModel = taskListModel,
            sidekickService = sidekickService,
            onTaskSelected = { task ->
                taskViewPanel = TaskViewPanel(
                    task = task,
                    onAllTasksClick = { showTaskList() }
                )
                contentPanel.add(taskViewPanel, TaskViewPanel.NAME)
                cardLayout.show(contentPanel, TaskViewPanel.NAME)
            },
            onNewTask = { showTaskCreation() }
        )
        contentPanel.add(taskListPanel, TaskListPanel.NAME)
    }

    private fun getCachedWorkspaceId(): String? {
        return PropertiesComponent.getInstance(project).getValue(WORKSPACE_ID_KEY_PREFIX + (project.basePath ?: ""))
    }

    internal suspend fun refreshTaskList() {
        val workspaceId = getCachedWorkspaceId()
        if (workspaceId != null) {
            taskListPanel.refreshTaskList()
        }
    }

    fun showTaskCreation() {
        cardLayout.show(contentPanel, TASK_CREATION_CARD)
    }

    fun showTaskList() {
        val workspaceId = getCachedWorkspaceId()
        if (workspaceId != null) {
            // Refresh the task list when it's displayed.
            scope.launch {
                refreshTaskList()
            }
            cardLayout.show(contentPanel, TaskListPanel.NAME)
        } else {
            cardLayout.show(contentPanel, LoadingPanel.NAME)
        }
    }

    fun showTaskView(task: Task) {
        contentPanel.remove(taskViewPanel)
        taskViewPanel = TaskViewPanel(
            task = task,
            onAllTasksClick = { showTaskList() }
        )
        contentPanel.add(taskViewPanel, TaskViewPanel.NAME)
        cardLayout.show(contentPanel, TaskViewPanel.NAME)
    }

    internal fun getTaskListModel(): TaskListModel = taskListModel

    // FIXME try to remove this function, we shouldn't need it in tests, which is the only place it's used today
    internal suspend fun updateWorkspaceContent() {
        val workspaceId = getCachedWorkspaceId()
        if (workspaceId == null) {
            loadingPanel?.setupWorkspace()
            return
        }
        refreshTaskList()
    }
}
