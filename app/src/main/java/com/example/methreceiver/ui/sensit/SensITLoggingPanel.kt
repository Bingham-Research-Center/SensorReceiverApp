package com.example.methreceiver.ui.sensit

import com.example.methreceiver.*
import com.example.methreceiver.ui.common.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SensITLoggingPanel(
    loggerState: CsvLoggerState,
    canViewData: Boolean,
    onSetup: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    DashboardPanel(
        modifier = modifier,
        contentPadding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SENSIT DATA LOGGER",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (loggerState.isLogging) {
                        "ACTIVE"
                    } else {
                        "IDLE"
                    },
                    color =
                        if (loggerState.isLogging) {
                            Green
                        } else {
                            TextMuted
                        },
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    if (loggerState.isConfigured) {
                        loggerState.currentFileName
                            .ifBlank { "CSV Ready" }
                    } else {
                        "Not configured"
                    },
                    color =
                        if (loggerState.isConfigured) {
                            androidx.compose.ui.graphics.Color.White
                        } else {
                            TextMuted
                        }
                )

                Text(
                    "Rows: ${loggerState.rowCount}",
                    color = TextSoft
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                LogButton(
                    text = "Setup",
                    color = Blue,
                    enabled = !loggerState.isLogging,
                    onClick = onSetup
                )

                LogButton(
                    text =
                        if (loggerState.isLogging) {
                            "Stop"
                        } else {
                            "Start"
                        },

                    color =
                        if (loggerState.isLogging) {
                            Orange
                        } else {
                            Green
                        },

                    enabled =
                        if (loggerState.isLogging) {
                            true
                        } else {
                            loggerState.isConfigured
                        },

                    onClick =
                        if (loggerState.isLogging) {
                            onStop
                        } else {
                            onStart
                        }
                )

                LogButton(
                    text = "View",
                    color = Cyan,
                    enabled = canViewData,
                    onClick = onView
                )
            }
        }
    }
}