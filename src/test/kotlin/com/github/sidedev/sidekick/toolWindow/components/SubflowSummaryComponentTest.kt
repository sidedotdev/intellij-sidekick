package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.api.SubflowStatus
import com.github.sidedev.sidekick.models.*
import com.intellij.testFramework.UsefulTestCase
import com.intellij.ui.AnimatedIcon
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class SubflowSummaryComponentTest : UsefulTestCase() {

    private lateinit var component: SubflowSummaryComponent

    // Helper to create a FlowAction for tests
    private fun createFlowAction(
        actionType: String = "generic_action",
        actionStatus: ActionStatus = ActionStatus.STARTED,
        updated: Instant = Clock.System.now(),
        actionParams: Map<String, JsonElement> = emptyMap()
    ): FlowAction {
        return FlowAction(
            id = "action-${Clock.System.now().toEpochMilliseconds()}",
            flowId = "flow1",
            subflowId = "subflow1",
            workspaceId = "ws1",
            created = Clock.System.now(),
            updated = updated,
            actionType = actionType,
            actionParams = actionParams,
            actionStatus = actionStatus,
            actionResult = "",
            isHumanAction = false
        )
    }

    // Helper to create a Subflow for tests
    private fun createSubflow(
        status: SubflowStatus,
        type: String = "code_context"
    ): Subflow {
        return Subflow(
            workspaceId = "ws1",
            id = "subflow1",
            name = "Test Code Context Subflow",
            type = type,
            description = "Generating code context",
            status = status,
            flowId = "flow1"
        )
    }

    override fun setUp() {
        super.setUp()
        component = SubflowSummaryComponent() // Assumes default constructor or DI if any
    }

    fun `test initial state matches STARTED with no specific action`() {
        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
        // Assuming loadingIcon is part of secondaryContentPanel and visible when panel is visible
        assertTrue(component.loadingIconContainer.isVisible) // Assuming loadingIcon is in a JBLabel named loadingIconContainer
        assertEquals(AnimatedIcon.Default::class.java, component.loadingIconContainer.icon::class.java)
    }

    fun `test update with SubflowStatus STARTED and null action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        component.update(null, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
        assertTrue(component.loadingIconContainer.isVisible)
        assertEquals(AnimatedIcon.Default::class.java, component.loadingIconContainer.icon::class.java)
    }

    fun `test test update with SubflowStatus STARTED and non-tool_call action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "some_processing_step")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Thinking...", component.secondaryLabel.text)
    }

    fun `test update with bulk search repository action - single search`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = BulkSearchRepositoryParams(
            searches = listOf(
                BulkSearchRepositoryParams.Search(
                    searchTerm = "findMe",
                    pathGlob = "*.kt"
                )
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.bulk_search_repository",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Searching for: 'findMe' in *.kt", component.secondaryLabel.text)
    }

    fun `test update with bulk search repository action - multiple searches`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = BulkSearchRepositoryParams(
            searches = listOf(
                BulkSearchRepositoryParams.Search(searchTerm = "term1", pathGlob = "*.kt"),
                BulkSearchRepositoryParams.Search(searchTerm = "term2", pathGlob = "*.java")
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.bulk_search_repository",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Searching for: 'term1' in *.kt, 'term2' in *.java", component.secondaryLabel.text)
    }

    fun `test update with retrieve code context action - single file`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = RetrieveCodeContextParams(
            analysis = "",
            codeContextRequests = listOf(
                RetrieveCodeContextParams.CodeContextRequest(
                    filePath = "src/main/kotlin/MyFile.kt",
                    symbolNames = listOf("MyClass", "myFunction")
                )
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.retrieve_code_context",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Looking up: src/main/kotlin/MyFile.kt (MyClass, myFunction)", component.secondaryLabel.text)
    }

    fun `test update with retrieve code context action - multiple files`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = RetrieveCodeContextParams(
            analysis = "",
            codeContextRequests = listOf(
                RetrieveCodeContextParams.CodeContextRequest(filePath = "src/main/kotlin/File1.kt"),
                RetrieveCodeContextParams.CodeContextRequest(filePath = "src/main/kotlin/File2.kt")
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.retrieve_code_context",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Looking up: src/main/kotlin/File1.kt, src/main/kotlin/File2.kt", component.secondaryLabel.text)
    }
    
    fun `test update with SubflowStatus STARTED and tool_call action with single word`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val action = createFlowAction(actionType = "tool_call.retrieve")
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Retrieve", component.secondaryLabel.text)
    }


    fun `test update with SubflowStatus COMPLETE`() {
        val subflow = createSubflow(status = SubflowStatus.COMPLETE)
        component.update(null, subflow) // Action shouldn't matter for COMPLETE status visibility

        assertEquals("Found Relevant Code", component.primaryLabel.text)
        assertFalse(component.secondaryContentPanel.isVisible)
    }

    fun `test update with SubflowStatus FAILED`() {
        val subflow = createSubflow(status = SubflowStatus.FAILED)
        component.update(null, subflow) // Action shouldn't matter for FAILED status visibility

        assertEquals("Failed to Find Code", component.primaryLabel.text)
        assertFalse(component.secondaryContentPanel.isVisible)
    }

    fun `test update with read file lines action - single file`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = ReadFileLinesParams(
            fileLines = listOf(
                ReadFileLinesParams.FileLine(
                    filePath = "src/main/kotlin/MyFile.kt",
                    lineNumber = 42
                )
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.read_file_lines",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Reading: src/main/kotlin/MyFile.kt", component.secondaryLabel.text)
    }

    fun `test update with read file lines action - multiple files`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = ReadFileLinesParams(
            fileLines = listOf(
                ReadFileLinesParams.FileLine(filePath = "src/main/kotlin/File1.kt", lineNumber = 10),
                ReadFileLinesParams.FileLine(filePath = "src/main/kotlin/File2.kt", lineNumber = 20)
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.read_file_lines",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Reading: src/main/kotlin/File1.kt, src/main/kotlin/File2.kt", component.secondaryLabel.text)
    }

    fun `test update with get help or input action`() {
        val subflow = createSubflow(status = SubflowStatus.STARTED)
        val toolParams = GetHelpOrInputParams(
            requests = listOf(
                GetHelpOrInputParams.Request(
                    content = "Test request",
                    selfHelp = GetHelpOrInputParams.Request.SelfHelp(
                        analysis = "Test analysis",
                        functions = emptyList(),
                        alreadyAttemptedTools = emptyList()
                    )
                )
            )
        )
        val params = mapOf("params" to Json.encodeToJsonElement(toolParams))
        val action = createFlowAction(
            actionType = "tool_call.get_help_or_input",
            actionParams = params
        )
        component.update(action, subflow)

        assertEquals("Finding Relevant Code", component.primaryLabel.text)
        assertTrue(component.secondaryContentPanel.isVisible)
        assertEquals("Waiting for input...", component.secondaryLabel.text)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : java.awt.Component> SubflowSummaryComponent.findComponentByName(name: String): T? {
        return this.components.find { it.name == name } as? T
    }
}