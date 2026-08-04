package com.example.methreceiver.ui.sensit

import com.example.methreceiver.*
import com.example.methreceiver.model.SensITSample
import com.example.methreceiver.ui.common.*

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SensITChartLine(
    val label: String,
    val color: Color,
    val points: List<Pair<Long, Double>>
)

@Composable
fun SensITConcentrationTrendPanel(
    samples: List<SensITSample>,
    selectedWindow: TrendWindow,
    onWindowSelected: (TrendWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val start = now - selectedWindow.durationMillis

    val visible = samples.filter {
        it.timeMillis >= start
    }

    val lines = listOf(
        SensITChartLine(
            "Calculated",
            Green,
            visible.mapNotNull {
                it.concentration?.let { value ->
                    it.timeMillis to value
                }
            }
        ),

        SensITChartLine(
            "Raw",
            Orange,
            visible.mapNotNull {
                it.concentrationRaw?.let { value ->
                    it.timeMillis to value
                }
            }
        ),

        SensITChartLine(
            "Calibrated",
            Blue,
            visible.mapNotNull {
                it.concentrationCal?.let { value ->
                    it.timeMillis to value
                }
            }
        ),

        SensITChartLine(
            "Temp. compensated",
            Purple,
            visible.mapNotNull {
                it.concentrationTemp?.let { value ->
                    it.timeMillis to value
                }
            }
        )
    )

    DashboardPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PanelTitle(
                    "CONCENTRATION CHANNELS (ppm)",
                    Icons.Outlined.ShowChart
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    TrendWindow.values().forEach { window ->
                        TinyTimeChip(
                            text = window.label,
                            active = selectedWindow == window,
                            onClick = {
                                onWindowSelected(window)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            ChartLegend(lines)

            Spacer(Modifier.height(4.dp))

            SensITMultiLineChart(
                lines = lines,
                window = selectedWindow,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SensITTemperatureTrendPanel(
    samples: List<SensITSample>,
    selectedWindow: TrendWindow,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val start = now - selectedWindow.durationMillis

    val visible = samples.filter {
        it.timeMillis >= start
    }

    val lines = listOf(
        SensITChartLine(
            "Cell",
            Orange,
            visible.mapNotNull {
                it.temperature?.let { value ->
                    it.timeMillis to value
                }
            }
        ),

        SensITChartLine(
            "Laser",
            Cyan,
            visible.mapNotNull {
                it.laserTemperature?.let { value ->
                    it.timeMillis to value
                }
            }
        )
    )

    DashboardPanel(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            PanelTitle(
                "THERMAL TREND (°C)",
                Icons.Outlined.Thermostat
            )

            Spacer(Modifier.height(6.dp))

            ChartLegend(lines)

            Spacer(Modifier.height(4.dp))

            SensITMultiLineChart(
                lines = lines,
                window = selectedWindow,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ChartLegend(
    lines: List<SensITChartLine>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        lines.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .then(
                            Modifier
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(line.color)
                    }
                }

                Text(
                    line.label,
                    color = TextSoft,
                    style =
                        androidx.compose.material3.MaterialTheme
                            .typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SensITMultiLineChart(
    lines: List<SensITChartLine>,
    window: TrendWindow,
    modifier: Modifier
) {
    val allValues =
        lines.flatMap { line ->
            line.points.map { it.second }
        }

    if (allValues.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Waiting for SensIT data",
                color = TextMuted
            )
        }

        return
    }

    Canvas(modifier = modifier) {
        val leftPad = 48f
        val rightPad = 14f
        val topPad = 10f
        val bottomPad = 28f

        val chartWidth =
            size.width - leftPad - rightPad

        val chartHeight =
            size.height - topPad - bottomPad

        val rawMin = allValues.minOrNull() ?: 0.0
        val rawMax = allValues.maxOrNull() ?: 1.0

        val rawRange = rawMax - rawMin
        val axisPadding =
            (rawRange * 0.12).coerceAtLeast(0.05)

        var minValue = rawMin - axisPadding
        var maxValue = rawMax + axisPadding

        if (maxValue - minValue < 0.1) {
            minValue -= 0.05
            maxValue += 0.05
        }

        val valueRange =
            (maxValue - minValue).coerceAtLeast(0.001)

        val axisPaint = Paint().apply {
            color =
                android.graphics.Color.rgb(
                    169,
                    184,
                    208
                )

            textSize = 15f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }

        val timePaint = Paint().apply {
            color =
                android.graphics.Color.rgb(
                    169,
                    184,
                    208
                )

            textSize = 15f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for (index in 0..4) {
            val y =
                topPad +
                        chartHeight * index / 4f

            drawLine(
                color = Color(0xFF1B2A47),
                start = Offset(leftPad, y),
                end = Offset(
                    size.width - rightPad,
                    y
                ),
                strokeWidth = 1f
            )

            val label =
                maxValue -
                        valueRange * index / 4.0

            drawContext.canvas.nativeCanvas.drawText(
                String.format(
                    Locale.US,
                    "%.2f",
                    label
                ),
                leftPad - 7f,
                y + 5f,
                axisPaint
            )
        }

        val endTime =
            System.currentTimeMillis()

        val startTime =
            endTime - window.durationMillis

        val formatter =
            SimpleDateFormat(
                "HH:mm",
                Locale.US
            )

        for (index in 0..4) {
            val x =
                leftPad +
                        chartWidth * index / 4f

            drawLine(
                color = Color(0xFF14233E),
                start = Offset(x, topPad),
                end = Offset(
                    x,
                    size.height - bottomPad
                ),
                strokeWidth = 1f
            )

            val labelTime =
                startTime +
                        window.durationMillis *
                        index / 4

            drawContext.canvas.nativeCanvas.drawText(
                formatter.format(
                    Date(labelTime)
                ),
                x,
                size.height - 6f,
                timePaint
            )
        }

        lines.forEach { line ->
            val points =
                line.points.map { pair ->
                    val xRatio =
                        (
                                (pair.first - startTime)
                                    .toDouble() /
                                        window.durationMillis
                                ).coerceIn(0.0, 1.0)

                    val yRatio =
                        (
                                (pair.second - minValue) /
                                        valueRange
                                ).coerceIn(0.0, 1.0)

                    Offset(
                        x =
                            leftPad +
                                    xRatio.toFloat() *
                                    chartWidth,

                        y =
                            size.height -
                                    bottomPad -
                                    yRatio.toFloat() *
                                    chartHeight
                    )
                }

            for (index in 0 until points.lastIndex) {
                drawLine(
                    color = line.color,
                    start = points[index],
                    end = points[index + 1],
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round
                )
            }

            points.forEach { point ->
                drawCircle(
                    color = line.color,
                    radius = 2.7f,
                    center = point
                )
            }
        }

        drawLine(
            color = Color(0xFF2A3A58),
            start = Offset(
                leftPad,
                topPad
            ),
            end = Offset(
                leftPad,
                size.height - bottomPad
            ),
            strokeWidth = 2f
        )

        drawLine(
            color = Color(0xFF2A3A58),
            start = Offset(
                leftPad,
                size.height - bottomPad
            ),
            end = Offset(
                size.width - rightPad,
                size.height - bottomPad
            ),
            strokeWidth = 2f
        )
    }
}