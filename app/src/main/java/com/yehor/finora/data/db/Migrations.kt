package com.yehor.finora.data.db

import androidx.room.migration.Migration

/**
 * The manual production migration chain. Version 2 (the `exchange_rate` table) is an
 * `@AutoMigration` declared on [FinoraDatabase], so this array stays empty.
 * `fallbackToDestructiveMigration` must never be used. Every version bump adds a migration plus a
 * `MigrationTestHelper` test.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
