package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.ExpenseNotificationListenerService
import android.provider.Settings
import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.preferences.TransactionDetectionPreferences
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.data.model.Currencies
import com.example.data.model.CurrencyInfo
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PunchyCoral
import com.example.ui.theme.SunnyYellow
import com.example.data.local.entities.UserEntity
import java.util.Locale

@Composable
fun SettingsScreen(
    currentUser: UserEntity?,
    onOpenAuthModal: () -> Unit,
    onUpdateSalaryAndPayday: (Double, Int, Boolean) -> Unit,
    onUpdateCurrency: (String) -> Unit,
    onExportData: suspend () -> String?,
    onImportData: suspend (String) -> Boolean,
    onRemoveAccount: () -> Unit,
    onUpdateProfile: (String, String, String?) -> Unit,
    onClearData: () -> Unit,
    onBackToHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    var showSalaryDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showRemoveAccountDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var isAutoDetectionEnabled by remember {
        mutableStateOf(TransactionDetectionPreferences.isAutoDetectionEnabled(context))
    }
    var hasNotificationAccess by remember {
        mutableStateOf(ExpenseNotificationListenerService.isNotificationAccessGranted(context))
    }
    var canPostNotifications by remember { mutableStateOf(areAppNotificationsAllowed(context)) }
    // Android only shows the permission dialog once; after that the request returns denied with no
    // dialog, so a second tap has to take the user to Settings instead.
    var postNotificationsRequested by remember { mutableStateOf(false) }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPostNotifications = areAppNotificationsAllowed(context)
        if (!granted) {
            Toast.makeText(
                context,
                "Notifications are off. Tap again to open Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Granting notification access happens in Android's own settings screen, so the result is only
    // visible once we come back. Re-checking on resume also gives us a reliable moment to revive a
    // listener that Android unbound while the app was in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess =
                    ExpenseNotificationListenerService.isNotificationAccessGranted(context)
                canPostNotifications = areAppNotificationsAllowed(context)
                if (hasNotificationAccess &&
                    TransactionDetectionPreferences.isAutoDetectionEnabled(context)
                ) {
                    ExpenseNotificationListenerService.ensureListenerConnected(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currency = currentUser?.currencySymbol ?: "₹"

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val json = onExportData()
                    if (json != null) {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            OutputStreamWriter(stream).use { writer ->
                                writer.write(json)
                            }
                        }
                        Toast.makeText(context, "Data exported successfully!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val json = InputStreamReader(stream).readText()
                        val success = onImportData(json)
                        if (success) {
                            Toast.makeText(context, "Data imported successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Import failed. Invalid file.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("settings_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackToHome != null) {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Text(
                text = "Settings & Profiles",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Account Profile Card (Flattened)
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
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
                                Text(text = currentUser?.avatarEmoji ?: "⚡", fontSize = 24.sp)
                            }
                        }

                        Column {
                            Text(
                                text = currentUser?.displayName ?: "Guest",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "@${currentUser?.username ?: "anon"} • ${currentUser?.email ?: "no-email"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenAuthModal,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch Profile",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // App Preferences (Flattened)
        Text(
            text = "APP PREFERENCES",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Currency", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        val currentInfo = Currencies.all.find { it.symbol == currency }
                        val displayText = if (currentInfo != null) "${currentInfo.name} (${currentInfo.symbol})" else currency
                        Text(
                            text = "Active: $displayText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showCurrencyDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "Change", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Automatic Transaction Detection
        Text(
            text = "AUTOMATIC TRANSACTION DETECTION",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Automatic Transaction Detection",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Parse bank & payment app notifications locally to record expenses and incomes automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isAutoDetectionEnabled,
                        onCheckedChange = { enabled ->
                            isAutoDetectionEnabled = enabled
                            TransactionDetectionPreferences.setAutoDetectionEnabled(context, enabled)
                            if (enabled) {
                                hasNotificationAccess =
                                    ExpenseNotificationListenerService.isNotificationAccessGranted(context)
                                if (hasNotificationAccess) {
                                    ExpenseNotificationListenerService.ensureListenerConnected(context)
                                } else {
                                    try {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Please open Settings and grant Notification Access to Expense Tracker", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                if (isAutoDetectionEnabled) {
                    if (!hasNotificationAccess) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Notification Access Required",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = "Android requires notification access permission to read bank SMS and payment app alerts. Tap below to enable access in Settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Please grant Notification Access in Android Settings", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Grant Notification Access", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MintGreen)
                            )
                            Text(
                                text = "Active • Listening for financial transaction notifications",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MintGreen
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Permission for the confirmation shown after a transaction is recorded.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            when {
                                canPostNotifications -> openAppNotificationSettings(context)
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !postNotificationsRequested -> {
                                    postNotificationsRequested = true
                                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                else -> openAppNotificationSettings(context)
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (canPostNotifications) MintGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Transaction Tracked Alerts",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (canPostNotifications) {
                                    "You'll get a notification each time a transaction is recorded."
                                } else {
                                    "Tap to allow notifications so the app can confirm each recorded transaction."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (canPostNotifications) MintGreen else MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = if (canPostNotifications) "Allowed" else "Not allowed",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (canPostNotifications) MintGreen else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Monthly Salary & Payday (Flattened)
        Text(
            text = "MONTHLY SALARY & PAYDAY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("salary_payday_card")
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentSalary = currentUser?.monthlySalary ?: 0.0
                val payday = currentUser?.paydayDayOfMonth ?: 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = if (currentSalary > 0)
                                "$currency${String.format(Locale.US, "%,.0f", currentSalary)}"
                            else
                                "Not set yet",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Payday: Day $payday of each month",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showSalaryDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("set_salary_button")
                    ) {
                        Text(
                            text = if (currentSalary > 0) "Edit Salary" else "Set Salary",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Data Management (Flattened)
        Text(
            text = "DATA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Backup & Import", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val fileName = "expense_tracker_backup_${System.currentTimeMillis()}.json"
                            exportLauncher.launch(fileName)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Button(
                        onClick = { importLauncher.launch("application/json") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                
                Button(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Account Lifecycle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Create New Account
            Button(
                onClick = onOpenAuthModal,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_account_button")
            ) {
                Icon(imageVector = Icons.Default.People, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Account", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }

            // Remove Account Section
            Button(
                onClick = { showRemoveAccountDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove Profile", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
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

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?", fontWeight = FontWeight.Black, color = Color.White) },
            text = {
                Text("This will permanently delete ALL transactions, wishlist items, and savings records from this account. Your profile and account settings will remain. This cannot be undone.", color = Color(0xFFF4F4F6))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Clear Everything", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
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
                Text("This will permanently delete your account '@${currentUser?.username}' and ALL its transactions, wishlist items, and savings records. This cannot be undone.", color = Color(0xFFF4F4F6))
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
                    Text("Delete Permanently", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAccountDialog = false }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Currency Change Dialog (Searchable List)
    if (showCurrencyDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var tempSelectedCurrency by remember { 
            mutableStateOf(Currencies.all.find { it.symbol == currency } ?: Currencies.all.first()) 
        }
        
        val filteredCurrencies = remember(searchQuery) {
            if (searchQuery.isBlank()) Currencies.all
            else Currencies.all.filter { 
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.code.contains(searchQuery, ignoreCase = true) ||
                it.symbol.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency", fontWeight = FontWeight.Black, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search country or code...", color = Color(0xFF94A3B8)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCurrencies) { item ->
                            val isSelected = tempSelectedCurrency == item
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .clickable {
                                        tempSelectedCurrency = item
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(text = item.flag, fontSize = 20.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) Color(0xFF0C0F14) else Color.White
                                        )
                                        Text(
                                            text = item.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) Color(0xFF0C0F14).copy(alpha = 0.8f) else Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = item.symbol,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        color = if (isSelected) Color(0xFF0C0F14) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCurrency(tempSelectedCurrency.symbol)
                        showCurrencyDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Choose", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Salary & Payday Edit Dialog
    if (showSalaryDialog) {
        var salaryInput by remember {
            mutableStateOf(
                if ((currentUser?.monthlySalary ?: 0.0) > 0.0)
                    String.format(Locale.US, "%.0f", currentUser!!.monthlySalary)
                else
                    ""
            )
        }
        var paydayInput by remember {
            mutableIntStateOf(currentUser?.paydayDayOfMonth ?: 1)
        }
        var logDepositThisMonth by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSalaryDialog = false },
            title = {
                Text(
                    text = "Monthly Salary & Payday",
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Set your expected monthly salary and the day of the month you get paid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )

                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = {
                            if (it.all { c -> c.isDigit() }) {
                                salaryInput = it
                                inputError = null
                            }
                        },
                        label = { Text("Monthly Salary Amount", color = Color(0xFF94A3B8)) },
                        prefix = { Text("$currency ", color = Color.White) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
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

                    Column {
                        Text(
                            text = "Payday (Day of Month: $paydayInput)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1, 5, 15, 25, 28, 30).forEach { day ->
                                val isSelected = paydayInput == day
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color.White else Color(0xFF2C2F36),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { paydayInput = day }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$day",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color(0xFF0C0F14) else Color(0xFFF4F4F6)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { logDepositThisMonth = !logDepositThisMonth }
                    ) {
                        Checkbox(
                            checked = logDepositThisMonth,
                            onCheckedChange = { logDepositThisMonth = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Record salary deposit for this month in ledger now",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF4F4F6)
                        )
                    }

                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sal = salaryInput.toDoubleOrNull() ?: 0.0
                        if (sal < 0.0) {
                            inputError = "Salary cannot be negative"
                            return@Button
                        }
                        onUpdateSalaryAndPayday(sal, paydayInput, logDepositThisMonth)
                        showSalaryDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF0C0F14))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSalaryDialog = false }) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** True when the app may post notifications: the runtime permission on Android 13+, and the
 *  app-level notification switch on every version. */
private fun areAppNotificationsAllowed(context: Context): Boolean {
    return try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (e: Exception) {
        false
    }
}

/** Opens this app's notification settings, falling back to its app info page. */
private fun openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
            return
        } catch (e: Exception) {
            // Fall through to the app info page below.
        }
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Open Settings > Apps > Expense Tracker > Notifications to allow notifications",
            Toast.LENGTH_LONG
        ).show()
    }
}
