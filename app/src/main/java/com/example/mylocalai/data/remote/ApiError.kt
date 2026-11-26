package com.example.mylocalai.data.remote

import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/** Single mapping for HTTP/timeout failures across chat, files and tasks. */
data class ApiError(
    val status: Int?,
    val message: String,
    val isTimeout: Boolean = false,
) {
    companion object {
        fun from(throwable: Throwable): ApiError {
            if (throwable is HttpException) {
                val raw = runCatching { throwable.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
                val detail = parseDetail(raw)
                val message = when (throwable.code()) {
                    429 -> "Queue is full — try again shortly."
                    503 -> "Storage is busy — retrying."
                    409 -> "That idempotency key was used for a different request."
                    404 -> "Not found on the server."
                    else -> detail ?: "Request failed (HTTP ${throwable.code()})."
                }
                return ApiError(throwable.code(), message)
            }
            val timeout = throwable is SocketTimeoutException ||
                throwable is TimeoutException ||
                throwable.cause is SocketTimeoutException ||
                throwable.message?.contains("timeout", ignoreCase = true) == true
            return ApiError(
                status = null,
                message = if (timeout) "The request timed out." else throwable.message?.takeIf(String::isNotBlank) ?: "Connection error.",
                isTimeout = timeout,
            )
        }

        private fun parseDetail(raw: String): String? {
            if (raw.isBlank()) return null
            val json = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return raw.take(160)
            return listOf("detail", "message", "error")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                ?: raw.take(160)
        }
    }
}
