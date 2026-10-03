package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.components.AppChrome
import com.example.ui.components.GradientTopBar
import com.example.ui.components.PhosphorIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ProfileScreen(
    currentUser: UserEntity?,
    transactions: List<TransactionItem>,
    onOpenAuthModal: () -> Unit,
    onRemoveAccount: () -> Unit,
    onUpdateProfile: (String, String, String?) -> Unit,
    onBackToHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    val onSurface = MaterialTheme.colorScheme.onSurface
    val userColor = remember(currentUser?.avatarColorHex, onSurface) {
        val hex = currentUser?.avatarColorHex ?: "#0C0F14"
        val isDarkAvatar = hex.lowercase() == "#0c0f14" || hex.lowercase() == "#242426"
        if (isDarkAvatar) {
            onSurface
        } else {
            try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                onSurface
            }
        }
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRemoveAccountDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .testTag("profile_screen")
        ) {
            Spacer(modifier = Modifier.height(AppChrome.topContentPadding))

            // Active Account Profile Section (Centered Layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Profile Pic
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(userColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUser?.avatarImagePath != null) {
                        AsyncImage(
                            model = "file:///android_asset/${currentUser.avatarImagePath}",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(text = currentUser?.avatarEmoji ?: "⚡", fontSize = 40.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Name
                Text(
                    text = currentUser?.displayName ?: "Guest User",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Username, Email
                Text(
                    text = "@${currentUser?.username ?: "anon"} • ${currentUser?.email ?: "no-email"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Action Buttons Row: "user +" icon button, switch icon button, edit icon button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "user +" icon button (New Account)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenAuthModal() }
                            .testTag("create_account_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.UserPlus,
                                contentDescription = "New Account",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Switch icon button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenAuthModal() }
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.ArrowsLeftRight,
                                contentDescription = "Switch Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Edit icon button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showEditProfileDialog = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.PencilSimple,
                                contentDescription = "Edit Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Remove icon button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showRemoveAccountDialog = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Bold.Trash,
                                contentDescription = "Remove Profile",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Spacer(modifier = Modifier.height(24.dp))

            // Monthly Expense Line Chart Card
            MonthlyExpenseChartCard(
                transactions = transactions,
                currency = currency
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Category Breakdown Section
            CategoryBreakdownCard(
                transactions = transactions,
                currency = currency
            )

            Spacer(modifier = Modifier.height(AppChrome.BottomContentPadding))
        }

        GradientTopBar(
            title = "Profile",
            navigationIcon = if (onBackToHome != null) {
                {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.ArrowLeft,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                null
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showEditProfileDialog && currentUser != null) {
        var nameInput by remember { mutableStateOf(currentUser.displayName) }
        var emailInput by remember { mutableStateOf(currentUser.email) }
        var selectedPfp by remember { mutableStateOf(currentUser.avatarImagePath) }

        val pfpImages = remember { (1..10).map { "pfp/pfp_$it.jpg" } }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Black, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // PFP Picker
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(userColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedPfp != null) {
                                AsyncImage(
                                    model = "file:///android_asset/$selectedPfp",
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(text = currentUser.avatarEmoji, fontSize = 32.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pfpImages) { path ->
                                val isSelected = selectedPfp == path
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { selectedPfp = path }
                                        .padding(if (isSelected) 2.dp else 0.dp)
                                ) {
                                    AsyncImage(
                                        model = "file:///android_asset/$path",
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF4B5563),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFF4F4F6)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color(0xFF4B5563),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFF4F4F6)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(nameInput, emailInput, selectedPfp)
                        showEditProfileDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showRemoveAccountDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveAccountDialog = false },
            title = { Text("Remove Account?", fontWeight = FontWeight.Black, color = Color.White) },
            text = {
                Text(
                    "This will permanently delete your account '@${currentUser?.username}' and ALL its transactions, wishlist items, and savings records. This cannot be undone.",
                    color = Color(0xFFF4F4F6)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveAccount()
                        showRemoveAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAccountDialog = false }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun MonthlyExpenseChartCard(
    transactions: List<TransactionItem>,
    currency: String
) {
    val monthData = remember(transactions) {
        val result = mutableListOf<Pair<String, Double>>()
        val expenseTxs = transactions.filter { it.type == TransactionType.EXPENSE }

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val monthNum = cal.get(Calendar.MONTH)
            val yearNum = cal.get(Calendar.YEAR)
            val monthLabel = SimpleDateFormat("MMM", Locale.US).format(cal.time)

            val monthExpense = expenseTxs.filter { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                txCal.get(Calendar.MONTH) == monthNum && txCal.get(Calendar.YEAR) == yearNum
            }.sumOf { it.amount }

            result.add(Pair(monthLabel, monthExpense))
        }
        result
    }

    val total6MonthExpense = remember(monthData) { monthData.sumOf { it.second } }
    val maxVal = remember(monthData) { monthData.maxOfOrNull { it.second }?.coerceAtLeast(100.0) ?: 100.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MONTHLY SPENDING TREND",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last 6 Months",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF6B6B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$currency${String.format(Locale.US, "%,.0f", total6MonthExpense)} total",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Line Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 32.dp.toPx()
                    val topPadding = 24.dp.toPx()
                    val chartHeight = height - bottomPadding - topPadding

                    val stepX = width / (monthData.size - 1).coerceAtLeast(1)

                    val points = monthData.mapIndexed { index, pair ->
                        val x = index * stepX
                        val yFraction = (pair.second / maxVal).toFloat().coerceIn(0f, 1f)
                        val y = height - bottomPadding - (yFraction * chartHeight)
                        Offset(x, y)
                    }

                    // Draw grid horizontal dashed line
                    val dashedStroke = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = androidx.compose.ui.geometry.Offset(0f, height - bottomPadding),
                        end = androidx.compose.ui.geometry.Offset(width, height - bottomPadding),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = androidx.compose.ui.geometry.Offset(0f, topPadding),
                        end = androidx.compose.ui.geometry.Offset(width, topPadding),
                        pathEffect = dashedStroke.pathEffect
                    )

                    if (points.isNotEmpty()) {
                        // Straight line path
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        // Fill path beneath line
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(points.last().x, height - bottomPadding)
                            lineTo(points.first().x, height - bottomPadding)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFFF6B6B).copy(alpha = 0.35f),
                                    Color(0xFFFF6B6B).copy(alpha = 0.0f)
                                ),
                                startY = topPadding,
                                endY = height - bottomPadding
                            )
                        )

                        // Draw main trend line
                        drawPath(
                            path = path,
                            color = Color(0xFFFF6B6B),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw point dots
                        points.forEach { point ->
                            drawCircle(
                                color = Color(0xFF0C0F14),
                                radius = 6.dp.toPx(),
                                center = point
                            )
                            drawCircle(
                                color = Color(0xFFFF6B6B),
                                radius = 4.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }

                // Month labels along the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    monthData.forEach { (monthLabel, amount) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
}

@Composable
private fun CategoryBreakdownCard(
    transactions: List<TransactionItem>,
    currency: String
) {
    val categoryExpenses = remember(transactions) {
        transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val totalCategoryExpense = remember(categoryExpenses) {
        categoryExpenses.sumOf { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
            Text(
                text = "EXPENSES BY CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (categoryExpenses.isEmpty()) {
                Text(
                    text = "No expenses recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    categoryExpenses.forEachIndexed { index, (categoryName, amount) ->
                        val catInfo = CategoryRegistry.getCategoryInfo(categoryName, TransactionType.EXPENSE)
                        val catColor = remember(catInfo.colorHex) {
                            try {
                                Color(android.graphics.Color.parseColor(catInfo.colorHex))
                            } catch (_: Exception) {
                                Color(0xFFFF6B6B)
                            }
                        }
                        val percentage = if (totalCategoryExpense > 0) (amount / totalCategoryExpense) * 100.0 else 0.0
                        val progressFraction = if (totalCategoryExpense > 0) (amount / totalCategoryExpense).toFloat() else 0f

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Category Icon Badge (Colored circle with icon)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(catColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catInfo.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Top Row: Category Name & Amount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = categoryName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$currency${String.format(Locale.US, "%,.0f", amount)}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Bottom Row: Colored Progress Bar + Percentage Text
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = progressFraction.coerceIn(0.02f, 1f))
                                                    .clip(CircleShape)
                                                    .background(catColor)
                                            )
                                        }

                                        Text(
                                            text = String.format(Locale.US, "%.2f%%", percentage),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (index < categoryExpenses.size - 1) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
