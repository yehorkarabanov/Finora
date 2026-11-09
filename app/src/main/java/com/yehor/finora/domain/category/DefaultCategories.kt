package com.yehor.finora.domain.category

import com.yehor.finora.domain.model.CategoryType

/** One seeded category and its children. Plain data, at most two levels deep. */
data class DefaultCategory(
    val name: String,
    val icon: String,
    val color: Long,
    val children: List<DefaultCategory> = emptyList(),
)

/**
 * The category set created inside database creation (design 5.4). Names are English user data,
 * editable and deletable like any other category. Icon keys must exist in `IconCatalog`
 * and may change during development; unknown keys render as the default icon.
 */
object DefaultCategories {

    private const val RED = 0xFFE53935L
    private const val ORANGE = 0xFFFB8C00L
    private const val AMBER = 0xFFFFB300L
    private const val GREEN = 0xFF43A047L
    private const val TEAL = 0xFF00897BL
    private const val BLUE = 0xFF1E88E5L
    private const val INDIGO = 0xFF3949ABL
    private const val PURPLE = 0xFF8E24AAL
    private const val PINK = 0xFFD81B60L
    private const val BROWN = 0xFF6D4C41L
    private const val GREY = 0xFF757575L

    val expense: List<DefaultCategory> = listOf(
        DefaultCategory(
            "Food", "restaurant", ORANGE,
            listOf(
                DefaultCategory("Groceries", "groceries", ORANGE),
                DefaultCategory("Restaurants & Cafes", "coffee", ORANGE),
            ),
        ),
        DefaultCategory("Housing", "home", BROWN),
        DefaultCategory("Transport", "car", BLUE),
        DefaultCategory("Shopping", "shopping", PINK),
        DefaultCategory("Entertainment", "entertainment", PURPLE),
        DefaultCategory("Health", "health", RED),
        DefaultCategory("Subscriptions", "subscriptions", INDIGO),
        DefaultCategory("Fees & Charges", "fees", GREY),
        DefaultCategory("Other", "other", GREY),
    )

    val income: List<DefaultCategory> = listOf(
        DefaultCategory("Salary", "salary", GREEN),
        DefaultCategory("Freelance", "freelance", TEAL),
        DefaultCategory("Investment Income", "dividends", GREEN),
        DefaultCategory("Gifts", "gift", PINK),
        DefaultCategory("Refunds", "refund", AMBER),
        DefaultCategory("Other", "other", GREY),
    )

    val transfer: List<DefaultCategory> = listOf(
        DefaultCategory("Own Account Transfer", "transfer", BLUE),
        DefaultCategory("Currency Exchange", "exchange", TEAL),
        DefaultCategory("Savings Transfer", "savings", GREEN),
        DefaultCategory("Investment Deposit", "investments", INDIGO),
        DefaultCategory("Other", "other", GREY),
    )

    /** Seed order: every parent appears before its children, grouped by type. */
    val all: List<Pair<CategoryType, DefaultCategory>> =
        expense.map { CategoryType.EXPENSE to it } +
            income.map { CategoryType.INCOME to it } +
            transfer.map { CategoryType.TRANSFER to it }

    /** Every icon key used by the defaults. */
    val iconKeys: Set<String> = all.flatMap { (_, c) -> listOf(c.icon) + c.children.map { it.icon } }.toSet()

    /** Number of rows the seed inserts. */
    val totalCount: Int = all.sumOf { (_, c) -> 1 + c.children.size }
}
