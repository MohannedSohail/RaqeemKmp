package org.mohtech.app.feature.dashboard.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mohtech.app.core.haptics.HapticKind
import org.mohtech.app.core.haptics.LocalRaqeemHaptics
import org.mohtech.app.core.theme.RaqeemColors
import org.mohtech.app.core.ui.RaqeemIcons
import org.mohtech.app.data.model.ExpenseCategory
import org.mohtech.app.feature.dashboard.ExpenseUiState
import org.mohtech.app.feature.dashboard.ui.WeeklySpendingChart
import org.mohtech.app.feature.reports.PdfReportWriter
import org.mohtech.app.feature.reports.ReportPeriod

/**
 * ReportsTab:
 * Analytics and Real Document Exporting (.xlsx, .pdf):
 * - Period selector chips
 * - Multi-currency isolated totals
 * - Canvas horizontal category distribution bars
 * - Weekly spending trend chart
 * - File generation (Excel & PDF) with background dispatching, FileProvider sharing & SAF save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab(
    state: ExpenseUiState,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onDismissExportResult: () -> Unit,
    onShareExportedFile: (filePath: String, mimeType: String) -> Unit,
    onSaveExportedFile: (sourcePath: String, defaultName: String, mimeType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalRaqeemHaptics.current
    val supportsPdf = remember { PdfReportWriter().supportsPdf }
    val report = state.reportData

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period Selector
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "الفترة الزمنية",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ReportPeriod.entries, key = { it.name }) { period ->
                        val isSelected = state.selectedReportPeriod == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RaqeemColors.SurfaceElevated else RaqeemColors.SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) RaqeemColors.EmeraldGreen else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    haptics.perform(HapticKind.TICK)
                                    onSelectPeriod(period)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = period.title,
                                color = if (isSelected) RaqeemColors.EmeraldGreen else RaqeemColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Summary Card with Multi-Currency Breakdown
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ملخص الإنفاق للفترة",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (report == null || report.isEmpty) {
                        Text(
                            text = "لا توجد حركات إنفاق خلال هذه الفترة",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 13.sp
                        )
                    } else {
                        report.totalsByCurrency.forEach { (curr, total) ->
                            val totalFormatted = if (total % 1.0 == 0.0) "${total.toInt()}" else "$total"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = curr,
                                    color = RaqeemColors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = totalFormatted,
                                        color = RaqeemColors.TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = curr,
                                        color = RaqeemColors.EmeraldGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Text(
                            text = "إجمالي العمليات: ${report.totalOperationsCount}",
                            color = RaqeemColors.TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Weekly Spending Chart
        item {
            WeeklySpendingChart(
                expenses = state.recentExpenses,
                weeklyBudget = state.weeklyBudget,
                currency = state.activeCurrency,
                weekStartDayIso = state.weekStartDay
            )
        }

        // Category Distribution Breakdown Bars
        if (report != null && !report.isEmpty) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RaqeemColors.SurfaceCard)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "توزيع التصنيفات",
                            color = RaqeemColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        report.categoryBreakdownByCurrency.forEach { (currency, summaries) ->
                            summaries.forEach { summary ->
                                val catColor = when (summary.category) {
                                    ExpenseCategory.GROCERIES -> RaqeemColors.CatGroceries
                                    ExpenseCategory.DINING -> RaqeemColors.CatDining
                                    ExpenseCategory.TRANSPORT -> RaqeemColors.CatTransport
                                    ExpenseCategory.BILLS -> RaqeemColors.CatBills
                                    ExpenseCategory.HEALTH -> RaqeemColors.CatHealth
                                    ExpenseCategory.GENERAL -> RaqeemColors.CatGeneral
                                }
                                val pctInt = (summary.percentage * 100).toInt()
                                val amtStr = if (summary.totalAmount % 1.0 == 0.0) "${summary.totalAmount.toInt()}" else "${summary.totalAmount}"

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(catColor))
                                            Text(
                                                text = summary.category.arabicTitle,
                                                color = RaqeemColors.TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Text(
                                            text = "$amtStr $currency ($pctInt%)",
                                            color = RaqeemColors.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    // Custom Canvas bar
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    ) {
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.06f),
                                            size = this.size,
                                            cornerRadius = CornerRadius(6f, 6f)
                                        )
                                        drawRoundRect(
                                            color = catColor,
                                            size = Size(this.size.width * summary.percentage.coerceIn(0.02f, 1f), this.size.height),
                                            cornerRadius = CornerRadius(6f, 6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Export Actions Section (Only Real Files: Excel & PDF)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaqeemColors.SurfaceCard)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "تصدير التقارير",
                        color = RaqeemColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "توليد ملفات رسمية متوافقة مع Excel ومستندات PDF، جاهزة للمشاركة والحفظ.",
                        color = RaqeemColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    if (state.isExportingReport) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RaqeemColors.SurfaceElevated)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = RaqeemColors.EmeraldGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "جارٍ إنشاء الملف محلياً...",
                                color = RaqeemColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        val isExportEnabled = report != null && !report.isEmpty

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Excel Export Button
                            Button(
                                onClick = {
                                    haptics.perform(HapticKind.TICK)
                                    onExportExcel()
                                },
                                enabled = isExportEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RaqeemColors.SurfaceElevated,
                                    contentColor = RaqeemColors.EmeraldGreen,
                                    disabledContainerColor = RaqeemColors.SurfaceElevated.copy(alpha = 0.5f),
                                    disabledContentColor = RaqeemColors.TextTertiary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RaqeemIcons.Export(size = 18.dp, tint = if (isExportEnabled) RaqeemColors.EmeraldGreen else RaqeemColors.TextTertiary)
                                    Text(
                                        text = "Excel (.xlsx)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // PDF Export Button (if supported)
                            if (supportsPdf) {
                                Button(
                                    onClick = {
                                        haptics.perform(HapticKind.TICK)
                                        onExportPdf()
                                    },
                                    enabled = isExportEnabled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RaqeemColors.SurfaceElevated,
                                        contentColor = RaqeemColors.ElectricCyan,
                                        disabledContainerColor = RaqeemColors.SurfaceElevated.copy(alpha = 0.5f),
                                        disabledContentColor = RaqeemColors.TextTertiary
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RaqeemIcons.Export(size = 18.dp, tint = if (isExportEnabled) RaqeemColors.ElectricCyan else RaqeemColors.TextTertiary)
                                        Text(
                                            text = "PDF (.pdf)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Success Sheet with Share & Save
    if (state.exportSuccessPath != null) {
        val path = state.exportSuccessPath
        val name = state.exportSuccessName ?: "raqeem_report.xlsx"
        val mime = state.exportMimeType ?: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        val sizeKb = (state.exportSuccessSize / 1024L).coerceAtLeast(1L)

        ModalBottomSheet(
            onDismissRequest = onDismissExportResult,
            containerColor = RaqeemColors.SurfaceCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(RaqeemColors.EmeraldGreen.copy(alpha = 0.12f))
                        .border(1.dp, RaqeemColors.EmeraldGreen.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    RaqeemIcons.Check(size = 24.dp, tint = RaqeemColors.EmeraldGreen)
                }

                Text(
                    text = "تم إنشاء التقرير بنجاح",
                    color = RaqeemColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RaqeemColors.SurfaceElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            color = RaqeemColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$sizeKb KB",
                            color = RaqeemColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.perform(HapticKind.CONFIRM)
                            onSaveExportedFile(path, name, mime)
                            onDismissExportResult()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = RaqeemColors.TextPrimary
                        )
                    ) {
                        Text(text = "حفظ في ملفاتي", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            haptics.perform(HapticKind.CONFIRM)
                            onShareExportedFile(path, mime)
                            onDismissExportResult()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RaqeemColors.EmeraldGreen,
                            contentColor = RaqeemColors.BackgroundSlate
                        )
                    ) {
                        Text(text = "مشاركة", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

