package com.example.methreceiver.model

data class SensITSample(
    val timeMillis: Long,

    val concentration: Double?,
    val concentrationRaw: Double?,
    val concentrationCal: Double?,
    val concentrationTemp: Double?,

    val temperature: Double?,
    val laserTemperature: Double?,

    val phdPower: Double?,
    val laserVoltage: Double?,
    val laserCurrent: Double?
)