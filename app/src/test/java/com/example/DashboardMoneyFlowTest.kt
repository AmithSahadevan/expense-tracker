package com.example

import android.content.Context
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.UserEntity
import com.example.data.model.MoneyFlowInput
import com.example.data.remote.ProductLookupService
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExpenseTrackerRepository
import com.example.ui.viewmodel.DashboardSummaryUiState
import com.example.ui.viewmodel.ExpenseTrackerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dashboard rule: money other people owe the user is "Expected", never Available Money.
 * Salary 50,000 - expenses 30,000 = 20,000 available, with 5,000 expected shown separately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardMoneyFlowTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var repository: ExpenseTrackerRepository
    private lateinit var viewModel: ExpenseTrackerViewModel
    private var userId: Long = 0

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ExpenseTrackerRepository(database)
        userId = database.userDao().insertUser(
            UserEntity(username = "amith", email = "a@t.com", displayName = "Amith", currencySymbol = "₹")
        )
        val auth = AuthRepository(database.userDao(), CoroutineScope(Dispatchers.Unconfined))
        auth.switchUser(database.userDao().findUserById(userId)!!)
        viewModel = ExpenseTrackerViewModel(auth, repository, ProductLookupService())
    }

    @After
    fun tearDown() {
        // Without this the view model's flows keep running and later Compose tests never see an idle main thread.
        viewModel.viewModelScope.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    /** Waits for the dashboard to reflect the data just written. */
    private fun awaitSummary(condition: (DashboardSummaryUiState) -> Boolean): DashboardSummaryUiState = runBlocking {
        withTimeout(10_000) { viewModel.dashboardSummary.first(condition) }
    }

    @Test
    fun moneyOwedToTheUserIsExpectedAndNeverAvailableMoney() = runBlocking {
        repository.addIncome(userId, "Salary", 50_000.0, "Salary")
        repository.addExpense(userId, "Rent", 30_000.0, "Bills")
        repository.addMoneyFlow(
            userId,
            MoneyFlowInput("Sarah", MoneyFlowDirection.OWED_TO_ME, 5_000.0, notes = "Lent for repairs")
        )
        repository.addMoneyFlow(userId, MoneyFlowInput("Rahul", MoneyFlowDirection.I_OWE, 1_500.0))

        val summary = awaitSummary {
            it.totalIncome == 50_000.0 && it.totalExpense == 30_000.0 &&
                it.moneyFlow.expectedCount == 1 && it.moneyFlow.obligationCount == 1
        }

        assertEquals("available money is income minus expenses only", 20_000.0, summary.remainingMoney, 0.001)
        assertEquals(5_000.0, summary.moneyFlow.expectedIncoming, 0.001)
        assertEquals(1_500.0, summary.moneyFlow.pendingObligations, 0.001)
        assertEquals("what people owe is not income", 50_000.0, summary.totalIncome, 0.001)
        assertEquals("what the user owes is not an expense", 30_000.0, summary.totalExpense, 0.001)
    }

    @Test
    fun settlingARecordLeavesPastIncomeAndExpensesUntouched() = runBlocking {
        repository.addIncome(userId, "Salary", 50_000.0, "Salary")
        repository.addExpense(userId, "Rent", 30_000.0, "Bills")
        repository.addMoneyFlow(userId, MoneyFlowInput("Sarah", MoneyFlowDirection.OWED_TO_ME, 5_000.0))
        val pending = awaitSummary {
            it.totalIncome == 50_000.0 && it.totalExpense == 30_000.0 && it.moneyFlow.expectedCount == 1
        }
        assertEquals(20_000.0, pending.remainingMoney, 0.001)

        val record = repository.getMoneyFlowsForUser(userId).first().single()
        viewModel.toggleMoneyFlowSettled(record)

        val settled = awaitSummary { it.moneyFlow.expectedCount == 0 && it.totalExpense == 30_000.0 }
        assertEquals("settling does not add the money to available money", 20_000.0, settled.remainingMoney, 0.001)
        assertEquals(50_000.0, settled.totalIncome, 0.001)
        assertEquals(30_000.0, settled.totalExpense, 0.001)
        assertEquals(0.0, settled.moneyFlow.expectedIncoming, 0.001)
        assertEquals(2, settled.recentTransactions.size)
    }
}
