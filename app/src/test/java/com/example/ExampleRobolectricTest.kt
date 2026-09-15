package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.UserEntity
import com.example.data.repository.ExpenseTrackerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ExpenseTrackerRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExpenseTrackerRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kyash", appName)
    }

    @Test
    fun testMainActivityLaunches() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        org.junit.Assert.assertNotNull(activity)
    }

    @Test
    fun testUserDataIsolation() = runBlocking {
        // User 1
        val user1Id = database.userDao().insertUser(
            UserEntity(username = "alice", email = "alice@test.com", displayName = "Alice")
        )
        // User 2
        val user2Id = database.userDao().insertUser(
            UserEntity(username = "bob", email = "bob@test.com", displayName = "Bob")
        )

        // Add expense for User 1
        database.transactionDao().insertExpense(
            ExpenseEntity(userId = user1Id, title = "Coffee", amount = 5.0, category = "Coffee & Drinks")
        )
        // Add expense for User 2
        database.transactionDao().insertExpense(
            ExpenseEntity(userId = user2Id, title = "Books", amount = 42.0, category = "Learning")
        )

        // Query transactions for User 1
        val user1Transactions = repository.getTransactionsForUser(user1Id).first()
        assertEquals(1, user1Transactions.size)
        assertEquals("Coffee", user1Transactions[0].title)
        assertEquals(5.0, user1Transactions[0].amount, 0.001)

        // Query transactions for User 2
        val user2Transactions = repository.getTransactionsForUser(user2Id).first()
        assertEquals(1, user2Transactions.size)
        assertEquals("Books", user2Transactions[0].title)
        assertEquals(42.0, user2Transactions[0].amount, 0.001)

        // Verify strict isolation: User 1 expenses do NOT contain User 2 data
        assertTrue(user1Transactions.none { it.userId == user2Id })
        assertTrue(user2Transactions.none { it.userId == user1Id })
    }

    @Test
    fun testTransactionsCompositeKeysAreDistinct() = runBlocking {
        val userId = database.userDao().insertUser(
            UserEntity(username = "charlie", email = "charlie@test.com", displayName = "Charlie")
        )
        // Insert both expense and income. Since both tables auto-increment, both will receive id = 1
        database.transactionDao().insertExpense(
            ExpenseEntity(userId = userId, title = "Lunch", amount = 15.0, category = "Food")
        )
        database.transactionDao().insertIncome(
            IncomeEntity(userId = userId, title = "Freelance", amount = 200.0, category = "Freelance")
        )

        val txList = repository.getTransactionsForUser(userId).first()
        assertEquals(2, txList.size)

        // Ensure distinct composite keys across items
        val keys = txList.map { "${it.type.name}_${it.id}" }
        assertEquals(2, keys.distinct().size)
    }
}
