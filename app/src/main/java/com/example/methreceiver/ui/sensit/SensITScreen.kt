package com.example.methreceiver.ui.sensit

import com.example.methreceiver.*
import com.example.methreceiver.logging.*
import com.example.methreceiver.model.SensITSample
import com.example.methreceiver.ui.common.*

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStreamWriter

@Composable
fun SensITScreen(
    packet: TelemetryPacket,
    connectionState: ConnectionState
) {
    val context = LocalContext.current

    val history =
        remember {
            mutableStateListOf<SensITSample>()
        }

    var selectedWindow by remember {
        mutableStateOf(TrendWindow.ONE_MIN)
    }

    var loggerState by remember {
        mutableStateOf(
            CsvLoggerState(
                fileName =
                    defaultSensITCsvFileName()
            )
        )
    }

    var writer by remember {
        mutableStateOf<OutputStreamWriter?>(null)
    }

    var showSetup by remember {
        mutableStateOf(false)
    }

    var showFiles by remember {
        mutableStateOf(false)
    }

    var showSaved by remember {
        mutableStateOf(false)
    }

    var loggingError by remember {
        mutableStateOf<String?>(null)
    }

    val loggedFiles =
        remember {
            mutableStateListOf<LoggedFileInfo>()
        }

    LaunchedEffect(
        packet.timestamp,
        packet.packetId
    ) {
        if (!packet.sensit.hasData) {
            return@LaunchedEffect
        }

        val sensit = packet.sensit
        val now = System.currentTimeMillis()

        history.add(
            SensITSample(
                timeMillis = now,

                concentration =
                    sensit.concentration,

                concentrationRaw =
                    sensit.concentrationRaw,

                concentrationCal =
                    sensit.concentrationCal,

                concentrationTemp =
                    sensit.concentrationTemp,

                temperature =
                    sensit.temperature,

                laserTemperature =
                    sensit.laserTemperature,

                phdPower =
                    sensit.phdPower,

                laserVoltage =
                    sensit.laserVoltage,

                laserCurrent =
                    sensit.laserCurrent
            )
        )

        val cutoff =
            now -
                    TrendWindow.ONE_HOUR.durationMillis

        history.removeAll {
            it.timeMillis < cutoff
        }

        if (
            loggerState.isLogging &&
            writer != null
        ) {
            try {
                writer?.write(
                    sensitCsvRow(packet)
                )

                writer?.flush()

                loggerState =
                    loggerState.copy(
                        rowCount =
                            loggerState.rowCount + 1
                    )
            } catch (exception: Exception) {
                loggingError =
                    exception.message
                        ?: "Failed to write SensIT CSV."

                runCatching {
                    writer?.close()
                }

                writer = null

                loggerState =
                    loggerState.copy(
                        isLogging = false
                    )
            }
        }
    }

    val folderPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                } catch (_: SecurityException) {
                    // Some providers do not permit persisted grants.
                }

                loggerState =
                    loggerState.copy(
                        folderUri = uri,
                        isConfigured =
                            loggerState.fileName.isNotBlank()
                    )
            }
        }

    fun startLogging() {
        try {
            val folderUri =
                loggerState.folderUri
                    ?: run {
                        loggingError =
                            "No logging folder selected."

                        return
                    }

            var fileName =
                loggerState.fileName.ifBlank {
                    defaultSensITCsvFileName()
                }

            if (
                !fileName.endsWith(
                    ".csv",
                    ignoreCase = true
                )
            ) {
                fileName += ".csv"
            }

            val directory =
                DocumentFile.fromTreeUri(
                    context,
                    folderUri
                )

            if (
                directory == null ||
                !directory.canWrite()
            ) {
                loggingError =
                    "Cannot access selected folder."

                return
            }

            directory.findFile(fileName)?.delete()

            val file =
                directory.createFile(
                    "text/csv",
                    fileName
                )

            if (file == null) {
                loggingError =
                    "Could not create SensIT CSV file."

                return
            }

            val output =
                context.contentResolver
                    .openOutputStream(
                        file.uri,
                        "w"
                    )

            if (output == null) {
                loggingError =
                    "Could not open SensIT CSV file."

                return
            }

            writer =
                OutputStreamWriter(output).apply {
                    write(sensitCsvHeader())
                    flush()
                }

            loggerState =
                loggerState.copy(
                    isLogging = true,
                    rowCount = 0,
                    currentFileUri = file.uri,
                    currentFileName = fileName
                )
        } catch (exception: Exception) {
            loggingError =
                exception.message
                    ?: "Failed to start SensIT logging."

            writer = null

            loggerState =
                loggerState.copy(
                    isLogging = false
                )
        }
    }

    fun stopLogging() {
        try {
            writer?.flush()
            writer?.close()
        } catch (exception: Exception) {
            loggingError =
                exception.message
                    ?: "Failed to close SensIT CSV."
        }

        writer = null

        val uri =
            loggerState.currentFileUri

        val fileName =
            loggerState.currentFileName

        if (
            uri != null &&
            fileName.isNotBlank()
        ) {
            loggedFiles.add(
                LoggedFileInfo(
                    fileName = fileName,
                    uri = uri,
                    rowCount =
                        loggerState.rowCount,
                    sizeBytes =
                        getUriSizeBytes(
                            context,
                            uri
                        ),
                    savedAt = nowString()
                )
            )
        }

        loggerState =
            CsvLoggerState(
                fileName =
                    defaultSensITCsvFileName()
            )

        showSaved = true
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                writer?.flush()
                writer?.close()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        if (!connectionState.connected) {
            DashboardPanel(
                modifier = Modifier.fillMaxWidth(),
                containerColor =
                    androidx.compose.ui.graphics.Color(
                        0xFF231626
                    )
            ) {
                Text(
                    "SensIT telemetry is unavailable.",
                    color = Orange,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SensITSummaryRow(
            sensit = packet.sensit
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            SensITConcentrationTrendPanel(
                samples = history,
                selectedWindow = selectedWindow,

                onWindowSelected = {
                    selectedWindow = it
                },

                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
            )

            SensITTemperatureTrendPanel(
                samples = history,
                selectedWindow = selectedWindow,

                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
            )

            SensITDiagnosticsPanel(
                sensit = packet.sensit,

                modifier = Modifier
                    .weight(0.78f)
                    .fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp),

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            SensITElectricalPanel(
                sensit = packet.sensit,

                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
            )

            SensITLoggingPanel(
                loggerState = loggerState,
                canViewData =
                    loggedFiles.isNotEmpty(),

                onSetup = {
                    showSetup = true
                },

                onStart = {
                    startLogging()
                },

                onStop = {
                    stopLogging()
                },

                onView = {
                    showFiles = true
                },

                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight()
            )
        }
    }

    if (showSetup) {
        LoggingSetupDialog(
            fileName = loggerState.fileName,
            folderSelected =
                loggerState.folderUri != null,

            onFileNameChanged = {
                loggerState =
                    loggerState.copy(
                        fileName = it
                    )
            },

            onChooseFolder = {
                folderPicker.launch(null)
            },

            onSave = {
                loggerState =
                    loggerState.copy(
                        isConfigured =
                            loggerState.folderUri != null &&
                                    loggerState.fileName.isNotBlank()
                    )

                showSetup = false
            },

            onDismiss = {
                showSetup = false
            }
        )
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = {
                showSaved = false
            },

            title = {
                Text("SensIT logging stopped")
            },

            text = {
                Text(
                    "SensIT data was stored in the selected location."
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showSaved = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    if (showFiles) {
        AlertDialog(
            onDismissRequest = {
                showFiles = false
            },

            title = {
                Text("SensIT CSV Files")
            },

            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    loggedFiles.forEach { file ->
                        Text(
                            file.fileName,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Rows: ${file.rowCount} · " +
                                    "Size: ${file.sizeBytes} bytes · " +
                                    "Saved: ${file.savedAt}",
                            color = TextSoft
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showFiles = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    loggingError?.let { message ->
        AlertDialog(
            onDismissRequest = {
                loggingError = null
            },

            title = {
                Text("SensIT logging error")
            },

            text = {
                Text(message)
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        loggingError = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}