package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.*
import com.github.sidedev.sidekick.api.TaskRequest
import com.github.sidedev.sidekick.api.FlowOptions
import com.github.sidedev.sidekick.api.websocket.FlowActionSession
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.components.TaskExecutionSection
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

        // Section category constants
        internal const val SECTION_REQUIREMENTS_PLANNING = "requirements-planning"
        internal const val SECTION_CODING = "coding"
        internal const val SECTION_UNKNOWN = "uncategorized"

        // Subflow type constants  
        private const val TYPE_MISSING_TYPE = "missing_type"
        private const val TYPE_DEV_REQUIREMENTS = "dev_requirements"
        private const val TYPE_DEV_PLAN = "dev_plan"
        private const val TYPE_LLM_STEP = "llm_step"
        private const val TYPE_CODING = "coding"
    }

    private val cachedSubflows = mutableMapOf<String, Subflow>()
    private var flowActionSession: FlowActionSession? = null
    private val coroutineScope = CoroutineScope(dispatcher + Job())
    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val scrollPane = JBScrollPane()
    private var isUserScrolling = false
    private var shouldAutoScroll = true
    private val taskSections = mutableMapOf<String, TaskExecutionSection>()
    
    // Tracks if we've seen requirements and planning subflows to update section name
    internal var hasRequirementsSubflow = false
    internal var hasPlanningSubflow = false

    /**
     * Determines the appropriate section id for a subflow based on
     * its type or its ancestor's type
     */
    internal suspend fun determineSectionId(subflow: Subflow): String {
        val primarySubflow = findPrimarySubflow(subflow)
        return when (primarySubflow?.type) {
            TYPE_DEV_REQUIREMENTS, TYPE_DEV_PLAN -> SECTION_REQUIREMENTS_PLANNING
            TYPE_LLM_STEP -> primarySubflow.name
            TYPE_CODING -> SECTION_CODING
            else -> SECTION_UNKNOWN
        }
    }

    /**
     * Traverses parent subflows to find a relevant type for categorization
     */
    internal suspend fun findPrimarySubflow(subflow: Subflow): Subflow? {
        // Check current subflow type
        subflow.type?.let {
            when (it) {
                TYPE_DEV_REQUIREMENTS, TYPE_DEV_PLAN, TYPE_LLM_STEP, TYPE_CODING,
                     -> return subflow
                else -> Unit
            }
        }
        
        // Traverse parent if exists
        val parentId = subflow.parentSubflowId ?: return null

        val parentSubflow = getSubflow(parentId).getOrNull()
        return if (parentSubflow == null) {
            // TODO make a visible error message for this?
            logger.error("failed to get subflow with id: $parentId")
            null
        } else {
            logger.info("subflow type ${subflow.type} has parent subflow type ${parentSubflow.type}")
            findPrimarySubflow(parentSubflow)
        }
    }

    /**
     * Gets the display name for a section based on its section id
     */
    internal fun getSectionName(sectionId: String): String {
        return when (sectionId) {
            SECTION_REQUIREMENTS_PLANNING -> {
                when {
                    hasRequirementsSubflow && hasPlanningSubflow -> "Requirements and Planning"
                    hasRequirementsSubflow -> "Requirements"
                    hasPlanningSubflow -> "Planning"
                    else -> "Requirements/Planning" // shouldn't really happen
                }
            }
            SECTION_CODING -> "Coding"
            SECTION_UNKNOWN -> "Unknown"
            else -> sectionId
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
        // FIXME rewrite task inputs section to inherit from accordion section, just like TaskSection does
        contentPanel.add(TaskInputsSection(taskRequest))

        // Configure scroll pane
        scrollPane.apply {
            setViewportView(contentPanel)
            // TODO // setColumnHeaderView()
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
        val actionComponent = FlowActionComponent(flowAction)

        // Handle actions without a subflow first
        if (flowAction.subflowId == null) {
            ApplicationManager.getApplication().invokeLater {
                // Add to last section if it exists and isn't requirements/planning
                val lastSection = taskSections.values.lastOrNull()
                if (lastSection != null && taskSections.keys.last() != SECTION_REQUIREMENTS_PLANNING) {
                    val maybeGlue = contentPanel.components.lastOrNull()
                    if (maybeGlue?.name == "end_glue") {
                        lastSection.remove(maybeGlue)
                    }
                    lastSection.addFlowAction(actionComponent)
                    lastSection.add(Box.createVerticalGlue().apply { name = "end_glue" });
                    lastSection.revalidate()
                    lastSection.repaint()
                } else {
                    // Add directly to content panel if no suitable section exists
                    val maybeGlue = contentPanel.components.lastOrNull()
                    if (maybeGlue?.name == "end_glue") {
                        contentPanel.remove(maybeGlue)
                    }
                    contentPanel.add(actionComponent)
                    contentPanel.add(Box.createVerticalGlue().apply { name = "end_glue" });
                    contentPanel.revalidate()
                    contentPanel.repaint()
                }
            }
            return
        }

        // For categorized actions, get subflow details first
        val subflowId = flowAction.subflowId
        val subflow = getSubflow(subflowId).getOrNull()
        if (subflow == null) {
            // TODO make a visible error message for this?
            logger.error("failed to get subflow with id: $subflowId")
            return
        }

        // Update requirements/planning tracking variables, used only for
        // determining the title of that section
        when (subflow.type) {
            TYPE_DEV_REQUIREMENTS -> hasRequirementsSubflow = true
            TYPE_DEV_PLAN -> hasPlanningSubflow = true
        }

        val sectionId = determineSectionId(subflow)
        logger.info("subflow type: ${subflow.type}, section id: $sectionId")

        // Create section if needed
        // Add flow action to the appropriate section
        ApplicationManager.getApplication().invokeLater {
            val section: TaskExecutionSection = synchronized(taskSections) {
                val existingSection = taskSections[sectionId]
                if (existingSection != null) {
                    existingSection.updateName(getSectionName(sectionId))
                    existingSection
                } else {
                    val maybeGlue = contentPanel.components.lastOrNull()
                    if (maybeGlue?.name == "end_glue") {
                        contentPanel.remove(maybeGlue)
                    }
                    val section = TaskExecutionSection(getSectionName(sectionId))
                    contentPanel.add(section)
                    contentPanel.add(Box.createVerticalGlue().apply { name = "end_glue" });

                    contentPanel.revalidate()
                    contentPanel.repaint()
                    taskSections[sectionId] = section
                    section
                }
            }
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
        }
    }

    private suspend fun getSubflow(subflowId: String): Result<Subflow> {
        val cachedSubflow = cachedSubflows[subflowId]
        if (cachedSubflow != null) {
            return Result.success(cachedSubflow)
        }

        // Fetch subflow details and determine category
        val subflowResponse = sidekickService.getSubflow(task.workspaceId, subflowId)
        val subflow = subflowResponse.getOrNull()
        if (subflow != null) {
            cachedSubflows[subflowId] = subflow
        }
        return subflowResponse.asResult()
    }

    private fun handleConnectionError(error: Throwable, flowId: String) {
        ApplicationManager.getApplication().invokeLater {
            logger.error(error)
            /*
            val errorLabel = JBLabel("Error: Failed to connect to flow actions. Retrying...").apply {
                border = JBUI.Borders.empty(8)
            }
            // FIXME the errorLabel should always exist, but be made visible when needed, instead of new labels being created every time an error occurs
            contentPanel.add(errorLabel)
            contentPanel.revalidate()
            contentPanel.repaint()
             */
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