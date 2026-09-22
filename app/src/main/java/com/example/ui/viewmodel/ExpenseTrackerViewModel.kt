package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.MoneyFlowCalculator
import com.example.data.model.MoneyFlowInput
import com.example.data.model.MoneyFlowSummary
import com.example.data.model.MoneyFlowValidator
import com.example.data.model.ProductLookupResult
import com.example.data.model.SavingsCalculator
import com.example.data.model.SavingsTransactionInput
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.UserDataBackup
import com.example.data.model.WishlistInputValidator
import com.example.data.model.WishlistItemInput
import com.example.data.repository.AuthRepository
import com.example.data.remote.ProductLookupService
import com.example.data.repository.ExpenseTrackerRepository
import com.example.ui.components.formatMoney
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class DashboardSummaryUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val remainingMoney: Double = 0.0, // Available money: income - expenses - money moved into savings
    val movedToSavings: Double = 0.0,
    val adultMoneyBalance: Double = 0.0,
    val emergencyFundBalance: Double = 0.0,
    val totalSavings: Double = 0.0,
    val currentMonthIncome: Double = 0.0,
    val currentMonthSpending: Double = 0.0,
    val currentMonthRemaining: Double = 0.0,
    // Pending money involving other people. Deliberately NOT part of remainingMoney:
    // money owed to the user has not arrived, and money the user owes has not left yet.
    val moneyFlow: MoneyFlowSummary = MoneyFlowSummary(),
    val recentTransactions: List<TransactionItem> = emptyList(),
    val savingsGoalsCount: Int = 0,
    val moneyFlowCount: Int = 0,
    val wishlistCount: Int = 0,
    val budgetsCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseTrackerViewModel(
    private val authRepository: AuthRepository,
    private val repository: ExpenseTrackerRepository,
    private val productLookup: ProductLookupService
) : ViewModel() {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val currentUser: StateFlow<UserEntity?> = authRepository.currentUser
    val allUsers: StateFlow<List<UserEntity>> = authRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Isolated Transactions for Active User ---
    val transactions: StateFlow<List<TransactionItem>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getTransactionsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalIncome: StateFlow<Double> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getTotalIncomeForUser(user.id) else flowOf(0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val totalExpense: StateFlow<Double> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getTotalExpenseForUser(user.id) else flowOf(0.0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val customCategories: StateFlow<List<CategoryEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getCustomCategoriesForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savingsList: StateFlow<List<SavingsEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getSavingsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val savingsGoals: StateFlow<List<SavingsGoalEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getSavingsGoalsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dedicated Adult Money & Emergency Fund System
    val savingsTransactions: StateFlow<List<SavingsTransactionEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getSavingsTransactionsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val adultMoneyBalance: StateFlow<Double> = savingsTransactions.map { list ->
        SavingsCalculator.balance(list, SavingsType.ADULT_MONEY)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val emergencyFundBalance: StateFlow<Double> = savingsTransactions.map { list ->
        SavingsCalculator.balance(list, SavingsType.EMERGENCY_FUND)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSavings: StateFlow<Double> = combine(adultMoneyBalance, emergencyFundBalance) { adult, emergency ->
        adult + emergency
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val moneyFlows: StateFlow<List<MoneyFlowEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getMoneyFlowsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val wishlist: StateFlow<List<WishlistItemEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getWishlistForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val budgets: StateFlow<List<BudgetEntity>> = currentUser.flatMapLatest { user ->
        if (user != null) repository.getBudgetsForUser(user.id) else flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private data class HubCounts(
        val goalsCount: Int,
        val flowCount: Int,
        val wishlistCount: Int,
        val budgetsCount: Int,
        val moneyFlowSummary: MoneyFlowSummary
    )

    private val hubCounts = combine(savingsGoals, moneyFlows, wishlist, budgets) { goals, flows, wishListItems, budgetItems ->
        HubCounts(goals.size, flows.size, wishListItems.size, budgetItems.size, MoneyFlowCalculator.summarize(flows))
    }.flowOn(Dispatchers.Default)

    private data class SavingsBalances(
        val adultMoney: Double,
        val emergencyFund: Double,
        val total: Double,
        val movedOutOfAvailable: Double
    )

    private val savingsBalancesFlow = combine(
        adultMoneyBalance,
        emergencyFundBalance,
        totalSavings,
        savingsTransactions
    ) { adult, emergency, total, list ->
        SavingsBalances(adult, emergency, total, SavingsCalculator.movedOutOfAvailableMoney(list))
    }

    val dashboardSummary: StateFlow<DashboardSummaryUiState> = combine(
        totalIncome,
        totalExpense,
        transactions,
        savingsBalancesFlow,
        hubCounts
    ) { income, expense, txList, savingsBal, counts ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfMonth = cal.timeInMillis

        val monthIncome = txList.filter { it.type == TransactionType.INCOME && it.date >= startOfMonth }.sumOf { it.amount }
        val monthSpending = txList.filter { it.type == TransactionType.EXPENSE && it.date >= startOfMonth }.sumOf { it.amount }

        DashboardSummaryUiState(
            totalIncome = income,
            totalExpense = expense,
            // Savings are never spendable: money moved into either savings type leaves Available Money.
            remainingMoney = income - expense - savingsBal.movedOutOfAvailable,
            movedToSavings = savingsBal.movedOutOfAvailable,
            adultMoneyBalance = savingsBal.adultMoney,
            emergencyFundBalance = savingsBal.emergencyFund,
            totalSavings = savingsBal.total,
            currentMonthIncome = monthIncome,
            currentMonthSpending = monthSpending,
            currentMonthRemaining = monthIncome - monthSpending,
            recentTransactions = txList.take(3),
            savingsGoalsCount = counts.goalsCount,
            moneyFlowCount = counts.flowCount,
            moneyFlow = counts.moneyFlowSummary,
            wishlistCount = counts.wishlistCount,
            budgetsCount = counts.budgetsCount
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardSummaryUiState()
    )

    fun clearActionMessage() {
        // No-op
    }

    // --- Action Handlers with Strict User Data Association ---

    fun addTransaction(
        type: TransactionType,
        title: String,
        amount: Double,
        category: String,
        notes: String = "",
        paymentMethod: String = "CARD",
        date: Long = System.currentTimeMillis(),
        recurrence: String = "NONE"
    ) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            if (type == TransactionType.EXPENSE) {
                repository.addExpense(
                    userId = user.id,
                    title = title,
                    amount = amount,
                    category = category,
                    date = date,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    recurrence = recurrence
                )
            } else {
                repository.addIncome(
                    userId = user.id,
                    title = title,
                    amount = amount,
                    category = category,
                    date = date,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    recurrence = recurrence
                )
            }
        }
    }

    fun updateTransaction(
        id: Long,
        type: TransactionType,
        title: String,
        amount: Double,
        category: String,
        date: Long,
        notes: String = "",
        paymentMethod: String = "CARD",
        recurrence: String = "NONE"
    ) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            if (type == TransactionType.EXPENSE) {
                repository.updateExpense(
                    userId = user.id,
                    id = id,
                    title = title,
                    amount = amount,
                    category = category,
                    date = date,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    recurrence = recurrence
                )
            } else {
                repository.updateIncome(
                    userId = user.id,
                    id = id,
                    title = title,
                    amount = amount,
                    category = category,
                    date = date,
                    notes = notes,
                    paymentMethod = paymentMethod,
                    recurrence = recurrence
                )
            }
        }
    }

    fun updateSalaryAndPayday(salary: Double, payday: Int, logDepositForThisMonth: Boolean = false) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateSalaryAndPayday(user.id, salary, payday)
            if (logDepositForThisMonth && salary > 0) {
                repository.addIncome(
                    userId = user.id,
                    title = "Monthly Salary",
                    amount = salary,
                    category = "Salary",
                    date = System.currentTimeMillis(),
                    notes = "Payday deposit (Day $payday)",
                    paymentMethod = "BANK",
                    recurrence = "MONTHLY"
                )
            }
        }
    }

    fun updateCurrency(symbol: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateCurrency(user.id, symbol)
        }
    }

    fun addCustomCategory(name: String, emoji: String = "🏷️", type: String = "EXPENSE", colorHex: String = "#6366F1") {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addCustomCategory(
                userId = user.id,
                name = name.trim(),
                emoji = emoji,
                type = type,
                colorHex = colorHex
            )
        }
    }

    fun deleteCustomCategory(id: Long) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteCustomCategory(user.id, id)
        }
    }

    fun deleteTransaction(item: TransactionItem) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return // Security check
        viewModelScope.launch {
            repository.deleteTransaction(user.id, item.id, item.type)
        }
    }

    fun addWishlistItem(input: WishlistItemInput) {
        val user = currentUser.value ?: return
        val clean = cleanWishlistInput(input) ?: return
        viewModelScope.launch {
            repository.addWishlistItem(user.id, clean)
        }
    }

    fun updateWishlistItem(id: Long, input: WishlistItemInput) {
        val user = currentUser.value ?: return
        val clean = cleanWishlistInput(input) ?: return
        viewModelScope.launch {
            repository.updateWishlistItem(user.id, id, clean)
        }
    }

    /** Reads product details from a pasted link, on-device. Failures come back as results, never exceptions. */
    suspend fun lookupProduct(link: String): ProductLookupResult = productLookup.lookup(link)

    fun deleteWishlistItem(item: WishlistItemEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.deleteWishlistItem(user.id, item.id)
        }
    }

    private fun cleanWishlistInput(input: WishlistItemInput): WishlistItemInput? {
        return WishlistInputValidator.clean(input)
    }

    fun toggleWishlistItem(item: WishlistItemEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.toggleWishlistPurchased(user.id, item.id, !item.isPurchased)
        }
    }

    fun addMoneyFlow(input: MoneyFlowInput) {
        val user = currentUser.value ?: return
        val clean = cleanMoneyFlowInput(input) ?: return
        viewModelScope.launch {
            repository.addMoneyFlow(user.id, clean)
        }
    }

    fun updateMoneyFlow(id: Long, input: MoneyFlowInput) {
        val user = currentUser.value ?: return
        val clean = cleanMoneyFlowInput(input) ?: return
        viewModelScope.launch {
            repository.updateMoneyFlow(user.id, id, clean)
        }
    }

    fun deleteMoneyFlow(item: MoneyFlowEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.deleteMoneyFlow(user.id, item.id)
        }
    }

    fun toggleMoneyFlowSettled(item: MoneyFlowEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            // Settling only flips this record's status: income, expenses and past entries stay untouched.
            repository.setMoneyFlowSettled(user.id, item.id, !item.isSettled)
        }
    }

    private fun cleanMoneyFlowInput(input: MoneyFlowInput): MoneyFlowInput? {
        return MoneyFlowValidator.clean(input)
    }

    fun addSavingsGoal(title: String, goalAmount: Double, emoji: String = "🎯") {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addSavingsGoal(user.id, title, goalAmount, 0.0, emoji)
        }
    }

    fun addSavingsTransaction(
        savingsType: String,
        transactionType: String,
        amount: Double,
        title: String,
        notes: String = "",
        date: Long = System.currentTimeMillis(),
        affectsAvailableMoney: Boolean = true
    ) {
        val user = currentUser.value ?: return
        if (amount <= 0) return

        val candidate = SavingsTransactionEntity(
            userId = user.id,
            savingsType = savingsType,
            transactionType = transactionType,
            amount = amount,
            title = title,
            notes = notes,
            date = date,
            affectsAvailableMoney = affectsAvailableMoney
        )
        if (rejectIfBalanceWouldGoNegative(removedId = null, added = candidate)) return
        viewModelScope.launch {
            repository.addSavingsTransaction(
                userId = user.id,
                savingsType = savingsType,
                transactionType = transactionType,
                amount = amount,
                title = title,
                notes = notes,
                date = date,
                affectsAvailableMoney = affectsAvailableMoney
            )
        }
    }

    fun updateSavingsTransaction(
        id: Long,
        savingsType: String,
        transactionType: String,
        amount: Double,
        title: String,
        notes: String = "",
        date: Long = System.currentTimeMillis(),
        affectsAvailableMoney: Boolean = true
    ) {
        val user = currentUser.value ?: return
        if (amount <= 0) return

        val candidate = SavingsTransactionEntity(
            id = id,
            userId = user.id,
            savingsType = savingsType,
            transactionType = transactionType,
            amount = amount,
            title = title,
            notes = notes,
            date = date,
            affectsAvailableMoney = affectsAvailableMoney
        )
        if (rejectIfBalanceWouldGoNegative(removedId = id, added = candidate)) return
        viewModelScope.launch {
            repository.updateSavingsTransaction(
                userId = user.id,
                id = id,
                savingsType = savingsType,
                transactionType = transactionType,
                amount = amount,
                title = title,
                notes = notes,
                date = date,
                affectsAvailableMoney = affectsAvailableMoney
            )
        }
    }

    fun addSavingsTransaction(input: SavingsTransactionInput) = with(input) {
        addSavingsTransaction(savingsType, transactionType, amount, title, notes, date, affectsAvailableMoney)
    }

    fun updateSavingsTransaction(id: Long, input: SavingsTransactionInput) = with(input) {
        updateSavingsTransaction(id, savingsType, transactionType, amount, title, notes, date, affectsAvailableMoney)
    }

    fun deleteSavingsTransaction(id: Long) {
        val user = currentUser.value ?: return
        if (rejectIfBalanceWouldGoNegative(removedId = id, added = null)) return
        viewModelScope.launch {
            repository.deleteSavingsTransaction(user.id, id)
        }
    }

    private fun rejectIfBalanceWouldGoNegative(removedId: Long?, added: SavingsTransactionEntity?): Boolean {
        SavingsCalculator.typeThatWouldGoNegative(savingsTransactions.value, removedId, added)
            ?: return false
        return true
    }

    private fun savingsTypeLabel(savingsType: String): String =
        if (savingsType == SavingsType.EMERGENCY_FUND) "Emergency Fund" else "Adult Money"

    fun addBudget(category: String, allocatedAmount: Double) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addBudget(user.id, category, allocatedAmount)
        }
    }

    // --- Authentication & User Isolation ---

    fun registerNewUser(
        username: String,
        email: String,
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImagePath: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.registerUser(
                username = username,
                email = email,
                displayName = displayName,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex,
                avatarImagePath = avatarImagePath
            )
            result.onSuccess {
                onSuccess()
            }.onFailure {
                onError(it.message ?: "Could not register user")
            }
        }
    }

    fun switchUser(user: UserEntity) {
        authRepository.switchUser(user)
    }

    fun logout() {
        authRepository.logout()
    }

    fun updateProfile(displayName: String, email: String, avatarImagePath: String?) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            authRepository.updateUser(
                user.copy(
                    displayName = displayName,
                    email = email,
                    avatarImagePath = avatarImagePath
                )
            )
        }
    }

    // --- Data Portability (Excel/JSON) ---

    suspend fun exportUserDataToJson(): String? {
        val user = currentUser.value ?: return null
        return try {
            val backup = repository.getUserDataForBackup(user.id)
            val adapter = moshi.adapter(UserDataBackup::class.java)
            adapter.indent("  ").toJson(backup)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importUserDataFromJson(json: String): Boolean {
        val user = currentUser.value ?: return false
        return try {
            val adapter = moshi.adapter(UserDataBackup::class.java)
            val backup = adapter.fromJson(json) ?: return false
            repository.restoreUserDataFromBackup(user.id, backup)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteCurrentUser() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            // 1. Delete all associated data
            repository.deleteAllUserData(user.id)
            // 2. Delete the user entity and auto-switch if others exist
            authRepository.deleteUser(user.id)
        }
    }

    fun clearCurrentUserData() {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteAllUserData(user.id)
        }
    }
}

class ExpenseTrackerViewModelFactory(
    private val authRepository: AuthRepository,
    private val repository: ExpenseTrackerRepository,
    private val productLookup: ProductLookupService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseTrackerViewModel::class.java)) {
            return ExpenseTrackerViewModel(authRepository, repository, productLookup) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
