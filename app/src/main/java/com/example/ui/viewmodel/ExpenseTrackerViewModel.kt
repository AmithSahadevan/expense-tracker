package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.ProductLookupResult
import com.example.data.model.SavingsCalculator
import com.example.data.model.SavingsTransactionInput
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.WishlistInputValidator
import com.example.data.model.WishlistItemInput
import com.example.data.repository.AuthRepository
import com.example.data.remote.ProductLookupService
import com.example.data.repository.ExpenseTrackerRepository
import com.example.ui.components.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val currentUser: StateFlow<UserEntity?> = authRepository.currentUser
    val allUsers: StateFlow<List<UserEntity>> = authRepository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

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
        val budgetsCount: Int
    )

    private val hubCounts = combine(savingsGoals, moneyFlows, wishlist, budgets) { goals, flows, wishListItems, budgetItems ->
        HubCounts(goals.size, flows.size, wishListItems.size, budgetItems.size)
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
            recentTransactions = txList.take(5),
            savingsGoalsCount = counts.goalsCount,
            moneyFlowCount = counts.flowCount,
            wishlistCount = counts.wishlistCount,
            budgetsCount = counts.budgetsCount
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardSummaryUiState()
    )

    fun clearActionMessage() {
        _actionMessage.value = null
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
                _actionMessage.value = "💸 Expense added: ${user.currencySymbol}${String.format("%.2f", amount)}"
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
                _actionMessage.value = "💰 Income logged: ${user.currencySymbol}${String.format("%.2f", amount)}"
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
            _actionMessage.value = "Transaction updated."
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
            _actionMessage.value = "Salary set to ${user.currencySymbol}${String.format("%.2f", salary)} (Payday: Day $payday)"
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
            _actionMessage.value = "Category \"$name\" created!"
        }
    }

    fun deleteCustomCategory(id: Long) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.deleteCustomCategory(user.id, id)
            _actionMessage.value = "Category deleted."
        }
    }

    fun deleteTransaction(item: TransactionItem) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return // Security check
        viewModelScope.launch {
            repository.deleteTransaction(user.id, item.id, item.type)
            _actionMessage.value = "Transaction removed."
        }
    }

    fun addWishlistItem(input: WishlistItemInput) {
        val user = currentUser.value ?: return
        val clean = cleanWishlistInput(input) ?: return
        viewModelScope.launch {
            repository.addWishlistItem(user.id, clean)
            _actionMessage.value = "✨ ${clean.title} added to wishlist!"
        }
    }

    fun updateWishlistItem(id: Long, input: WishlistItemInput) {
        val user = currentUser.value ?: return
        val clean = cleanWishlistInput(input) ?: return
        viewModelScope.launch {
            val updated = repository.updateWishlistItem(user.id, id, clean)
            _actionMessage.value = if (updated) "Wishlist item updated." else "Wishlist item not found."
        }
    }

    /** Reads product details from a pasted link, on-device. Failures come back as results, never exceptions. */
    suspend fun lookupProduct(link: String): ProductLookupResult = productLookup.lookup(link)

    fun deleteWishlistItem(item: WishlistItemEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.deleteWishlistItem(user.id, item.id)
            _actionMessage.value = "Removed \"${item.title}\" from wishlist."
        }
    }

    private fun cleanWishlistInput(input: WishlistItemInput): WishlistItemInput? {
        val clean = WishlistInputValidator.clean(input)
        if (clean == null) {
            val errors = WishlistInputValidator.validate(input)
            _actionMessage.value = errors.title ?: errors.price ?: errors.url ?: errors.imageUrl
        }
        return clean
    }

    fun toggleWishlistItem(item: WishlistItemEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.toggleWishlistPurchased(user.id, item.id, !item.isPurchased)
        }
    }

    fun addMoneyFlow(person: String, direction: String, amount: Double, notes: String = "") {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addMoneyFlow(user.id, person, direction, amount, notes)
            _actionMessage.value = "🤝 Debt/Flow recorded with $person"
        }
    }

    fun toggleMoneyFlowSettled(item: MoneyFlowEntity) {
        val user = currentUser.value ?: return
        if (item.userId != user.id) return
        viewModelScope.launch {
            repository.setMoneyFlowSettled(user.id, item.id, !item.isSettled)
            _actionMessage.value = if (!item.isSettled) "Marked as settled!" else "Marked as pending."
        }
    }

    fun addSavingsGoal(title: String, goalAmount: Double, emoji: String = "🎯") {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addSavingsGoal(user.id, title, goalAmount, 0.0, emoji)
            _actionMessage.value = "🎯 Savings goal created: $title"
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
        if (amount <= 0) {
            _actionMessage.value = "Enter an amount greater than zero."
            return
        }
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
            val actionLabel = if (transactionType == SavingsActionType.DEPOSIT) "added to" else "withdrawn from"
            _actionMessage.value = "${formatMoney(user.currencySymbol, amount)} $actionLabel ${savingsTypeLabel(savingsType)}"
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
        if (amount <= 0) {
            _actionMessage.value = "Enter an amount greater than zero."
            return
        }
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
            val updated = repository.updateSavingsTransaction(
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
            _actionMessage.value = if (updated) "Savings record updated." else "Savings record not found."
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
            _actionMessage.value = "Savings record removed."
        }
    }

    private fun rejectIfBalanceWouldGoNegative(removedId: Long?, added: SavingsTransactionEntity?): Boolean {
        val negativeType = SavingsCalculator.typeThatWouldGoNegative(savingsTransactions.value, removedId, added)
            ?: return false
        val currency = currentUser.value?.currencySymbol ?: "$"
        val balance = SavingsCalculator.balance(savingsTransactions.value, negativeType)
        _actionMessage.value = "Not enough in ${savingsTypeLabel(negativeType)} " +
            "(balance ${formatMoney(currency, balance)}). Balances can't go below zero."
        return true
    }

    private fun savingsTypeLabel(savingsType: String): String =
        if (savingsType == SavingsType.EMERGENCY_FUND) "Emergency Fund" else "Adult Money"

    fun addBudget(category: String, allocatedAmount: Double) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repository.addBudget(user.id, category, allocatedAmount)
            _actionMessage.value = "📊 Budget envelope set for $category"
        }
    }

    // --- Authentication & User Isolation ---

    fun registerNewUser(
        username: String,
        email: String,
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.registerUser(
                username = username,
                email = email,
                displayName = displayName,
                avatarEmoji = avatarEmoji,
                avatarColorHex = avatarColorHex
            )
            result.onSuccess {
                _actionMessage.value = "🎉 Welcome aboard, ${it.displayName}!"
                onSuccess()
            }.onFailure {
                onError(it.message ?: "Could not register user")
            }
        }
    }

    fun switchUser(user: UserEntity) {
        authRepository.switchUser(user)
        _actionMessage.value = "Switched account to ${user.displayName}"
    }

    fun logout() {
        authRepository.logout()
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
