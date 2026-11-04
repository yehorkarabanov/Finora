package com.yehor.finora.domain.account

enum class AccountType {
    BANK, CASH, SAVINGS, INVESTMENT, OTHER;

    companion object {
        /** A missing or unknown stored value means [OTHER]. */
        fun fromStorage(value: String?): AccountType =
            entries.firstOrNull { it.name == value } ?: OTHER
    }
}
