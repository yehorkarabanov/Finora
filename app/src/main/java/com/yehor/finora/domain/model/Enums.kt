package com.yehor.finora.domain.model

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** A missing or unknown value means [SYSTEM]. */
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

enum class AccountStatus { ACTIVE, ARCHIVED }

enum class CategoryType { INCOME, EXPENSE, TRANSFER }

enum class OperationType { INCOME, EXPENSE, TRANSFER, OPENING_BALANCE }
