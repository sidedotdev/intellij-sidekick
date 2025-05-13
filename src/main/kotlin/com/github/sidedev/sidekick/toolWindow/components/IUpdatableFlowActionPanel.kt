package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.models.FlowAction

/**
 * Interface for UI components that can display and update FlowAction data.
 */
interface IUpdatableFlowActionPanel {
    /**
     * Updates the component to display the data from the new FlowAction.
     * @param newFlowAction The new FlowAction data to display.
     */
    fun update(newFlowAction: FlowAction)
}