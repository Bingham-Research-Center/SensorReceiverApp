package com.example.methreceiver

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.nativeCanvas
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.io.OutputStreamWriter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DashboardBg
                ) {
                    ReceiverDashboardApp()
                }
            }
        }
    }
}

data class MethaneSample(
    val timeMillis: Long,
    val ppm: Double
)

enum class TrendWindow(val label: String, val durationMillis: Long) {
    ONE_MIN("1M", 60_000L),
    FIVE_MIN("5M", 5 * 60_000L),
    FIFTEEN_MIN("15M", 15 * 60_000L),
    ONE_HOUR("1H", 60 * 60_000L)
}

data class TelemetryPacket(
    val timestamp: String = "",
    val methanePpm: Double? = null,
    val methaneState: Int? = null,
    val methaneStatus: String = "",

    val windSpeedMps: Double? = null,
    val windDirectionDeg: Double? = null,
    val windSpeed2dMps: Double? = null,
    val windUMps: Double? = null,
    val windVMps: Double? = null,
    val windWMps: Double? = null,
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val pressureHpa: Double? = null,
    val pitchDeg: Double? = null,
    val rollDeg: Double? = null,
    val magneticHeadingDeg: Double? = null,

    val gpsStatus: String = "",
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val gpsAltM: Double? = null,
    val gpsSatellites: Int? = null,
    val gpsHdop: Double? = null,
    val gpsSpeedKnots: Double? = null,
    val gpsCourseDeg: Double? = null,
    val gpsTimestampUtc: String = "",


    val batteryVoltageV: Double? = null,
    val batteryStatus: String = "",

    val radioRssi: Int? = null,
    val radioRemoteRssi: Int? = null,
    val radioNoise: Int? = null,
    val radioRemoteNoise: Int? = null,

    val packetId: Long? = null
)

data class ConnectionState(
    val connected: Boolean = false,
    val status: String = "Disconnected",
    val lastMessageAt: String = "—",
    val sessionStartMillis: Long = System.currentTimeMillis(),
    val packetsPerSecond: Double = 0.0,
    val lastPacketMillis: Long = 0L
)

data class LoggedFileInfo(
    val fileName: String,
    val uri: Uri,
    val rowCount: Long,
    val sizeBytes: Long,
    val savedAt: String
)

data class CsvLoggerState(
    val folderUri: Uri? = null,
    val fileName: String = "",
    val isConfigured: Boolean = false,
    val isLogging: Boolean = false,
    val rowCount: Long = 0L,
    val currentFileUri: Uri? = null,
    val currentFileName: String = ""
)

private const val DEFAULT_WS_URL = "ws://192.168.4.1:8765"
private val DashboardBg = Color(0xFF050B18)
private val HeaderBg = Color(0xFF091224)
private val PanelBg = Color(0xFF0B1730)
private val PanelBorder = Color(0xFF17325A)
private val Cyan = Color(0xFF21C7FF)
private val Green = Color(0xFF57E76D)
private val Purple = Color(0xFF9F62FF)
private val Orange = Color(0xFFFFB020)
private val Blue = Color(0xFF5D8DFF)
private val Yellow = Color(0xFFFFD23F)
private val TextSoft = Color(0xFFA9B8D0)
private val TextMuted = Color(0xFF7083A4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverDashboardApp() {
    var connectionState by remember { mutableStateOf(ConnectionState()) }
    var latestPacket by remember { mutableStateOf(TelemetryPacket()) }
    var autoReconnect by remember { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }
    var uiNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var batteryMinVoltage by remember { mutableStateOf(5.0) }
    var batteryMaxVoltage by remember { mutableStateOf(7.4) }
    var batteryLowPercent by remember { mutableStateOf(25f) }
    var batteryCriticalPercent by remember { mutableStateOf(10f) }
    val methaneHistory = remember { mutableStateListOf<MethaneSample>() }
    var selectedTrendWindow by remember { mutableStateOf(TrendWindow.ONE_MIN) }
    var loggerState by remember { mutableStateOf(CsvLoggerState(fileName = defaultCsvFileName())) }
    var showLoggingSetup by remember { mutableStateOf(false) }
    var loggingError by remember { mutableStateOf<String?>(null) }
    var showLoggedFiles by remember { mutableStateOf(false) }
    var showLogSavedMessage by remember { mutableStateOf(false) }
    val loggedFiles = remember { mutableStateListOf<LoggedFileInfo>() }
    var csvWriter by remember { mutableStateOf<OutputStreamWriter?>(null) }
    val context = LocalContext.current
    val rssiHistory = remember { mutableStateListOf<Int>() }
    val packetArrivalTimes = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            uiNowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val telemetryStale =
        connectionState.connected &&
                connectionState.lastPacketMillis > 0L &&
                uiNowMillis - connectionState.lastPacketMillis > 5000L

    val socketManager = remember {
        TelemetryWebSocketClient(
            onConnecting = {
                isConnecting = true
                connectionState = connectionState.copy(status = "Connecting...")
            },
            onConnected = {
                isConnecting = false
                connectionState = connectionState.copy(connected = true, status = "Connected")
            },
            onDisconnected = { reason ->
                isConnecting = false
                connectionState = connectionState.copy(connected = false, status = reason)
            },
            onPacket = { packet ->
                latestPacket = packet

                if (loggerState.isLogging && csvWriter != null) {
                    csvWriter?.write(csvRow(packet))
                    csvWriter?.flush()
                    loggerState = loggerState.copy(rowCount = loggerState.rowCount + 1)
                }

                val now = System.currentTimeMillis()
                packetArrivalTimes.add(now)
                while (packetArrivalTimes.isNotEmpty() && now - packetArrivalTimes.first() > 5000) {
                    packetArrivalTimes.removeAt(0)
                }

                val rate = if (packetArrivalTimes.size >= 2) {
                    packetArrivalTimes.size / 5.0
                } else {
                    0.0
                }

                connectionState = connectionState.copy(
                    connected = true,
                    status = "Streaming",
                    lastMessageAt = nowString(),
                    packetsPerSecond = rate,
                    lastPacketMillis = now
                )

                packet.methanePpm?.let {
                    val now = System.currentTimeMillis()
                    methaneHistory.add(MethaneSample(now, it))

                    val cutoff = now - TrendWindow.ONE_HOUR.durationMillis
                    methaneHistory.removeAll { sample -> sample.timeMillis < cutoff }
                }

                packet.radioRssi?.let {
                    rssiHistory.add(it)
                    if (rssiHistory.size > 12) rssiHistory.removeAt(0)
                }
            }
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            loggerState = loggerState.copy(
                folderUri = uri,
                isConfigured = loggerState.fileName.isNotBlank()
            )
        }
    }

    fun startLogging() {
        try {
            val folderUri = loggerState.folderUri
            if (folderUri == null) {
                loggingError = "No logging folder selected."
                return
            }

            var fileName = loggerState.fileName.ifBlank { defaultCsvFileName() }
            if (!fileName.endsWith(".csv", ignoreCase = true)) {
                fileName += ".csv"
            }

            val pickedDir = DocumentFile.fromTreeUri(context, folderUri)

            if (pickedDir == null || !pickedDir.canWrite()) {
                loggingError = "Cannot access selected folder."
                return
            }

            pickedDir.findFile(fileName)?.delete()

            val newFile = pickedDir.createFile("text/csv", fileName)

            if (newFile == null) {
                loggingError = "Failed to create CSV file in selected folder."
                return
            }

            val outputStream = context.contentResolver.openOutputStream(newFile.uri, "w")

            if (outputStream == null) {
                loggingError = "Could not open CSV file for writing."
                return
            }

            val writer = OutputStreamWriter(outputStream)
            writer.write(csvHeader())
            writer.flush()

            csvWriter = writer

            loggerState = loggerState.copy(
                isLogging = true,
                rowCount = 0L,
                currentFileUri = newFile.uri,
                currentFileName = fileName
            )
        } catch (e: Exception) {
            loggingError = e.message ?: "Failed to start logging."
            csvWriter = null
            loggerState = loggerState.copy(isLogging = false)
        }
    }

    fun stopLogging() {
        try {
            csvWriter?.flush()
            csvWriter?.close()
        } catch (e: Exception) {
            loggingError = e.message ?: "Failed to close CSV file."
        }

        csvWriter = null

        val uri = loggerState.currentFileUri
        val fileName = loggerState.currentFileName
        val finalRows = loggerState.rowCount

        if (uri != null && fileName.isNotBlank()) {
            loggedFiles.add(
                LoggedFileInfo(
                    fileName = fileName,
                    uri = uri,
                    rowCount = finalRows,
                    sizeBytes = getUriSizeBytes(context, uri),
                    savedAt = nowString()
                )
            )
        }

        // Reset logging setup after each completed log
        loggerState = CsvLoggerState(
            fileName = defaultCsvFileName()
        )

        showLogSavedMessage = true
    }


    DisposableEffect(Unit) {
        socketManager.connect(DEFAULT_WS_URL)
        onDispose { socketManager.disconnect() }
    }

    LaunchedEffect(autoReconnect, connectionState.connected, isConnecting) {
        if (autoReconnect && !connectionState.connected && !isConnecting) {
            delay(3000)
            socketManager.connect(DEFAULT_WS_URL)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashboardBg)
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HeaderRow(
            state = connectionState,
            packet = latestPacket,
            isConnecting = isConnecting,
            batteryMinVoltage = batteryMinVoltage,
            batteryMaxVoltage = batteryMaxVoltage,
            batteryLowPercent = batteryLowPercent,
            batteryCriticalPercent = batteryCriticalPercent,
            onSettingsClicked = { showSettingsDialog = true }
        )

        ConnectionIssueBanner(
            state = connectionState,
            onReconnectClicked = {
                scope.launch(Dispatchers.IO) {
                    socketManager.connect(DEFAULT_WS_URL)
                }
            }
        )

        TelemetryStaleBanner(
            stale = telemetryStale,
            lastPacketAt = connectionState.lastMessageAt
        )

        MetricGrid(
            packet = latestPacket,
            isLogging = loggerState.isLogging,
            loggingTarget = if (loggerState.isConfigured) "CSV Ready" else "Not configured",
            loggedRows = loggerState.rowCount,
            canStartLogging = loggerState.isConfigured && !loggerState.isLogging,
            canStopLogging = loggerState.isLogging,
            canViewData = loggedFiles.isNotEmpty(),
            onSetupLogging = { showLoggingSetup = true },
            onStartLogging = { startLogging() },
            onStopLogging = { stopLogging() },
            onViewData = { showLoggedFiles = true }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
            ) {
                MethaneTrendPanel(
                    samples = methaneHistory.toList(),
                    selectedWindow = selectedTrendWindow,
                    onWindowSelected = { selectedTrendWindow = it }
                )
            }

            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
            ) {
                WindDirectionPanel(packet = latestPacket)
            }

            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                SystemStatusPanel(packet = latestPacket, state = connectionState)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.82f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                GpsLocationPanel(packet = latestPacket)
            }

            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
            ) {
                RadioLinkPanel(rssiValues = rssiHistory.toList(), packet = latestPacket)
            }

            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
            ) {
                AlertsPanel(packet = latestPacket)
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            batteryMinVoltage = batteryMinVoltage,
            batteryMaxVoltage = batteryMaxVoltage,
            batteryLowPercent = batteryLowPercent,
            batteryCriticalPercent = batteryCriticalPercent,
            onBatteryMinVoltageChanged = { batteryMinVoltage = it },
            onBatteryMaxVoltageChanged = { batteryMaxVoltage = it },
            onBatteryLowPercentChanged = { batteryLowPercent = it },
            onBatteryCriticalPercentChanged = { batteryCriticalPercent = it },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showLoggingSetup) {
        LoggingSetupDialog(
            fileName = loggerState.fileName,
            folderSelected = loggerState.folderUri != null,
            onFileNameChanged = { loggerState = loggerState.copy(fileName = it) },
            onChooseFolder = { folderPickerLauncher.launch(null) },
            onSave = {
                loggerState = loggerState.copy(
                    isConfigured = loggerState.folderUri != null && loggerState.fileName.isNotBlank()
                )
                showLoggingSetup = false
            },
            onDismiss = { showLoggingSetup = false }
        )
    }

    if (showLogSavedMessage) {
        AlertDialog(
            onDismissRequest = { showLogSavedMessage = false },
            title = { Text("Logging stopped") },
            text = { Text("Data stored in selected location.") },
            confirmButton = {
                TextButton(onClick = { showLogSavedMessage = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showLoggedFiles) {
        AlertDialog(
            onDismissRequest = { showLoggedFiles = false },
            title = { Text("Logged CSV Files") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loggedFiles.isEmpty()) {
                        Text("No logged files yet.")
                    } else {
                        loggedFiles.forEach { file ->
                            Text(file.fileName)
                            Text(
                                "Rows: ${file.rowCount}   Size: ${file.sizeBytes} bytes   Saved: ${file.savedAt}",
                                color = TextSoft
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoggedFiles = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (loggingError != null) {
        AlertDialog(
            onDismissRequest = { loggingError = null },
            title = { Text("Logging error") },
            text = { Text(loggingError ?: "Unknown error") },
            confirmButton = {
                TextButton(onClick = { loggingError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun TelemetryStaleBanner(
    stale: Boolean,
    lastPacketAt: String
) {
    if (!stale) return

    DashboardPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFF261B10),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(2.dp, Yellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = Yellow, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    "Sensor station application on drone is stopped running! Reboot the broadcasting Raspberry Pi",
                    color = Yellow,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Receiver Pi is online, but no new sensor packets are arriving. Last packet: $lastPacketAt",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    batteryMinVoltage: Double,
    batteryMaxVoltage: Double,
    batteryLowPercent: Float,
    batteryCriticalPercent: Float,
    onBatteryMinVoltageChanged: (Double) -> Unit,
    onBatteryMaxVoltageChanged: (Double) -> Unit,
    onBatteryLowPercentChanged: (Float) -> Unit,
    onBatteryCriticalPercentChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSection by remember { mutableStateOf("Battery") }

    var minVoltageText by remember { mutableStateOf(batteryMinVoltage.toString()) }
    var maxVoltageText by remember { mutableStateOf(batteryMaxVoltage.toString()) }
    var lowText by remember { mutableStateOf(batteryLowPercent.toInt().toString()) }
    var criticalText by remember { mutableStateOf(batteryCriticalPercent.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.width(130.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSideItem(
                        text = "Battery",
                        selected = selectedSection == "Battery",
                        onClick = { selectedSection = "Battery" }
                    )

                    SettingsSideItem(
                        text = "Logging",
                        selected = selectedSection == "Logging",
                        onClick = { selectedSection = "Logging" }
                    )

                    SettingsSideItem(
                        text = "Connection",
                        selected = selectedSection == "Connection",
                        onClick = { selectedSection = "Connection" }
                    )

                    SettingsSideItem(
                        text = "Display",
                        selected = selectedSection == "Display",
                        onClick = { selectedSection = "Display" }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (selectedSection) {
                        "Battery" -> {
                            Text("Battery Calibration", fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = minVoltageText,
                                onValueChange = { minVoltageText = it },
                                label = { Text("Minimum voltage") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = maxVoltageText,
                                onValueChange = { maxVoltageText = it },
                                label = { Text("Maximum voltage") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = lowText,
                                onValueChange = { lowText = it },
                                label = { Text("Low warning threshold (%)") },
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = criticalText,
                                onValueChange = { criticalText = it },
                                label = { Text("Critical threshold (%)") },
                                singleLine = true
                            )

                            Text(
                                "Battery percent is calculated from measured voltage using min/max voltage.",
                                color = TextSoft,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        else -> {
                            Text("$selectedSection settings coming later.", color = TextSoft)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    minVoltageText.toDoubleOrNull()?.let(onBatteryMinVoltageChanged)
                    maxVoltageText.toDoubleOrNull()?.let(onBatteryMaxVoltageChanged)
                    lowText.toFloatOrNull()?.let(onBatteryLowPercentChanged)
                    criticalText.toFloatOrNull()?.let(onBatteryCriticalPercentChanged)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsSideItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Blue.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Blue.copy(alpha = 0.7f) else PanelBorder.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Blue else TextSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun BatteryHeaderStatus(
    voltage: Double?,
    minVoltage: Double,
    maxVoltage: Double,
    lowPercent: Float,
    criticalPercent: Float
) {
    val percent = batteryPercentFromVoltage(voltage, minVoltage, maxVoltage)
    val color = batteryColor(percent, lowPercent, criticalPercent)

    Column(
        modifier = Modifier.width(150.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Battery", color = TextSoft, style = MaterialTheme.typography.bodySmall)
            Text(
                voltage?.let { "${format1(it)} V" } ?: "--",
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFF16233F))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }

        Text(
            if (voltage == null) "No battery data" else batteryLabel(percent, lowPercent, criticalPercent),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LoggingSetupDialog(
    fileName: String,
    folderSelected: Boolean,
    onFileNameChanged: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logging Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = onFileNameChanged,
                    label = { Text("CSV file name") },
                    singleLine = true
                )

                Button(onClick = onChooseFolder) {
                    Text(if (folderSelected) "Change Folder" else "Choose Folder")
                }

                Text(
                    if (folderSelected) "Storage location selected" else "Choose tablet, USB, or SD card folder",
                    color = if (folderSelected) Green else TextSoft
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = fileName.isNotBlank() && folderSelected
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
@Composable
fun HeaderRow(
    state: ConnectionState,
    packet: TelemetryPacket,
    isConnecting: Boolean,
    batteryMinVoltage: Double,
    batteryMaxVoltage: Double,
    batteryLowPercent: Float,
    batteryCriticalPercent: Float,
    onSettingsClicked: () -> Unit
) {
    val localTime = rememberLocalTime()

    DashboardPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = HeaderBg,
        contentPadding = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoTitleBlock(logoRes = R.drawable.usu)
                Spacer(modifier = Modifier.width(18.dp))
                StatusBadge("Wi-Fi", if (state.connected) "Connected" else "Waiting", Icons.Outlined.Router)
                Spacer(modifier = Modifier.width(10.dp))
                StatusBadge("Pi Server", if (state.connected) "Reachable" else "Offline", Icons.Outlined.NetworkCheck)
                Spacer(modifier = Modifier.width(10.dp))
                StatusBadge("WebSocket",if (state.connected) "Streaming" else shortConnectionStatus(state.status), Icons.Outlined.Sensors)
                Spacer(modifier = Modifier.width(10.dp))
                StatusBadge("GPS", gpsStatusText(state.connected), Icons.Outlined.LocationOn)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Local Time", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        localTime,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                BatteryHeaderStatus(
                    voltage = packet.batteryVoltageV,
                    minVoltage = batteryMinVoltage,
                    maxVoltage = batteryMaxVoltage,
                    lowPercent = batteryLowPercent,
                    criticalPercent = batteryCriticalPercent
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = TextSoft,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onSettingsClicked() }
                )
            }
        }
    }
}

@Composable
fun ConnectionIssueBanner(
    state: ConnectionState,
    onReconnectClicked: () -> Unit
) {
    if (state.connected) return

    DashboardPanel(
        modifier = Modifier.fillMaxWidth(),
        containerColor = Color(0xFF231626),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(2.dp, Orange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Orange, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        "Receiver connection lost",
                        color = Orange,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Check tablet Wi-Fi is connected to receiver Pi hotspot!",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = onReconnectClicked,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Reconnect")
            }
        }
    }
}

@Composable
fun LogoTitleBlock(@DrawableRes logoRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = logoRes),
            contentDescription = "Utah State University logo",
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("Sensor Receiver Dashboard", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Bingham Research Center", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatusBadge(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, color = TextSoft, style = MaterialTheme.typography.bodySmall)
                Text(value, color = Green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricGrid(
    packet: TelemetryPacket,
    isLogging: Boolean,
    loggingTarget: String,
    loggedRows: Long,
    canStartLogging: Boolean,
    canStopLogging: Boolean,
    canViewData: Boolean,
    onSetupLogging: () -> Unit,
    onStartLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onViewData: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DualMetricCard(
            modifier = Modifier.weight(1f),
            title = "AIR DATA",
            leftLabel = "METHANE",
            leftValue = packet.methanePpm?.let { format2(it) } ?: "--",
            leftUnit = "ppm",
            leftIcon = Icons.Outlined.WarningAmber,
            leftColor = Green,
            rightLabel = "WIND SPEED",
            rightValue = packet.windSpeedMps?.let { format1(it) } ?: "--",
            rightUnit = "m/s",
            rightIcon = Icons.Outlined.Air,
            rightColor = Cyan
        )

        TripleMetricCard(
            modifier = Modifier.weight(1f),
            title = "ENVIRONMENT",
            firstLabel = "TEMP.",
            firstValue = packet.temperatureC?.let { format1(cToF(it)) } ?: "--",
            firstUnit = "°F",
            firstIcon = Icons.Outlined.Thermostat,
            firstColor = Orange,
            secondLabel = "HUMIDITY",
            secondValue = packet.humidityPercent?.let { format1(it) } ?: "--",
            secondUnit = "%",
            secondIcon = Icons.Outlined.WaterDrop,
            secondColor = Blue,
            thirdLabel = "PRESSURE",
            thirdValue = packet.pressureHpa?.let { format1(it) } ?: "--",
            thirdUnit = "hPa",
            thirdIcon = Icons.Outlined.Cloud,
            thirdColor = Cyan
        )

        LoggingMetricCard(
            modifier = Modifier.weight(0.85f),
            isLogging = isLogging,
            loggingTarget = loggingTarget,
            loggedRows = loggedRows,
            canStartLogging = canStartLogging,
            canStopLogging = canStopLogging,
            canViewData = canViewData,
            onSetupLogging = onSetupLogging,
            onStartLogging = onStartLogging,
            onStopLogging = onStopLogging,
            onViewData = onViewData
        )

        DeviceStatusCard(
            modifier = Modifier.weight(1.15f),
            packet = packet
        )
    }
}

@Composable
fun DualMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    leftLabel: String,
    leftValue: String,
    leftUnit: String,
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector,
    leftColor: Color,
    rightLabel: String,
    rightValue: String,
    rightUnit: String,
    rightIcon: androidx.compose.ui.graphics.vector.ImageVector,
    rightColor: Color
) {
    DashboardPanel(modifier = modifier) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompactMetricItem(
                    modifier = Modifier.weight(1f),
                    label = leftLabel,
                    value = leftValue,
                    unit = leftUnit,
                    icon = leftIcon,
                    color = leftColor
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(62.dp)
                        .background(PanelBorder)
                )

                CompactMetricItem(
                    modifier = Modifier.weight(1f),
                    label = rightLabel,
                    value = rightValue,
                    unit = rightUnit,
                    icon = rightIcon,
                    color = rightColor
                )
            }
        }
    }
}

@Composable
fun DeviceStatusCard(
    modifier: Modifier = Modifier,
    packet: TelemetryPacket
) {
    val methaneError =
        packet.methaneStatus.startsWith("error", ignoreCase = true) &&
                packet.methaneStatus != "error_0"

    val methaneHealthText = if (methaneError) {
        "Error: ${methaneErrorLabel(packet.methaneStatus)}"
    } else {
        "Health: Good"
    }

    val methaneStatusText = when {
        packet.methaneStatus.isBlank() -> "--"
        else -> methaneErrorLabel(packet.methaneStatus)
    }

    DashboardPanel(modifier = modifier, contentPadding = 12.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "DEVICE STATUS",
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Methane Sensor",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        packet.methaneState?.let {
                            "State: ${methaneStateShortLabel(it)} ($it)"
                        } ?: "State: --",
                        color = Green,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        methaneHealthText,
                        color = if (methaneError) Orange else Green,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Wind Sensor",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "State: --",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        "Health: --",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun TripleMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    firstLabel: String,
    firstValue: String,
    firstUnit: String,
    firstIcon: androidx.compose.ui.graphics.vector.ImageVector,
    firstColor: Color,
    secondLabel: String,
    secondValue: String,
    secondUnit: String,
    secondIcon: androidx.compose.ui.graphics.vector.ImageVector,
    secondColor: Color,
    thirdLabel: String,
    thirdValue: String,
    thirdUnit: String,
    thirdIcon: androidx.compose.ui.graphics.vector.ImageVector,
    thirdColor: Color
) {
    DashboardPanel(modifier = modifier, contentPadding = 12.dp) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactMetricItem(
                    modifier = Modifier.weight(1f),
                    label = firstLabel,
                    value = firstValue,
                    unit = firstUnit,
                    icon = firstIcon,
                    color = firstColor
                )

                CompactMetricItem(
                    modifier = Modifier.weight(1f),
                    label = secondLabel,
                    value = secondValue,
                    unit = secondUnit,
                    icon = secondIcon,
                    color = secondColor
                )

                CompactMetricItem(
                    modifier = Modifier.weight(1f),
                    label = thirdLabel,
                    value = thirdValue,
                    unit = thirdUnit,
                    icon = thirdIcon,
                    color = thirdColor
                )
            }
        }
    }
}

@Composable
fun CompactMetricItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                color = TextSoft,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = color,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    unit,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun PanelTitle(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MethaneTrendPanel(
    samples: List<MethaneSample>,
    selectedWindow: TrendWindow,
    onWindowSelected: (TrendWindow) -> Unit
) {
    val now = System.currentTimeMillis()
    val visibleSamples = samples.filter {
        it.timeMillis >= now - selectedWindow.durationMillis
    }

    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PanelTitle("METHANE TREND (ppm)", Icons.Outlined.ShowChart)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrendWindow.values().forEach { window ->
                        TinyTimeChip(
                            text = window.label,
                            active = selectedWindow == window,
                            onClick = { onWindowSelected(window) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            MethaneLineChart(
                samples = visibleSamples,
                window = selectedWindow
            )
        }
    }
}

@Composable
fun TinyTimeChip(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFF143A25) else Color(0xFF111F39))
            .border(
                1.dp,
                if (active) Green.copy(alpha = 0.8f) else PanelBorder,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clickable { onClick() }
    ) {
        Text(
            text,
            color = if (active) Green else TextSoft,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun LoggingMetricCard(
    modifier: Modifier = Modifier,
    isLogging: Boolean,
    loggingTarget: String,
    loggedRows: Long,
    canStartLogging: Boolean,
    canStopLogging: Boolean,
    canViewData: Boolean,
    onSetupLogging: () -> Unit,
    onStartLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onViewData: () -> Unit
) {
    DashboardPanel(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LOGGING",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (isLogging) "ACTIVE" else "IDLE",
                    color = if (isLogging) Green else TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    loggingTarget,
                    color = if (loggingTarget == "Not configured") TextMuted else Color.White,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Rows: $loggedRows",
                    color = TextSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LogButton(
                    text = "Setup",
                    color = Blue,
                    enabled = !isLogging,
                    onClick = onSetupLogging
                )

                LogButton(
                    text = if (isLogging) "Stop" else "Start",
                    color = if (isLogging) Orange else Green,
                    enabled = if (isLogging) canStopLogging else canStartLogging,
                    onClick = if (isLogging) onStopLogging else onStartLogging
                )

                LogButton(
                    text = "View",
                    color = Cyan,
                    enabled = canViewData,
                    onClick = onViewData
                )
            }
        }
    }
}

@Composable
fun LogButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) color.copy(alpha = 0.16f) else TextMuted.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (enabled) color.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.25f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) color else TextMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}
@Composable
fun MethaneLineChart(
    samples: List<MethaneSample>,
    window: TrendWindow
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val leftPad = 46f
        val rightPad = 18f
        val topPad = 10f
        val bottomPad = 26f

        val chartWidth = width - leftPad - rightPad
        val chartHeight = height - topPad - bottomPad

        val displaySamples = if (samples.isEmpty()) {
            val now = System.currentTimeMillis()
            listOf(
                MethaneSample(now - 50_000, 3.0),
                MethaneSample(now - 40_000, 3.2),
                MethaneSample(now - 30_000, 3.1),
                MethaneSample(now - 20_000, 3.4),
                MethaneSample(now - 10_000, 3.3),
                MethaneSample(now, 3.5)
            )
        } else {
            samples
        }

        val now = System.currentTimeMillis()
        val startTime = now - window.durationMillis

        val visiblePpm = displaySamples.map { it.ppm }
        val rawMin = visiblePpm.minOrNull() ?: 0.0
        val rawMax = visiblePpm.maxOrNull() ?: 10.0

        val padding = ((rawMax - rawMin) * 0.15).coerceAtLeast(0.25)
        var minValue = (rawMin - padding).coerceAtLeast(0.0)
        var maxValue = rawMax + padding

        if (maxValue - minValue < 1.0) {
            val center = (maxValue + minValue) / 2.0
            minValue = (center - 0.5).coerceAtLeast(0.0)
            maxValue = center + 0.5
        }

        val valueRange = maxValue - minValue

        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(169, 184, 208)
            textSize = 16f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        val xPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(169, 184, 208)
            textSize = 16f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Horizontal grid + dynamic Y-axis labels
        for (i in 0..4) {
            val y = topPad + chartHeight * i / 4f
            drawLine(
                color = Color(0xFF1B2A47),
                start = Offset(leftPad, y),
                end = Offset(width - rightPad, y),
                strokeWidth = 1f
            )

            val labelValue = maxValue - (valueRange * i / 4.0)
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", labelValue),
                leftPad - 8f,
                y + 5f,
                axisPaint
            )
        }

        // Vertical grid + 24-hour X-axis labels
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.US)

        for (i in 0..5) {
            val x = leftPad + chartWidth * i / 5f
            drawLine(
                color = Color(0xFF14233E),
                start = Offset(x, topPad),
                end = Offset(x, height - bottomPad),
                strokeWidth = 1f
            )

            val labelTime = startTime + (window.durationMillis * i / 5)
            drawContext.canvas.nativeCanvas.drawText(
                timeFormatter.format(Date(labelTime)),
                x,
                height - 6f,
                xPaint
            )
        }

        val points = displaySamples.map { sample ->
            val xRatio = ((sample.timeMillis - startTime).toDouble() / window.durationMillis)
                .coerceIn(0.0, 1.0)
                .toFloat()

            val yRatio = ((sample.ppm - minValue) / valueRange)
                .coerceIn(0.0, 1.0)
                .toFloat()

            Offset(
                x = leftPad + xRatio * chartWidth,
                y = height - bottomPad - yRatio * chartHeight
            )
        }

        if (points.size >= 2) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points.first().x, height - bottomPad)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height - bottomPad)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Green.copy(alpha = 0.35f),
                        Green.copy(alpha = 0.04f)
                    ),
                    startY = topPad,
                    endY = height - bottomPad
                )
            )

            for (i in 0 until points.lastIndex) {
                drawLine(
                    color = Green,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 1.5f,
                    cap = StrokeCap.Round
                )
            }

            points.forEach { point ->
                drawCircle(color = Green, radius = 2.5f, center = point)
                drawCircle(color = PanelBg, radius = 1.5f, center = point)
            }
        }

        // Axis lines
        drawLine(
            color = Color(0xFF2A3A58),
            start = Offset(leftPad, topPad),
            end = Offset(leftPad, height - bottomPad),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFF2A3A58),
            start = Offset(leftPad, height - bottomPad),
            end = Offset(width - rightPad, height - bottomPad),
            strokeWidth = 2f
        )
    }
}

@Composable
fun WindDirectionPanel(packet: TelemetryPacket) {
    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelTitle("WIND DIRECTION", Icons.Outlined.Explore)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.75f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "From",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        compassLongLabel(packet.windDirectionDeg),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            packet.windDirectionDeg?.let { "${it.roundToInt()}°" } ?: "--",
                            color = Purple,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            compassLabel(packet.windDirectionDeg),
                            color = Purple,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    CompassGauge(packet.windDirectionDeg)
                }
            }
        }
    }
}

@Composable
fun CompassGauge(deg: Double?) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val s = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f + 4f)

        val outerR = s * 0.34f
        val innerR = outerR * 0.55f
        val labelR = outerR * 1.28f

        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 16f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val smallLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(169, 184, 208)
            textSize = 14f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        drawCircle(
            color = Color(0xFF101D35),
            radius = outerR * 1.05f,
            center = center
        )

        drawCircle(
            color = Color(0xFF2D3E5E),
            radius = outerR,
            center = center,
            style = Stroke(width = 3f)
        )

        drawCircle(
            color = Color(0xFF182844),
            radius = innerR,
            center = center,
            style = Stroke(width = 2f)
        )

        // ticks
        for (i in 0 until 32) {
            val angleDeg = i * 11.25 - 90.0
            val angle = Math.toRadians(angleDeg)
            val major = i % 4 == 0

            val tickOuter = outerR
            val tickInner = if (major) outerR * 0.82f else outerR * 0.92f

            drawLine(
                color = if (major) Color.White.copy(alpha = 0.85f) else TextSoft.copy(alpha = 0.55f),
                start = Offset(
                    center.x + cos(angle).toFloat() * tickInner,
                    center.y + sin(angle).toFloat() * tickInner
                ),
                end = Offset(
                    center.x + cos(angle).toFloat() * tickOuter,
                    center.y + sin(angle).toFloat() * tickOuter
                ),
                strokeWidth = if (major) 3f else 1.6f,
                cap = StrokeCap.Round
            )
        }

        // direction labels
        val labels = listOf(
            "N" to 0.0,
            "NE" to 45.0,
            "E" to 90.0,
            "SE" to 135.0,
            "S" to 180.0,
            "SW" to 225.0,
            "W" to 270.0,
            "NW" to 315.0
        )

        labels.forEach { (label, angleDegRaw) ->
            val angle = Math.toRadians(angleDegRaw - 90.0)
            val x = center.x + cos(angle).toFloat() * labelR
            val y = center.y + sin(angle).toFloat() * labelR + 8f

            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                y,
                if (label.length == 1) labelPaint else smallLabelPaint
            )
        }

        // arrow
        if (deg != null) {
            val a = Math.toRadians(deg - 90.0)

            val tip = Offset(
                center.x + cos(a).toFloat() * outerR * 0.78f,
                center.y + sin(a).toFloat() * outerR * 0.78f
            )

            val left = Offset(
                center.x + cos(a + 2.45).toFloat() * outerR * 0.32f,
                center.y + sin(a + 2.45).toFloat() * outerR * 0.32f
            )

            val right = Offset(
                center.x + cos(a - 2.45).toFloat() * outerR * 0.32f,
                center.y + sin(a - 2.45).toFloat() * outerR * 0.32f
            )

            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(left.x, left.y)
                lineTo(center.x, center.y)
                lineTo(right.x, right.y)
                close()
            }

            drawPath(
                path = arrowPath,
                brush = Brush.linearGradient(
                    colors = listOf(Purple, Color(0xFFB98CFF)),
                    start = center,
                    end = tip
                )
            )
        }
    }
}

@Composable
fun SystemStatusPanel(packet: TelemetryPacket, state: ConnectionState) {
    val context = LocalContext.current
    val tabletBattery = getTabletBatteryPercent(context)

    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelTitle("SYSTEM STATUS", Icons.Outlined.Settings)

            StatusRowItem(
                "Connection",
                if (state.connected) "Connected" else state.status,
                if (state.connected) Green else Orange
            )

            StatusRowItem(
                "Last Packet",
                state.lastMessageAt,
                Color.White
            )

            StatusRowItem(
                "Packets Received",
                packet.packetId?.toString() ?: "--",
                Color.White
            )

            StatusRowItem(
                "Uptime",
                formatUptime(state.sessionStartMillis),
                Color.White
            )

            StatusRowItem(
                "Data Rate",
                if (state.packetsPerSecond > 0.0) String.format(Locale.US, "%.1f pkt/s", state.packetsPerSecond) else "--",
                Color.White
            )

            BatteryRow("Pi Battery", null)
            BatteryRow("Tablet Battery", tabletBattery)
        }
    }
}

@Composable
fun StatusRowItem(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSoft)
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BatteryRow(label: String, percent: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextSoft)
            Text(percent?.let { "$it%" } ?: "--", color = Color.White)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFF16233F))
        ) {
            if (percent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(99.dp))
                        .background(Green)
                )
            }
        }
    }
}

@Composable
fun GpsLocationPanel(packet: TelemetryPacket) {
    val hasFix = packet.gpsStatus == "fix" || packet.gpsStatus == "ok" || packet.gpsLat != null

    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelTitle("GPS LOCATION", Icons.Outlined.LocationOn)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GpsStat("Status", if (packet.gpsStatus.isNotBlank()) packet.gpsStatus else "--", if (hasFix) Green else Orange)
                    GpsStat("Latitude", packet.gpsLat?.let { format6(it) } ?: "--", Color.White)
                    GpsStat("Longitude", packet.gpsLon?.let { format6(it) } ?: "--", Color.White)
                    GpsStat("Altitude", packet.gpsAltM?.let { "${format1(it)} m" } ?: "--", Blue)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GpsStat("Satellites", packet.gpsSatellites?.toString() ?: "--", Blue)
                    GpsStat("HDOP", packet.gpsHdop?.let { format1(it) } ?: "--", Blue)
                    GpsStat("Speed", packet.gpsSpeedKnots?.let { "${format1(it)} kt" } ?: "--", TextSoft)
                    GpsStat("Course", packet.gpsCourseDeg?.let { "${it.roundToInt()}°" } ?: "--", TextSoft)
                }
            }
        }
    }
}

@Composable
fun GpsStat(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSoft, style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RadioLinkPanel(rssiValues: List<Int>, packet: TelemetryPacket) {
    val displayValues = if (rssiValues.isEmpty()) {
        listOf(-78, -74, -70, -68, -72, -75, -80, -84, -88, -90, -86, -82)
    } else {
        rssiValues
    }

    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelTitle("RADIO LINK (RSSI)", Icons.Outlined.Podcasts)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BarChart(displayValues, Modifier.weight(1f))

                Column(
                    modifier = Modifier.width(155.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioStat(
                        label = "Local RSSI",
                        value = packet.radioRssi?.let { "$it dBm" } ?: "--",
                        color = Yellow
                    )

                    RadioStat(
                        label = "Remote RSSI",
                        value = packet.radioRemoteRssi?.let { "$it dBm" } ?: "--",
                        color = Yellow
                    )

                    RadioStat(
                        label = "Noise",
                        value = packet.radioNoise?.let { "$it dBm" } ?: "--",
                        color = TextSoft
                    )

                    RadioStat(
                        label = "Remote Noise",
                        value = packet.radioRemoteNoise?.let { "$it dBm" } ?: "--",
                        color = TextSoft
                    )

                    RadioStat(
                        label = "Link Quality",
                        value = linkQuality(packet.radioRssi, packet.radioNoise),
                        color = linkQualityColor(packet.radioRssi, packet.radioNoise)
                    )
                }
            }
        }
    }
}

@Composable
fun BarChart(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxHeight()) {
        val width = size.width
        val height = size.height
        val padding = 18f
        val chartHeight = height - padding * 2
        val minRssi = -120f
        val maxRssi = -30f
        val barWidth = (width - padding * 2) / (values.size * 1.55f)

        for (i in 0..3) {
            val y = padding + chartHeight * i / 3f
            drawLine(color = Color(0xFF1B2A47), start = Offset(padding, y), end = Offset(width - padding, y), strokeWidth = 1f)
        }

        values.forEachIndexed { index, value ->
            val ratio = ((value - minRssi) / (maxRssi - minRssi)).coerceIn(0f, 1f)
            val barHeight = ratio * chartHeight
            val left = padding + index * barWidth * 1.55f
            val top = height - padding - barHeight
            drawRoundRect(
                color = when {
                    index >= values.size - 2 -> Color(0xFF54627E)
                    value >= -70 -> Green
                    value >= -80 -> Yellow
                    else -> Color(0xFFFFD060)
                },
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
            )
        }
    }
}

@Composable
fun AlertsPanel(packet: TelemetryPacket) {
    val methaneWarning = (packet.methanePpm ?: 0.0) >= 150.0
    val windWarning = (packet.windSpeedMps ?: 0.0) >= 25.0

    val alerts = buildList {
        if (methaneWarning) {
            add("Methane Warning")
        }
        if (windWarning) {
            add("Strong Wind Warning")
        }
    }

    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PanelTitle("ALERTS", Icons.Outlined.WarningAmber)

            if (alerts.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(2.dp, Green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Green, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            "All Clear",
                            color = Green,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("No active alerts", color = TextSoft)
                    }
                }
            } else {
                if (methaneWarning) {
                    WarningAlertRow(
                        text = "Methane Warning (${format2(packet.methanePpm ?: 0.0)} ppm)",
                        color = Orange
                    )
                }

                if (windWarning) {
                    WarningAlertRow(
                        text = "Strong Wind (${format1(packet.windSpeedMps ?: 0.0)} m/s)",
                        color = Yellow
                    )
                }
            }
        }
    }
}


@Composable
fun DashboardPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = PanelBg,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                .padding(contentPadding),
            content = content
        )
    }
}

class TelemetryWebSocketClient(
    private val onConnecting: () -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit,
    private val onPacket: (TelemetryPacket) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webSocket: WebSocket? = null

    fun connect(url: String) {
        disconnect()
        mainHandler.post { onConnecting() }

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    onConnected()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { parseTelemetryPacket(text) }
                    .onSuccess { packet ->
                        mainHandler.post {
                            onPacket(packet)
                        }
                    }
                    .onFailure {
                        mainHandler.post {
                            onDisconnected("Bad packet")
                        }
                    }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    onDisconnected(if (reason.isBlank()) "Disconnected" else reason)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                mainHandler.post {
                    onDisconnected(if (reason.isBlank()) "Closing" else reason)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    onDisconnected(t.message ?: "Connection failed")
                }
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Manual disconnect")
        webSocket = null
    }
}

fun parseTelemetryPacket(text: String): TelemetryPacket {
    val json = JSONObject(text)
    return TelemetryPacket(
        timestamp = json.optString("timestamp", ""),
        methanePpm = json.optDoubleOrNull("methane_ppm"),
        methaneState = json.optIntOrNull("methane_state"),
        methaneStatus = json.optString("methane_status", ""),

        windSpeedMps = json.optDoubleOrNull("wind_speed_mps"),
        windDirectionDeg = json.optDoubleOrNull("wind_direction_deg"),
        windSpeed2dMps = json.optDoubleOrNull("wind_speed_2d_mps"),
        windUMps = json.optDoubleOrNull("wind_u_mps"),
        windVMps = json.optDoubleOrNull("wind_v_mps"),
        windWMps = json.optDoubleOrNull("wind_w_mps"),
        temperatureC = json.optDoubleOrNull("temperature_c"),
        humidityPercent = json.optDoubleOrNull("humidity_percent"),
        pressureHpa = json.optDoubleOrNull("pressure_hpa"),
        pitchDeg = json.optDoubleOrNull("pitch_deg"),
        rollDeg = json.optDoubleOrNull("roll_deg"),
        magneticHeadingDeg = json.optDoubleOrNull("magnetic_heading_deg"),

        gpsStatus = json.optString("gps_status", ""),
        gpsLat = json.optDoubleOrNull("gps_lat"),
        gpsLon = json.optDoubleOrNull("gps_lon"),
        gpsAltM = json.optDoubleOrNull("gps_alt_m"),
        gpsSatellites = json.optIntOrNull("gps_satellites"),
        gpsHdop = json.optDoubleOrNull("gps_hdop"),
        gpsSpeedKnots = json.optDoubleOrNull("gps_speed_knots"),
        gpsCourseDeg = json.optDoubleOrNull("gps_course_deg"),
        gpsTimestampUtc = json.optString("gps_timestamp_utc", ""),


        batteryVoltageV = json.optDoubleOrNull("battery_voltage_v"),
        batteryStatus = json.optString("battery_status", ""),

        radioRssi = json.optIntOrNull("radio_rssi"),
        radioRemoteRssi = json.optIntOrNull("radio_remote_rssi"),
        radioNoise = json.optIntOrNull("radio_noise"),
        radioRemoteNoise = json.optIntOrNull("radio_remote_noise"),

        packetId = json.optLongOrNull("packet_id")
    )
}

fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

fun methaneAlert(value: Double?): String {
    if (value == null) return "No active telemetry"
    return when {
        value >= 10.0 -> "High Alert"
        value >= 5.0 -> "Elevated"
        else -> "Normal Range"
    }
}

fun windDescriptor(value: Double?): String {
    if (value == null) return "No wind data"
    return when {
        value < 1.5 -> "Calm"
        value < 4.0 -> "Light Breeze"
        value < 8.0 -> "Moderate"
        else -> "Strong"
    }
}

fun signalQuality(rssi: Int?): String {
    if (rssi == null) return "Unknown"
    return when {
        rssi >= -70 -> "Good"
        rssi >= -90 -> "Fair"
        rssi >= -110 -> "Weak"
        else -> "Poor"
    }
}

fun gpsStatusText(connected: Boolean): String = if (connected) "Locked" else "Searching"

fun compassLabel(deg: Double?): String {
    if (deg == null) return "—"
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((deg % 360) / 45.0).roundToInt()) % 8
    return directions[index]
}

fun compassLongLabel(deg: Double?): String {
    return when (compassLabel(deg)) {
        "N" -> "North"
        "NE" -> "Northeast"
        "E" -> "East"
        "SE" -> "Southeast"
        "S" -> "South"
        "SW" -> "Southwest"
        "W" -> "West"
        "NW" -> "Northwest"
        else -> "Unknown"
    }
}

fun nowString(): String = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date())
fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
fun format6(value: Double): String = String.format(Locale.US, "%.6f", value)

fun formatUptime(startMillis: Long): String {
    val totalSeconds = ((System.currentTimeMillis() - startMillis) / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

fun getTabletBatteryPercent(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return null
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return (level * 100) / scale
}

@Composable
fun rememberLocalTime(): String {
    var currentTime by remember { mutableStateOf(nowString()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = nowString()
            delay(1000)
        }
    }

    return currentTime
}

@Composable
fun WarningAlertRow(
    text: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RadioStat(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSoft, style = MaterialTheme.typography.bodySmall)
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

fun linkQuality(rssi: Int?, noise: Int?): String {
    if (rssi == null || noise == null) return "--"

    val snr = rssi - noise

    return when {
        snr >= 30 -> "Excellent"
        snr >= 20 -> "Good"
        snr >= 10 -> "Fair"
        else -> "Poor"
    }
}

fun linkQualityColor(rssi: Int?, noise: Int?): Color {
    if (rssi == null || noise == null) return TextSoft

    val snr = rssi - noise

    return when {
        snr >= 30 -> Green
        snr >= 20 -> Cyan
        snr >= 10 -> Yellow
        else -> Orange
    }
}

@Composable
fun CompassLabel(
    text: String,
    alignment: Alignment,
    top: androidx.compose.ui.unit.Dp = 0.dp,
    bottom: androidx.compose.ui.unit.Dp = 0.dp,
    start: androidx.compose.ui.unit.Dp = 0.dp,
    end: androidx.compose.ui.unit.Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = start, end = end, top = top, bottom = bottom),
        contentAlignment = alignment
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}


fun cToF(celsius: Double): Double = (celsius * 9.0 / 5.0) + 32.0

fun shortConnectionStatus(status: String): String {
    return when {
        status.contains("failed", ignoreCase = true) -> "No Link"
        status.contains("timeout", ignoreCase = true) -> "Timeout"
        status.contains("Connection refused", ignoreCase = true) -> "Refused"
        status.contains("Connecting", ignoreCase = true) -> "Connecting"
        else -> status.take(18)
    }
}

fun defaultCsvFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "meth_telemetry_$stamp.csv"
}

fun csvHeader(): String {
    return "timestamp_iso,gps_lat_deg,gps_lon_deg,ch4_ppm,wind_u_mps,wind_v_mps,wind_w_mps,wind_total_mps,wind_direction_deg\n"
}

fun csvRow(packet: TelemetryPacket): String {
    fun d(value: Double?): String = value?.let { String.format(Locale.US, "%.6f", it) } ?: ""

    return listOf(
        packet.timestamp,
        d(packet.gpsLat),
        d(packet.gpsLon),
        d(packet.methanePpm),
        d(packet.windUMps),
        d(packet.windVMps),
        d(packet.windWMps),
        d(packet.windSpeedMps),
        d(packet.windDirectionDeg)
    ).joinToString(",") + "\n"
}

fun getUriSizeBytes(context: android.content.Context, uri: Uri): Long {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0) {
            cursor.getLong(sizeIndex)
        } else {
            0L
        }
    } ?: 0L
}

fun batteryPercentFromVoltage(
    voltage: Double?,
    minVoltage: Double,
    maxVoltage: Double
): Float {
    if (voltage == null) return 0f
    if (maxVoltage <= minVoltage) return 0f

    return (((voltage - minVoltage) / (maxVoltage - minVoltage)) * 100.0)
        .coerceIn(0.0, 100.0)
        .toFloat()
}

fun batteryColor(
    percent: Float,
    lowPercent: Float,
    criticalPercent: Float
): Color {
    return when {
        percent <= criticalPercent -> Orange
        percent <= lowPercent -> Yellow
        else -> Green
    }
}

fun batteryLabel(
    percent: Float,
    lowPercent: Float,
    criticalPercent: Float
): String {
    return when {
        percent <= criticalPercent -> "Critical"
        percent <= lowPercent -> "Low"
        else -> "Good"
    }
}

fun methaneStateShortLabel(state: Int): String {
    return when (state) {
        10 -> "Startup"
        20 -> "Warm up"
        30 -> "Set par"
        35 -> "Final warm up"
        37 -> "Housekeeping"
        40 -> "Measurement"
        50 -> "Housekeeping"
        110 -> "Error"
        else -> "Unknown"
    }
}

fun methaneErrorLabel(status: String): String {
    if (status == "ok") return "All good!"

    val code = status
        .removePrefix("error_")
        .toIntOrNull()

    return when (code) {
        0 -> "No error"
        1 -> "Laser temp. error 1"
        2 -> "Laser temp. 2"
        3 -> "Laser temp. 3"
        4 -> "EEPROM error"
        5 -> "Laser 1 vol. error"
        6 -> "Laser 1 cur. error"
        7 -> "Laser 2 vol. error"
        8 -> "Laser 2 current error"
        9 -> "Calibration error 1"
        10 -> "Low power error"
        11 -> "Centering error"
        12 -> "Negative reading error"
        13 -> "Calibration error 2"
        14 -> "Flat ramp error"
        else -> status.ifBlank { "--" }
    }
}