package com.example.methreceiver.model

data class SensITPacket(
    val concentration: Double? = null,

    val errorId: Int? = null,
    val errorName: String = "",

    val activeState: Int? = null,
    val activeStateName: String = "",

    val counterM: Int? = null,

    val extConnection: Int? = null,
    val extConnected: Boolean? = null,

    val concentrationRaw: Double? = null,
    val concentrationCal: Double? = null,
    val concentrationTemp: Double? = null,

    val phdPower: Double? = null,

    val sensitivityLevel: Int? = null,
    val sensitivityLabel: String = "",

    val temperature: Double? = null,
    val laserTemperature: Double? = null,
    val laserVoltage: Double? = null,
    val laserCurrent: Double? = null,

    val bit: Int? = null,
    val lastTimeAcq: Double? = null
) {
    val hasData: Boolean
        get() =
            concentration != null ||
                    activeState != null ||
                    errorId != null ||
                    counterM != null
}