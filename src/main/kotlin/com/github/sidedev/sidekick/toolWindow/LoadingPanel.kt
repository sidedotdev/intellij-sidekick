package com.github.sidedev.sidekick.toolWindow

import com.github.sidedev.sidekick.MyBundle
import com.github.sidedev.sidekick.api.SidekickService
import com.github.sidedev.sidekick.api.response.ApiResponse
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class LoadingPanel(
    private val project: Project,
    private val sidekickService: SidekickService,
    private val onWorkspaceLoaded: (String) -> Unit
) : JBPanel<JBPanel<*>>() {
    internal val statusLabel: JLabel
    private val retryButton: JButton

    override fun getName() = NAME

    init {
        layout = BorderLayout()

        val topPanel = JPanel(BorderLayout())
        statusLabel = JLabel(MyBundle.message("statusLabel", "Loading...")).apply {
            horizontalAlignment = SwingConstants.CENTER
            text = "Setting up workspace..."
        }
        topPanel.add(statusLabel, BorderLayout.CENTER)

        retryButton = JButton("Retry").apply {
            isVisible = false
            addActionListener {
                ApplicationManager.getApplication().executeOnPooledThread {
                    CoroutineScope(Dispatchers.IO).launch {
                        setupWorkspace()
                    }
                }
            }
        }
        topPanel.add(retryButton, BorderLayout.EAST)
        add(topPanel, BorderLayout.CENTER)

        // Start loading workspace
        ApplicationManager.getApplication().executeOnPooledThread {
            CoroutineScope(Dispatchers.IO).launch {
                setupWorkspace()
            }
        }
    }

    private fun getWorkspaceIdKey(): String {
        return SidekickToolWindow.WORKSPACE_ID_KEY_PREFIX + (project.basePath ?: "")
    }

    private fun setCachedWorkspaceId(workspaceId: String) {
        PropertiesComponent.getInstance(project).setValue(getWorkspaceIdKey(), workspaceId)
    }

    internal suspend fun setupWorkspace() {
        ApplicationManager.getApplication().invokeLater {
            statusLabel.text = "Setting up workspace..."
            statusLabel.isVisible = true
            retryButton.isVisible = false
        }

        when (val workspacesResult = sidekickService.getWorkspaces()) {
            is ApiResponse.Success -> {
                val workspaces = workspacesResult.data
                val projectPath = project.basePath
                if (projectPath != null) {
                    for (workspace in workspaces) {
                        if (projectPath == workspace.localRepoDir) {
                            // Cache the workspace ID
                            setCachedWorkspaceId(workspace.id)
                            onWorkspaceLoaded(workspace.id)
                            return
                        }
                    }
                }

                // No matching workspace found
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = MyBundle.message("statusLabel", "No workspace set up yet")
                }
            }
            is ApiResponse.Error -> {
                val message = "Side is not running. Please run `side start`. Error: ${workspacesResult.error.error}"
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = MyBundle.message("statusLabel", message)
                    retryButton.isVisible = true
                }
            }
        }
    }

    companion object {
        const val NAME: String = "LOADING"
    }
}