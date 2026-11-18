package com.yehor.finora.data.db

import android.content.ContentValues
import androidx.room.OnConflictStrategy
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehor.finora.domain.category.DefaultCategories
import com.yehor.finora.domain.category.DefaultCategory
import com.yehor.finora.domain.model.CategoryType

/**
 * Inserts [DefaultCategories] when the database file is first created (design 5.4). Runs inside
 * Room's creation transaction, so no code can read or write before the defaults exist, and they
 * are never recreated for an existing file. Uses only the [SupportSQLiteDatabase] passed in.
 */
object DefaultCategorySeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        DefaultCategories.all.forEach { (type, category) ->
            val parentId = insert(db, category, type, parentId = null, now = now)
            category.children.forEach { child -> insert(db, child, type, parentId, now) }
        }
    }

    private fun insert(
        db: SupportSQLiteDatabase,
        category: DefaultCategory,
        type: CategoryType,
        parentId: Long?,
        now: Long,
    ): Long {
        val values = ContentValues().apply {
            put("name", category.name)
            put("icon", category.icon)
            put("color", category.color)
            put("type", type.name)
            if (parentId != null) put("parent_id", parentId) else putNull("parent_id")
            put("created_at", now)
        }
        val id = db.insert("category", OnConflictStrategy.ABORT, values)
        check(id != -1L) { "Failed to seed category ${category.name}" }
        return id
    }
}
