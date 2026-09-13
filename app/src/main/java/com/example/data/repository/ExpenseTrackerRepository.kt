package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.dao.BudgetDao
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.MoneyFlowDao
import com.example.data.local.dao.SavingsDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.dao.UserDao
import com.example.data.local.dao.WishlistDao
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseTrackerRepository(
    private val transactionDao: TransactionDao,
    private val savingsDao: SavingsDao,
    private val moneyFlowDao: MoneyFlowDao,
    private val wishlistDao: WishlistDao,
    private val budgetDao: BudgetDao,
    private val userDao: UserDao,
    private val categoryDao: CategoryDao
) {
    constructor(database: AppDatabase) : this(
        transactionDao = database.transactionDao(),
        savingsDao = database.savingsDao(),
        moneyFlowDao = database.moneyFlowDao(),
        wishlistDao = database.wishlistDao(),
        budgetDao = database.budgetDao(),
        userDao = database.userDao(),
        categoryDao = database.categoryDao()
    )

    // --- Unified Transactions Flow (Isolated by User) ---
    fun getTransactionsForUser(userId: Long): Flow<List<TransactionItem>> {
        return combine(
            transactionDao.getExpensesForUser(userId),
            transactionDao.getIncomeForUser(userId)
        ) { expenses, incomeList ->
            val expenseItems = expenses.map {
                TransactionItem(
                    id = it.id,
                    userId = it.userId,
                    title = it.title,
                    amount = it.amount,
                    type = TransactionType.EXPENSE,
                    category = it.category,
                    date = it.date,
                    notes = it.notes,
                    paymentMethod = it.paymentMethod,
                    recurrence = it.recurrence
                )
            }
            val incomeItems = incomeList.map {
                TransactionItem(
                    id = it.id,
                    userId = it.userId,
                    title = it.title,
                    amount = it.amount,
                    type = TransactionType.INCOME,
                    category = it.category,
                    date = it.date,
                    notes = it.notes,
                    paymentMethod = it.paymentMethod,
                    recurrence = it.recurrence
                )
            }
            (expenseItems + incomeItems).sortedByDescending { it.date }
        }
    }

    fun getTotalIncomeForUser(userId: Long): Flow<Double> {
        return transactionDao.getTotalIncomeForUser(userId).map { it ?: 0.0 }
    }

    fun getTotalExpenseForUser(userId: Long): Flow<Double> {
        return transactionDao.getTotalExpensesForUser(userId).map { it ?: 0.0 }
    }

    suspend fun addExpense(
        userId: Long,
        title: String,
        amount: Double,
        category: String,
        date: Long = System.currentTimeMillis(),
        notes: String = "",
        paymentMethod: String = "CARD",
        recurrence: String = "NONE"
    ): Long = withContext(Dispatchers.IO) {
        val entity = ExpenseEntity(
            userId = userId,
            title = title,
            amount = amount,
            category = category,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod,
            recurrence = recurrence
        )
        transactionDao.insertExpense(entity)
    }

    suspend fun updateExpense(
        userId: Long,
        id: Long,
        title: String,
        amount: Double,
        category: String,
        date: Long,
        notes: String = "",
        paymentMethod: String = "CARD",
        recurrence: String = "NONE"
    ) = withContext(Dispatchers.IO) {
        val entity = ExpenseEntity(
            id = id,
            userId = userId,
            title = title,
            amount = amount,
            category = category,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod,
            recurrence = recurrence
        )
        transactionDao.updateExpense(entity)
    }

    suspend fun addIncome(
        userId: Long,
        title: String,
        amount: Double,
        category: String,
        date: Long = System.currentTimeMillis(),
        notes: String = "",
        paymentMethod: String = "BANK",
        recurrence: String = "NONE"
    ): Long = withContext(Dispatchers.IO) {
        val entity = IncomeEntity(
            userId = userId,
            title = title,
            amount = amount,
            category = category,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod,
            recurrence = recurrence
        )
        transactionDao.insertIncome(entity)
    }

    suspend fun updateIncome(
        userId: Long,
        id: Long,
        title: String,
        amount: Double,
        category: String,
        date: Long,
        notes: String = "",
        paymentMethod: String = "BANK",
        recurrence: String = "NONE"
    ) = withContext(Dispatchers.IO) {
        val entity = IncomeEntity(
            id = id,
            userId = userId,
            title = title,
            amount = amount,
            category = category,
            date = date,
            notes = notes,
            paymentMethod = paymentMethod,
            recurrence = recurrence
        )
        transactionDao.updateIncome(entity)
    }

    suspend fun updateSalaryAndPayday(userId: Long, salary: Double, payday: Int) = withContext(Dispatchers.IO) {
        userDao.updateSalaryAndPayday(userId, salary, payday)
    }

    // --- Custom Categories ---
    fun getCustomCategoriesForUser(userId: Long): Flow<List<CategoryEntity>> =
        categoryDao.getCustomCategoriesForUser(userId)

    suspend fun addCustomCategory(
        userId: Long,
        name: String,
        emoji: String = "🏷️",
        type: String = "EXPENSE",
        colorHex: String = "#6366F1"
    ): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(
            CategoryEntity(
                userId = userId,
                name = name,
                emoji = emoji,
                type = type,
                colorHex = colorHex
            )
        )
    }

    suspend fun deleteCustomCategory(userId: Long, id: Long) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(id, userId)
    }

    suspend fun deleteTransaction(userId: Long, id: Long, type: TransactionType) = withContext(Dispatchers.IO) {
        if (type == TransactionType.EXPENSE) {
            transactionDao.deleteExpense(id, userId)
        } else {
            transactionDao.deleteIncome(id, userId)
        }
    }

    // --- Savings ---
    fun getSavingsForUser(userId: Long): Flow<List<SavingsEntity>> =
        savingsDao.getSavingsForUser(userId)

    fun getSavingsGoalsForUser(userId: Long): Flow<List<SavingsGoalEntity>> =
        savingsDao.getSavingsGoalsForUser(userId)

    fun getTotalSavingsForUser(userId: Long): Flow<Double> =
        savingsDao.getTotalSavingsForUser(userId).map { it ?: 0.0 }

    fun getSavingsTransactionsForUser(userId: Long): Flow<List<SavingsTransactionEntity>> =
        savingsDao.getSavingsTransactionsForUser(userId)

    suspend fun addSavingsTransaction(
        userId: Long,
        savingsType: String,
        transactionType: String,
        amount: Double,
        title: String,
        notes: String = "",
        date: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        savingsDao.insertSavingsTransaction(
            SavingsTransactionEntity(
                userId = userId,
                savingsType = savingsType,
                transactionType = transactionType,
                amount = amount,
                title = title,
                notes = notes,
                date = date
            )
        )
    }

    suspend fun updateSavingsTransaction(
        userId: Long,
        id: Long,
        savingsType: String,
        transactionType: String,
        amount: Double,
        title: String,
        notes: String = "",
        date: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        savingsDao.updateSavingsTransaction(
            SavingsTransactionEntity(
                id = id,
                userId = userId,
                savingsType = savingsType,
                transactionType = transactionType,
                amount = amount,
                title = title,
                notes = notes,
                date = date
            )
        )
    }

    suspend fun deleteSavingsTransaction(userId: Long, id: Long) = withContext(Dispatchers.IO) {
        savingsDao.deleteSavingsTransaction(id, userId)
    }

    suspend fun addSavingsGoal(
        userId: Long,
        title: String,
        goalAmount: Double,
        savedAmount: Double = 0.0,
        emoji: String = "🎯"
    ): Long = withContext(Dispatchers.IO) {
        savingsDao.insertSavingsGoal(
            SavingsGoalEntity(
                userId = userId,
                title = title,
                goalAmount = goalAmount,
                savedAmount = savedAmount,
                emoji = emoji
            )
        )
    }

    // --- Money Flow (Debts / IOUs) ---
    fun getMoneyFlowsForUser(userId: Long): Flow<List<MoneyFlowEntity>> =
        moneyFlowDao.getMoneyFlowsForUser(userId)

    suspend fun addMoneyFlow(
        userId: Long,
        personName: String,
        direction: String,
        amount: Double,
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        moneyFlowDao.insertMoneyFlow(
            MoneyFlowEntity(
                userId = userId,
                personName = personName,
                direction = direction,
                amount = amount,
                notes = notes
            )
        )
    }

    suspend fun setMoneyFlowSettled(userId: Long, id: Long, settled: Boolean) = withContext(Dispatchers.IO) {
        moneyFlowDao.setSettled(id, userId, settled)
    }

    // --- Wishlist ---
    fun getWishlistForUser(userId: Long): Flow<List<WishlistItemEntity>> =
        wishlistDao.getWishlistForUser(userId)

    suspend fun addWishlistItem(
        userId: Long,
        title: String,
        estimatedCost: Double,
        priority: String = "MEDIUM",
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        wishlistDao.insertWishlistItem(
            WishlistItemEntity(
                userId = userId,
                title = title,
                estimatedCost = estimatedCost,
                priority = priority,
                notes = notes
            )
        )
    }

    suspend fun toggleWishlistPurchased(userId: Long, id: Long, isPurchased: Boolean) = withContext(Dispatchers.IO) {
        wishlistDao.setPurchased(id, userId, isPurchased)
    }

    // --- Budgets ---
    fun getBudgetsForUser(userId: Long): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForUser(userId)

    suspend fun addBudget(
        userId: Long,
        category: String,
        allocatedAmount: Double
    ): Long = withContext(Dispatchers.IO) {
        budgetDao.insertBudget(
            BudgetEntity(
                userId = userId,
                category = category,
                allocatedAmount = allocatedAmount
            )
        )
    }
}
