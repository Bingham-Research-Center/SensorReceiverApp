package com.example.methreceiver.logging

import com.example.methreceiver.TelemetryPacket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun defaultSensITCsvFileName(): String {
    val timestamp =
        SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

    return "sensit_telemetry_$timestamp.csv"
}

fun sensitCsvHeader(): String {
    return listOf(
        "timestamp_iso",
        "sensit_concentration_ppm",
        "sensit_error_id",
        "sensit_active_state",
        "sensit_counter_m",
        "sensit_external_connection_flag",
        "sensit_concentration_raw_ppm",
        "sensit_concentration_calibrated_ppm",
        "sensit_concentration_temperature_compensated_ppm",
        "sensit_phd_power_v",
        "sensit_sensitivity_level",
        "sensit_cell_temperature_c",
        "sensit_laser_temperature_c",
        "sensit_laser_voltage_v",
        "sensit_laser_current",
        "sensit_bit_depth",
        "sensit_last_time_acq"
    ).joinToString(",") + "\n"
}

fun sensitCsvRow(packet: TelemetryPacket): String {
    val sensit = packet.sensit

    fun number(value: Number?): String =
        value?.toString() ?: ""

    return listOf(
        packet.timestamp,
        number(sensit.concentration),
        number(sensit.errorId),
        number(sensit.activeState),
        number(sensit.counterM),
        number(sensit.extConnection),
        number(sensit.concentrationRaw),
        number(sensit.concentrationCal),
        number(sensit.concentrationTemp),
        number(sensit.phdPower),
        number(sensit.sensitivityLevel),
        number(sensit.temperature),
        number(sensit.laserTemperature),
        number(sensit.laserVoltage),
        number(sensit.laserCurrent),
        number(sensit.bit),
        number(sensit.lastTimeAcq)
    ).joinToString(",") + "\n"
}