package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Tappable date row that opens a custom date picker matching the exact design. With [clearable], the date can be removed. */
@Composable
fun DatePickerField(
    label: String,
    date: Long?,
    onDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
    clearable: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { showPicker = true }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                Text(
                    text = date?.let { dateFormat.format(Date(it)) } ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (date != null) Color(0xFFF4F4F6) else Color(0xFF94A3B8)
                )
            }
            if (clearable && date != null) {
                IconButton(onClick = { onDateChange(null) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear $label",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        val reference = date ?: System.currentTimeMillis()
        AppDatePickerDialog(
            initialDateMillis = reference,
            onDateSelected = { pickedMillis ->
                onDateChange(keepTimeOfDay(pickedMillis, reference))
            },
            onDismissRequest = { showPicker = false }
        )
    }
}

@Composable
fun AppDatePickerDialog(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit
) {
    val todayCal = remember { Calendar.getInstance() }
    val selectedCal = remember(initialDateMillis) {
        Calendar.getInstance().apply { timeInMillis = initialDateMillis }
    }

    var selectedYear by remember { mutableIntStateOf(selectedCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(selectedCal.get(Calendar.MONTH)) }
    var selectedDay by remember { mutableIntStateOf(selectedCal.get(Calendar.DAY_OF_MONTH)) }

    var displayYear by remember { mutableIntStateOf(selectedYear) }
    var displayMonth by remember { mutableIntStateOf(selectedMonth) }

    var isYearView by remember { mutableStateOf(false) }

    val headerCal = remember(selectedYear, selectedMonth, selectedDay) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
        }
    }
    val dayOfWeekName = remember(headerCal.get(Calendar.DAY_OF_WEEK)) {
        SimpleDateFormat("EEE", Locale.US).format(headerCal.time)
    }
    val monthShortName = remember(selectedMonth) {
        SimpleDateFormat("MMM", Locale.US).format(headerCal.time)
    }
    val headerText = "$dayOfWeekName, $selectedDay $monthShortName, $selectedYear"

    val displayMonthCal = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val displayMonthName = remember(displayMonth, displayYear) {
        SimpleDateFormat("MMMM yyyy", Locale.US).format(displayMonthCal.time)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12141A)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Centered "Wed, 30 Sept, 2026"
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Subheader Row: "September 2026 ▾" + Chevrons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isYearView = !isYearView }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = displayMonthName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isYearView) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Toggle Year View",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (!isYearView) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (displayMonth == 0) {
                                            displayMonth = 11
                                            displayYear--
                                        } else {
                                            displayMonth--
                                        }
                                    }
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (displayMonth == 11) {
                                            displayMonth = 0
                                            displayYear++
                                        } else {
                                            displayMonth++
                                        }
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF2C2F36), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                if (isYearView) {
                    val years = remember(displayYear) {
                        val startYear = displayYear - 7
                        (startYear..(startYear + 14)).toList()
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        years.chunked(3).forEach { rowYears ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowYears.forEach { year ->
                                    val isSelected = year == selectedYear
                                    Box(
                                        modifier = Modifier
                                            .width(84.dp)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .clickable {
                                                displayYear = year
                                                selectedYear = year
                                                isYearView = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$year",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                            ),
                                            color = if (isSelected) Color(0xFF0C0F14) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            weekdays.forEach { dayLetter ->
                                Text(
                                    text = dayLetter,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }

                        val firstDayOfWeek = (displayMonthCal.get(Calendar.DAY_OF_WEEK) - 1) % 7
                        val maxDaysInMonth = displayMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val totalCells = firstDayOfWeek + maxDaysInMonth

                        val rows = (totalCells + 6) / 7
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (rowIndex in 0 until rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (colIndex in 0 until 7) {
                                        val cellIndex = rowIndex * 7 + colIndex
                                        if (cellIndex < firstDayOfWeek || cellIndex >= totalCells) {
                                            Spacer(modifier = Modifier.size(38.dp))
                                        } else {
                                            val dayNum = cellIndex - firstDayOfWeek + 1
                                            val isSelected = (dayNum == selectedDay && displayMonth == selectedMonth && displayYear == selectedYear)
                                            val isToday = (dayNum == todayCal.get(Calendar.DAY_OF_MONTH) &&
                                                    displayMonth == todayCal.get(Calendar.MONTH) &&
                                                    displayYear == todayCal.get(Calendar.YEAR))

                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else Color.Transparent)
                                                    .then(
                                                        if (isToday && !isSelected)
                                                            Modifier.border(1.5.dp, Color.White, CircleShape)
                                                        else Modifier
                                                    )
                                                    .clickable {
                                                        selectedDay = dayNum
                                                        selectedMonth = displayMonth
                                                        selectedYear = displayYear
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (isSelected) Color(0xFF0C0F14) else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2C2F36), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            val resultCal = Calendar.getInstance().apply {
                                timeInMillis = initialDateMillis
                                set(Calendar.YEAR, selectedYear)
                                set(Calendar.MONTH, selectedMonth)
                                set(Calendar.DAY_OF_MONTH, selectedDay)
                            }
                            onDateSelected(resultCal.timeInMillis)
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            text = "Confirm",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun keepTimeOfDay(pickedUtcMidnight: Long, previous: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMidnight }
    return Calendar.getInstance().apply {
        timeInMillis = previous
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
