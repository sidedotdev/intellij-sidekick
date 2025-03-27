package com.github.sidedev.sidekick.toolWindow

import java.util.concurrent.ConcurrentHashMap
import com.github.sidedev.sidekick.toolWindow.SidekickToolWindow

/**
 * Singleton manager for SidekickToolWindow instances.
 * Provides thread-safe access to tool windows across the IDE.
 */
object SidekickToolWindowManager {
    private val toolWindows = ConcurrentHashMap<String, SidekickToolWindow>()

    /**
     * Stores a SidekickToolWindow instance for the given project.
     * @param projectBasePath The base path of the project
     * @param window The SidekickToolWindow instance to store
     */
    fun storeWindow(projectBasePath: String, window: SidekickToolWindow) {
        toolWindows[projectBasePath] = window
    }

    /**
     * Retrieves the SidekickToolWindow instance for the given project.
     * @param projectBasePath The base path of the project
     * @return The SidekickToolWindow instance or null if not found
     */
    fun getWindow(projectBasePath: String): SidekickToolWindow? {
        return toolWindows[projectBasePath]
    }

    /**
     * Removes the SidekickToolWindow instance for the given project.
     * @param projectBasePath The base path of the project
     */
    fun removeWindow(projectBasePath: String) {
        toolWindows.remove(projectBasePath)
    }
}