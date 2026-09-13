package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.UserEntity
import com.example.data.model.WishlistItemInput
import com.example.data.model.WishlistPriority
import com.example.data.repository.ExpenseTrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WishlistPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun productDetailsAreStoredAndOnlyTheOwnerCanChangeThem() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = ExpenseTrackerRepository(database)
            val alice = database.userDao().insertUser(UserEntity(username = "alice", email = "a@t.com", displayName = "Alice"))
            val bob = database.userDao().insertUser(UserEntity(username = "bob", email = "b@t.com", displayName = "Bob"))

            val input = WishlistItemInput(
                title = "Sony WH-1000XM5",
                price = 28_000.0,
                url = "https://www.amazon.in/dp/B0C8",
                imageUrl = "https://images.example.com/xm5.jpg",
                store = "Amazon",
                description = "Black, noise cancelling",
                dateAdded = 1_000L,
                targetPurchaseDate = 2_000L,
                notes = "Birthday treat",
                priority = WishlistPriority.HIGH
            )
            val id = repository.addWishlistItem(alice, input)

            val saved = repository.getWishlistForUser(alice).first().single()
            assertEquals("Sony WH-1000XM5", saved.title)
            assertEquals(28_000.0, saved.estimatedCost, 0.001)
            assertEquals("https://www.amazon.in/dp/B0C8", saved.url)
            assertEquals("https://images.example.com/xm5.jpg", saved.imageUrl)
            assertEquals("Amazon", saved.store)
            assertEquals("Black, noise cancelling", saved.description)
            assertEquals(1_000L, saved.dateAdded)
            assertEquals(2_000L, saved.targetPurchaseDate)
            assertEquals("Birthday treat", saved.notes)
            assertEquals(WishlistPriority.HIGH, saved.priority)
            assertTrue(repository.getWishlistForUser(bob).first().isEmpty())

            assertFalse(repository.updateWishlistItem(bob, id, input.copy(title = "Not yours")))

            repository.toggleWishlistPurchased(alice, id, true)
            assertTrue(repository.updateWishlistItem(alice, id, input.copy(price = 25_000.0, targetPurchaseDate = null)))
            val edited = repository.getWishlistForUser(alice).first().single()
            assertEquals("Sony WH-1000XM5", edited.title)
            assertEquals(25_000.0, edited.estimatedCost, 0.001)
            assertNull(edited.targetPurchaseDate)
            assertTrue("editing keeps the purchased flag", edited.isPurchased)
            assertEquals(saved.createdAt, edited.createdAt)

            repository.deleteWishlistItem(bob, id)
            assertEquals(1, repository.getWishlistForUser(alice).first().size)
            repository.deleteWishlistItem(alice, id)
            assertTrue(repository.getWishlistForUser(alice).first().isEmpty())
        } finally {
            database.close()
        }
    }
}
