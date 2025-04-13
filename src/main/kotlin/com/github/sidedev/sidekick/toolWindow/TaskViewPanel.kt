package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.Task
import com.github.sidedev.sidekick.api.websocket.FlowActionSession
import com.github.sidedev.sidekick.models.FlowAction
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
    private val actionsPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private val scrollPane = JBScrollPane()
    private var isUserScrolling = false
    private var shouldAutoScroll = true

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

        // Create main content panel
        val contentPanel = JBPanel<JBPanel<*>>().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // Create and configure the description text area
        val descriptionArea = JBTextArea().apply {
            text = task.description
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            border = JBUI.Borders.empty(8)
        }

        // Add description and actions to content panel
        contentPanel.add(descriptionArea)
        contentPanel.add(Box.createVerticalStrut(16))
        contentPanel.add(actionsPanel)

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

        // Connect to flow actions if we have a flow
        task.flows?.firstOrNull()?.let { flow ->
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

    private fun handleFlowAction(flowAction: FlowAction) {
        ApplicationManager.getApplication().invokeLater {
            val actionComponent = FlowActionComponent(flowAction)
            actionsPanel.add(actionComponent)
            actionsPanel.revalidate()
            actionsPanel.repaint()

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

    private fun handleConnectionError(error: Throwable, flowId: String) {
        ApplicationManager.getApplication().invokeLater {
            val errorLabel = JBLabel("Error: Failed to connect to flow actions. Retrying...").apply {
                border = JBUI.Borders.empty(8)
            }
            actionsPanel.add(errorLabel)
            actionsPanel.revalidate()
            actionsPanel.repaint()
        }

        coroutineScope.launch {
            delay(RECONNECT_DELAY_MS)
            connectToFlowActions(flowId)
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
        flowActionSession?.close()
    }
}