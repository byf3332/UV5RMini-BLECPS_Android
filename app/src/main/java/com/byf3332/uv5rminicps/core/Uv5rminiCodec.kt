package com.byf3332.uv5rminicps.core

import java.nio.charset.Charset
import kotlin.math.roundToInt

object Uv5rminiCodec {
    private val gb2312: Charset = Charset.forName("GB2312")
    private val dcsCodes = listOf(
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74,
        114, 115, 116, 125, 131, 132, 134, 143, 145, 152, 155, 156, 162, 165, 172, 174,
        205, 212, 223, 225, 226, 243, 244, 245, 246, 251, 252, 255, 261, 263, 265, 266, 271, 274,
        306, 311, 315, 325, 331, 332, 343, 346, 351, 356, 364, 365, 371,
        411, 412, 413, 423, 431, 432, 445, 446, 452, 454, 455, 462, 464, 465, 466,
        503, 506, 516, 523, 526, 532, 546, 565,
        606, 612, 624, 627, 631, 632, 654, 662, 664, 703, 712, 723, 731, 732, 734, 743, 754
    )

    fun bcdLe4ToMhz(bytes: ByteArray, offset: Int): Double? {
        val slice = bytes.copyOfRange(offset, offset + 4)
        if (slice.all { it.toInt() and 0xFF == 0xFF }) return null
        val digits = StringBuilder()
        for (i in 3 downTo 0) {
            val b = slice[i].toInt() and 0xFF
            val hi = (b ushr 4) and 0x0F
            val lo = b and 0x0F
            if (hi > 9 || lo > 9) return null
            digits.append(hi).append(lo)
        }
        return digits.toString().toInt() / 100000.0
    }

    fun mhzToBcdLe4(mhz: Double): ByteArray {
        val scaled = (mhz * 100000.0).roundToInt().coerceIn(0, 99_999_999)
        val s = scaled.toString().padStart(8, '0')
        val out = ByteArray(4)
        for (i in 0 until 4) {
            val hi = s[(3 - i) * 2].digitToInt()
            val lo = s[(3 - i) * 2 + 1].digitToInt()
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun decodeName12(record: ByteArray, offset: Int = 20): String {
        val out = ArrayList<Byte>(12)
        for (i in 0 until 12) {
            val b = record[offset + i]
            if ((b.toInt() and 0xFF) == 0xFF) break
            out.add(b)
        }
        return out.toByteArray().toString(gb2312).trim()
    }

    fun encodeName12(name: String): ByteArray {
        val raw = name.toByteArray(gb2312)
        val out = ByteArray(12) { 0xFF.toByte() }
        val n = minOf(12, raw.size)
        System.arraycopy(raw, 0, out, 0, n)
        return out
    }

    fun decodeChannel(record: ByteArray, channelNo: Int): Channel {
        val b0f = record[15].toInt() and 0xFF
        return Channel(
            channel = channelNo,
            rxFreqMhz = bcdLe4ToMhz(record, 0),
            txFreqMhz = bcdLe4ToMhz(record, 4),
            rxTone = decodeTone(record[8].toInt() and 0xFF, record[9].toInt() and 0xFF),
            txTone = decodeTone(record[10].toInt() and 0xFF, record[11].toInt() and 0xFF),
            signalGroupUi = (record[12].toInt() and 0xFF) % 20 + 1,
            pttIdModeIndex = (record[13].toInt() and 0xFF) % 4,
            txPower = if (((record[14].toInt() and 0xFF) % 2) == 0) "high" else "low",
            bandwidth = if (((b0f ushr 6) and 1) == 1) "narrow" else "wide",
            sqModeIndex = (b0f ushr 4) and 0x03,
            busyLock = (b0f ushr 3) and 1,
            scanAdd = (b0f ushr 2) and 1,
            fhss = b0f and 1,
            name = decodeName12(record),
        )
    }

    fun encodeChannel(baseRecord: ByteArray, ch: Channel): ByteArray {
        val rec = baseRecord.copyOf()
        if (isEmptyChannelRecord(baseRecord)) {
            // Fresh channels in UV5R Mini expect a canonical control byte baseline.
            rec[15] = 0x33
        }
        ch.rxFreqMhz?.let { mhzToBcdLe4(it).copyInto(rec, 0) }
        ch.txFreqMhz?.let { mhzToBcdLe4(it).copyInto(rec, 4) }
        encodeTone(ch.rxTone).copyInto(rec, 8)
        encodeTone(ch.txTone).copyInto(rec, 10)
        rec[12] = ((ch.signalGroupUi - 1).mod(20)).toByte()
        rec[13] = (ch.pttIdModeIndex.mod(4)).toByte()
        rec[14] = if (ch.txPower.lowercase() == "high") 0 else 1

        var b0f = rec[15].toInt() and 0xFF
        b0f = if (ch.bandwidth.lowercase() == "narrow") b0f or 0x40 else b0f and 0xBF
        b0f = (b0f and 0xCF) or ((ch.sqModeIndex and 0x03) shl 4)
        b0f = (b0f and 0xF7) or ((ch.busyLock and 1) shl 3)
        b0f = (b0f and 0xFB) or ((ch.scanAdd and 1) shl 2)
        b0f = (b0f and 0xFE) or (ch.fhss and 1)
        rec[15] = b0f.toByte()

        encodeName12(ch.name).copyInto(rec, 20)
        return rec
    }

    private fun isEmptyChannelRecord(rec: ByteArray): Boolean {
        if (rec.size < 32) return false
        val emptyFreq = (0..7).all { (rec[it].toInt() and 0xFF) == 0xFF }
        val emptyTone = (8..11).all { (rec[it].toInt() and 0xFF) == 0x00 }
        val emptyFlags = (12..15).all { (rec[it].toInt() and 0xFF) == 0x00 }
        return emptyFreq && emptyTone && emptyFlags
    }

    private fun decodeTone(lo: Int, hi: Int): String {
        if (hi == 0) {
            if (lo == 0 || lo > 0xD2) return "OFF"
            if (lo in 1..dcsCodes.size) {
                return "D%03dN".format(dcsCodes[lo - 1])
            }
            if (lo in (dcsCodes.size + 1)..(dcsCodes.size * 2)) {
                return "D%03dI".format(dcsCodes[lo - dcsCodes.size - 1])
            }
            return "DCS_IDX_$lo"
        }
        if (lo == 0 || lo == 0xFF) return "OFF"
        val v = (hi shl 8) or lo
        return String.format("%.1f", v / 10.0)
    }

    private fun encodeTone(text: String): ByteArray {
        val t = text.trim().uppercase()
        if (t.isEmpty() || t == "OFF" || t == "0") return byteArrayOf(0, 0)
        if (t.startsWith("DCS_IDX_")) {
            val idx = t.substringAfter("DCS_IDX_").toIntOrNull() ?: 0
            return byteArrayOf((idx and 0xFF).toByte(), 0x00)
        }
        val dcsMatch = Regex("""D(\d{3})([NI])""").matchEntire(t)
        if (dcsMatch != null) {
            val code = dcsMatch.groupValues[1].toIntOrNull() ?: 0
            val pol = dcsMatch.groupValues[2]
            val idx = dcsCodes.indexOf(code)
            if (idx >= 0) {
                val raw = if (pol == "I") idx + 1 + dcsCodes.size else idx + 1
                return byteArrayOf((raw and 0xFF).toByte(), 0x00)
            }
        }
        val v = (t.toDoubleOrNull()?.times(10.0)?.toInt()) ?: 0
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }
}
