package com.github.sidedev.sidekick.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ToolParams

@Serializable
@SerialName("retrieve_code_context")
data class RetrieveCodeContextParams(
    val analysis: String = "",
    @SerialName("code_context_requests")
    val codeContextRequests: List<CodeContextRequest>
) : ToolParams {
    @Serializable
    data class CodeContextRequest(
        @SerialName("file_path")
        val filePath: String,
        @SerialName("symbol_names")
        val symbolNames: List<String>? = null
    )
}

@Serializable
@SerialName("bulk_search_repository")
data class BulkSearchRepositoryParams(
    @SerialName("context_lines")
    val contextLines: Int = 0,
    val searches: List<Search>
) : ToolParams {
    @Serializable
    data class Search(
        @SerialName("path_glob")
        val pathGlob: String,
        @SerialName("search_term")
        val searchTerm: String
    )
}

@Serializable
@SerialName("read_file_lines")
data class ReadFileLinesParams(
    @SerialName("file_lines")
    val fileLines: List<FileLine>,
    @SerialName("window_size")
    val windowSize: Int = 0,
) : ToolParams {
    @Serializable
    data class FileLine(
        @SerialName("file_path")
        val filePath: String,
        @SerialName("line_number")
        val lineNumber: Int
    )
}

@Serializable
@SerialName("get_help_or_input")
data class GetHelpOrInputParams(
    val requests: List<Request>
) : ToolParams {
    @Serializable
    data class Request(
        val content: String,
        @SerialName("self_help")
        val selfHelp: SelfHelp
    ) {
        @Serializable
        data class SelfHelp(
            val analysis: String = "",
            val functions: List<String> = emptyList(),
            @SerialName("already_attempted_tools")
            val alreadyAttemptedTools: List<String> = emptyList()
        )
    }
}