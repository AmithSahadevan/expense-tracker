package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.CurrencySymbols
import com.example.data.model.LinkAddPlan
import com.example.data.model.LinkAddPlanner
import com.example.data.model.ProductLookupFailure
import com.example.data.model.ProductLookupResult
import com.example.data.model.StoreNames
import com.example.data.model.WebLinks
import com.example.data.model.WishlistAffordability
import com.example.data.model.WishlistAffordabilityCalculator
import com.example.data.model.WishlistInputValidator
import com.example.data.model.WishlistItemInput
import com.example.data.model.WishlistPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

private enum class FormStep { PASTE_LINK, DETAILS }

private sealed interface LookupStatus {
    data object Idle : LookupStatus
    data object Loading : LookupStatus
    data class Done(val result: ProductLookupResult) : LookupStatus
}

/** A link-based add waiting for the user to confirm, because some details couldn't be read. */
private data class PendingLinkAdd(val plan: LinkAddPlan, val failure: ProductLookupResult.Failed?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistItemFormSheet(
    initialItem: WishlistItemEntity?,
    currency: String,
    adultMoneyBalance: Double,
    onLookupProduct: suspend (String) -> ProductLookupResult,
    onDismiss: () -> Unit,
    onSave: (WishlistItemInput) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("wishlist_form_sheet")
    ) {
        WishlistItemForm(
            initialItem = initialItem,
            currency = currency,
            adultMoneyBalance = adultMoneyBalance,
            onLookupProduct = onLookupProduct,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Add or edit a wishlist product. New items start from a pasted link: details are read from the page and
 * the product is added right away. If some details couldn't be read, the user confirms first and can add
 * them later, or reviews them in the full form. [adultMoneyBalance] drives the affordability preview;
 * the Emergency Fund is never used.
 */
@Composable
fun WishlistItemForm(
    initialItem: WishlistItemEntity?,
    currency: String,
    adultMoneyBalance: Double,
    onLookupProduct: suspend (String) -> ProductLookupResult,
    onDismiss: () -> Unit,
    onSave: (WishlistItemInput) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEdit = initialItem != null
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var step by remember { mutableStateOf(if (isEdit) FormStep.DETAILS else FormStep.PASTE_LINK) }
    var linkText by remember { mutableStateOf("") }
    var lookupStatus by remember { mutableStateOf<LookupStatus>(LookupStatus.Idle) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }

    var name by remember { mutableStateOf(initialItem?.title ?: "") }
    var priceText by remember { mutableStateOf(initialItem?.estimatedCost?.let(::plainAmount) ?: "") }
    var store by remember { mutableStateOf(initialItem?.store ?: "") }
    var productUrl by remember { mutableStateOf(initialItem?.url ?: "") }
    var imageUrl by remember { mutableStateOf(initialItem?.imageUrl ?: "") }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }
    var priority by remember { mutableStateOf(initialItem?.priority ?: WishlistPriority.MEDIUM) }
    var dateAdded by remember { mutableStateOf(initialItem?.dateAdded ?: System.currentTimeMillis()) }
    var targetDate by remember { mutableStateOf(initialItem?.targetPurchaseDate) }
    var showErrors by remember { mutableStateOf(false) }
    var pendingLinkAdd by remember { mutableStateOf<PendingLinkAdd?>(null) }

    /** Reads the link's page. With [addWhenDone] the product is added once read, asking first if details are missing. */
    fun startLookup(rawLink: String, addWhenDone: Boolean = false) {
        lookupJob?.cancel()
        val link = WebLinks.findInText(rawLink)
        if (link == null) {
            lookupStatus = LookupStatus.Done(ProductLookupResult.Failed(ProductLookupFailure.INVALID_LINK))
            return
        }
        lookupStatus = LookupStatus.Loading
        lookupJob = scope.launch {
            val result = try {
                onLookupProduct(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProductLookupResult.Failed(ProductLookupFailure.NO_PRODUCT_DETAILS, link)
            }
            if (result is ProductLookupResult.Found) {
                // Found values replace what's in the form; everything stays editable before saving.
                val product = result.product
                productUrl = result.url
                product.title?.let { name = it }
                product.price?.let { priceText = plainAmount(it) }
                product.imageUrl?.let { imageUrl = it }
                product.store?.let { store = it }
                if (!addWhenDone) step = FormStep.DETAILS
            }
            lookupStatus = LookupStatus.Done(result)
            if (addWhenDone) {
                when (result) {
                    is ProductLookupResult.Found -> {
                        val plan = LinkAddPlanner.fromLookup(result, currency)
                        if (plan.isComplete) onSave(plan.input) else pendingLinkAdd = PendingLinkAdd(plan, failure = null)
                    }
                    is ProductLookupResult.Failed ->
                        pendingLinkAdd = PendingLinkAdd(LinkAddPlanner.linkOnly(result.url ?: link), failure = result)
                }
            }
        }
    }

    fun cancelLookup() {
        lookupJob?.cancel()
        lookupStatus = LookupStatus.Idle
    }

    fun enterManually() {
        val link = (lookupStatus as? LookupStatus.Done)?.result?.url ?: WebLinks.findInText(linkText)
        if (link != null) {
            if (productUrl.isBlank()) productUrl = link
            if (store.isBlank()) StoreNames.fromUrl(link)?.let { store = it }
        }
        step = FormStep.DETAILS
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (step) {
            FormStep.PASTE_LINK -> PasteLinkStep(
                linkText = linkText,
                onLinkChange = {
                    linkText = it
                    if (lookupStatus is LookupStatus.Done) lookupStatus = LookupStatus.Idle
                },
                status = lookupStatus,
                onPaste = {
                    val pasted = clipboard.getText()?.text.orEmpty().trim()
                    if (pasted.isNotEmpty()) {
                        linkText = pasted
                        if (lookupStatus is LookupStatus.Done) lookupStatus = LookupStatus.Idle
                    }
                },
                onAdd = { startLookup(linkText, addWhenDone = true) },
                onCancel = ::cancelLookup,
                onEnterManually = ::enterManually,
                onDismiss = onDismiss
            )

            FormStep.DETAILS -> {
                val input = WishlistItemInput(
                    title = name,
                    price = priceText.toDoubleOrNull() ?: 0.0,
                    url = productUrl,
                    imageUrl = imageUrl,
                    store = store,
                    description = "",
                    dateAdded = dateAdded,
                    targetPurchaseDate = targetDate,
                    notes = notes,
                    priority = priority
                )
                val errors = WishlistInputValidator.validate(input)
                val foundResult = (lookupStatus as? LookupStatus.Done)?.result as? ProductLookupResult.Found

                FormHeader(
                    title = when {
                        isEdit -> "Edit Product"
                        foundResult != null -> "Review Product"
                        else -> "Add to Wishlist"
                    },
                    subtitle = when {
                        isEdit -> "Update the product details"
                        foundResult != null -> "Check the details we found before saving"
                        else -> "Enter the product details"
                    },
                    onDismiss = onDismiss
                )

                if (!isEdit) {
                    TextButton(
                        onClick = { step = FormStep.PASTE_LINK },
                        modifier = Modifier.testTag("wishlist_back_to_link")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use a product link")
                    }
                }

                when (val status = lookupStatus) {
                    LookupStatus.Loading -> LookupLoadingBanner(onCancel = ::cancelLookup)
                    is LookupStatus.Done -> when (val result = status.result) {
                        is ProductLookupResult.Found -> LookupFoundBanner(result = result, currency = currency)
                        is ProductLookupResult.Failed -> LookupFailureCard(
                            failure = result,
                            footer = "Fill in the details below instead.",
                            onRetry = null
                        )
                    }
                    LookupStatus.Idle -> Unit
                }

                // Image link with a live preview, so a broken link is obvious before saving.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductImage(
                        imageUrl = WebLinks.normalize(imageUrl).orEmpty(),
                        productName = name,
                        placeholderFontSize = 26,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    OutlinedTextField(
                        value = imageUrl,
                        onValueChange = { imageUrl = it },
                        label = { Text("Image URL") },
                        placeholder = { Text("https://…/photo.jpg") },
                        isError = showErrors && errors.imageUrl != null,
                        supportingText = { Text(if (showErrors && errors.imageUrl != null) errors.imageUrl else "Preview appears on the left") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wishlist_form_image_url")
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name *") },
                    placeholder = { Text("e.g. Sony WH-1000XM5") },
                    isError = showErrors && errors.title != null,
                    supportingText = if (showErrors && errors.title != null) ({ Text(errors.title) }) else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wishlist_form_name")
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() }
                        priceText = cleaned
                    },
                    label = { Text("Price") },
                    prefix = { Text("$currency ") },
                    isError = showErrors && errors.price != null,
                    supportingText = when {
                        showErrors && errors.price != null -> ({ Text(errors.price) })
                        priceText.isBlank() -> ({ Text("Optional. You can add it later.") })
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wishlist_form_price")
                )

                if (input.price > 0) {
                    AffordabilityPreview(price = input.price, adultMoneyBalance = adultMoneyBalance, currency = currency)
                }

                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("Store / merchant") },
                    placeholder = { Text("e.g. Amazon, Croma, Nike") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wishlist_form_store")
                )

                OutlinedTextField(
                    value = productUrl,
                    onValueChange = { productUrl = it },
                    label = { Text("Product URL") },
                    placeholder = { Text("https://…") },
                    isError = showErrors && errors.url != null,
                    supportingText = when {
                        showErrors && errors.url != null -> ({ Text(errors.url) })
                        productUrl.isNotBlank() -> ({ Text("Tap ↻ to fill in details from this link") })
                        else -> null
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { startLookup(productUrl) },
                            enabled = productUrl.isNotBlank() && lookupStatus != LookupStatus.Loading,
                            modifier = Modifier.testTag("wishlist_refetch")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Fill in details from this link")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wishlist_form_url")
                )

                Text(
                    text = "PRIORITY",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    WishlistPriority.all.forEach { option ->
                        ChoicePill(
                            label = WishlistPriority.label(option),
                            selected = priority == option,
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = { priority = option }
                        )
                    }
                }

                DatePickerField(
                    label = "Date added",
                    date = dateAdded,
                    onDateChange = { picked -> picked?.let { dateAdded = it } }
                )

                DatePickerField(
                    label = "Target purchase date (optional)",
                    date = targetDate,
                    onDateChange = { targetDate = it },
                    placeholder = "No target date",
                    clearable = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val clean = WishlistInputValidator.clean(input)
                        if (clean == null) showErrors = true else onSave(clean)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("wishlist_form_save")
                ) {
                    Text(
                        text = if (isEdit) "Save Changes" else "Add to Wishlist",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (showErrors && !errors.isValid) {
                    Text(
                        text = "Please fix the highlighted fields.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    pendingLinkAdd?.let { pending ->
        IncompleteDetailsDialog(
            pending = pending,
            onAddAnyway = {
                pendingLinkAdd = null
                onSave(pending.plan.input)
            },
            onReviewDetails = {
                pendingLinkAdd = null
                if (pending.failure == null) step = FormStep.DETAILS else enterManually()
            },
            onDismiss = { pendingLinkAdd = null }
        )
    }
}

@Composable
private fun IncompleteDetailsDialog(
    pending: PendingLinkAdd,
    onAddAnyway: () -> Unit,
    onReviewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val plan = pending.plan
    val (title, message) = if (pending.failure != null) {
        "Product details unavailable" to
            "${failureMessage(pending.failure)} You can still add this link to your wishlist and enter the " +
            "product details manually at any time."
    } else {
        val missing = plan.missingDetails
        val lines = buildList {
            if (missing.isNotEmpty()) {
                add("We couldn't retrieve the ${joinWithAnd(missing)} for this product from ${WebLinks.displayHost(plan.input.url)}.")
            }
            addAll(plan.notes)
            add(
                if (missing.isNotEmpty()) "You can add it to your wishlist now and fill in the missing details manually at any time."
                else "You can add it to your wishlist now and edit the details at any time."
            )
        }
        (if (missing.isNotEmpty()) "Some details are incomplete" else "Please review before adding") to lines.joinToString(" ")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onAddAnyway, modifier = Modifier.testTag("wishlist_add_anyway")) { Text("Add Anyway") }
        },
        dismissButton = {
            TextButton(onClick = onReviewDetails, modifier = Modifier.testTag("wishlist_review_details")) {
                Text("Review Details")
            }
        },
        modifier = Modifier.testTag("wishlist_incomplete_dialog")
    )
}

/** ["name", "price", "image"] -> "name, price and image" */
private fun joinWithAnd(items: List<String>): String =
    if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " and " + items.last()

@Composable
private fun ColumnScope.PasteLinkStep(
    linkText: String,
    onLinkChange: (String) -> Unit,
    status: LookupStatus,
    onPaste: () -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit
) {
    val loading = status is LookupStatus.Loading

    FormHeader(
        title = "Add to Wishlist",
        subtitle = "Paste a product link to add it to your wishlist",
        onDismiss = onDismiss
    )

    OutlinedTextField(
        value = linkText,
        onValueChange = onLinkChange,
        label = { Text("Product link") },
        placeholder = { Text("https://www.amazon.in/…") },
        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
        trailingIcon = {
            if (linkText.isEmpty()) {
                IconButton(onClick = onPaste, enabled = !loading, modifier = Modifier.testTag("wishlist_paste_link")) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste link")
                }
            } else {
                IconButton(onClick = { onLinkChange("") }, enabled = !loading) {
                    Icon(Icons.Default.Close, contentDescription = "Clear link")
                }
            }
        },
        enabled = !loading,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (linkText.isNotBlank() && !loading) onAdd() }),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_link_input")
    )

    Button(
        onClick = onAdd,
        enabled = linkText.isNotBlank() && !loading,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("wishlist_add_from_link")
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Reading product page…", fontWeight = FontWeight.Bold)
        } else {
            Text("Add to Wishlist", fontWeight = FontWeight.Bold)
        }
    }

    if (loading) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancel")
        }
    }

    val failure = (status as? LookupStatus.Done)?.result as? ProductLookupResult.Failed
    if (failure != null) {
        val retryable = failure.reason in setOf(
            ProductLookupFailure.NO_CONNECTION,
            ProductLookupFailure.TIMED_OUT,
            ProductLookupFailure.SITE_ERROR
        )
        LookupFailureCard(
            failure = failure,
            footer = "You can still add it by entering the details yourself.",
            onRetry = if (retryable) onAdd else null
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }

    OutlinedButton(
        onClick = onEnterManually,
        enabled = !loading,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("wishlist_enter_manually")
    ) {
        Text("Enter details manually", fontWeight = FontWeight.Bold)
    }

    Text(
        text = "The page is read directly from your phone, like opening it in a browser. No account or API key is used.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FormHeader(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LookupLoadingBanner(onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_lookup_loading")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Text(
                text = "Reading product page…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun LookupFoundBanner(result: ProductLookupResult.Found, currency: String) {
    val product = result.product
    val notes = buildList {
        val missing = product.missingFields
        if (missing.isNotEmpty()) {
            add("Couldn't find the ${missing.joinToString(" or ")}; please add ${if (missing.size == 1) "it" else "them"} below.")
        }
        if (product.price != null && !CurrencySymbols.matches(product.currencyCode, currency)) {
            add("The page lists the price in ${product.currencyCode}. Your wishlist uses $currency, so check the amount.")
        }
        if (!product.looksLikeProductPage) add("This may not be a product page, so double-check everything.")
        add("Review and edit anything before saving.")
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_lookup_found")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "✓ Details found on ${WebLinks.displayHost(result.url)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            notes.forEach { note ->
                Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun LookupFailureCard(failure: ProductLookupResult.Failed, footer: String, onRetry: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_lookup_error")
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = if (onRetry != null) 4.dp else 12.dp)) {
            Text(
                text = failureMessage(failure),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("wishlist_lookup_retry")) { Text("Try again") }
            }
        }
    }
}

private fun failureMessage(failure: ProductLookupResult.Failed): String = when (failure.reason) {
    ProductLookupFailure.INVALID_LINK -> "That doesn't look like a web link."
    ProductLookupFailure.NO_CONNECTION -> "Couldn't reach that website. Check the link and your internet connection."
    ProductLookupFailure.TIMED_OUT -> "The store took too long to respond."
    ProductLookupFailure.PAGE_NOT_FOUND -> "That page wasn't found. The product may have been removed."
    ProductLookupFailure.SITE_ERROR -> "The store's website had a problem" + (failure.httpStatus?.let { " (error $it)" } ?: "") + "."
    ProductLookupFailure.BLOCKED -> "This store doesn't let apps read its product pages."
    ProductLookupFailure.NOT_A_WEB_PAGE -> "That link doesn't open a web page."
    ProductLookupFailure.NO_PRODUCT_DETAILS -> "We couldn't find product details on that page."
}

@Composable
private fun AffordabilityPreview(price: Double, adultMoneyBalance: Double, currency: String) {
    val message = when (val result = WishlistAffordabilityCalculator.evaluate(price, adultMoneyBalance)) {
        is WishlistAffordability.CanAfford ->
            "✓ Can afford with Adult Money · ${formatMoney(currency, result.remainingAfterPurchase)} left after purchase"
        is WishlistAffordability.MoreNeeded ->
            "${formatMoney(currency, result.amountNeeded)} more needed (Adult Money: ${formatMoney(currency, adultMoneyBalance.coerceAtLeast(0.0))})"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** "28999" for whole amounts; never scientific notation. */
private fun plainAmount(value: Double): String =
    BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString()
