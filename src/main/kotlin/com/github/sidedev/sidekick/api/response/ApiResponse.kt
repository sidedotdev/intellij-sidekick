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

    // similar to Rust's unwrap, not recommended
    fun unwrap(): T = when (this) {
        is Success -> this.data
        is Error -> throw IllegalStateException("This ApiResponse object is an error: ${this.error}")
    }

    // similar to Rust's try! macro (later replaced by `?` syntax), except we need the caller to provide a
    // lambda function to return @funcName
    fun unwrapOrEscalate(f: (E) -> Unit) = when (this) {
        is Success -> this
        is Error -> {
            f(this.error)
            throw IllegalStateException("unwrapOrEscalate - this should be unreachable with correct usage: This ApiResponse object is an error: ${this.error}")
        }
    }

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
