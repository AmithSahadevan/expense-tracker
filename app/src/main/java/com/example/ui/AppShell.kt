package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MoneyFlowInput
import com.example.data.model.ProductLookupResult
import com.example.data.model.SavingsTransactionInput
import com.example.data.model.WishlistItemInput
import com.example.ui.components.AddTransactionSheet
import com.example.ui.components.PlayfulTopBar
import com.example.ui.components.UserAuthModal
import com.example.ui.navigation.AppDestination
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoneyFlowScreen
import com.example.ui.screens.SavingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.viewmodel.ExpenseTrackerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    viewModel: ExpenseTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val savingsTransactions by viewModel.savingsTransactions.collectAsStateWithLifecycle()
    val moneyFlows by viewModel.moneyFlows.collectAsStateWithLifecycle()
    val wishlist by viewModel.wishlist.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pagerDestinations = remember {
        listOf(
            AppDestination.SETTINGS,
            AppDestination.HOME,
            AppDestination.TRANSACTIONS,
            AppDestination.WISHLIST,
            AppDestination.SAVINGS,
            AppDestination.MONEY_FLOW,
            AppDestination.BUDGETS
        )
    }

    val pagerState = rememberPagerState(initialPage = 1) { pagerDestinations.size }
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }

    // Sync Pager -> currentDestination
    LaunchedEffect(pagerState.currentPage) {
        currentDestination = pagerDestinations[pagerState.currentPage]
    }

    // Handle "Navigate To" requests from within screens (e.g. "See all -> transactions")
    val navigateToDestination = { routeName: String ->
        val dest = AppDestination.entries.find { it.route == routeName }
        if (dest != null) {
            val page = pagerDestinations.indexOf(dest)
            if (page != -1) {
                scope.launch { pagerState.scrollToPage(page) }
            }
        }
    }

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<com.example.data.model.TransactionItem?>(null) }
    var showAuthModal by remember { mutableStateOf(false) }
    var showMoreMenuSheet by remember { mutableStateOf(false) }

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val authSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val moreHubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Tablet & Desktop Canonical Layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { showAuthModal = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            FloatingActionButton(
                                onClick = { showAddTransactionSheet = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("rail_fab_add")
                            ) {
                                Icon(imageVector = Icons.Outlined.Add, contentDescription = "Add Flow")
                            }
                        }
                    }
                ) {
                    AppDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = currentDestination == dest,
                            onClick = { navigateToDestination(dest.route) },
                            icon = { Icon(imageVector = dest.icon, contentDescription = dest.title) },
                            label = { Text(dest.title, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("rail_item_${dest.route}")
                        )
                    }
                }

                // Main Content for Wide Screens
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        PlayfulTopBar(
                            currentUser = currentUser,
                            onUserClick = { showAuthModal = true }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(modifier = Modifier.widthIn(max = 1100.dp)) {
                            ScreenRouter(
                                pagerState = pagerState,
                                pagerDestinations = pagerDestinations,
                                currentUser = currentUser,
                                summary = summary,
                                transactions = transactions,
                                moneyFlows = moneyFlows,
                                wishlist = wishlist,
                                savingsTransactions = savingsTransactions,
                                budgets = budgets,
                                allUsersCount = allUsers.size,
                                onNavigateTo = navigateToDestination,
                                onOpenAddTransaction = {
                                    editingTransaction = null
                                    showAddTransactionSheet = true
                                },
                                onOpenAuthModal = { showAuthModal = true },
                                onDeleteTransaction = { viewModel.deleteTransaction(it) },
                                onEditTransaction = { item ->
                                    editingTransaction = item
                                    showAddTransactionSheet = true
                                },
                                onUpdateSalaryAndPayday = { salary, payday, logThisMonth ->
                                    viewModel.updateSalaryAndPayday(salary, payday, logThisMonth)
                                },
                                onAddMoneyFlow = { viewModel.addMoneyFlow(it) },
                                onUpdateMoneyFlow = { id, input -> viewModel.updateMoneyFlow(id, input) },
                                onDeleteMoneyFlow = { viewModel.deleteMoneyFlow(it) },
                                onToggleMoneyFlow = { viewModel.toggleMoneyFlowSettled(it) },
                                onAddWishlistItem = { viewModel.addWishlistItem(it) },
                                onUpdateWishlistItem = { id, input -> viewModel.updateWishlistItem(id, input) },
                                onDeleteWishlistItem = { viewModel.deleteWishlistItem(it) },
                                onLookupProduct = viewModel::lookupProduct,
                                onToggleWishlist = { viewModel.toggleWishlistItem(it) },
                                onAddSavingsTransaction = { viewModel.addSavingsTransaction(it) },
                                onUpdateSavingsTransaction = { id, input -> viewModel.updateSavingsTransaction(id, input) },
                                onDeleteSavingsTransaction = { viewModel.deleteSavingsTransaction(it) },
                                onAddBudget = { c, a -> viewModel.addBudget(c, a) }
                            )
                        }
                    }
                }
            }
        } else {
            // Mobile Canonical Layout with Playful TopBar & BottomBar + Navigation Hub
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        PlayfulTopBar(
                            currentUser = currentUser,
                            onUserClick = { showAuthModal = true }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                ) {
                    ScreenRouter(
                        pagerState = pagerState,
                        pagerDestinations = pagerDestinations,
                        currentUser = currentUser,
                        summary = summary,
                        transactions = transactions,
                        moneyFlows = moneyFlows,
                        wishlist = wishlist,
                        savingsTransactions = savingsTransactions,
                        budgets = budgets,
                        allUsersCount = allUsers.size,
                        onNavigateTo = navigateToDestination,
                        onOpenAddTransaction = {
                            editingTransaction = null
                            showAddTransactionSheet = true
                        },
                        onOpenAuthModal = { showAuthModal = true },
                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                        onEditTransaction = { item ->
                            editingTransaction = item
                            showAddTransactionSheet = true
                        },
                        onUpdateSalaryAndPayday = { salary, payday, logThisMonth ->
                            viewModel.updateSalaryAndPayday(salary, payday, logThisMonth)
                        },
                        onAddMoneyFlow = { viewModel.addMoneyFlow(it) },
                        onUpdateMoneyFlow = { id, input -> viewModel.updateMoneyFlow(id, input) },
                        onDeleteMoneyFlow = { viewModel.deleteMoneyFlow(it) },
                        onToggleMoneyFlow = { viewModel.toggleMoneyFlowSettled(it) },
                        onAddWishlistItem = { viewModel.addWishlistItem(it) },
                        onUpdateWishlistItem = { id, input -> viewModel.updateWishlistItem(id, input) },
                        onDeleteWishlistItem = { viewModel.deleteWishlistItem(it) },
                        onLookupProduct = viewModel::lookupProduct,
                        onToggleWishlist = { viewModel.toggleWishlistItem(it) },
                        onAddSavingsTransaction = { viewModel.addSavingsTransaction(it) },
                        onUpdateSavingsTransaction = { id, input -> viewModel.updateSavingsTransaction(id, input) },
                        onDeleteSavingsTransaction = { viewModel.deleteSavingsTransaction(it) },
                        onAddBudget = { c, a -> viewModel.addBudget(c, a) }
                    )

                    AmoebaFloatingBottomDocker(
                        currentDestination = currentDestination,
                        onNavigate = { dest -> navigateToDestination(dest.route) },
                        onOpenAddSheet = { showAddTransactionSheet = true },
                        onOpenMoreMenu = { showMoreMenuSheet = true },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }

    // Add / Edit Transaction Bottom Sheet
    if (showAddTransactionSheet) {
        AddTransactionSheet(
            currency = currentUser?.currencySymbol ?: "₹",
            sheetState = addSheetState,
            onDismiss = {
                showAddTransactionSheet = false
                editingTransaction = null
            },
            initialItem = editingTransaction,
            customCategories = customCategories,
            onCreateCustomCategory = { name, emoji, type, colorHex ->
                viewModel.addCustomCategory(name, emoji, type, colorHex)
            },
            onSaveTransaction = { type, title, amount, category, notes, method, date, recurrence ->
                val currentEdit = editingTransaction
                if (currentEdit != null) {
                    viewModel.updateTransaction(
                        id = currentEdit.id,
                        type = type,
                        title = title,
                        amount = amount,
                        category = category,
                        date = date,
                        notes = notes,
                        paymentMethod = method,
                        recurrence = recurrence
                    )
                } else {
                    viewModel.addTransaction(
                        type = type,
                        title = title,
                        amount = amount,
                        category = category,
                        notes = notes,
                        paymentMethod = method,
                        date = date,
                        recurrence = recurrence
                    )
                }
                editingTransaction = null
                showAddTransactionSheet = false
            }
        )
    }

    // User Authentication & Account Switcher Modal
    if (showAuthModal) {
        UserAuthModal(
            currentUser = currentUser,
            allUsers = allUsers,
            sheetState = authSheetState,
            onDismiss = { showAuthModal = false },
            onSwitchUser = { user ->
                viewModel.switchUser(user)
            },
            onRegisterUser = { username, email, displayName, emoji, colorHex ->
                viewModel.registerNewUser(
                    username = username,
                    email = email,
                    displayName = displayName,
                    avatarEmoji = emoji,
                    avatarColorHex = colorHex,
                    onSuccess = {},
                    onError = {}
                )
            }
        )
    }

    // More Hub Quick Modal Sheet (for quick access to Budgets & Settings on mobile)
    if (showMoreMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenuSheet = false },
            sheetState = moreHubSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "App Hub & Controls",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    onClick = {
                        navigateToDestination(AppDestination.MONEY_FLOW.route)
                        showMoreMenuSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(text = "Money Flow", fontWeight = FontWeight.Bold)
                            Text(text = "Track debts, IOUs & shared expenses", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    onClick = {
                        navigateToDestination(AppDestination.SAVINGS.route)
                        showMoreMenuSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("more_hub_savings")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(text = "Savings", fontWeight = FontWeight.Bold)
                            Text(text = "Adult Money & protected Emergency Fund", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    onClick = {
                        navigateToDestination(AppDestination.BUDGETS.route)
                        showMoreMenuSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(text = "Category Budgets", fontWeight = FontWeight.Bold)
                            Text(text = "Manage spending caps & allocations", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    onClick = {
                        navigateToDestination(AppDestination.SETTINGS.route)
                        showMoreMenuSheet = false
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(text = "Settings & Profiles", fontWeight = FontWeight.Bold)
                            Text(text = "User data isolation, accounts & specs", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    onClick = {
                        showMoreMenuSheet = false
                        showAuthModal = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(text = "Switch Profile (${allUsers.size} accounts)", fontWeight = FontWeight.Bold)
                            Text(text = "Current: @${currentUser?.username}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenRouter(
    pagerState: PagerState,
    pagerDestinations: List<AppDestination>,
    currentUser: com.example.data.local.entities.UserEntity?,
    summary: com.example.ui.viewmodel.DashboardSummaryUiState,
    transactions: List<com.example.data.model.TransactionItem>,
    moneyFlows: List<com.example.data.local.entities.MoneyFlowEntity>,
    wishlist: List<com.example.data.local.entities.WishlistItemEntity>,
    savingsTransactions: List<com.example.data.local.entities.SavingsTransactionEntity>,
    budgets: List<com.example.data.local.entities.BudgetEntity>,
    allUsersCount: Int,
    onNavigateTo: (String) -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenAuthModal: () -> Unit,
    onDeleteTransaction: (com.example.data.model.TransactionItem) -> Unit,
    onEditTransaction: (com.example.data.model.TransactionItem) -> Unit,
    onUpdateSalaryAndPayday: (Double, Int, Boolean) -> Unit,
    onAddMoneyFlow: (MoneyFlowInput) -> Unit,
    onUpdateMoneyFlow: (Long, MoneyFlowInput) -> Unit,
    onDeleteMoneyFlow: (com.example.data.local.entities.MoneyFlowEntity) -> Unit,
    onToggleMoneyFlow: (com.example.data.local.entities.MoneyFlowEntity) -> Unit,
    onAddWishlistItem: (WishlistItemInput) -> Unit,
    onUpdateWishlistItem: (Long, WishlistItemInput) -> Unit,
    onDeleteWishlistItem: (com.example.data.local.entities.WishlistItemEntity) -> Unit,
    onLookupProduct: suspend (String) -> ProductLookupResult,
    onToggleWishlist: (com.example.data.local.entities.WishlistItemEntity) -> Unit,
    onAddSavingsTransaction: (SavingsTransactionInput) -> Unit,
    onUpdateSavingsTransaction: (Long, SavingsTransactionInput) -> Unit,
    onDeleteSavingsTransaction: (Long) -> Unit,
    onAddBudget: (String, Double) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true,
        key = { it }
    ) { pageIndex ->
        val destination = pagerDestinations[pageIndex]
        when (destination) {
            AppDestination.HOME -> HomeScreen(
                currentUser = currentUser,
                summary = summary,
                onNavigateTo = onNavigateTo,
                onOpenAddTransaction = onOpenAddTransaction
            )
            AppDestination.TRANSACTIONS -> TransactionsScreen(
                currentUser = currentUser,
                transactions = transactions,
                onDeleteTransaction = onDeleteTransaction,
                onEditTransaction = onEditTransaction,
                onOpenAddTransaction = onOpenAddTransaction
            )
            AppDestination.MONEY_FLOW -> MoneyFlowScreen(
                currentUser = currentUser,
                moneyFlows = moneyFlows,
                onAddMoneyFlow = onAddMoneyFlow,
                onUpdateMoneyFlow = onUpdateMoneyFlow,
                onDeleteMoneyFlow = onDeleteMoneyFlow,
                onToggleSettled = onToggleMoneyFlow
            )
            AppDestination.WISHLIST -> WishlistScreen(
                currentUser = currentUser,
                wishlistItems = wishlist,
                adultMoneyBalance = summary.adultMoneyBalance,
                onAddWishlistItem = onAddWishlistItem,
                onUpdateWishlistItem = onUpdateWishlistItem,
                onDeleteWishlistItem = onDeleteWishlistItem,
                onLookupProduct = onLookupProduct,
                onTogglePurchased = onToggleWishlist
            )
            AppDestination.SAVINGS -> SavingsScreen(
                currentUser = currentUser,
                savingsTransactions = savingsTransactions,
                adultMoneyBalance = summary.adultMoneyBalance,
                emergencyFundBalance = summary.emergencyFundBalance,
                onAddSavingsTransaction = onAddSavingsTransaction,
                onUpdateSavingsTransaction = onUpdateSavingsTransaction,
                onDeleteSavingsTransaction = onDeleteSavingsTransaction
            )
            AppDestination.BUDGETS -> BudgetsScreen(
                currentUser = currentUser,
                budgets = budgets,
                onAddBudget = onAddBudget
            )
            AppDestination.SETTINGS -> SettingsScreen(
                currentUser = currentUser,
                allUsersCount = allUsersCount,
                onOpenAuthModal = onOpenAuthModal,
                onUpdateSalaryAndPayday = onUpdateSalaryAndPayday
            )
        }
    }
}

@Composable
private fun AmoebaFloatingBottomDocker(
    currentDestination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDockerIndex = when (currentDestination) {
        AppDestination.HOME -> 0
        AppDestination.TRANSACTIONS -> 1
        AppDestination.WISHLIST -> 3
        AppDestination.MONEY_FLOW, AppDestination.SAVINGS, AppDestination.BUDGETS, AppDestination.SETTINGS -> 4
    }

    var previousIndex by remember { mutableIntStateOf(selectedDockerIndex) }
    val isMovingRight = selectedDockerIndex >= previousIndex

    LaunchedEffect(selectedDockerIndex) {
        previousIndex = selectedDockerIndex
    }

    // Dynamic dual spring: the leading edge travels ahead with higher stiffness,
    // while the trailing edge lags behind, stretching the bubble horizontally like an amoeba in transit.
    val animLeft by animateFloatAsState(
        targetValue = selectedDockerIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (isMovingRight) 180f else 480f
        ),
        label = "amoeba_left"
    )

    val animRight by animateFloatAsState(
        targetValue = selectedDockerIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = if (isMovingRight) 480f else 180f
        ),
        label = "amoeba_right"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("mobile_bottom_bar"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val density = LocalDensity.current
                val slotWidthPx = with(density) { (maxWidth / 5).toPx() }
                val bubbleBaseDiameterPx = with(density) { 46.dp.toPx() }
                val bubbleRadiusPx = bubbleBaseDiameterPx / 2f

                val minSlot = minOf(animLeft, animRight)
                val maxSlot = maxOf(animLeft, animRight)
                val stretch = maxSlot - minSlot

                // Calculate fluid amoeba boundaries
                val leftPx = (minSlot + 0.5f) * slotWidthPx - bubbleRadiusPx
                val rightPx = (maxSlot + 0.5f) * slotWidthPx + bubbleRadiusPx
                val bubbleWidthPx = (rightPx - leftPx).coerceAtLeast(bubbleBaseDiameterPx)
                // Volume preservation: subtle squash in height as horizontal stretch increases
                val squashFactor = (1f - (stretch * 0.12f)).coerceIn(0.78f, 1.0f)
                val bubbleHeightPx = bubbleBaseDiameterPx * squashFactor

                val bubbleLeftDp = with(density) { leftPx.toDp() }
                val bubbleWidthDp = with(density) { bubbleWidthPx.toDp() }
                val bubbleHeightDp = with(density) { bubbleHeightPx.toDp() }

                // 1. Organic Amoeba Bubble Indicator
                Box(
                    modifier = Modifier
                        .offset(x = bubbleLeftDp)
                        .size(width = bubbleWidthDp, height = bubbleHeightDp)
                        .align(Alignment.CenterStart)
                        .shadow(
                            elevation = 3.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )

                // 2. Interactive Docker Icons
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 0. Home
                    DockerSlotItem(
                        icon = AppDestination.HOME.icon,
                        contentDescription = AppDestination.HOME.title,
                        isSelected = selectedDockerIndex == 0,
                        onClick = { onNavigate(AppDestination.HOME) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_item_home")
                    )

                    // 1. Transactions
                    DockerSlotItem(
                        icon = AppDestination.TRANSACTIONS.icon,
                        contentDescription = AppDestination.TRANSACTIONS.title,
                        isSelected = selectedDockerIndex == 1,
                        onClick = { onNavigate(AppDestination.TRANSACTIONS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_item_transactions")
                    )

                    // 2. Middle: Add Flow (+) Action Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 5.dp,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { onOpenAddSheet() }
                                .testTag("dock_add_flow_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = "Add Flow",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // 3. Wishlist
                    DockerSlotItem(
                        icon = AppDestination.WISHLIST.icon,
                        contentDescription = AppDestination.WISHLIST.title,
                        isSelected = selectedDockerIndex == 3,
                        onClick = { onNavigate(AppDestination.WISHLIST) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_item_wishlist")
                    )

                    // 4. More
                    DockerSlotItem(
                        icon = Icons.AutoMirrored.Outlined.MenuOpen,
                        contentDescription = "More",
                        isSelected = selectedDockerIndex == 4,
                        onClick = { onOpenMoreMenu() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_item_more")
                    )
                }
            }
        }
    }
}

@Composable
private fun DockerSlotItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "docker_icon_scale"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
