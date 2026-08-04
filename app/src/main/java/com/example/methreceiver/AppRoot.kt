package com.example.methreceiver

import com.example.methreceiver.ui.common.*
import com.example.methreceiver.ui.sensit.SensITScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WS_URL = "ws://192.168.4.1:8765"

enum class AppPage {
    Dashboard,
    SensIT
}

@Composable
fun MethReceiverApp() {
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

    var page by remember {
        mutableStateOf(AppPage.Dashboard)
    }

    /*
     * Shared telemetry state.
     *
     * These values stay alive while switching between Dashboard and SensIT.
     */
    var latestPacket by remember {
        mutableStateOf(TelemetryPacket())
    }

    var connectionState by remember {
        mutableStateOf(ConnectionState())
    }

    var isConnecting by remember {
        mutableStateOf(false)
    }

    val packetArrivalTimes = remember {
        mutableStateListOf<Long>()
    }

    /*
     * Shared settings used by the constant header.
     */
    var showSettingsDialog by remember {
        mutableStateOf(false)
    }

    var batteryMinVoltage by remember {
        mutableStateOf(3.0)
    }

    var batteryMaxVoltage by remember {
        mutableStateOf(5.0)
    }

    var batteryLowPercent by remember {
        mutableStateOf(25f)
    }

    var batteryCriticalPercent by remember {
        mutableStateOf(10f)
    }

    /*
     * One WebSocket connection for the entire application.
     *
     * Do not create a second WebSocket in Dashboard or SensIT.
     */
    val socketManager = remember {
        TelemetryWebSocketClient(
            onConnecting = {
                isConnecting = true

                connectionState = connectionState.copy(
                    connected = false,
                    status = "Connecting..."
                )
            },

            onConnected = {
                isConnecting = false

                connectionState = connectionState.copy(
                    connected = true,
                    status = "Connected"
                )
            },

            onDisconnected = { reason ->
                isConnecting = false

                connectionState = connectionState.copy(
                    connected = false,
                    status = reason
                )
            },

            onPacket = { packet ->
                latestPacket = packet

                val now = System.currentTimeMillis()

                packetArrivalTimes.add(now)

                while (
                    packetArrivalTimes.isNotEmpty() &&
                    now - packetArrivalTimes.first() > 5_000L
                ) {
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
            }
        )
    }

    /*
     * Connect once when the application enters composition.
     */
    DisposableEffect(Unit) {
        socketManager.connect(WS_URL)

        onDispose {
            socketManager.disconnect()
        }
    }

    /*
     * Background automatic reconnect.
     */
    LaunchedEffect(
        connectionState.connected,
        isConnecting
    ) {
        if (!connectionState.connected && !isConnecting) {
            delay(3_000L)

            if (!connectionState.connected && !isConnecting) {
                socketManager.connect(WS_URL)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,

        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = HeaderBg,
                drawerContentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Text(
                    text = "Sensor Receiver",
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                NavigationDrawerItem(
                    label = {
                        Text("Dashboard")
                    },

                    selected = page == AppPage.Dashboard,

                    onClick = {
                        page = AppPage.Dashboard

                        scope.launch {
                            drawerState.close()
                        }
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("SensIT")
                    },

                    selected = page == AppPage.SensIT,

                    onClick = {
                        page = AppPage.SensIT

                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DashboardBg)
                .systemBarsPadding()
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),

            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            /*
             * Constant header.
             *
             * This composable is outside the page-selection `when`, so it
             * remains unchanged when the user switches pages.
             */
            HeaderRow(
                state = connectionState,
                packet = latestPacket,
                isConnecting = isConnecting,
                batteryMinVoltage = batteryMinVoltage,
                batteryMaxVoltage = batteryMaxVoltage,
                batteryLowPercent = batteryLowPercent,
                batteryCriticalPercent = batteryCriticalPercent,

                onMenuClicked = {
                    scope.launch {
                        drawerState.open()
                    }
                },

                onSettingsClicked = {
                    showSettingsDialog = true
                }
            )

            /*
             * Only this content changes when selecting another page.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (page) {
                    AppPage.Dashboard -> {
                        ReceiverDashboardContent(
                            latestPacket = latestPacket,
                            connectionState = connectionState,

                            onReconnect = {
                                socketManager.connect(WS_URL)
                            },

                            onCheckRadio = {
                                socketManager.sendRadioCheckRequest()
                            }
                        )
                    }

                    AppPage.SensIT -> {
                        SensITScreen(
                            packet = latestPacket,
                            connectionState = connectionState
                        )
                    }
                }
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                batteryMinVoltage = batteryMinVoltage,
                batteryMaxVoltage = batteryMaxVoltage,
                batteryLowPercent = batteryLowPercent,
                batteryCriticalPercent = batteryCriticalPercent,

                onBatteryMinVoltageChanged = {
                    batteryMinVoltage = it
                },

                onBatteryMaxVoltageChanged = {
                    batteryMaxVoltage = it
                },

                onBatteryLowPercentChanged = {
                    batteryLowPercent = it
                },

                onBatteryCriticalPercentChanged = {
                    batteryCriticalPercent = it
                },

                onDismiss = {
                    showSettingsDialog = false
                }
            )
        }
    }
}