package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.*
import com.github.sidedev.sidekick.api.TaskRequest
import com.github.sidedev.sidekick.api.FlowOptions
import com.github.sidedev.sidekick.api.websocket.FlowActionSession
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.components.TaskSection
import com.github.sidedev.sidekick.toolWindow.components.TaskInputsSection
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Point
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.SwingUtilities
import com.intellij.openapi.diagnostic.logger

class TaskViewPanel(
    private val task: Task,
    private val onAllTasksClick: () -> Unit,
    private val sidekickService: SidekickService = SidekickService(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: Logger = logger<TaskViewPanel>(),
) : JBPanel<TaskViewPanel>(BorderLayout()), Disposable {

    companion object {
        const val NAME = "TaskView"
        private const val RECONNECT_DELAY_MS = 1000L
        private const val MAX_PARENT_TRAVERSAL_DEPTH = 20
        
        // Section category constants
        internal const val CATEGORY_REQUIREMENTS_PLANNING = "requirements-planning"
        internal const val CATEGORY_CODING = "coding"
        internal const val CATEGORY_UNCATEGORIZED = "uncategorized"
        private const val LLM_STEP_PREFIX = "llm_step:"
        
        // Subflow type constants  
        private const val TYPE_DEV_REQUIREMENTS = "dev_requirements"
        private const val TYPE_DEV_PLAN = "dev_plan"
        private const val TYPE_LLM_STEP = "llm_step"
    }

    private var flowActionSession: FlowActionSession? = null
    private val coroutineScope = CoroutineScope(dispatcher + Job())
    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val scrollPane = JBScrollPane()
    private var isUserScrolling = false
    private var shouldAutoScroll = true
    private val taskSections = mutableMapOf<String, TaskSection>()
    private val trackedSubflowIds = mutableSetOf<String>()
    
    // Tracks if we've seen requirements and planning subflows to update section name
    private var hasRequirementsSubflow = false
    private var hasPlanningSubflow = false

    /**
     * Determines the appropriate section category for a subflow based on its type and ancestry
     */
    internal suspend fun determineSubflowCategory(subflow: Subflow): String {
        val type = findRelevantSubflowType(subflow)
        return when (type) {
            TYPE_DEV_REQUIREMENTS, TYPE_DEV_PLAN -> CATEGORY_REQUIREMENTS_PLANNING
            TYPE_LLM_STEP -> "$LLM_STEP_PREFIX${subflow.id}"
            else -> CATEGORY_CODING
        }
    }

    /**
     * Traverses parent subflows to find a relevant type for categorization
     */
    internal suspend fun findRelevantSubflowType(subflow: Subflow, depth: Int = 0): String? {
        if (depth >= MAX_PARENT_TRAVERSAL_DEPTH) return null
        
        // Check current subflow type
        subflow.type?.let { return it }
        
        // Traverse parent if exists
        val parentId = subflow.parentSubflowId ?: return null
        return sidekickService.getSubflow(task.workspaceId, parentId)
            .map { parent -> findRelevantSubflowType(parent, depth + 1) }
            .getOrNull()
    }

    /**
     * Gets the display name for a section based on its category
     */
    internal fun getSectionName(category: String): String {
        return when {
            category == CATEGORY_REQUIREMENTS_PLANNING -> {
                when {
                    hasRequirementsSubflow && hasPlanningSubflow -> "Requirements and Planning"
                    hasRequirementsSubflow -> "Requirements"
                    hasPlanningSubflow -> "Planning"
                    else -> "Requirements and Planning" // Default if type not yet known
                }
            }
            category == CATEGORY_CODING -> "Coding"
            category.startsWith(LLM_STEP_PREFIX) -> "Step ${category.substringAfter(LLM_STEP_PREFIX)}"
            else -> "Uncategorized"
        }
    }

    init {
        // Create and configure the "All Tasks" link
        val allTasksLink = JBLabel("<html><u>All Tasks</u></html>").apply {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = JBUI.Borders.empty(8)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    onAllTasksClick()
                }
            })
        }

        // Add task inputs section
        val taskRequest = TaskRequest(
            description = task.description,
            status = task.status.toString(),
            agentType = task.agentType.toString(),
            flowType = task.flowType ?: "",
            flowOptions = FlowOptions(
                determineRequirements = task.flowOptions?.get("determineRequirements")?.toString()?.toBoolean() ?: false,
                planningPrompt = task.flowOptions?.get("planningPrompt")?.toString(),
                envType = task.flowOptions?.get("envType")?.toString()
            )
        )
        contentPanel.add(TaskInputsSection(taskRequest))
        contentPanel.add(Box.createVerticalStrut(16))

        // Configure scroll pane
        scrollPane.apply {
            setViewportView(contentPanel)
            verticalScrollBar.unitIncrement = 16
            viewport.addChangeListener { 
                if (!isUserScrolling) return@addChangeListener
                
                val viewRect = viewport.viewRect
                val viewHeight = viewport.view.height
                shouldAutoScroll = viewRect.y + viewRect.height >= viewHeight - 50
            }
            verticalScrollBar.addAdjustmentListener { 
                isUserScrolling = true
            }
        }

        // Add other components to the panel
        add(allTasksLink, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        // connect to flow actions if we have a flow
        task.flows?.firstOrNull()?.let { flow ->
            connectToFlowActions(flow.id)
        }
        // TODO if we don't have a flow yet, poll for it
    }

    private fun connectToFlowActions(flowId: String) {
        coroutineScope.launch {
            try {
                val session = sidekickService.connectToFlowActions(
                    workspaceId = task.workspaceId,
                    flowId = flowId,
                    onMessage = { flowAction ->
                        handleFlowAction(flowAction)
                    },
                    onError = { error ->
                        handleConnectionError(error, flowId)
                    }
                ).getOrThrow()
                
                flowActionSession = session
            } catch (e: Exception) {
                handleConnectionError(e, flowId)
            }
        }
    }

    private suspend fun handleFlowAction(flowAction: FlowAction) {
        flowAction.subflowId?.let { subflowId ->
            if (!trackedSubflowIds.contains(subflowId)) {
                trackedSubflowIds.add(subflowId)

                // Fetch subflow details and create section
                val subflowResponse = sidekickService.getSubflow(task.workspaceId, subflowId)
                subflowResponse.map { subflow ->
                    ApplicationManager.getApplication().invokeLater {
                        var section: TaskSection? = null
                        synchronized(taskSections) {
                            if (!taskSections.containsKey(subflowId)) {
                                section = TaskSection(subflow.name)
                                taskSections[subflowId] = section!!
                            }
                        }
                        if (section != null) {
                            contentPanel.add(section)
                            contentPanel.add(Box.createVerticalStrut(8))
                            contentPanel.revalidate()
                            contentPanel.repaint()
                        }
                    }
                }
                subflowResponse.mapError { e ->
                    logger.error(Exception("OMG 123: " + e.error))
                    // FIXME handle non-connection error by rendering on a secondary error label
                }
            }
        }

        ApplicationManager.getApplication().invokeLater {
            val actionComponent = FlowActionComponent(flowAction)
            val section = taskSections[flowAction.subflowId]
            
            if (section != null) {
                section.addFlowAction(actionComponent)
                section.revalidate()
                section.repaint()

                if (shouldAutoScroll) {
                    SwingUtilities.invokeLater {
                        val viewport = scrollPane.viewport
                        val viewRect = viewport.viewRect
                        val viewHeight = viewport.view.height
                        viewport.viewPosition = Point(viewRect.x, viewHeight - viewRect.height)
                    }
                }
            } else {
                logger.error("Section not found for flow action: ${flowAction.subflowId}")
            }
        }
    }

    private fun handleConnectionError(error: Throwable, flowId: String) {
        ApplicationManager.getApplication().invokeLater {
            val errorLabel = JBLabel("Error: Failed to connect to flow actions. Retrying...").apply {
                border = JBUI.Borders.empty(8)
            }
            // FIXME the errorLabel should always exist, but be made visible when needed, instead of new labels being created every time an error occurs
            contentPanel.add(errorLabel)
            contentPanel.revalidate()
            contentPanel.repaint()
        }

        coroutineScope.launch {
            delay(RECONNECT_DELAY_MS)
            connectToFlowActions(flowId)
        }
    }

    override fun dispose() {
        coroutineScope.launch {
            flowActionSession?.close()
        }
        coroutineScope.cancel()
    }
}