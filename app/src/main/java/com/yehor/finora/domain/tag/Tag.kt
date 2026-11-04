package com.yehor.finora.domain.tag

data class Tag(
    val id: Long,
    val name: String,
    val color: Long?,
    val createdAt: Long,
)

data class TagWithUsage(
    val tag: Tag,
    val usageCount: Int,
)

enum class TagMatchMode { ANY, ALL }
