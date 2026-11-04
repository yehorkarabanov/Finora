package com.yehor.finora.ui.common

import com.yehor.finora.domain.model.CategoryType

/** 16 ARGB colors. A stored color outside the palette still renders; null falls back to a default. */
object ColorPalette {
    val colors: List<Long> = listOf(
        0xFFE53935L, // red
        0xFFF4511EL, // deep orange
        0xFFFB8C00L, // orange
        0xFFFFB300L, // amber
        0xFF7CB342L, // lime
        0xFF43A047L, // green
        0xFF00897BL, // teal
        0xFF00ACC1L, // cyan
        0xFF1E88E5L, // blue
        0xFF3949ABL, // indigo
        0xFF5E35B1L, // deep purple
        0xFF8E24AAL, // purple
        0xFFD81B60L, // pink
        0xFF6D4C41L, // brown
        0xFF546E7AL, // blue grey
        0xFF757575L, // grey
    )

    const val ACCOUNT_DEFAULT = 0xFF1E88E5L
    private const val EXPENSE_DEFAULT = 0xFFE53935L
    private const val INCOME_DEFAULT = 0xFF43A047L
    private const val TRANSFER_DEFAULT = 0xFF546E7AL

    fun defaultFor(type: CategoryType): Long = when (type) {
        CategoryType.EXPENSE -> EXPENSE_DEFAULT
        CategoryType.INCOME -> INCOME_DEFAULT
        CategoryType.TRANSFER -> TRANSFER_DEFAULT
    }

    /** The stored color, or [fallback] when null. Out-of-palette values are returned unchanged. */
    fun resolve(color: Long?, fallback: Long): Long = color ?: fallback
}
