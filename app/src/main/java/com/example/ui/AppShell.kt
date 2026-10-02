package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import com.example.ui.components.PhosphorIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.unit.Dp
import com.example.ui.components.AppHubDrawerContent
import com.example.ui.components.BottomFadeScrim
import com.example.ui.components.ProfileAvatar
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BudgetCalculator
import com.example.data.model.BudgetInput
import com.example.data.model.BudgetsSummary
import com.example.data.model.GoalsSummary
import com.example.data.model.MoneyFlowInput
import com.example.data.model.ProductLookupResult
import com.example.data.model.SavingsGoalInput
import com.example.data.model.SavingsTransactionInput
import com.example.data.model.WishlistItemInput
import com.example.ui.components.AddTransactionSheet
import com.example.ui.components.UserAuthModal
import com.example.ui.navigation.AppDestination
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoneyFlowScreen
import com.example.ui.screens.SavingsScreen
import com.example.ui.screens.SettingsScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import com.example.ui.screens.TransactionDetailScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.WishlistScreen
import com.example.ui.viewmodel.ExpenseTrackerViewModel
import com.example.data.model.TransactionItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.ui.viewmodel.DashboardSummaryUiState
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val budgetsSummary by viewModel.budgetsSummary.collectAsStateWithLifecycle()
    val goalsSummary by viewModel.goalsSummary.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    val pagerDestinations = remember {
        listOf(
            AppDestination.HOME,
            AppDestination.TRANSACTIONS,
            AppDestination.WISHLIST,
            AppDestination.SAVINGS,
            AppDestination.MONEY_FLOW,
            AppDestination.BUDGETS,
            AppDestination.SETTINGS
        )
    }

    val pagerState = rememberPagerState(initialPage = 0) { pagerDestinations.size }
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
    var editingTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    var viewingTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionItem?>(null) }
    var showAuthModal by remember { mutableStateOf(false) }
    var isWishlistDetailActive by remember { mutableStateOf(false) }
    // Page whose own add form the dock's add button asked to open; cleared once that page opens it.
    var dockAddRequest by remember { mutableStateOf<AppDestination?>(null) }

    val addSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val authSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val openDrawerOnSwipeRight = remember(pagerState, drawerState, scope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val horizontal = abs(available.x) > abs(available.y)
                val atFirstPage = pagerState.currentPage == 0 &&
                        pagerState.currentPageOffsetFraction == 0f
                return if (available.x > 0f && horizontal && atFirstPage && drawerState.isClosed) {
                    scope.launch { drawerState.open() }
                    available
                } else {
                    Offset.Zero
                }
            }
        }
    }

    // Retain the last viewing transaction item so content remains fully rendered during exit transition
    var activeDetailTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    if (viewingTransaction != null) {
        activeDetailTransaction = viewingTransaction
    }

    val detailSlideProgress by animateFloatAsState(
        targetValue = if (viewingTransaction != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "transaction_detail_slide"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        // Background Screen Container (Shifts to the left with dimming, creating depth/parallax movement like Instagram/iOS push)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -(size.width * 0.25f * detailSlideProgress)
                }
        ) {
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
                                        imageVector = PhosphorIcons.Bold.User,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                FloatingActionButton(
                                    onClick = { 
                                        editingTransaction = null
                                        showAddTransactionSheet = true 
                                    },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("rail_fab_add")
                                ) {
                                    Icon(imageVector = PhosphorIcons.Bold.Plus, contentDescription = "Add Flow")
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
                    Scaffold { innerPadding ->
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
                                    budgetsSummary = budgetsSummary,
                                    goalsSummary = goalsSummary,
                                    allUsersCount = allUsers.size,
                                    onNavigateTo = navigateToDestination,
                                    onOpenAddTransaction = {
                                        editingTransaction = null
                                        showAddTransactionSheet = true
                                    },
                                    onOpenAuthModal = { showAuthModal = true },
                                    onDeleteTransaction = { transactionToDelete = it },
                                    onEditTransaction = { item ->
                                        viewingTransaction = item
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
                                    onAddBudget = { viewModel.addBudget(it) },
                                    onUpdateBudget = { id, input -> viewModel.updateBudget(id, input) },
                                    onDeleteBudget = { viewModel.deleteBudget(it) },
                                    onAddGoal = { input, starting -> viewModel.addSavingsGoal(input, starting) },
                                    onUpdateGoal = { id, input -> viewModel.updateSavingsGoal(id, input) },
                                    onDeleteGoal = { viewModel.deleteSavingsGoal(it) },
                                    onContributeToGoal = { goalId, type, amount, note -> viewModel.contributeToGoal(goalId, type, amount, note) },
                                    onUpdateCurrency = { viewModel.updateCurrency(it) },
                                    onExportData = viewModel::exportUserDataToJson,
                                    onImportData = viewModel::importUserDataFromJson,
                                    onRemoveAccount = { viewModel.deleteCurrentUser() },
                                    onUpdateProfile = { name, email, pfp -> viewModel.updateProfile(name, email, pfp) },
                                    onClearData = { viewModel.clearCurrentUserData() }
                                )
                            }
                        }
                    }
                }
            } else {
                // Mobile canonical layout: pages draw full-bleed, with the gradient top bar
                // and the floating dock overlaid on top of them.
                val showDock = currentDestination != AppDestination.SETTINGS &&
                        if (currentDestination == AppDestination.WISHLIST) !isWishlistDetailActive else true

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen || viewingTransaction == null,
                    scrimColor = Color.Black.copy(alpha = 0.55f),
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.background,
                            drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.width(312.dp)
                        ) {
                            AppHubDrawerContent(
                                currentUser = currentUser,
                                currentDestination = currentDestination,
                                accountCount = allUsers.size,
                                onNavigate = { dest ->
                                    scope.launch { drawerState.close() }
                                    navigateToDestination(dest.route)
                                },
                                onSwitchProfile = {
                                    scope.launch { drawerState.close() }
                                    showAuthModal = true
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { _ ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(openDrawerOnSwipeRight)
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
                            budgetsSummary = budgetsSummary,
                            goalsSummary = goalsSummary,
                            allUsersCount = allUsers.size,
                            onNavigateTo = navigateToDestination,
                            onOpenAddTransaction = {
                                editingTransaction = null
                                showAddTransactionSheet = true
                            },
                            onOpenAuthModal = { showAuthModal = true },
                            onDeleteTransaction = { transactionToDelete = it },
                            onEditTransaction = { item ->
                                viewingTransaction = item
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
                            onAddBudget = { viewModel.addBudget(it) },
                            onUpdateBudget = { id, input -> viewModel.updateBudget(id, input) },
                            onDeleteBudget = { viewModel.deleteBudget(it) },
                            onAddGoal = { input, starting -> viewModel.addSavingsGoal(input, starting) },
                            onUpdateGoal = { id, input -> viewModel.updateSavingsGoal(id, input) },
                            onDeleteGoal = { viewModel.deleteSavingsGoal(it) },
                            onContributeToGoal = { goalId, type, amount, note -> viewModel.contributeToGoal(goalId, type, amount, note) },
                            onUpdateCurrency = { viewModel.updateCurrency(it) },
                            onExportData = viewModel::exportUserDataToJson,
                            onImportData = viewModel::importUserDataFromJson,
                            onRemoveAccount = { viewModel.deleteCurrentUser() },
                            onUpdateProfile = { name, email, pfp -> viewModel.updateProfile(name, email, pfp) },
                            onClearData = { viewModel.clearCurrentUserData() },
                            dockAddRequest = dockAddRequest,
                            onDockAddRequestHandled = { dockAddRequest = null },
                            onWishlistDetailToggle = { isWishlistDetailActive = it }
                        )

                        // Bottom chrome: a fade that darkens page content as it scrolls
                        // beneath, with the dock floating on top so it stays legible.
                        AnimatedVisibility(
                            visible = showDock,
                            enter = slideInVertically(
                                initialOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                            exit = slideOutVertically(
                                targetOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                            label = "bottom_dock_visibility",
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                BottomFadeScrim()
                                Column {
                                    FloatingDock(
                                        currentDestination = currentDestination,
                                        currentUser = currentUser,
                                        pageOrder = pagerDestinations,
                                        onNavigate = { dest -> navigateToDestination(dest.route) },
                                        onDockAdd = {
                                            when (currentDestination) {
                                                AppDestination.TRANSACTIONS -> {
                                                    editingTransaction = null
                                                    showAddTransactionSheet = true
                                                }
                                                AppDestination.WISHLIST,
                                                AppDestination.MONEY_FLOW,
                                                AppDestination.BUDGETS -> dockAddRequest = currentDestination
                                                AppDestination.HOME,
                                                AppDestination.SAVINGS,
                                                AppDestination.SETTINGS -> Unit
                                            }
                                        },
                                        onOpenProfile = {
                                            navigateToDestination(AppDestination.SETTINGS.route)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Spacer(modifier = Modifier.navigationBarsPadding())
                                }
                            }
                        }
                        }
                    }
                }
            }

            // Dark Scrim overlay over background during Transaction Detail slide
            if (detailSlideProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f * detailSlideProgress))
                )
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
            onRegisterUser = { username, email, displayName, emoji, colorHex, imagePath ->
                viewModel.registerNewUser(
                    username = username,
                    email = email,
                    displayName = displayName,
                    avatarEmoji = emoji,
                    avatarColorHex = colorHex,
                    avatarImagePath = imagePath,
                    onSuccess = {},
                    onError = {}
                )
            }
        )
    }

    // The former "App Hub & Controls" bottom sheet now lives in the navigation drawer
    // (AppHubDrawerContent), opened from the dock's profile avatar or an edge swipe.

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = viewingTransaction != null && !drawerState.isOpen) {
        viewingTransaction = null
    }

    BackHandler(
        enabled = currentDestination == AppDestination.SETTINGS &&
                viewingTransaction == null &&
                !drawerState.isOpen
    ) {
        navigateToDestination(AppDestination.HOME.route)
    }

    // Transaction Detail View (GPU accelerated parallax slide animation matching Instagram/iOS push physics)
    if (detailSlideProgress > 0f || viewingTransaction != null) {
        activeDetailTransaction?.let { tx ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (1f - detailSlideProgress) * size.width
                        shadowElevation = 16.dp.toPx()
                    }
            ) {
                TransactionDetailScreen(
                    item = tx,
                    currency = currentUser?.currencySymbol ?: "₹",
                    onBack = { viewingTransaction = null },
                    onEdit = {
                        editingTransaction = tx
                        showAddTransactionSheet = true
                    },
                    onDelete = {
                        transactionToDelete = tx
                    }
                )
            }
        }
    }

    // Delete Confirmation Dialog
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${tx.title}\" (${currentUser?.currencySymbol ?: "₹"}${String.format("%.0f", tx.amount)})? This cannot be undone.",
                    color = Color(0xFFF4F4F6)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        transactionToDelete = null
                        viewingTransaction = null // Close detail if deleting from there
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ScreenRouter(
    pagerState: PagerState,
    pagerDestinations: List<AppDestination>,
    currentUser: UserEntity?,
    summary: DashboardSummaryUiState,
    transactions: List<TransactionItem>,
    moneyFlows: List<MoneyFlowEntity>,
    wishlist: List<WishlistItemEntity>,
    savingsTransactions: List<SavingsTransactionEntity>,
    budgets: List<BudgetEntity>,
    budgetsSummary: BudgetsSummary,
    goalsSummary: GoalsSummary,
    allUsersCount: Int,
    onNavigateTo: (String) -> Unit,
    onOpenAddTransaction: () -> Unit,
    onOpenAuthModal: () -> Unit,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onUpdateSalaryAndPayday: (Double, Int, Boolean) -> Unit,
    onAddMoneyFlow: (MoneyFlowInput) -> Unit,
    onUpdateMoneyFlow: (Long, MoneyFlowInput) -> Unit,
    onDeleteMoneyFlow: (MoneyFlowEntity) -> Unit,
    onToggleMoneyFlow: (MoneyFlowEntity) -> Unit,
    onAddWishlistItem: (WishlistItemInput) -> Unit,
    onUpdateWishlistItem: (Long, WishlistItemInput) -> Unit,
    onDeleteWishlistItem: (WishlistItemEntity) -> Unit,
    onLookupProduct: suspend (String) -> ProductLookupResult,
    onToggleWishlist: (WishlistItemEntity) -> Unit,
    onAddSavingsTransaction: (SavingsTransactionInput) -> Unit,
    onUpdateSavingsTransaction: (Long, SavingsTransactionInput) -> Unit,
    onDeleteSavingsTransaction: (Long) -> Unit,
    onAddBudget: (BudgetInput) -> Unit,
    onUpdateBudget: (Long, BudgetInput) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onAddGoal: (SavingsGoalInput, Double) -> Unit,
    onUpdateGoal: (Long, SavingsGoalInput) -> Unit,
    onDeleteGoal: (Long) -> Unit,
    onContributeToGoal: (Long, String, Double, String) -> String?,
    onUpdateCurrency: (String) -> Unit,
    onExportData: suspend () -> String?,
    onImportData: suspend (String) -> Boolean,
    onRemoveAccount: () -> Unit,
    onUpdateProfile: (String, String, String?) -> Unit,
    onClearData: () -> Unit,
    dockAddRequest: AppDestination? = null,
    onDockAddRequestHandled: () -> Unit = {},
    onWishlistDetailToggle: (Boolean) -> Unit = {}
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
                budgetsSummary = budgetsSummary,
                goalsSummary = goalsSummary,
                onNavigateTo = onNavigateTo,
                onOpenAddTransaction = onOpenAddTransaction,
                onEditTransaction = onEditTransaction,
                onOpenAuthModal = onOpenAuthModal
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
                onToggleSettled = onToggleMoneyFlow,
                addRequested = dockAddRequest == AppDestination.MONEY_FLOW,
                onAddRequestHandled = onDockAddRequestHandled
            )
            AppDestination.WISHLIST -> WishlistScreen(
                currentUser = currentUser,
                wishlistItems = wishlist,
                adultMoneyBalance = summary.adultMoneyBalance,
                onAddWishlistItem = onAddWishlistItem,
                onUpdateWishlistItem = onUpdateWishlistItem,
                onDeleteWishlistItem = onDeleteWishlistItem,
                onLookupProduct = onLookupProduct,
                onTogglePurchased = onToggleWishlist,
                addRequested = dockAddRequest == AppDestination.WISHLIST,
                onAddRequestHandled = onDockAddRequestHandled,
                onDetailViewToggle = onWishlistDetailToggle
            )
            AppDestination.SAVINGS -> SavingsScreen(
                currentUser = currentUser,
                savingsTransactions = savingsTransactions,
                adultMoneyBalance = summary.adultMoneyBalance,
                emergencyFundBalance = summary.emergencyFundBalance,
                goalsSummary = goalsSummary,
                onAddSavingsTransaction = onAddSavingsTransaction,
                onUpdateSavingsTransaction = onUpdateSavingsTransaction,
                onDeleteSavingsTransaction = onDeleteSavingsTransaction,
                onAddGoal = onAddGoal,
                onUpdateGoal = onUpdateGoal,
                onDeleteGoal = onDeleteGoal,
                onContributeToGoal = onContributeToGoal
            )
            AppDestination.BUDGETS -> BudgetsScreen(
                currentUser = currentUser,
                budgets = budgets,
                summary = budgetsSummary,
                suggestedCategories = BudgetCalculator.categoriesWithoutBudget(budgets, transactions),
                onAddBudget = onAddBudget,
                onUpdateBudget = onUpdateBudget,
                onDeleteBudget = onDeleteBudget,
                addRequested = dockAddRequest == AppDestination.BUDGETS,
                onAddRequestHandled = onDockAddRequestHandled
            )
            AppDestination.SETTINGS -> SettingsScreen(
                currentUser = currentUser,
                onOpenAuthModal = onOpenAuthModal,
                onUpdateSalaryAndPayday = onUpdateSalaryAndPayday,
                onUpdateCurrency = onUpdateCurrency,
                onExportData = onExportData,
                onImportData = onImportData,
                onRemoveAccount = onRemoveAccount,
                onUpdateProfile = onUpdateProfile,
                onClearData = onClearData,
                onBackToHome = { onNavigateTo(AppDestination.HOME.route) }
            )
        }
    }
}

/**
 * The floating dock: Home, Transactions, Wishlist, the page-aware add button, and the
 * profile avatar pushed to the right. It draws no bar of its own - the [BottomFadeScrim]
 * behind it supplies the darkening, so page content dissolves underneath rather than
 * being cut off by a solid edge.
 */
@Composable
private fun FloatingDock(
    currentDestination: AppDestination,
    currentUser: UserEntity?,
    pageOrder: List<AppDestination>,
    onNavigate: (AppDestination) -> Unit,
    onDockAdd: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DockCircleItem(
            icon = AppDestination.HOME.icon,
            contentDescription = AppDestination.HOME.title,
            isSelected = currentDestination == AppDestination.HOME,
            onClick = { onNavigate(AppDestination.HOME) },
            modifier = Modifier.testTag("nav_item_home")
        )
        DockCircleItem(
            icon = AppDestination.TRANSACTIONS.icon,
            contentDescription = AppDestination.TRANSACTIONS.title,
            isSelected = currentDestination == AppDestination.TRANSACTIONS,
            onClick = { onNavigate(AppDestination.TRANSACTIONS) },
            modifier = Modifier.testTag("nav_item_transactions")
        )
        DockCircleItem(
            icon = AppDestination.WISHLIST.icon,
            contentDescription = AppDestination.WISHLIST.title,
            isSelected = currentDestination == AppDestination.WISHLIST,
            onClick = { onNavigate(AppDestination.WISHLIST) },
            modifier = Modifier.testTag("nav_item_wishlist")
        )
        DockAddMorphButton(
            currentDestination = currentDestination,
            pageOrder = pageOrder,
            onClick = onDockAdd,
            size = DockItemSize,
            modifier = Modifier.testTag("dock_add_flow_button")
        )

        Spacer(modifier = Modifier.weight(1f))

        ProfileAvatar(
            currentUser = currentUser,
            size = DockItemSize,
            onClick = onOpenProfile,
            modifier = Modifier.testTag("dock_profile_button")
        )
    }
}

private val DockItemSize = 52.dp

// Accent of the page's own add FAB; null for pages without one (dock button shows the "O").
private fun AppDestination.dockAddAccent(): Color? = when (this) {
    AppDestination.TRANSACTIONS -> Color(0xFFFF6B6B)
    AppDestination.WISHLIST -> Color(0xFFFD79A8)
    AppDestination.MONEY_FLOW -> Color(0xFF10B981)
    AppDestination.BUDGETS -> Color(0xFF0984E3)
    AppDestination.HOME, AppDestination.SAVINGS, AppDestination.SETTINGS -> null
}

@Composable
private fun DockAddMorphButton(
    currentDestination: AppDestination,
    pageOrder: List<AppDestination>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val accent = currentDestination.dockAddAccent()

    // Each hop between two pages that both own an add button spins the plus a quarter turn,
    // clockwise when moving to a page further right in the pager, counter-clockwise when moving left.
    var plusTurns by remember { mutableIntStateOf(0) }
    var previousDestination by remember { mutableStateOf(currentDestination) }
    LaunchedEffect(currentDestination) {
        if (previousDestination != currentDestination &&
            accent != null &&
            previousDestination.dockAddAccent() != null
        ) {
            val movingRight = pageOrder.indexOf(currentDestination) > pageOrder.indexOf(previousDestination)
            plusTurns += if (movingRight) 1 else -1
        }
        previousDestination = currentDestination
    }

    // 0 = hollow "O", 1 = plus-shaped cutout
    val morph by animateFloatAsState(
        targetValue = if (accent != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 240f),
        label = "dock_add_morph"
    )
    val rotation by animateFloatAsState(
        targetValue = plusTurns * 90f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 220f),
        label = "dock_add_rotation"
    )
    val tint by animateColorAsState(
        targetValue = accent ?: Color.White,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "dock_add_tint"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = accent != null, onClick = onClick)
            .semantics { contentDescription = "Add Flow" }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        drawCircle(color = tint)

        // The hole is two rounded bars. As full circles of holeDiameter they form the "O";
        // shrinking to long thin bars they become the "+". Thickness eases out faster than
        // length so the circle pinches into arms rather than just scaling down.
        // Proportions are fractions of the button so the shape survives any size.
        val canvasSize = this.size.minDimension
        val holeDiameter = canvasSize * (32f / 42f)
        val plusLength = canvasSize * (16f / 42f)
        val plusThickness = canvasSize * (3f / 42f)
        val maxHole = canvasSize - 2.dp.toPx()

        val thicknessProgress = if (morph in 0f..1f) 1f - (1f - morph) * (1f - morph) else morph
        val thickness = (holeDiameter + (plusThickness - holeDiameter) * thicknessProgress)
            .coerceIn(0f, maxHole)
        val length = (holeDiameter + (plusLength - holeDiameter) * morph)
            .coerceIn(thickness, maxHole)
        val corner = CornerRadius(thickness / 2f)

        rotate(rotation) {
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(center.x - length / 2f, center.y - thickness / 2f),
                size = Size(length, thickness),
                cornerRadius = corner,
                blendMode = BlendMode.Clear
            )
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(center.x - thickness / 2f, center.y - length / 2f),
                size = Size(thickness, length),
                cornerRadius = corner,
                blendMode = BlendMode.Clear
            )
        }
    }
}

/**
 * One dock circle. Selected reads as a solid light disc with a dark glyph; unselected is a
 * hairline ring over the darkened background.
 */
@Composable
private fun DockCircleItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.White.copy(alpha = 0.06f)
        },
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "dock_item_container"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            Color.White.copy(alpha = 0.78f)
        },
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "dock_item_tint"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0f else 0.16f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "dock_item_border"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 400f
        ),
        label = "dock_item_scale"
    )

    Box(
        modifier = modifier
            .size(DockItemSize)
            .clip(CircleShape)
            .background(containerColor)
            .border(width = 1.5.dp, color = Color.White.copy(alpha = borderAlpha), shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}
