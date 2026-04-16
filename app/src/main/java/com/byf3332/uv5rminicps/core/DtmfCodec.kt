package com.byf3332.uv5rminicps.core

import java.nio.charset.Charset

data class DtmfContact(
    val index: Int,
    var code: String = "",
    var name: String = "",
)

data class DtmfState(
    var contacts: MutableList<DtmfContact> = mutableListOf(),
    var localId: String = "",
    var codeDurationMs: String = "100ms",
    var codeGapMs: String = "50ms",
    var hangup: String = "4s",
    var separator: String = "*",
    var groupCall: String = "#",
    var onlineCode: String = "",
    var offlineCode: String = "",
)

object DtmfCodec {
    private const val CONTACT_COUNT = 20
    private val gb2312: Charset = Charset.forName("GB2312")
    private const val DTMF_TABLE = "0123456789ABCD*#"

    private const val BLK_A000 = 0xA000
    private const val BLK_A040 = 0xA040
    private const val BLK_A080 = 0xA080
    private const val BLK_A0C0 = 0xA0C0
    private const val BLK_A100 = 0xA100
    private const val BLK_A140 = 0xA140
    private const val BLK_A180 = 0xA180

    private const val MEMBER_CODE_OFFSET = 0
    private const val MEMBER_NAME_OFFSET = 5

    private val memberRecordBases = intArrayOf(
        0xA000 + 0x20,
        0xA000 + 0x30,
        0xA040 + 0x00,
        0xA040 + 0x10,
        0xA040 + 0x20,
        0xA040 + 0x30,
        0xA080 + 0x00,
        0xA080 + 0x10,
        0xA080 + 0x20,
        0xA080 + 0x30,
        0xA0C0 + 0x00,
        0xA0C0 + 0x10,
        0xA0C0 + 0x20,
        0xA0C0 + 0x30,
        0xA100 + 0x00,
        0xA100 + 0x10,
        0xA100 + 0x20,
        0xA100 + 0x30,
        0xA140 + 0x00,
        0xA140 + 0x10,
    )

    fun codeDurationOptions(): List<String> = listOf("50ms", "100ms", "200ms", "300ms", "400ms", "500ms")
    fun codeGapOptions(): List<String> = listOf("50ms", "100ms", "200ms", "300ms", "400ms", "500ms")
    fun hangupOptions(): List<String> = (3..10).map { "${it}s" }
    fun separatorOptions(): List<String> = listOf("A", "B", "C", "D", "*", "#")
    fun groupCallOptions(): List<String> = listOf("OFF") + separatorOptions()

    fun decode(image: ByteArray): DtmfState {
        if (image.size < 0xA160) return emptyState()
        val contacts = MutableList(CONTACT_COUNT) { i -> DtmfContact(index = i + 1) }
        readContacts(image, contacts)

        val localId = decodeDtmfWord(image, BLK_A000 + 0x00, 3)
        val online = decodeDtmfWord(image, BLK_A180 + 0x00, 16)
        val offline = decodeDtmfWord(image, BLK_A180 + 0x10, 16)
        val duration = codeDurationOptions().getOrElse(image.byteAt(BLK_A000 + 0x07) % 6) { "100ms" }
        val gap = codeGapOptions().getOrElse(image.byteAt(BLK_A000 + 0x08) % 6) { "50ms" }
        val hangup = "4s"
        val sep = separatorOptions().getOrElse(image.byteAt(BLK_A000 + 0x09) % 6) { "*" }
        val group = groupCallOptions().getOrElse(image.byteAt(BLK_A000 + 0x0A) % 7) { "OFF" }

        return DtmfState(
            contacts = contacts,
            localId = localId,
            codeDurationMs = duration,
            codeGapMs = gap,
            hangup = hangup,
            separator = sep,
            groupCall = group,
            onlineCode = online,
            offlineCode = offline,
        )
    }

    fun apply(image: ByteArray, state: DtmfState): ByteArray {
        if (image.size < 0xA160) return image
        val out = image.copyOf()

        writeContacts(out, state.contacts)

        encodeDtmfWord(out, BLK_A000 + 0x00, 3, state.localId)
        out[BLK_A000 + 0x07] = codeDurationOptions().indexOf(state.codeDurationMs).let { if (it < 0) 1 else it }.toByte()
        out[BLK_A000 + 0x08] = codeGapOptions().indexOf(state.codeGapMs).let { if (it < 0) 1 else it }.toByte()
        out[BLK_A000 + 0x09] = separatorOptions().indexOf(state.separator).let { if (it < 0) 0 else it }.toByte()
        out[BLK_A000 + 0x0A] = groupCallOptions().indexOf(state.groupCall).let { if (it < 0) 0 else it }.toByte()
        encodeDtmfWord(out, BLK_A180 + 0x00, 16, state.onlineCode)
        encodeDtmfWord(out, BLK_A180 + 0x10, 16, state.offlineCode)
        return out
    }

    private fun emptyState(): DtmfState {
        return DtmfState(
            contacts = (1..CONTACT_COUNT).map { DtmfContact(it) }.toMutableList()
        )
    }

    private fun ByteArray.byteAt(index: Int): Int {
        if (index !in indices) return 0xFF
        return this[index].toInt() and 0xFF
    }

    private fun readContacts(image: ByteArray, contacts: MutableList<DtmfContact>) {
        memberRecordBases.forEachIndexed { idx, base ->
            val c = contacts[idx]
            c.code = decodeDtmfWord(image, base + MEMBER_CODE_OFFSET, 3)
            c.name = decodeName(image, base + MEMBER_NAME_OFFSET, 10)
        }
    }

    private fun writeContacts(out: ByteArray, contacts: MutableList<DtmfContact>) {
        memberRecordBases.forEachIndexed { idx, base ->
            val c = contacts.getOrNull(idx) ?: DtmfContact(idx + 1)
            encodeDtmfWord(out, base + MEMBER_CODE_OFFSET, 3, c.code)
            encodeName(out, base + MEMBER_NAME_OFFSET, 10, c.name)
        }
    }

    private fun decodeName(image: ByteArray, offset: Int, length: Int): String {
        val bytes = mutableListOf<Byte>()
        for (i in 0 until length) {
            val b = image[offset + i]
            val v = b.toInt() and 0xFF
            if (v == 0xFF || v == 0x00) break
            bytes += b
        }
        return bytes.toByteArray().toString(gb2312).trim()
    }

    private fun encodeName(out: ByteArray, offset: Int, length: Int, text: String) {
        encodeFixedString(text, length, gb2312, 0xFF).copyInto(out, offset)
    }

    private fun decodeDtmfWord(image: ByteArray, offset: Int, maxLen: Int): String {
        if (image.byteAt(offset) == 0xFF) return ""
        var n = 0
        while (n < maxLen && image.byteAt(offset + n) != 0xFF) n++
        val sb = StringBuilder()
        for (i in 0 until n) {
            val idx = image.byteAt(offset + i) % 16
            sb.append(DTMF_TABLE[idx])
        }
        return sb.toString()
    }

    private fun encodeDtmfWord(out: ByteArray, offset: Int, maxLen: Int, text: String) {
        for (i in 0 until maxLen) out[offset + i] = 0xFF.toByte()
        val t = text.trim().uppercase()
        var pos = 0
        for (ch in t) {
            if (pos >= maxLen) break
            val idx = DTMF_TABLE.indexOf(ch)
            if (idx < 0) break
            out[offset + pos] = idx.toByte()
            pos += 1
        }
    }

    private fun encodeFixedString(text: String, length: Int, charset: Charset, pad: Int): ByteArray {
        val out = ByteArray(length) { pad.toByte() }
        val raw = text.trim().toByteArray(charset)
        val n = minOf(length, raw.size)
        System.arraycopy(raw, 0, out, 0, n)
        return out
    }
}
