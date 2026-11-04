package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameRules
import com.yehor.finora.domain.tag.TagWithUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(private val db: FinoraDatabase) {

    fun observeAllWithUsage(): Flow<List<TagWithUsage>> =
        db.tagDao().observeAllWithUsage().map { rows ->
            val collator = Collator.getInstance(Locale.getDefault())
            rows.map { row ->
                TagWithUsage(
                    tag = Tag(
                        id = row.tag.id,
                        name = row.tag.name,
                        color = row.tag.color,
                        createdAt = row.tag.createdAt,
                    ),
                    usageCount = row.usageCount,
                )
            }.sortedWith { a, b -> collator.compare(a.tag.name, b.tag.name) }
        }

    suspend fun create(name: String, color: Long?, now: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            val error = TagNameRules.validate(name)
            if (error != null) throw InvalidTagNameException(error)
            val normalized = TagNameRules.normalize(name)
            val key = TagNameRules.key(normalized)

            val existing = db.tagDao().getAll()
            val duplicate = existing.find { TagNameRules.key(it.name) == key }
            if (duplicate != null) {
                throw DuplicateTagNameException(duplicate.id)
            }

            db.tagDao().insert(
                TagEntity(
                    name = normalized,
                    color = color,
                    createdAt = now,
                ),
            )
        }

    suspend fun rename(id: Long, name: String) = db.withTransaction {
        val error = TagNameRules.validate(name)
        if (error != null) throw InvalidTagNameException(error)
        val normalized = TagNameRules.normalize(name)
        val key = TagNameRules.key(normalized)

        val current = db.tagDao().getById(id)
            ?: throw InvariantViolationException("Unknown tag $id")

        val existing = db.tagDao().getAll()
        val duplicate = existing.find { it.id != id && TagNameRules.key(it.name) == key }
        if (duplicate != null) {
            throw DuplicateTagNameException(duplicate.id)
        }

        db.tagDao().updateDetails(id, normalized, current.color)
    }

    suspend fun setColor(id: Long, color: Long?) =
        db.tagDao().setColor(id, color)

    suspend fun delete(id: Long) =
        db.tagDao().deleteById(id)

    suspend fun usageCount(id: Long): Int =
        db.tagDao().usageCount(id)
}
