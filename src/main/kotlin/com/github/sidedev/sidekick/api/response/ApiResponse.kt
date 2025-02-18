package com.github.sidedev.sidekick.api.response

sealed class ApiResponse<out T, out E> {
    data class Success<T>(val data: T) : ApiResponse<T, Nothing>()
    data class Error<E>(val error: E) : ApiResponse<Nothing, E>()

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    fun getErrorIfAny() : E? = when (this) {
        is Error -> this.error
        else -> null
    }

    fun getDataOrThrow() : T = when (this) {
        is Success -> this.data
        else -> throw IllegalStateException("This ApiResponse object should not be an error.")
    }

}