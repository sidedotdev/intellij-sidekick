package com.github.sidedev.sidekick.toolWindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.ContentFactory;

public class SidekickToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        SidekickToolWindow myToolWindow = new SidekickToolWindow(toolWindow);
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

        public SidekickToolWindow(ToolWindow toolWindow) {
            this.toolWindow = toolWindow;
        }

        public JBPanel<JBPanel<?>> getContent() {
            JBPanel<JBPanel<?>> mainPanel = new JBPanel<>();
            return mainPanel;
        }
    }
}