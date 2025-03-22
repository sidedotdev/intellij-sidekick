package com.github.sidedev.sidekick.api.response

sealed class ApiResponse<out T, out E> {
    data class Success<out T>(
        val data: T,
    ) : ApiResponse<T, Nothing>()

    data class Error<out E>(
        val error: E,
    ) : ApiResponse<Nothing, E>()

    fun isSuccess(): Boolean = this is Success

    fun isError(): Boolean = this is Error

    fun getErrorIfAny(): E? = when (this) {
        is Error -> this.error
        else -> null
    }

    fun getDataOrThrow(): T = when (this) {
        is Success -> this.data
        else -> throw IllegalStateException("This ApiResponse object is an error: ${(this as Error).error}")
    }

    fun <U> map(f: (T) -> U): ApiResponse<U, E> {
        if (isSuccess()) return Success(f((this as Success).data))
        return (this as Error)
    }

    fun <F> mapError(f: (E) -> F): ApiResponse<T, F> {
        if (isError()) return Error(f((this as Error).error))
        return (this as Success)
    }

    fun <U> mapOrElse(successMapper: (T) -> U, errorMapper: (E) -> U): U = when {
        isSuccess() -> successMapper((this as Success).data)
        else -> errorMapper((this as Error).error)
    }
}
