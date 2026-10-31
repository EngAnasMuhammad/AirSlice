package com.anasdev.fluxlink.domain.model

data class FileMetadata(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModified: Long,
    val formattedDate: String,
    val lastAccessedMillis: Long = lastModified,
    val formattedAccessDate: String = formattedDate,
    val mimeType: String,
    val isMediaImage: Boolean,
    val isMediaVideo: Boolean
)
