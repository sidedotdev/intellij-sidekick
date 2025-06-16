package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.*
import com.github.sidedev.sidekick.api.TaskRequest
import com.github.sidedev.sidekick.api.FlowOptions
import com.github.sidedev.sidekick.api.websocket.FlowActionSession
import com.github.sidedev.sidekick.api.websocket.FlowEventsSession
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.models.flowEvent.FlowEvent
import com.github.sidedev.sidekick.toolWindow.components.FlowActionComponent
import com.github.sidedev.sidekick.toolWindow.components.TaskExecutionSection
import com.github.sidedev.sidekick.toolWindow.components.TaskInputsSection
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.SwingUtilities
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.JBUI
import javax.swing.border.EmptyBorder

class TaskViewPanel(
    private val task: Task,
    private val sidekickService: SidekickService = SidekickService(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val logger: Logger = logger<TaskViewPanel>(),
) : JBPanel<TaskViewPanel>(BorderLayout()), Disposable {

    companion object {
        const val NAME = "TaskView"
        private const val RECONNECT_DELAY_MS = 1000L
        private const val DEBOUNCE_DELAY_MS = 100L

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
        private const val TYPE_PASS_TESTS = "pass_tests"
    }

    private val debounceMutex = Mutex()
    private val debounceJobs = mutableMapOf<String, Job>()

    private val cachedSubflows = mutableMapOf<String, Subflow>()
    private var flowActionSession: FlowActionSession? = null
    private var flowEventsSession: FlowEventsSession? = null
    private val coroutineScope = CoroutineScope(dispatcher + Job())
    private val contentPanel = JBPanel<JBPanel<*>>().apply {
        border = EmptyBorder(JBUI.insets(5))
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
            TYPE_LLM_STEP, TYPE_PASS_TESTS -> primarySubflow.name
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
                TYPE_DEV_REQUIREMENTS, TYPE_DEV_PLAN, TYPE_LLM_STEP, TYPE_CODING, TYPE_PASS_TESTS
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
            logger.debug("subflow type ${subflow.type} has parent subflow type ${parentSubflow.type}")
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
                // Connect to flow actions
                val actionSession = sidekickService.connectToFlowActions(
                    workspaceId = task.workspaceId,
                    flowId = flowId,
                    onMessage = { flowAction ->
                        handleFlowAction(flowAction)
                    },
                    onError = { error ->
                        handleConnectionError(error, flowId)
                    }
                ).getOrThrow()
                
                flowActionSession = actionSession

                // Connect to flow events
                val eventSession = sidekickService.connectToFlowEvents(
                    workspaceId = task.workspaceId,
                    flowId = flowId,
                    onMessage = { flowEvent ->
                        handleFlowEvent(flowEvent)
                    },
                    onError = { error ->
                        handleConnectionError(error, flowId)
                    }
                ).getOrThrow()

                flowEventsSession = eventSession
            } catch (e: Exception) {
                handleConnectionError(e, flowId)
            }
        }
    }

    private fun connectToFlowEvents(parentId: String) {
        coroutineScope.launch {
            try {
                flowEventsSession?.subscribeToParent(parentId)
                if (flowEventsSession == null) {
                    logger.error("missing flow events session when trying to connect to flow events")
                }
            } catch (e: Exception) {
                logger.error("failed to get subscribe to flow events for parent id: $parentId")
            }
        }
    }

    private suspend fun handleFlowEvent(flowEvent: FlowEvent) {
        // Process the flow event
        ApplicationManager.getApplication().invokeLater {
            logger.trace("Processing flow event: $flowEvent")
            // TODO: Add specific event handling logic here as needed
        }
    }

    private suspend fun handleFlowAction(flowAction: FlowAction) {
        debounceMutex.withLock {
            // Cancel any existing debounce job for this action
            debounceJobs[flowAction.id]?.cancel()

            // Create new debounce job
            debounceJobs[flowAction.id] = coroutineScope.launch {
                try {
                    delay(DEBOUNCE_DELAY_MS)

                    processFlowActionUpdate(flowAction)

                    // If action is non-terminal, subscribe to parent
                    if (flowAction.actionStatus.isNonTerminal()) {
                        connectToFlowEvents(flowAction.flowId)
                    }
                } finally {
                    debounceMutex.withLock {
                        debounceJobs.remove(flowAction.id)
                    }
                }
            }
        }
    }

    private suspend fun processFlowActionUpdate(flowAction: FlowAction) {
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
                    lastSection.processAction(flowAction, subflow = null)
                    lastSection.add(Box.createVerticalGlue().apply { name = "end_glue" });
                    lastSection.revalidate()
                    lastSection.repaint()
                } else {
                    // Add directly to content panel if no suitable section exists
                    val maybeGlue = contentPanel.components.lastOrNull()
                    if (maybeGlue?.name == "end_glue") {
                        contentPanel.remove(maybeGlue)
                    }
                    val actionComponent = FlowActionComponent(flowAction)
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
        logger.debug("subflow type: ${subflow.type}, section id: $sectionId")

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
            // Use processAction instead of addFlowAction
            section.processAction(flowAction, subflow)
            // Keep revalidate/repaint for now, although processAction handles it for new components
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
            flowEventsSession?.close()
            
            // Cancel all debounce jobs
            debounceMutex.withLock {
                debounceJobs.values.forEach { it.cancel() }
                debounceJobs.clear()
            }
            coroutineScope.cancel()
        }
    }
}