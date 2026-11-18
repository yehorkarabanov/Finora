package com.yehor.finora

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.DefaultCategorySeedCallback
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.domain.category.DefaultCategories
import com.yehor.finora.domain.model.CategoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultCategorySeedTest {

    private lateinit var context: Context
    private var db: FinoraDatabase? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(FILE)
    }

    private fun inMemory(seed: Boolean): FinoraDatabase =
        Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .apply { if (seed) addCallback(DefaultCategorySeedCallback) }
            .build()
            .also { db = it }

    @Test
    fun userCategoryInsertedFirstDoesNotPreventSeeding() = runBlocking {
        val database = inMemory(seed = true)
        database.categoryDao().insert(
            CategoryEntity(
                name = "Mine", icon = null, color = null, type = CategoryType.EXPENSE,
                parentId = null, createdAt = 0,
            ),
        )
        assertEquals(DefaultCategories.totalCount + 1, database.categoryDao().getAll().size)
    }

    @Test
    fun seededRowsAreValidShallowAndCoverAllTypes() = runBlocking {
        val database = inMemory(seed = true)
        val all = database.categoryDao().getAll()
        assertEquals(DefaultCategories.totalCount, all.size)
        assertEquals(CategoryType.entries.toSet(), all.map { it.type }.toSet())
        val byId = all.associateBy { it.id }
        all.forEach { c ->
            val parent = c.parentId?.let { byId.getValue(it) }
            if (parent != null) {
                assertEquals(parent.type, c.type)
                assertTrue("depth <= 2", parent.parentId == null)
            }
        }
        val food = all.single { it.name == "Food" }
        assertEquals(
            setOf("Groceries", "Restaurants & Cafes"),
            all.filter { it.parentId == food.id }.map { it.name }.toSet(),
        )
        val violations = InvariantChecker.check(database.openHelper.writableDatabase)
        assertTrue(violations.toString(), violations.isEmpty())
    }

    @Test
    fun deletedDefaultsStayDeletedAfterReopen() = runBlocking {
        fun open() = Room.databaseBuilder(context, FinoraDatabase::class.java, FILE)
            .addCallback(FinoraDatabaseCallback)
            .addCallback(DefaultCategorySeedCallback)
            .build()

        val first = open()
        assertEquals(DefaultCategories.totalCount, first.categoryDao().getAll().size)
        // Children first, so foreign keys hold.
        first.openHelper.writableDatabase.execSQL("DELETE FROM category WHERE parent_id IS NOT NULL")
        first.openHelper.writableDatabase.execSQL("DELETE FROM category")
        first.close()

        val second = open().also { db = it }
        assertEquals(0, second.categoryDao().getAll().size)
    }

    @Test
    fun databaseWithoutCallbackHasNoCategories() = runBlocking {
        val database = inMemory(seed = false)
        assertEquals(0, database.categoryDao().getAll().size)
    }

    private companion object {
        const val FILE = "seed-test.db"
    }
}
