package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.AffordabilityChip
import com.example.ui.components.ChoicePill
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.PriorityBadge
import com.example.ui.components.ProductImage
import com.example.ui.components.PurchasedBadge
import com.example.ui.components.WishlistItemFormSheet
import com.example.ui.components.formatMoney

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
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var filter by rememberSaveable { mutableStateOf(WishlistFilter.ALL) }
    var selectedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WishlistItemEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<WishlistItemEntity?>(null) }
    // Hoisted so the list keeps its scroll position after returning from a product's details.
    val gridState = rememberLazyGridState()

    val selectedItem = wishlistItems.find { it.id == selectedItemId }
    BackHandler(enabled = selectedItem != null) { selectedItemId = null }

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
                filter = filter,
                onFilterChange = { filter = it },
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
            text = { Text("\"${item.title}\" (${formatMoney(currency, item.estimatedCost)}) will be deleted. This can't be undone.") },
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

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun WishlistBrowse(
    items: List<WishlistItemEntity>,
    filter: WishlistFilter,
    onFilterChange: (WishlistFilter) -> Unit,
    adultMoneyBalance: Double,
    currency: String,
    gridState: LazyGridState,
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color(0xFFFD79A8),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp, end = 4.dp)
                    .testTag("fab_add_wishlist")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Wishlist Item")
            }
        },
        modifier = Modifier.testTag("wishlist_screen")
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = 160.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            fullWidthItem("header") {
                Column {
                    Text(
                        text = "Wishlist Vault",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Dream big, buy intentionally",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            fullWidthItem("spending_power") {
                WishlistSpendingPowerCard(
                    adultMoneyBalance = adultMoneyBalance,
                    currency = currency
                )
            }

            if (items.isEmpty()) {
                fullWidthItem("empty") {
                    Box(modifier = Modifier.padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
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
                }
            } else {
                fullWidthItem("filters") {
                    var showFilterMenu by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                        WishlistFilter.ALL -> "${option.label} (${items.size})"
                                        WishlistFilter.CAN_AFFORD -> "${option.label} ($canAffordCount)"
                                        WishlistFilter.NEED_MORE -> "${option.label} ($needMoreCount)"
                                        else -> option.label
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onFilterChange(option)
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
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
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                items(visibleItems, key = { it.id }) { item ->
                    WishlistProductCard(
                        item = item,
                        adultMoneyBalance = adultMoneyBalance,
                        currency = currency,
                        onClick = { onOpenItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistSpendingPowerCard(
    adultMoneyBalance: Double,
    currency: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
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

@Composable
private fun WishlistProductCard(
    item: WishlistItemEntity,
    adultMoneyBalance: Double,
    currency: String,
    onClick: () -> Unit
) {
    val affordability = WishlistAffordabilityCalculator.evaluate(item.estimatedCost, adultMoneyBalance)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_card_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                ProductImage(
                    imageUrl = item.imageUrl,
                    productName = item.title,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(if (item.isPurchased) 0.5f else 1f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
                PriorityBadge(
                    priority = item.priority,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.store.ifBlank { "Store not added" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatMoney(currency, item.estimatedCost),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Fixed minimum height keeps cards in the same row aligned.
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .heightIn(min = 58.dp)
                        .testTag("wishlist_status_${item.id}")
                ) {
                    when {
                        item.isPurchased -> PurchasedBadge()
                        affordability is WishlistAffordability.CanAfford -> {
                            AffordabilityChip(result = affordability, currency = currency)
                            Text(
                                text = "${formatMoney(currency, affordability.remainingAfterPurchase)} left after purchase",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        affordability is WishlistAffordability.MoreNeeded -> {
                            Text(
                                text = "${formatMoney(currency, affordability.amountNeeded)} more needed",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                            LinearProgressIndicator(
                                progress = { affordability.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = Color.White,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
