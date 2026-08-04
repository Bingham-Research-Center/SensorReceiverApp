package com.example.methreceiver.util

fun sensitActiveStateLabel(
    state: Int?,
    transmittedName: String = ""
): String {
    if (transmittedName.isNotBlank()) {
        return transmittedName
    }

    return when (state) {
        10 -> "Startup"
        20 -> "Initial warm-up"
        30 -> "Set parameters"
        35 -> "Final warm-up"
        37 -> "Housekeeping before ready"
        40 -> "Measurement"
        50 -> "Housekeeping"
        110 -> "Error state"
        null -> "No state data"
        else -> "Unknown state"
    }
}

fun sensitErrorLabel(
    errorId: Int?,
    transmittedName: String = ""
): String {
    if (transmittedName.isNotBlank()) {
        return transmittedName
    }

    return when (errorId) {
        0 -> "No error"
        1 -> "Laser temperature error 1"
        2 -> "Laser temperature error 2"
        3 -> "Laser temperature error 3"
        4 -> "EEPROM error"
        5 -> "Laser 1 voltage error"
        6 -> "Laser 1 current error"
        7 -> "Laser 2 voltage error"
        8 -> "Laser 2 current error"
        9 -> "Calibration error 1"
        10 -> "Low power error"
        11 -> "Centering error"
        12 -> "Negative reading error"
        13 -> "Calibration error 2"
        14 -> "Flat ramp error"
        null -> "No error data"
        else -> "Unknown error"
    }
}

fun sensitSensitivityLabel(
    level: Int?,
    transmittedLabel: String = ""
): String {
    if (transmittedLabel.isNotBlank()) {
        return transmittedLabel
    }

    return when (level) {
        0 -> "HIGH"
        1 -> "LOW"
        null -> "--"
        else -> "Unknown"
    }
}

fun isSensITNormalState(state: Int?): Boolean =
    state == 40 || state == 50