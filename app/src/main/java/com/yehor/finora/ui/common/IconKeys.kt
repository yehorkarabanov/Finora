package com.yehor.finora.ui.common

/**
 * Stable string keys for icons. Keys are stored in user data and exported files, so they are
 * never renamed. Plain Kotlin so JVM tests can read it; [IconCatalog] maps keys to vectors.
 */
object IconKeys {
    const val DEFAULT = "other"

    /** Icons offered for accounts. */
    val account: List<String> = listOf(
        "wallet", "bank", "card", "cash", "savings", "trending_up", "chart", "business",
        "phone", "paid", "lock", "star",
    )

    /** Icons offered for categories. Includes every icon used by the default categories. */
    val category: List<String> = listOf(
        "food", "cart", "restaurant", "coffee", "home", "car", "bus", "flight",
        "shopping", "entertainment", "health", "fitness", "pets", "school", "build", "bolt",
        "subscription", "fee", "work", "laptop", "gift", "refund", "swap", "exchange",
        "savings", "trending_up", "other",
    )

    val all: Set<String> = (account + category).toSet()

    /** Unknown or null keys fall back to [DEFAULT]. */
    fun resolve(key: String?): String = if (key != null && key in all) key else DEFAULT
}
