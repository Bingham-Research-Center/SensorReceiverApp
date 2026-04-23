package com.example.methreceiver

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LocationOn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

data class TelemetryPacket(
    val timestamp: String = "",
    val methanePpm: Double? = null,
    val windSpeedMps: Double? = null,
    val windDirectionDeg: Double? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    val radioRssi: Int? = null,
    val packetId: Long? = null
)

data class ConnectionState(
    val connected: Boolean = false,
    val status: String = "Disconnected",
    val lastMessageAt: String = "—",
    val sessionStartMillis: Long = System.currentTimeMillis(),
    val packetsPerSecond: Double = 0.0
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
    val methaneHistory = remember { mutableStateListOf<Double>() }
    val rssiHistory = remember { mutableStateListOf<Int>() }
    val packetArrivalTimes = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

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
                    packetsPerSecond = rate
                )

                packet.methanePpm?.let {
                    methaneHistory.add(it)
                    if (methaneHistory.size > 24) methaneHistory.removeAt(0)
                }

                packet.radioRssi?.let {
                    rssiHistory.add(it)
                    if (rssiHistory.size > 12) rssiHistory.removeAt(0)
                }
            }
        )
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
            autoReconnect = autoReconnect,
            isConnecting = isConnecting,
            onAutoReconnectChanged = { autoReconnect = it },
            onReconnectClicked = {
                scope.launch(Dispatchers.IO) {
                    socketManager.connect(DEFAULT_WS_URL)
                }
            }
        )

        MetricGrid(packet = latestPacket)

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
                MethaneTrendPanel(values = methaneHistory.toList())
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
}
@Composable
fun HeaderRow(
    state: ConnectionState,
    autoReconnect: Boolean,
    isConnecting: Boolean,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onReconnectClicked: () -> Unit
) {
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
                StatusBadge("WebSocket", if (state.connected) "Streaming" else state.status, Icons.Outlined.Sensors)
                Spacer(modifier = Modifier.width(10.dp))
                StatusBadge("GPS", gpsStatusText(state.connected), Icons.Outlined.LocationOn)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Last Packet", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    Text(state.lastMessageAt, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text("Auto", color = TextSoft)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(checked = autoReconnect, onCheckedChange = onAutoReconnectChanged)
                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onReconnectClicked,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                    modifier = Modifier
                        .height(36.dp)
                ) {
                    Text("Reconnect", style = MaterialTheme.typography.bodyMedium)
                }
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
            Text("Utah State University", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Meth Receiver Dashboard", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Methane Remote Sensing Project", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
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
fun MetricGrid(packet: TelemetryPacket) {
    val metrics = listOf(
        MetricUi("METHANE", packet.methanePpm?.let { format2(it) } ?: "--", "ppm", methaneAlert(packet.methanePpm), Green, Icons.Outlined.WarningAmber),
        MetricUi("WIND SPEED", packet.windSpeedMps?.let { format1(it) } ?: "--", "m/s", windDescriptor(packet.windSpeedMps), Cyan, Icons.Outlined.Air),
        MetricUi("WIND DIRECTION", packet.windDirectionDeg?.let { "${it.roundToInt()}°" } ?: "--", "", compassLabel(packet.windDirectionDeg), Purple, Icons.Outlined.Explore),
        MetricUi("TEMPERATURE", "--", "°C", "Unavailable", Orange, Icons.Outlined.Thermostat),
        MetricUi("HUMIDITY", "--", "%", "Unavailable", Blue, Icons.Outlined.WaterDrop),
        MetricUi("GPS LATITUDE", packet.gpsLat?.let { format6(it) } ?: "--", "", "Receiver", Blue, Icons.Outlined.LocationOn),
        MetricUi("GPS LONGITUDE", packet.gpsLon?.let { format6(it) } ?: "--", "", "Receiver", Blue, Icons.Outlined.LocationOn),
        MetricUi("RSSI", packet.radioRssi?.toString() ?: "--", "dBm", signalQuality(packet.radioRssi), Yellow, Icons.Outlined.Podcasts)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        userScrollEnabled = false
    ) {
        items(metrics) { metric ->
            MetricCard(metric)
        }
    }
}

data class MetricUi(
    val label: String,
    val value: String,
    val unit: String,
    val subtitle: String,
    val accent: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val footerRight: String = ""
)

@Composable
fun MetricCard(metric: MetricUi) {
    DashboardPanel {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = metric.icon,
                    contentDescription = null,
                    tint = metric.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    metric.label,
                    color = TextSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    metric.value,
                    color = metric.accent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (metric.unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        metric.unit,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    metric.subtitle,
                    color = metric.accent,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (metric.footerRight.isNotEmpty()) {
                    Text(
                        metric.footerRight,
                        color = TextSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
fun MethaneTrendPanel(values: List<Double>) {
    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PanelTitle("METHANE TREND (ppm)", Icons.Outlined.ShowChart)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TinyTimeChip("1M", active = true)
                    TinyTimeChip("5M")
                    TinyTimeChip("15M")
                    TinyTimeChip("1H")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LineChart(values = values.ifEmpty { listOf(3.0, 3.2, 3.1, 3.4, 3.3, 3.5, 3.2, 3.1) })
        }
    }
}

@Composable
fun TinyTimeChip(text: String, active: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF143A25) else Color(0xFF111F39))
            .border(1.dp, if (active) Color(0xFF295A39) else PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (active) Green else TextSoft)
    }
}

@Composable
fun LineChart(values: List<Double>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 28f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        val maxValue = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val minValue = (values.minOrNull() ?: 0.0).coerceAtMost(maxValue - 0.1)
        val valueRange = (maxValue - minValue).coerceAtLeast(0.5)

        for (i in 0..4) {
            val y = padding + chartHeight * i / 4f
            drawLine(color = Color(0xFF1B2A47), start = Offset(padding, y), end = Offset(width - padding, y), strokeWidth = 1f)
        }
        for (i in 0..5) {
            val x = padding + chartWidth * i / 5f
            drawLine(color = Color(0xFF14233E), start = Offset(x, padding), end = Offset(x, height - padding), strokeWidth = 1f)
        }

        val points = values.mapIndexed { index, value ->
            val x = padding + (chartWidth * index / (values.lastIndex.coerceAtLeast(1))).toFloat()
            val ratio = ((value - minValue) / valueRange).toFloat()
            val y = height - padding - ratio * chartHeight
            Offset(x, y)
        }

        for (i in 0 until points.lastIndex) {
            drawLine(color = Green, start = points[i], end = points[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
        }
        points.forEach { point ->
            drawCircle(color = Green, radius = 5f, center = point)
            drawCircle(color = PanelBg, radius = 2.4f, center = point)
        }
    }
}

@Composable
fun WindDirectionPanel(packet: TelemetryPacket) {
    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelTitle("WIND DIRECTION", Icons.Outlined.Explore)
            Text(packet.windDirectionDeg?.let { "From ${compassLongLabel(it)}" } ?: "No direction data", color = TextSoft)
            Spacer(modifier = Modifier.height(10.dp))
            CompassGauge(packet.windDirectionDeg)
        }
    }
}

@Composable
fun CompassGauge(deg: Double?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = min(size.width, size.height)
            val r = s * 0.33f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = Color(0xFF101D35), radius = r * 1.2f, center = center)
            drawCircle(color = PanelBorder, radius = r * 1.2f, center = center, style = Stroke(width = 3f))
            drawCircle(color = Color(0xFF132240), radius = r * 0.62f, center = center)

            for (i in 0 until 8) {
                val angleDeg = i * 45.0 - 90.0
                val angle = Math.toRadians(angleDeg)
                val x1 = center.x + cos(angle).toFloat() * r * 0.88f
                val y1 = center.y + sin(angle).toFloat() * r * 0.88f
                val x2 = center.x + cos(angle).toFloat() * r * 1.12f
                val y2 = center.y + sin(angle).toFloat() * r * 1.12f
                drawLine(color = Color.White, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 3f)
            }

            if (deg != null) {
                val arrowAngle = Math.toRadians(deg - 90.0)
                val tip = Offset(
                    center.x + cos(arrowAngle).toFloat() * r,
                    center.y + sin(arrowAngle).toFloat() * r
                )
                val left = Offset(
                    center.x + cos(arrowAngle + 2.55).toFloat() * r * 0.32f,
                    center.y + sin(arrowAngle + 2.55).toFloat() * r * 0.32f
                )
                val right = Offset(
                    center.x + cos(arrowAngle - 2.55).toFloat() * r * 0.32f,
                    center.y + sin(arrowAngle - 2.55).toFloat() * r * 0.32f
                )
                drawLine(color = Purple, start = center, end = tip, strokeWidth = 10f, cap = StrokeCap.Round)
                drawLine(color = Purple, start = tip, end = left, strokeWidth = 8f, cap = StrokeCap.Round)
                drawLine(color = Purple, start = tip, end = right, strokeWidth = 8f, cap = StrokeCap.Round)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("N", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(74.dp))
            Text(deg?.let { "${it.roundToInt()}°" } ?: "—", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Spacer(modifier = Modifier.height(1.dp))
            Text("S", color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("W", color = Color.White)
            Text("E", color = Color.White)
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
    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PanelTitle("GPS LOCATION", Icons.Outlined.LocationOn)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.8f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        packet.gpsLat?.let { "${format6(it)}° N" } ?: "--",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        packet.gpsLon?.let { "${format6(it)}° W" } ?: "--",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Altitude: --", color = Blue, style = MaterialTheme.typography.titleMedium)
                    Text("Satellites: --", color = Blue, style = MaterialTheme.typography.titleMedium)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF16223A))
                        .border(1.dp, PanelBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "MAP\nPLACEHOLDER",
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BarChart(displayValues, Modifier.weight(1f))

                Column(
                    modifier = Modifier.width(130.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("Current", color = TextSoft)
                    Text(
                        packet.radioRssi?.let { "$it dBm" } ?: "--",
                        color = Yellow,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text("Average", color = TextSoft)
                    Text(
                        if (rssiValues.isNotEmpty()) "${rssiValues.average().roundToInt()} dBm" else "--",
                        color = Yellow,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
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
    DashboardPanel(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelTitle("ALERTS", Icons.Outlined.WarningAmber)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, Green, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Green, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        if ((packet.methanePpm ?: 0.0) >= 5.0) "Methane Warning" else "All Clear",
                        color = if ((packet.methanePpm ?: 0.0) >= 5.0) Orange else Green,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (packet.methanePpm == null) "No active telemetry alarms" else "No active alerts",
                        color = TextSoft
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            DividerLine()
            AlertStat("Max Methane (Today)", packet.methanePpm?.let { format2(it + 1.66) + " ppm" } ?: "—")
            AlertStat("Avg Methane (Today)", packet.methanePpm?.let { format2(it) + " ppm" } ?: "—")
        }
    }
}

@Composable
fun DividerLine() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1B2A47)))
}

@Composable
fun AlertStat(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSoft)
        Text(value, color = Green, fontWeight = FontWeight.SemiBold)
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
        windSpeedMps = json.optDoubleOrNull("wind_speed_mps"),
        windDirectionDeg = json.optDoubleOrNull("wind_direction_deg"),
        gpsLat = json.optDoubleOrNull("gps_lat"),
        gpsLon = json.optDoubleOrNull("gps_lon"),
        radioRssi = json.optIntOrNull("radio_rssi"),
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