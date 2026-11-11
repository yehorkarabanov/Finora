package com.yehor.finora.designsystem.icon

/** A titled section of the icon picker. [id] maps to a string resource in the UI layer. */
data class IconGroup(val id: String, val keys: List<String>)

/**
 * String keys for icons, named after the concept (not the glyph). Keys are the stored identifier
 * and may change during development; an unknown or null key renders as [DEFAULT] via [resolve],
 * so old data never crashes. Plain Kotlin so JVM tests can read it; [IconCatalog] maps keys to
 * vectors.
 *
 * Within [category] and within [account] a key appears once; the same key may be offered to both.
 */
object IconKeys {
    const val DEFAULT = "other"

    /** Groups offered for accounts. */
    val accountGroups: List<IconGroup> = listOf(
        IconGroup("everyday", listOf("wallet", "cash", "card", "phone", "pos")),
        IconGroup("banks", listOf("bank", "savings", "deposit", "euro", "dollar")),
        IconGroup(
            "investments",
            listOf("investments", "brokerage", "crypto", "gold", "real_estate", "pension", "dividends"),
        ),
        IconGroup("other", listOf("business", "gift_card", "loan", "favorite", "other")),
    )

    /** Groups offered for categories. Includes every icon used by the default categories. */
    val categoryGroups: List<IconGroup> = listOf(
        IconGroup(
            "food",
            listOf("groceries", "restaurant", "coffee", "bakery", "bar", "delivery", "pizza", "dessert"),
        ),
        IconGroup(
            "transport",
            listOf(
                "car", "fuel", "bus", "train", "taxi", "bike", "scooter", "motorcycle", "parking",
                "ev_charging", "car_repair", "flight", "ship",
            ),
        ),
        IconGroup(
            "home",
            listOf(
                "home", "rent", "utilities", "water", "gas", "internet", "phone", "tv",
                "furniture", "kitchen", "garden", "cleaning", "repairs",
            ),
        ),
        IconGroup(
            "shopping",
            listOf("shopping", "clothing", "electronics", "beauty", "jewelry", "books"),
        ),
        IconGroup("health", listOf("health", "pharmacy", "doctor", "fitness", "mental_health", "vaccine")),
        IconGroup("education", listOf("school", "courses", "kids", "toys", "baby")),
        IconGroup(
            "leisure",
            listOf(
                "entertainment", "music", "games", "sports", "travel", "hotel", "beach", "camera",
                "events", "tickets", "hiking",
            ),
        ),
        IconGroup("personal", listOf("gift", "pets", "charity", "haircut", "laundry", "family")),
        IconGroup(
            "finance",
            listOf(
                "subscriptions", "fees", "taxes", "insurance", "loan", "interest", "savings",
                "investments", "exchange", "transfer", "refund", "atm",
            ),
        ),
        IconGroup(
            "income",
            listOf("salary", "freelance", "business", "bonus", "rental_income", "dividends", "store"),
        ),
        IconGroup("other", listOf("favorite", "flag", "other")),
    )

    /** Icons offered for accounts, flattened from [accountGroups]. */
    val account: List<String> = accountGroups.flatMap { it.keys }

    /** Icons offered for categories, flattened from [categoryGroups]. */
    val category: List<String> = categoryGroups.flatMap { it.keys }

    val all: Set<String> = (account + category).toSet()

    /** Unknown or null keys fall back to [DEFAULT]. */
    fun resolve(key: String?): String = if (key != null && key in all) key else DEFAULT
}
