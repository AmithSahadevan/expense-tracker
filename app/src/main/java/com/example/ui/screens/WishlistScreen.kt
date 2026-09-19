package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.ProductLookupResult
import com.example.data.model.WishlistAffordability
import com.example.data.model.WishlistAffordabilityCalculator
import com.example.data.model.WishlistBrowser
import com.example.data.model.WishlistFilter
import com.example.data.model.WishlistItemInput
import com.example.data.model.WishlistPrice
import com.example.ui.components.AffordabilityChip
import com.example.ui.components.ChoicePill
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.PriorityBadge
import com.example.ui.components.ProductImage
import com.example.ui.components.PurchasedBadge
import com.example.ui.components.WishlistItemFormSheet
import com.example.ui.components.formatMoney
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import kotlin.random.Random

enum class WishlistViewMode {
    CAROUSEL, GRID
}

@Composable
fun WishlistScreen(
    currentUser: UserEntity?,
    wishlistItems: List<WishlistItemEntity>,
    // Affordability is based on Adult Money ONLY. The Emergency Fund is deliberately not a parameter.
    adultMoneyBalance: Double,
    onAddWishlistItem: (WishlistItemInput) -> Unit,
    onUpdateWishlistItem: (id: Long, input: WishlistItemInput) -> Unit,
    onDeleteWishlistItem: (WishlistItemEntity) -> Unit,
    onTogglePurchased: (WishlistItemEntity) -> Unit,
    onLookupProduct: suspend (String) -> ProductLookupResult,
    modifier: Modifier = Modifier,
    // Set by the dock's add button; the screen opens its add form and reports back.
    addRequested: Boolean = false,
    onAddRequestHandled: () -> Unit = {},
    onDetailViewToggle: (Boolean) -> Unit = {}
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var filter by rememberSaveable { mutableStateOf(WishlistFilter.ALL) }
    var viewMode by rememberSaveable { mutableStateOf(WishlistViewMode.CAROUSEL) }

    val affordableItems = remember(wishlistItems, adultMoneyBalance) {
        wishlistItems.filter { item ->
            val affordability = WishlistAffordabilityCalculator.evaluate(item.estimatedCost, adultMoneyBalance)
            affordability is WishlistAffordability.CanAfford && !item.isPurchased
        }
    }

    var selectedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WishlistItemEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<WishlistItemEntity?>(null) }

    LaunchedEffect(selectedItemId) {
        onDetailViewToggle(selectedItemId != null)
    }

    LaunchedEffect(addRequested) {
        if (addRequested) {
            editingItem = null
            showForm = true
            onAddRequestHandled()
        }
    }
    // Hoisted so the list keeps its scroll position after returning from a product's details.
    val gridState = rememberLazyStaggeredGridState()

    val selectedItem = wishlistItems.find { it.id == selectedItemId }
    BackHandler(enabled = selectedItem != null) { selectedItemId = null }
    BackHandler(enabled = selectedItem == null && viewMode == WishlistViewMode.GRID) {
        viewMode = WishlistViewMode.CAROUSEL
    }

    AnimatedContent(
        targetState = selectedItem?.id,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "wishlist_detail_transition",
        modifier = modifier
    ) { detailId ->
        val detailItem = wishlistItems.find { it.id == detailId }
        if (detailItem != null) {
            WishlistItemDetail(
                item = detailItem,
                adultMoneyBalance = adultMoneyBalance,
                currency = currency,
                onBack = { selectedItemId = null },
                onEdit = {
                    editingItem = detailItem
                    showForm = true
                },
                onDelete = { pendingDelete = detailItem },
                onTogglePurchased = { onTogglePurchased(detailItem) }
            )
        } else {
            WishlistBrowse(
                items = wishlistItems,
                affordableItems = affordableItems,
                filter = filter,
                onFilterChange = { filter = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                adultMoneyBalance = adultMoneyBalance,
                currency = currency,
                gridState = gridState,
                onOpenItem = { selectedItemId = it.id },
                onAddClick = {
                    editingItem = null
                    showForm = true
                }
            )
        }
    }

    if (showForm) {
        WishlistItemFormSheet(
            initialItem = editingItem,
            currency = currency,
            adultMoneyBalance = adultMoneyBalance,
            onLookupProduct = onLookupProduct,
            onDismiss = {
                showForm = false
                editingItem = null
            },
            onSave = { input ->
                val editing = editingItem
                if (editing != null) onUpdateWishlistItem(editing.id, input) else onAddWishlistItem(input)
                showForm = false
                editingItem = null
            }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove from wishlist?", fontWeight = FontWeight.Bold) },
            text = {
                val priced = if (WishlistPrice.isSet(item.estimatedCost)) " (${formatMoney(currency, item.estimatedCost)})" else ""
                Text("\"${item.title}\"$priced will be deleted. This can't be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWishlistItem(item)
                        if (selectedItemId == item.id) selectedItemId = null
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun LazyStaggeredGridScope.fullWidthItem(
    key: String,
    content: @Composable () -> Unit
) {
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }
}

@Composable
private fun WishlistBrowse(
    items: List<WishlistItemEntity>,
    affordableItems: List<WishlistItemEntity>,
    filter: WishlistFilter,
    onFilterChange: (WishlistFilter) -> Unit,
    viewMode: WishlistViewMode,
    onViewModeChange: (WishlistViewMode) -> Unit,
    adultMoneyBalance: Double,
    currency: String,
    gridState: LazyStaggeredGridState,
    onOpenItem: (WishlistItemEntity) -> Unit,
    onAddClick: () -> Unit
) {
    val visibleItems = remember(items, filter, adultMoneyBalance) {
        WishlistBrowser.apply(items, filter, adultMoneyBalance)
    }
    val canAffordCount = remember(items, adultMoneyBalance) {
        WishlistBrowser.apply(items, WishlistFilter.CAN_AFFORD, adultMoneyBalance).size
    }
    val needMoreCount = remember(items, adultMoneyBalance) {
        WishlistBrowser.apply(items, WishlistFilter.NEED_MORE, adultMoneyBalance).size
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("wishlist_screen")
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = innerPadding.calculateTopPadding() + 42.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (viewMode == WishlistViewMode.GRID) {
                    IconButton(
                        onClick = { onViewModeChange(WishlistViewMode.CAROUSEL) },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Carousel",
                            tint = Color.White
                        )
                    }
                }
                
                Text(
                    text = "Wishlist",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FunkyEmptyState(
                        icon = Icons.Outlined.StarOutline,
                        headline = "Your wishlist is looking lonely",
                        subtext = "That special thing you've been eyeing? Add its name, price and photo link, and we'll track when your Adult Money can cover it.",
                        actionButtonText = "+ Add Wishlist Item",
                        onActionClick = onAddClick,
                        badgeText = "Blank Shelf",
                        accentColor = Color(0xFFFD79A8)
                    )
                }
            } else {
                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "view_mode_transition"
                ) { mode ->
                    if (mode == WishlistViewMode.GRID) {
                        WishlistGridView(
                            visibleItems = visibleItems,
                            filter = filter,
                            onFilterChange = onFilterChange,
                            adultMoneyBalance = adultMoneyBalance,
                            currency = currency,
                            gridState = gridState,
                            onOpenItem = onOpenItem,
                            totalCount = items.size,
                            canAffordCount = canAffordCount,
                            needMoreCount = needMoreCount
                        )
                    } else {
                        WishlistCarouselView(
                            items = affordableItems,
                            adultMoneyBalance = adultMoneyBalance,
                            currency = currency,
                            onOpenItem = onOpenItem,
                            onViewAll = { onViewModeChange(WishlistViewMode.GRID) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistGridView(
    visibleItems: List<WishlistItemEntity>,
    filter: WishlistFilter,
    onFilterChange: (WishlistFilter) -> Unit,
    adultMoneyBalance: Double,
    currency: String,
    gridState: LazyStaggeredGridState,
    onOpenItem: (WishlistItemEntity) -> Unit,
    totalCount: Int,
    canAffordCount: Int,
    needMoreCount: Int
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 160.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 16.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        fullWidthItem("spending_power") {
            WishlistSpendingPowerCard(
                adultMoneyBalance = adultMoneyBalance,
                currency = currency
            )
        }

        fullWidthItem("filters") {
            WishlistFilterRow(
                filter = filter,
                onFilterChange = onFilterChange,
                totalCount = totalCount,
                canAffordCount = canAffordCount,
                needMoreCount = needMoreCount
            )
        }

        if (visibleItems.isEmpty()) {
            fullWidthItem("filter_empty") {
                Text(
                    text = when (filter) {
                        WishlistFilter.CAN_AFFORD ->
                            "Nothing fits your Adult Money yet. Add to Adult Money from the Savings screen to unlock items."
                        WishlistFilter.NEED_MORE -> "Everything you want is within reach of your Adult Money"
                        else -> "No items to show."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        items(visibleItems, key = { it.id }) { item ->
            WishlistProductCard(
                item = item,
                onClick = { onOpenItem(item) }
            )
        }
    }
}

@Composable
private fun WishlistFilterRow(
    filter: WishlistFilter,
    onFilterChange: (WishlistFilter) -> Unit,
    totalCount: Int,
    canAffordCount: Int,
    needMoreCount: Int
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Filter: ${filter.label}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            IconButton(onClick = { showFilterMenu = true }) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Wishlist",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = showFilterMenu,
                onDismissRequest = { showFilterMenu = false }
            ) {
                WishlistFilter.entries.forEach { option ->
                    val label = when (option) {
                        WishlistFilter.ALL -> "${option.label} ($totalCount)"
                        WishlistFilter.CAN_AFFORD -> "${option.label} ($canAffordCount)"
                        WishlistFilter.NEED_MORE -> "${option.label} ($needMoreCount)"
                        else -> option.label
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onFilterChange(option)
                            showFilterMenu = false
                        },
                        modifier = Modifier.testTag("wishlist_filter_${option.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistCarouselView(
    items: List<WishlistItemEntity>,
    adultMoneyBalance: Double,
    currency: String,
    onOpenItem: (WishlistItemEntity) -> Unit,
    onViewAll: () -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Nothing matches your Adult Money yet",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Save more to unlock items on your wishlist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onViewAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD79A8)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("View All", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Large number for infinite-like scrolling start at a middle multiple
    val initialPage = items.size * 50
    val pagerState = rememberPagerState(initialPage = initialPage) { items.size * 100 }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Space reserved for header
        Spacer(modifier = Modifier.height(100.dp))
        
        // 1. Area for Cards (Centered between header and dock)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 92.dp),
                beyondViewportPageCount = 3,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val itemIndex = page % items.size
                val item = items[itemIndex]
                
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1.5f)

                Card(
                    onClick = { onOpenItem(item) },
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = lerp(0.dp, 16.dp, (1f - absOffset).coerceAtLeast(0f))),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .graphicsLayer {
                            val scale = lerp(0.68f, 1f, (1f - absOffset).coerceAtLeast(0f))
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(0.3f, 1f, (1f - absOffset).coerceAtLeast(0f))
                            translationY = absOffset * 60f
                            rotationZ = -pageOffset * 10f
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ProductImage(
                            imageUrl = item.imageUrl,
                            productName = item.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (item.isPurchased) 0.5f else 1f)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                        startY = 300f
                                    )
                                )
                        )
                        
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            if (WishlistPrice.isSet(item.estimatedCost)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = formatMoney(currency, item.estimatedCost),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    
                                    if (!item.isPurchased) {
                                        val affordability = WishlistAffordabilityCalculator.evaluate(item.estimatedCost, adultMoneyBalance)
                                        when (affordability) {
                                            is WishlistAffordability.CanAfford -> {
                                                Surface(
                                                    color = Color(0xFF55B894).copy(alpha = 0.2f),
                                                    shape = CircleShape,
                                                    border = BorderStroke(1.dp, Color(0xFF55B894).copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        text = "READY",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = Color(0xFF55B894),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            is WishlistAffordability.MoreNeeded -> {
                                                LinearProgressIndicator(
                                                    progress = { affordability.progress },
                                                    modifier = Modifier
                                                        .width(60.dp)
                                                        .height(6.6.dp)
                                                        .clip(CircleShape),
                                                    color = Color(0xFF55B894),
                                                    trackColor = Color.White.copy(alpha = 0.2f)
                                                )
                                            }
                                        }
                                    } else {
                                        PurchasedBadge()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Area for Button (Centered between cards and dock)
        Box(
            modifier = Modifier.weight(0.6f),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onViewAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD79A8)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp).padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
        
        // Bottom reservation
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun WishlistSpendingPowerCard(
    adultMoneyBalance: Double,
    currency: String,
    modifier: Modifier = Modifier,
    isHero: Boolean = false
) {
    if (isHero) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("wishlist_spending_power_hero"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CURRENT ADULT MONEY",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatMoney(currency, adultMoneyBalance.coerceAtLeast(0.0)),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )

                Text(
                    text = "Spendable Adult Money · Ready for items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = modifier
                .fillMaxWidth()
                .testTag("wishlist_spending_power")
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CreditCard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Current Adult Money",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Text(
                        text = formatMoney(currency, adultMoneyBalance.coerceAtLeast(0.0)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistProductCard(
    item: WishlistItemEntity,
    onClick: () -> Unit
) {
    // Pinterest-style variable height logic
    val randomRatio = remember(item.id) { Random(item.id).nextFloat() * 0.5f + 0.8f }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("wishlist_card_${item.id}")
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(randomRatio)
            ) {
                ProductImage(
                    imageUrl = item.imageUrl,
                    productName = item.title,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(if (item.isPurchased) 0.5f else 1f)
                )
                
                if (item.isPurchased) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        PurchasedBadge()
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.8f to Color.Black,
                                1.0f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
