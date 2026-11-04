package com.yehor.finora.data.db

import androidx.room.migration.Migration

/**
 * The production migration chain. Foundation ships schema v1, so the chain is empty.
 * `fallbackToDestructiveMigration` must never be used. Every version bump adds a versioned
 * [Migration] here plus a `MigrationTestHelper` test.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
