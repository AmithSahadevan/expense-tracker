package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ExpenseTrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SavingsPersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun savingsRecordsCanBeEditedOnlyByTheirOwner() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = ExpenseTrackerRepository(database)
            val alice = database.userDao().insertUser(UserEntity(username = "alice", email = "a@t.com", displayName = "Alice"))
            val bob = database.userDao().insertUser(UserEntity(username = "bob", email = "b@t.com", displayName = "Bob"))

            val id = repository.addSavingsTransaction(
                userId = alice,
                savingsType = SavingsType.ADULT_MONEY,
                transactionType = SavingsActionType.DEPOSIT,
                amount = 20_000.0,
                title = "Salary cut",
                affectsAvailableMoney = false
            )
            val created = repository.getSavingsTransactionsForUser(alice).first().single()
            assertFalse(created.affectsAvailableMoney)

            val hijacked = repository.updateSavingsTransaction(
                userId = bob, id = id, savingsType = SavingsType.ADULT_MONEY,
                transactionType = SavingsActionType.DEPOSIT, amount = 1.0, title = "mine now"
            )
            assertFalse(hijacked)

            val edited = repository.updateSavingsTransaction(
                userId = alice, id = id, savingsType = SavingsType.EMERGENCY_FUND,
                transactionType = SavingsActionType.DEPOSIT, amount = 25_000.0, title = "Moved",
                date = created.date, affectsAvailableMoney = true
            )
            assertTrue(edited)

            val after = repository.getSavingsTransactionsForUser(alice).first().single()
            assertEquals(SavingsType.EMERGENCY_FUND, after.savingsType)
            assertEquals(25_000.0, after.amount, 0.001)
            assertEquals(created.createdAt, after.createdAt)
            assertTrue(repository.getSavingsTransactionsForUser(bob).first().isEmpty())
        } finally {
            database.close()
        }
    }
}
