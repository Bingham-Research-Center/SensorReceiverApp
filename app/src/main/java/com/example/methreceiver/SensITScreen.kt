package com.example.methreceiver

import com.example.methreceiver.ui.common.*

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SensITScreen(
    packet: TelemetryPacket,
    connectionState: ConnectionState
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        /*
         * This is page content, not another application header.
         */
        DashboardPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SensIT Diagnostics",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    if (connectionState.connected) {
                        "Live telemetry connected"
                    } else {
                        "No telemetry connection"
                    },

                color =
                    if (connectionState.connected) {
                        Green
                    } else {
                        Orange
                    }
            )
        }

        DashboardPanel(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text =
                    "Methane concentration: ${
                        packet.methanePpm?.let {
                            format2(it)
                        } ?: "--"
                    } ppm",

                color = Green,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Sensor state: ${
                        packet.methaneState ?: "--"
                    }",

                color = Color.White
            )

            Text(
                text =
                    "Sensor status: ${
                        packet.methaneStatus.ifBlank {
                            "--"
                        }
                    }",

                color = TextSoft
            )
        }
    }
}