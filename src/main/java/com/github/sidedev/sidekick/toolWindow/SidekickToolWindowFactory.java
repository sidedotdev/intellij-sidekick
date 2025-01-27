package com.github.sidedev.sidekick.toolWindow;

import com.github.sidedev.sidekick.MyBundle;
import com.github.sidedev.sidekick.api.WorkspaceService;
import com.github.sidedev.sidekick.api.WorkspaceResponse;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class SidekickToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SidekickToolWindow myToolWindow = new SidekickToolWindow(toolWindow, project);
        var content = ContentFactory.getInstance().createContent(
                myToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return true;
    }

    private static class SidekickToolWindow {
        private final ToolWindow toolWindow;
        private final Project project;
        private final WorkspaceService workspaceService;
        
        public SidekickToolWindow(ToolWindow toolWindow, Project project) {
            this.toolWindow = toolWindow;
            this.project = project;
            this.workspaceService = new WorkspaceService();
        }

        public JBPanel<JBPanel<?>> getContent() {
            JBPanel<JBPanel<?>> mainPanel = new JBPanel<>();
            mainPanel.setLayout(new BorderLayout());
            
            JLabel statusLabel = new JLabel(MyBundle.message("statusLabel", "?"));
            statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
            mainPanel.add(statusLabel, BorderLayout.CENTER);
            
            // Check workspace status
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                String message = determineWorkspaceStatus();
                ApplicationManager.getApplication().invokeLater(() -> {
                    statusLabel.setText(MyBundle.message("statusLabel", message));
                });
            });
            
            return mainPanel;
        }

        private String determineWorkspaceStatus() {
            Optional<WorkspaceResponse> response = workspaceService.getWorkspaces();
            if (response.isEmpty()) {
                return "Side is not running. Please run `side start`";
            }

            String projectPath = project.getBasePath();
            if (projectPath != null) {
                for (WorkspaceResponse.Workspace workspace : response.get().getWorkspaces()) {
                    if (projectPath.equals(workspace.getLocalRepoDir())) {
                        return "Found workspace " + workspace.getId();
                    }
                }
            }
            
            return "No workspace set up yet";
        }
    }
}