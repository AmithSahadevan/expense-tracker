package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.ui.components.FunkyEmptyState

@Composable
fun WishlistScreen(
    currentUser: UserEntity?,
    wishlistItems: List<WishlistItemEntity>,
    onAddWishlistItem: (title: String, cost: Double, priority: String, notes: String) -> Unit,
    onTogglePurchased: (WishlistItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "$"
    var showAddDialog by remember { mutableStateOf(false) }
    var itemTitle by remember { mutableStateOf("") }
    var itemCost by remember { mutableStateOf("") }
    var itemPriority by remember { mutableStateOf("HIGH") }
    var itemNotes by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
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
        modifier = modifier.testTag("wishlist_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
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

            Spacer(modifier = Modifier.height(18.dp))

            if (wishlistItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp, bottom = 140.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FunkyEmptyState(
                        emoji = "✨",
                        headline = "Your wishlist is looking lonely",
                        subtext = "That special thing you've been eyeing? Give it a home here until your wallet gives you the green light.",
                        actionButtonText = "+ Add Wishlist Item",
                        onActionClick = { showAddDialog = true },
                        badgeText = "Blank Shelf",
                        accentColor = Color(0xFFFD79A8)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(wishlistItems, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isPurchased)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (item.priority) {
                                                "MUST_HAVE" -> Color(0xFFFF6B6B).copy(alpha = 0.2f)
                                                "HIGH" -> Color(0xFFFD79A8).copy(alpha = 0.2f)
                                                else -> Color(0xFF6C5CE7).copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = item.priority.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = when (item.priority) {
                                                    "MUST_HAVE" -> Color(0xFFFF6B6B)
                                                    "HIGH" -> Color(0xFFFD79A8)
                                                    else -> Color(0xFF6C5CE7)
                                                }
                                            )
                                        }
                                    }

                                    if (item.notes.isNotBlank()) {
                                        Text(
                                            text = item.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$currency${String.format("%.2f", item.estimatedCost)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(onClick = { onTogglePurchased(item) }) {
                                        Icon(
                                            imageVector = if (item.isPurchased)
                                                Icons.Filled.Favorite
                                            else
                                                Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Toggle Purchased",
                                            tint = if (item.isPurchased) Color(0xFFFD79A8) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add to Wishlist",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = itemTitle,
                        onValueChange = { itemTitle = it },
                        label = { Text("What are you eyeing?") },
                        placeholder = { Text("e.g. Noise-cancelling headphones") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = itemCost,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) itemCost = it },
                        label = { Text("Estimated Cost ($currency)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = itemNotes,
                        onValueChange = { itemNotes = it },
                        label = { Text("Notes / Motivation") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cost = itemCost.toDoubleOrNull() ?: 0.0
                        if (itemTitle.isNotBlank() && cost > 0) {
                            onAddWishlistItem(itemTitle, cost, itemPriority, itemNotes)
                            showAddDialog = false
                            itemTitle = ""
                            itemCost = ""
                            itemNotes = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save to Wishlist")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}
