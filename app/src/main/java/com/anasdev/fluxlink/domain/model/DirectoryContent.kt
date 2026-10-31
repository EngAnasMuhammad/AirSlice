package com.anasdev.fluxlink.domain.model

data class DirectoryContent(
    val currentPath: String,
    val parentPath: String?,
    val folderName: String,
    val totalItems: Int,
    val items: List<FileMetadata>
)
