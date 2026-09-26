package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.components.formatMoney
import com.example.ui.components.toColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("MMMM d, yyyy • h:mm a", Locale.getDefault())
}

@Composable
fun TransactionDetailScreen(
    item: TransactionItem,
    currency: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val catInfo = CategoryRegistry.getCategoryInfo(item.category, item.type)
    val formattedDate = remember(item.date) {
        detailDateFormatThreadLocal.get()?.format(Date(item.date)) ?: ""
    }

    val transactionKey = remember(item.id, item.type) {
        TransactionAttachmentManager.getTransactionKey(item.type, item.id)
    }

    var attachments by remember(transactionKey) {
        mutableStateOf<List<TransactionAttachment>>(emptyList())
    }

    LaunchedEffect(transactionKey) {
        val loaded = withContext(Dispatchers.IO) {
            TransactionAttachmentManager.loadAttachments(context, transactionKey)
        }
        attachments = loaded
    }

    var selectedAttachmentIndex by remember { mutableStateOf<Int?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // System Chooser Launcher (File Picker + Camera)
    val systemChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val urisToProcess = mutableListOf<Uri>()

            if (data?.clipData != null) {
                val clip = data.clipData!!
                for (i in 0 until clip.itemCount) {
                    urisToProcess.add(clip.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                urisToProcess.add(data.data!!)
            } else {
                tempCameraUri?.let { uri ->
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            if (stream.available() > 0) {
                                urisToProcess.add(uri)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (urisToProcess.isNotEmpty()) {
                val maxAllowed = 5 - attachments.size
                val selected = urisToProcess.take(maxAllowed)
                var addedCount = 0
                for (u in selected) {
                    val att = TransactionAttachmentManager.addAttachmentFromUri(context, transactionKey, u)
                    if (att != null) addedCount++
                }
                if (addedCount > 0) {
                    attachments = TransactionAttachmentManager.loadAttachments(context, transactionKey)
                    Toast.makeText(context, "Added $addedCount attachment(s)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        tempCameraUri?.let { uri ->
            val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            val chooserIntent = Intent.createChooser(getContentIntent, "Add Attachment").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
            }
            systemChooserLauncher.launch(chooserIntent)
        }
    }

    fun handleAddAttachment() {
        if (attachments.size >= 5) {
            Toast.makeText(context, "Maximum 5 attachments allowed", Toast.LENGTH_SHORT).show()
            return
        }

        val tempUri = TransactionAttachmentManager.createTempCameraUri(context)
        tempCameraUri = tempUri

        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, tempUri)
        }

        val chooserIntent = Intent.createChooser(getContentIntent, "Add Attachment").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        }

        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            systemChooserLauncher.launch(chooserIntent)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Transaction Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        modifier = modifier.fillMaxSize().graphicsLayer()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(catInfo.colorHex.toColor()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catInfo.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Category
            Text(
                text = item.category,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Amount
            Text(
                text = "${if (item.type == TransactionType.EXPENSE) "-" else ""}${formatMoney(currency, item.amount)}",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(24.dp))

            // Info Rows
            DetailRow(label = "Date", value = formattedDate)
            DetailRow(label = "Payment Method", value = item.paymentMethod.lowercase().replaceFirstChar { it.uppercase() })
            if (item.recurrence != "NONE") {
                DetailRow(label = "Repeat", value = item.recurrence.lowercase().replaceFirstChar { it.uppercase() })
            }
            if (item.notes.isNotBlank()) {
                DetailRow(label = "Notes", value = item.notes)
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(24.dp))

            // ATTACHMENTS SECTION
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Receipts & Attachments",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Count Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (attachments.size >= 5) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${attachments.size}/5",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (attachments.size >= 5) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Attachments Deck + Dashed "Add More" Card
                val cardSize = 104.dp
                val overlapOffset = 32.dp
                val scrollStateDeck = rememberScrollState()

                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollStateDeck)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (attachments.isNotEmpty()) {
                        val totalDeckWidth = cardSize + (overlapOffset * (attachments.size - 1))
                        Box(
                            modifier = Modifier
                                .width(totalDeckWidth)
                                .height(cardSize)
                        ) {
                            attachments.forEachIndexed { index, att ->
                                val startOffset = overlapOffset * index
                                Box(
                                    modifier = Modifier
                                        .offset(x = startOffset)
                                        .size(cardSize)
                                ) {
                                    AttachmentDeckCard(
                                        attachment = att,
                                        onClick = { selectedAttachmentIndex = index }
                                    )
                                }
                            }
                        }

                        if (attachments.size < 5) {
                            Spacer(modifier = Modifier.width(14.dp))
                            AddMoreCard(
                                cardSize = cardSize,
                                onClick = { handleAddAttachment() }
                            )
                        }
                    } else {
                        // Empty state: single Add More card
                        AddMoreCard(
                            cardSize = cardSize,
                            onClick = { handleAddAttachment() }
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                }

                if (attachments.size >= 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Maximum limit of 5 attachments reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons (Delete & Edit Transaction)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Edit", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Attachment Viewer Dialog
    selectedAttachmentIndex?.let { initialIndex ->
        if (attachments.isNotEmpty()) {
            AttachmentViewerDialog(
                attachments = attachments,
                initialIndex = initialIndex,
                onDismiss = { selectedAttachmentIndex = null },
                onDelete = { attachmentId ->
                    attachments = TransactionAttachmentManager.deleteAttachment(
                        context,
                        transactionKey,
                        attachmentId
                    )
                    Toast.makeText(context, "Attachment removed", Toast.LENGTH_SHORT).show()
                    if (attachments.isEmpty()) {
                        selectedAttachmentIndex = null
                    }
                }
            )
        } else {
            selectedAttachmentIndex = null
        }
    }
}

@Composable
private fun AddMoreCard(
    cardSize: Dp,
    onClick: () -> Unit
) {
    DashedBorderBox(
        modifier = Modifier
            .size(cardSize)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        cornerRadius = 16.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add More",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun DashedBorderBox(
    modifier: Modifier = Modifier,
    borderColor: Color,
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 1.5.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val dashLengthPx = with(density) { dashLength.toPx() }
    val gapLengthPx = with(density) { gapLength.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val stroke = remember(strokeWidthPx, dashLengthPx, gapLengthPx) {
        Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx), 0f)
        )
    }

    Box(
        modifier = modifier
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = stroke
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun AttachmentDeckCard(
    attachment: TransactionAttachment,
    onClick: () -> Unit
) {
    val file = remember(attachment.filePath) { File(attachment.filePath) }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (attachment.isPdf) {
                var pdfBitmap by remember(attachment.filePath) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(attachment.filePath) {
                    pdfBitmap = withContext(Dispatchers.IO) {
                        TransactionAttachmentManager.renderPdfFirstPage(file)
                    }
                }
                val currentPdfBitmap = pdfBitmap
                if (currentPdfBitmap != null) {
                    Image(
                        bitmap = currentPdfBitmap.asImageBitmap(),
                        contentDescription = "PDF Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = file,
                    contentDescription = "Image preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Expand Icon badge on top right
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentViewerDialog(
    attachments: List<TransactionAttachment>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (attachmentId: String) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val safeIndex = currentIndex.coerceIn(0, (attachments.size - 1).coerceAtLeast(0))
    if (attachments.isEmpty()) {
        onDismiss()
        return
    }

    val currentAttachment = attachments[safeIndex]
    val file = remember(currentAttachment.filePath) { File(currentAttachment.filePath) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with counter, title, Delete button, and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${safeIndex + 1} of ${attachments.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentAttachment.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Attachment",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Middle area with Left arrow, content view, and Right arrow
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Arrow Button
                    FilledTonalIconButton(
                        onClick = {
                            if (safeIndex > 0) currentIndex = safeIndex - 1
                        },
                        enabled = safeIndex > 0,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous File"
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Content View (Image or PDF)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentAttachment.isPdf) {
                            var pdfBitmap by remember(currentAttachment.filePath) { mutableStateOf<Bitmap?>(null) }
                            LaunchedEffect(currentAttachment.filePath) {
                                pdfBitmap = withContext(Dispatchers.IO) {
                                    TransactionAttachmentManager.renderPdfFirstPage(file)
                                }
                            }
                            val currentPdfBitmap = pdfBitmap
                            if (currentPdfBitmap != null) {
                                Image(
                                    bitmap = currentPdfBitmap.asImageBitmap(),
                                    contentDescription = "PDF page preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("PDF Document", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        } else {
                            AsyncImage(
                                model = file,
                                contentDescription = currentAttachment.fileName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right Arrow Button
                    FilledTonalIconButton(
                        onClick = {
                            if (safeIndex < attachments.size - 1) currentIndex = safeIndex + 1
                        },
                        enabled = safeIndex < attachments.size - 1,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next File"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PDF Action Button if viewing PDF
                if (currentAttachment.isPdf) {
                    Button(
                        onClick = {
                            TransactionAttachmentManager.openPdfWithExternalApp(context, file)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open with PDF Viewer", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Close Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Attachment?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${currentAttachment.fileName}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        val idToDelete = currentAttachment.id
                        if (attachments.size <= 1) {
                            onDismiss()
                        } else if (safeIndex >= attachments.size - 1) {
                            currentIndex = (safeIndex - 1).coerceAtLeast(0)
                        }
                        onDelete(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            textAlign = TextAlign.End
        )
    }
}
