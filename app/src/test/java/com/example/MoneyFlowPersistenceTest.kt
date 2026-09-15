package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.UserEntity
import com.example.data.model.MoneyFlowInput
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
class MoneyFlowPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun recordsAreStoredEditedAndDeletedOnlyByTheirOwner() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = ExpenseTrackerRepository(database)
            val alice = database.userDao().insertUser(UserEntity(username = "alice", email = "a@t.com", displayName = "Alice"))
            val bob = database.userDao().insertUser(UserEntity(username = "bob", email = "b@t.com", displayName = "Bob"))

            val input = MoneyFlowInput(
                personName = "Sarah",
                direction = MoneyFlowDirection.OWED_TO_ME,
                amount = 2_000.0,
                date = 1_000L,
                dueDate = 5_000L,
                notes = "Concert tickets"
            )
            val id = repository.addMoneyFlow(alice, input)

            val saved = repository.getMoneyFlowsForUser(alice).first().single()
            assertEquals("Sarah", saved.personName)
            assertEquals(MoneyFlowDirection.OWED_TO_ME, saved.direction)
            assertEquals(2_000.0, saved.amount, 0.001)
            assertEquals(1_000L, saved.date)
            assertEquals(5_000L, saved.dueDate)
            assertEquals("Concert tickets", saved.notes)
            assertFalse("new records start pending", saved.isSettled)
            assertTrue(repository.getMoneyFlowsForUser(bob).first().isEmpty())

            assertFalse(repository.updateMoneyFlow(bob, id, input.copy(personName = "Not yours")))

            repository.setMoneyFlowSettled(alice, id, true)
            assertTrue(repository.updateMoneyFlow(alice, id, input.copy(amount = 2_500.0, dueDate = null)))
            val edited = repository.getMoneyFlowsForUser(alice).first().single()
            assertEquals(2_500.0, edited.amount, 0.001)
            assertNull(edited.dueDate)
            assertTrue("editing keeps the settled status", edited.isSettled)
            assertEquals(saved.createdAt, edited.createdAt)

            repository.setMoneyFlowSettled(alice, id, false)
            assertFalse(repository.getMoneyFlowsForUser(alice).first().single().isSettled)

            repository.deleteMoneyFlow(bob, id)
            assertEquals(1, repository.getMoneyFlowsForUser(alice).first().size)
            repository.deleteMoneyFlow(alice, id)
            assertTrue(repository.getMoneyFlowsForUser(alice).first().isEmpty())
        } finally {
            database.close()
        }
    }
}
