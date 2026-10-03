package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.export.ExportMonth
import com.example.ui.components.AppChrome
import com.example.ui.components.ChoicePill
import com.example.ui.components.GradientTopBar
import com.example.ui.components.PhosphorIcons
import kotlinx.coroutines.launch
import java.util.Calendar

/** What Android needs to be told so the saved file opens in a spreadsheet app. */
const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Picks the months to export and writes the workbook wherever the user chooses.
 *
 * An export is always scoped to months of one year: a whole-history export would only grow, and
 * the months on offer are the ones that actually hold records, so nothing empty can be picked.
 */
@Composable
fun ExportExcelScreen(
    months: List<ExportMonth>,
    fileNameFor: (Set<ExportMonth>) -> String,
    onBuildFile: suspend (Set<ExportMonth>) -> ByteArray?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val years = remember(months) { months.map { it.year }.distinct().sortedDescending() }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    // This year unless nothing was recorded in it, in which case the latest year that has records.
    var year by remember(years) { mutableStateOf(years.firstOrNull { it == thisYear } ?: years.firstOrNull()) }
    val monthsOfYear = remember(months, year) { months.filter { it.year == year } }

    var picked by remember { mutableStateOf(emptySet<ExportMonth>()) }
    var exporting by remember { mutableStateOf(false) }

    // Keeping a hidden selection from another year would quietly widen the export.
    LaunchedEffect(year) { picked = emptySet() }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(XLSX_MIME)
    ) { uri: Uri? ->
        if (uri == null) {
            exporting = false
            return@rememberLauncherForActivityResult
        }
        val chosen = picked
        scope.launch {
            val saved = try {
                val bytes = onBuildFile(chosen)
                bytes != null && context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
            } catch (e: Exception) {
                false
            }
            exporting = false
            Toast.makeText(
                context,
                if (saved) "Spreadsheet saved" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
            if (saved) onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .testTag("export_excel_screen")
        ) {
            Spacer(modifier = Modifier.height(AppChrome.topContentPadding))

            Text(
                text = "Transactions, budgets, savings, goals and money flow, each on its own sheet. " +
                    "The wishlist is included as links only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (years.isEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Nothing is recorded yet, so there is nothing to export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("export_nothing_recorded")
                )
            } else {
                Spacer(modifier = Modifier.height(28.dp))
                SectionCaption(text = "YEAR")
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("export_years")
                ) {
                    items(years, key = { it }) { candidate ->
                        ChoicePill(
                            label = candidate.toString(),
                            selected = candidate == year,
                            accent = Color.White,
                            onClick = { year = candidate },
                            modifier = Modifier.testTag("export_year_$candidate")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionCaption(
                        text = if (picked.isEmpty()) "MONTHS" else "MONTHS · ${picked.size} SELECTED"
                    )
                    Text(
                        text = if (picked.size == monthsOfYear.size) "Clear" else "Select all",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                picked = if (picked.size == monthsOfYear.size) {
                                    emptySet()
                                } else {
                                    monthsOfYear.toSet()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("export_toggle_all_months")
                    )
                }

                monthsOfYear.forEach { month ->
                    MonthRow(
                        month = month,
                        selected = month in picked,
                        onToggle = { picked = if (month in picked) picked - month else picked + month }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        exporting = true
                        saveLauncher.launch(fileNameFor(picked))
                    },
                    enabled = picked.isNotEmpty() && !exporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0C0F14)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_confirm_button")
                ) {
                    Text(
                        text = if (exporting) "Exporting…" else "Export spreadsheet",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        GradientTopBar(
            title = "Export as Excel",
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("export_back_button")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowLeft,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MonthRow(
    month: ExportMonth,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 14.dp)
            .testTag("export_month_${month.key}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = month.monthLabel,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = PhosphorIcons.Bold.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
