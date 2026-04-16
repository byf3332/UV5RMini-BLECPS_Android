package com.byf3332.uv5rminicps.core

import java.util.Locale
import kotlin.math.roundToInt

data class VfoState(
    var freqMhz: Double? = null,
    var rxTone: String = "OFF",
    var txTone: String = "OFF",
    var sqModeIndex: Int = 0,
    var power: String = "high",
    var bandwidth: String = "wide",
    var stepIndex: Int = 1,
    var signalGroupUi: Int = 1,
    var offsetDir: String = "off",
    var offsetMhz: Double = 0.0,
    var fhss: Int = 0,
)

object VfoCodec {
    private const val A_OFF = 0x8000
    private const val B_OFF = 0x8020
    private val dcsCodes = listOf(
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74,
        114, 115, 116, 125, 131, 132, 134, 143, 145, 152, 155, 156, 162, 165, 172, 174,
        205, 212, 223, 225, 226, 243, 244, 245, 246, 251, 252, 255, 261, 263, 265, 266, 271, 274,
        306, 311, 315, 325, 331, 332, 343, 346, 351, 356, 364, 365, 371,
        411, 412, 413, 423, 431, 432, 445, 446, 452, 454, 455, 462, 464, 465, 466,
        503, 506, 516, 523, 526, 532, 546, 565,
        606, 612, 624, 627, 631, 632, 654, 662, 664, 703, 712, 723, 731, 732, 734, 743, 754
    )

    fun decode(image: ByteArray): Pair<VfoState, VfoState> {
        if (image.size < B_OFF + 32) return VfoState() to VfoState()
        return decodeOne(image, A_OFF) to decodeOne(image, B_OFF)
    }

    fun apply(image: ByteArray, a: VfoState, b: VfoState): ByteArray {
        if (image.size < B_OFF + 32) return image
        val out = image.copyOf()
        encodeOne(out, A_OFF, a)
        encodeOne(out, B_OFF, b)
        return out
    }

    private fun decodeOne(img: ByteArray, off: Int): VfoState {
        val rec = img.copyOfRange(off, off + 32)
        val e = rec[0x0E].toInt() and 0xFF
        val signalRaw = e and 0x0F
        return VfoState(
            // CPS.c CaculateFreq(): 8 decimal-digit bytes + inserted dot before 5 decimals.
            freqMhz = decodeFreqDigits(rec, 0),
            rxTone = decodeTone(rec[0x08].toInt() and 0xFF, rec[0x09].toInt() and 0xFF),
            txTone = decodeTone(rec[0x0A].toInt() and 0xFF, rec[0x0B].toInt() and 0xFF),
            sqModeIndex = (rec[0x1B].toInt() and 0xFF) % 4,
            power = if ((rec[0x10].toInt() and 0xFF) % 2 == 0) "high" else "low",
            bandwidth = if (((rec[0x11].toInt() and 0xFF) ushr 6 and 1) == 1) "narrow" else "wide",
            stepIndex = (rec[0x13].toInt() and 0xFF) % 6,
            // UI is 1-based; payload low nibble is 0-based for this item.
            signalGroupUi = (signalRaw % 16) + 1,
            offsetDir = when (((rec[0x0E].toInt() and 0xFF) ushr 4) and 0x03) {
                1 -> "+"
                2 -> "-"
                else -> "off"
            },
            // CPS.c CaculateOffset(): 7 decimal-digit bytes + inserted dot before 4 decimals.
            offsetMhz = decodeOffsetDigits(rec, 0x14) ?: 0.0,
            fhss = (rec[0x11].toInt() and 0x01),
        )
    }

    private fun encodeOne(img: ByteArray, off: Int, v: VfoState) {
        val rec = img.copyOfRange(off, off + 32)
        // Keep untouched bytes untouched, patch only CPS-mapped VFO fields.
        v.freqMhz?.let { encodeFreqDigits(it).copyInto(rec, 0x00) }
        encodeTone(v.rxTone).copyInto(rec, 0x08)
        encodeTone(v.txTone).copyInto(rec, 0x0A)

        val dir = when (v.offsetDir.trim()) {
            "+" -> 1
            "-" -> 2
            else -> 0
        } and 0x03
        val sig = ((v.signalGroupUi - 1).coerceIn(0, 15) and 0x0F)
        rec[0x0E] = ((dir shl 4) or sig).toByte()

        rec[0x10] = if (v.power.equals("high", true)) 0 else 1

        var b11 = rec[0x11].toInt() and 0xFF
        b11 = if (v.bandwidth.equals("narrow", true)) b11 or 0x40 else b11 and 0xBF
        b11 = (b11 and 0xFE) or (v.fhss and 1)
        rec[0x11] = b11.toByte()

        rec[0x13] = (v.stepIndex.coerceIn(0, 5)).toByte()
        encodeOffsetDigits(v.offsetMhz).copyInto(rec, 0x14)
        rec[0x1B] = (v.sqModeIndex.coerceIn(0, 3)).toByte()
        System.arraycopy(rec, 0, img, off, 32)
    }

    private fun decodeFreqDigits(bytes: ByteArray, offset: Int): Double? {
        if ((bytes[offset].toInt() and 0xFF) == 0xFF) return null
        val sb = StringBuilder(8)
        for (i in 0 until 8) {
            val d = bytes[offset + i].toInt() and 0xFF
            if (d > 9) return null
            sb.append(d)
        }
        val s = sb.toString()
        return s.toIntOrNull()?.div(100000.0)
    }

    private fun encodeFreqDigits(mhz: Double): ByteArray {
        val scaled = (mhz * 100000.0).roundToInt().coerceIn(0, 999_99999)
        val s = scaled.toString().padStart(8, '0')
        val out = ByteArray(8)
        for (i in 0 until 8) out[i] = s[i].digitToInt().toByte()
        return out
    }

    private fun decodeOffsetDigits(bytes: ByteArray, offset: Int): Double? {
        if ((bytes[offset].toInt() and 0xFF) == 0xFF) return null
        val sb = StringBuilder(7)
        for (i in 0 until 7) {
            val d = bytes[offset + i].toInt() and 0xFF
            sb.append(if (d < 10) d else 0)
        }
        return sb.toString().toIntOrNull()?.div(10000.0)
    }

    private fun encodeOffsetDigits(mhz: Double): ByteArray {
        val scaled = (mhz * 10000.0).roundToInt().coerceIn(0, 9_999_999)
        val s = scaled.toString().padStart(7, '0')
        val out = ByteArray(7)
        for (i in 0 until 7) out[i] = s[i].digitToInt().toByte()
        return out
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
        return String.format(Locale.US, "%.1f", v / 10.0)
    }

    private fun encodeTone(text: String): ByteArray {
        val t = text.trim().uppercase(Locale.ROOT)
        if (t.isEmpty() || t == "OFF" || t == "0") return byteArrayOf(0, 0)
        if (t.startsWith("DCS_IDX_")) {
            val idx = t.substringAfter("DCS_IDX_").toIntOrNull() ?: 0
            return byteArrayOf((idx and 0xFF).toByte(), 0)
        }
        val dcsMatch = Regex("""D(\d{3})([NI])""").matchEntire(t)
        if (dcsMatch != null) {
            val code = dcsMatch.groupValues[1].toIntOrNull() ?: 0
            val pol = dcsMatch.groupValues[2]
            val idx = dcsCodes.indexOf(code)
            if (idx >= 0) {
                val raw = if (pol == "I") idx + 1 + dcsCodes.size else idx + 1
                return byteArrayOf((raw and 0xFF).toByte(), 0)
            }
        }
        val v = ((t.toDoubleOrNull() ?: 0.0) * 10.0).toInt()
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }

}
