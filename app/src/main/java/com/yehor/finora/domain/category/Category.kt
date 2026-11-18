package com.yehor.finora.domain.category

import com.yehor.finora.domain.model.CategoryType

data class Category(
    val id: Long,
    val name: String,
    val icon: String?,
    val color: Long?,
    val type: CategoryType,
    val parentId: Long?,
    val createdAt: Long,
)

data class CategoryNode(val category: Category, val children: List<CategoryNode>)
