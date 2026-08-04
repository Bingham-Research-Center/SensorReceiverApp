package com.example.methreceiver.ui.sensit

import com.example.methreceiver.DashboardPanel
import com.example.methreceiver.model.SensITPacket
import com.example.methreceiver.ui.common.*
import com.example.methreceiver.util.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun SensITSummaryRow(
    sensit: SensITPacket
) {
    val stateColor =
        when {
            sensit.activeState == null -> TextMuted
            isSensITNormalState(sensit.activeState) -> Green
            sensit.activeState == 110 -> Orange
            else -> Yellow
        }

    val errorColor =
        when (sensit.errorId) {
            null -> TextMuted
            0 -> Green
            else -> Orange
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),

        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        SensITSummaryCard(
            modifier = Modifier.weight(1f),
            title = "CONCENTRATION",
            value = formatSensIT(sensit.concentration, 4),
            unit = "ppm",
            subtitle = "Calculated signal",
            color = Green
        )

        SensITSummaryCard(
            modifier = Modifier.weight(1f),
            title = "ACTIVE STATE",
            value = sensit.activeState?.toString() ?: "--",
            unit = "",
            subtitle = sensitActiveStateLabel(
                sensit.activeState,
                sensit.activeStateName
            ),
            color = stateColor
        )

        SensITSummaryCard(
            modifier = Modifier.weight(1f),
            title = "ERROR",
            value = sensit.errorId?.toString() ?: "--",
            unit = "",
            subtitle = sensitErrorLabel(
                sensit.errorId,
                sensit.errorName
            ),
            color = errorColor
        )

        SensITSummaryCard(
            modifier = Modifier.weight(1f),
            title = "ACQUISITION",
            value = sensit.counterM?.toString() ?: "--",
            unit = "/ 999",
            subtitle =
                "Last time: ${
                    formatSensIT(sensit.lastTimeAcq, 3)
                }",
            color = Blue
        )
    }
}

@Composable
private fun SensITSummaryCard(
    modifier: Modifier,
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    color: Color
) {
    DashboardPanel(
        modifier = modifier,
        contentPadding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment =
                    androidx.compose.ui.Alignment.Bottom
            ) {
                Text(
                    value,
                    color = color,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(5.dp))

                    Text(
                        unit,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                subtitle,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SensITDiagnosticsPanel(
    sensit: SensITPacket,
    modifier: Modifier = Modifier
) {
    DashboardPanel(
        modifier = modifier,
        contentPadding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "FIRMWARE & CONNECTION",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            SensITInfoRow(
                "Firmware state",
                sensit.activeState?.let {
                    "$it · ${
                        sensitActiveStateLabel(
                            it,
                            sensit.activeStateName
                        )
                    }"
                } ?: "--",
                if (isSensITNormalState(sensit.activeState)) {
                    Green
                } else {
                    Yellow
                }
            )

            SensITInfoRow(
                "Error",
                sensitErrorLabel(
                    sensit.errorId,
                    sensit.errorName
                ),
                if (sensit.errorId == 0) Green else Orange
            )

            SensITInfoRow(
                "External device",
                when (sensit.extConnected) {
                    true -> "Connected"
                    false -> "Disconnected"
                    null -> "--"
                },
                if (sensit.extConnected == true) Green else TextSoft
            )

            SensITInfoRow(
                "Sensitivity",
                sensitSensitivityLabel(
                    sensit.sensitivityLevel,
                    sensit.sensitivityLabel
                ),
                Cyan
            )

            SensITInfoRow(
                "Bit depth",
                sensit.bit?.let { "$it bit" } ?: "--",
                Blue
            )

            SensITInfoRow(
                "Counter M",
                sensit.counterM?.let { "$it / 999" } ?: "--",
                Color.White
            )

            SensITInfoRow(
                "Last acquisition",
                formatSensIT(sensit.lastTimeAcq, 3),
                Color.White
            )
        }
    }
}

@Composable
fun SensITElectricalPanel(
    sensit: SensITPacket,
    modifier: Modifier = Modifier
) {
    DashboardPanel(
        modifier = modifier,
        contentPadding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                "SIGNAL, THERMAL & LASER VALUES",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement =
                    Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    SensITInfoRow(
                        "Raw concentration",
                        "${formatSensIT(sensit.concentrationRaw, 4)} ppm",
                        Orange
                    )

                    SensITInfoRow(
                        "Calibrated",
                        "${formatSensIT(sensit.concentrationCal, 4)} ppm",
                        Blue
                    )

                    SensITInfoRow(
                        "Temperature compensated",
                        "${formatSensIT(sensit.concentrationTemp, 4)} ppm",
                        Purple
                    )

                    SensITInfoRow(
                        "PHD power",
                        "${formatSensIT(sensit.phdPower, 4)} V",
                        Cyan
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    SensITInfoRow(
                        "Cell temperature",
                        "${formatSensIT(sensit.temperature, 2)} °C",
                        Orange
                    )

                    SensITInfoRow(
                        "Laser temperature",
                        "${formatSensIT(sensit.laserTemperature, 2)} °C",
                        Yellow
                    )

                    SensITInfoRow(
                        "Laser voltage",
                        "${formatSensIT(sensit.laserVoltage, 4)} V",
                        Blue
                    )

                    SensITInfoRow(
                        "Laser current",
                        formatSensIT(sensit.laserCurrent, 4),
                        Green
                    )
                }
            }
        }
    }
}

@Composable
private fun SensITInfoRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = TextSoft,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.width(8.dp))

        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun formatSensIT(
    value: Double?,
    decimals: Int
): String {
    if (value == null) return "--"

    return String.format(
        Locale.US,
        "%.${decimals}f",
        value
    )
}