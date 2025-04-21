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