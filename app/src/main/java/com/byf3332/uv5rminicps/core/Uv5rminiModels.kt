package com.byf3332.uv5rminicps.core

object Uv5rminiSpec {
    const val MIN_CHANNEL = 1
    const val MAX_CHANNEL = 999
    const val CHANNEL_RECORD_SIZE = 32
    val EMPTY_CHANNEL_RECORD: ByteArray = byteArrayOf(
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
        0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
    )
}

data class Channel(
    val channel: Int,
    var rxFreqMhz: Double? = null,
    var txFreqMhz: Double? = null,
    var rxTone: String = "OFF",
    var txTone: String = "OFF",
    var signalGroupUi: Int = 1,
    var pttIdModeIndex: Int = 0,
    var txPower: String = "high",
    var bandwidth: String = "wide",
    var sqModeIndex: Int = 0,
    var scanAdd: Int = 1,
    var busyLock: Int = 0,
    var fhss: Int = 0,
    var name: String = "",
)

data class RadioState(
    val channels: MutableList<Channel> = mutableListOf(),
    val deletedChannels: MutableSet<Int> = sortedSetOf(),
)
