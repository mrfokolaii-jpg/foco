package com.example.data.connectors

/**
 * Shared result type for every external connector (GitHub, Hugging Face, Google Drive...).
 * Keeps the UI and the agent engine provider-agnostic.
 */
sealed class ConnectorResult<out T> {
    data class Ok<T>(val value: T) : ConnectorResult<T>()
    data class Err(val message: String, val httpCode: Int? = null) : ConnectorResult<Nothing>()

    val isOk: Boolean get() = this is Ok

    fun valueOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): String? = (this as? Err)?.message

    inline fun <R> map(transform: (T) -> R): ConnectorResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }
}

data class RemoteRepo(
    val fullName: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val htmlUrl: String
)

data class RemoteFile(
    val path: String,
    val sha: String,
    val content: String
)

data class RemoteDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class HfModelInfo(
    val id: String,
    val downloads: Long,
    val likes: Long,
    val pipelineTag: String
)
