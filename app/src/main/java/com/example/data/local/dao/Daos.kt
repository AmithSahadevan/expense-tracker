package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserById(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET monthlySalary = :salary, paydayDayOfMonth = :payday WHERE id = :userId")
    suspend fun updateSalaryAndPayday(userId: Long, salary: Double, payday: Int)

    @Query("UPDATE users SET currencySymbol = :symbol WHERE id = :userId")
    suspend fun updateCurrency(userId: Long, symbol: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface TransactionDao {
    // --- Expenses ---
    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    fun getExpensesForUser(userId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId")
    fun getTotalExpensesForUser(userId: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getExpenseById(id: Long, userId: Long): ExpenseEntity?

    @Query("DELETE FROM expenses WHERE id = :id AND userId = :userId")
    suspend fun deleteExpense(id: Long, userId: Long): Int

    // --- Income ---
    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    suspend fun getExpensesListForUser(userId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM income WHERE userId = :userId ORDER BY date DESC")
    fun getIncomeForUser(userId: Long): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM income WHERE userId = :userId ORDER BY date DESC")
    suspend fun getIncomeListForUser(userId: Long): List<IncomeEntity>

    @Query("SELECT SUM(amount) FROM income WHERE userId = :userId")
    fun getTotalIncomeForUser(userId: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: IncomeEntity): Long

    @Update
    suspend fun updateIncome(income: IncomeEntity)

    @Query("SELECT * FROM income WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getIncomeById(id: Long, userId: Long): IncomeEntity?

    @Query("DELETE FROM income WHERE id = :id AND userId = :userId")
    suspend fun deleteIncome(id: Long, userId: Long): Int
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSavingsForUser(userId: Long): Flow<List<SavingsEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAt DESC")
    fun getSavingsGoalsForUser(userId: Long): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getSavingsGoalsListForUser(userId: Long): List<SavingsGoalEntity>

    @Query("SELECT SUM(currentAmount) FROM savings WHERE userId = :userId")
    fun getTotalSavingsForUser(userId: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavings(savings: SavingsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsGoal(goal: SavingsGoalEntity): Long

    @Query("DELETE FROM savings WHERE id = :id AND userId = :userId")
    suspend fun deleteSavings(id: Long, userId: Long): Int

    @Query("DELETE FROM savings_goals WHERE id = :id AND userId = :userId")
    suspend fun deleteSavingsGoal(id: Long, userId: Long): Int

    // --- Adult Money & Emergency Fund Transactions ---
    @Query("SELECT * FROM savings_transactions WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getSavingsTransactionsForUser(userId: Long): Flow<List<SavingsTransactionEntity>>

    @Query("SELECT * FROM savings_transactions WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getSavingsTransactionsListForUser(userId: Long): List<SavingsTransactionEntity>

    @Query("SELECT * FROM savings_transactions WHERE userId = :userId AND savingsType = :type ORDER BY date DESC, id DESC")
    fun getSavingsTransactionsByType(userId: Long, type: String): Flow<List<SavingsTransactionEntity>>

    @Query("SELECT * FROM savings_transactions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getSavingsTransactionById(id: Long, userId: Long): SavingsTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavingsTransaction(transaction: SavingsTransactionEntity): Long

    @Update
    suspend fun updateSavingsTransaction(transaction: SavingsTransactionEntity)

    @Query("DELETE FROM savings_transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteSavingsTransaction(id: Long, userId: Long): Int
}

@Dao
interface MoneyFlowDao {
    @Query("SELECT * FROM money_flow WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getMoneyFlowsForUser(userId: Long): Flow<List<MoneyFlowEntity>>

    @Query("SELECT * FROM money_flow WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getMoneyFlowsListForUser(userId: Long): List<MoneyFlowEntity>

    @Query("SELECT * FROM money_flow WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getMoneyFlowById(id: Long, userId: Long): MoneyFlowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoneyFlow(moneyFlow: MoneyFlowEntity): Long

    @Update
    suspend fun updateMoneyFlow(moneyFlow: MoneyFlowEntity)

    @Query("UPDATE money_flow SET isSettled = :isSettled WHERE id = :id AND userId = :userId")
    suspend fun setSettled(id: Long, userId: Long, isSettled: Boolean): Int

    @Query("DELETE FROM money_flow WHERE id = :id AND userId = :userId")
    suspend fun deleteMoneyFlow(id: Long, userId: Long): Int
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items WHERE userId = :userId ORDER BY dateAdded DESC, id DESC")
    fun getWishlistForUser(userId: Long): Flow<List<WishlistItemEntity>>

    @Query("SELECT * FROM wishlist_items WHERE userId = :userId ORDER BY dateAdded DESC, id DESC")
    suspend fun getWishlistListForUser(userId: Long): List<WishlistItemEntity>

    @Query("SELECT * FROM wishlist_items WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getWishlistItemById(id: Long, userId: Long): WishlistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistItem(item: WishlistItemEntity): Long

    @Update
    suspend fun updateWishlistItem(item: WishlistItemEntity)

    @Query("UPDATE wishlist_items SET isPurchased = :isPurchased WHERE id = :id AND userId = :userId")
    suspend fun setPurchased(id: Long, userId: Long, isPurchased: Boolean): Int

    @Query("DELETE FROM wishlist_items WHERE id = :id AND userId = :userId")
    suspend fun deleteWishlistItem(id: Long, userId: Long): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY createdAt DESC")
    fun getBudgetsForUser(userId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getBudgetsListForUser(userId: Long): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Query("UPDATE budgets SET spentAmount = :spent WHERE id = :id AND userId = :userId")
    suspend fun updateSpent(id: Long, userId: Long, spent: Double): Int

    @Query("DELETE FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun deleteBudget(id: Long, userId: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories WHERE userId = :userId ORDER BY name ASC")
    fun getCustomCategoriesForUser(userId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM custom_categories WHERE userId = :userId ORDER BY name ASC")
    suspend fun getCustomCategoriesListForUser(userId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("DELETE FROM custom_categories WHERE id = :id AND userId = :userId")
    suspend fun deleteCategory(id: Long, userId: Long): Int
}
