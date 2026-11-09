package com.yehor.finora.ui.common

import com.yehor.finora.domain.model.CategoryType

/**
 * 28 ARGB colors ordered by hue (reds, oranges/yellows, greens, teals/cyans, blues, indigos/violets,
 * purples/pinks, browns, neutrals). Each keeps >= 3:1 against a white check mark and stays visible
 * through `finance.accent()` on dark surfaces. A stored color outside the palette still renders;
 * null falls back to a default.
 */
object ColorPalette {
    val colors: List<Long> = listOf(
        // Reds
        0xFFDC2626L, // red
        0xFFE5533DL, // coral
        // Oranges and yellows
        0xFFEA6A0CL, // orange
        0xFFD97706L, // amber
        0xFFB7860BL, // gold
        // Greens
        0xFF779A0BL, // olive
        0xFF4D9A12L, // lime
        0xFF16A34AL, // green
        0xFF0E9F6EL, // emerald
        // Teals and cyans
        0xFF0D9488L, // teal
        0xFF0891B2L, // cyan
        // Blues
        0xFF0A8FD8L, // sky
        0xFF1E88E5L, // azure
        0xFF2563EBL, // blue
        // Indigos and violets
        0xFF4F46E5L, // indigo
        0xFF6B5CE7L, // brand violet
        0xFF7C3AEDL, // violet
        // Purples and pinks
        0xFF9C3FE0L, // purple
        0xFFC026D3L, // fuchsia
        0xFFD1288FL, // magenta
        0xFFDB2777L, // pink
        0xFFE11D48L, // rose
        // Browns
        0xFF8D5B3AL, // sienna
        0xFF6D4C41L, // cocoa
        // Neutrals
        0xFF5F8A6EL, // sage
        0xFF64748BL, // slate
        0xFF71717AL, // grey
        0xFF52525BL, // charcoal
    )

    const val ACCOUNT_DEFAULT = 0xFF2563EBL // blue
    private const val EXPENSE_DEFAULT = 0xFFDC2626L // red
    private const val INCOME_DEFAULT = 0xFF16A34AL // green
    private const val TRANSFER_DEFAULT = 0xFF64748BL // slate

    fun defaultFor(type: CategoryType): Long = when (type) {
        CategoryType.EXPENSE -> EXPENSE_DEFAULT
        CategoryType.INCOME -> INCOME_DEFAULT
        CategoryType.TRANSFER -> TRANSFER_DEFAULT
    }

    /** The stored color, or [fallback] when null. Out-of-palette values are returned unchanged. */
    fun resolve(color: Long?, fallback: Long): Long = color ?: fallback
}
