package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.*
import com.github.sidedev.sidekick.api.TaskRequest
import com.github.sidedev.sidekick.api.FlowOptions
import com.github.sidedev.sidekick.api.websocket.FlowActionSession
import com.github.sidedev.sidekick.models.FlowAction
import com.github.sidedev.sidekick.toolWindow.components.SubflowSection
import com.github.sidedev.sidekick.toolWindow.components.TaskInputsSection
import com.github.sidedev.sidekick.toolWindow.FlowActionComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Point
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingUtilities

class TaskViewPanel(
    private val task: Task,
    private val onAllTasksClick: () -> Unit,
    private val sidekickService: SidekickService = SidekickService(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
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
    private val subflowSections = mutableMapOf<String, SubflowSection>()

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

        // Add components to the panel
        add(allTasksLink, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        // Initialize sections and connect to flow actions if we have a flow
        task.flows?.firstOrNull()?.let { flow ->
            initializeFlowSections(flow)
            connectToFlowActions(flow.id)
        }
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

    private fun initializeFlowSections(flow: Flow) {
        coroutineScope.launch {
            val subflows = sidekickService.getSubflowsForFlow(task.workspaceId, flow.id).getOrNull() ?: return@launch
            
            ApplicationManager.getApplication().invokeLater {
                // Add requirements section if needed
                if (task.flowOptions?.get("determineRequirements")?.toString()?.toBoolean() == true) {
                    val requirementsSubflow = subflows.find { it.type == "requirements" }
                    requirementsSubflow?.let { subflow ->
                        val section = SubflowSection(subflow)
                        subflowSections[subflow.id] = section
                        contentPanel.add(section)
                        contentPanel.add(Box.createVerticalStrut(8))
                    }
                }

                // Add step sections based on flow type
                if (task.flowType == "planned_dev") {
                    subflows.filter { it.type == "step" }.forEach { subflow ->
                        val section = SubflowSection(subflow)
                        subflowSections[subflow.id] = section
                        contentPanel.add(section)
                        contentPanel.add(Box.createVerticalStrut(8))
                    }
                } else {
                    // For basic_dev, add a single coding section
                    val codingSubflow = subflows.find { it.type == "coding" }
                    codingSubflow?.let { subflow ->
                        val section = SubflowSection(subflow)
                        subflowSections[subflow.id] = section
                        contentPanel.add(section)
                    }
                }
                
                contentPanel.revalidate()
                contentPanel.repaint()
            }
        }
    }

    private fun handleFlowAction(flowAction: FlowAction) {
        ApplicationManager.getApplication().invokeLater {
            val actionComponent = FlowActionComponent(flowAction)
            val section = subflowSections[flowAction.subflowId]
            
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
            }
        }
    }

    private fun handleConnectionError(error: Throwable, flowId: String) {
        ApplicationManager.getApplication().invokeLater {
            val errorLabel = JBLabel("Error: Failed to connect to flow actions. Retrying...").apply {
                border = JBUI.Borders.empty(8)
            }
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