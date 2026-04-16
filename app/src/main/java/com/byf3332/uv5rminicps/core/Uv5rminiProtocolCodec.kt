package com.byf3332.uv5rminicps.core

object Uv5rminiProtocolCodec {
    private val defaultKey = byteArrayOf('C'.code.toByte(), 'O'.code.toByte(), ' '.code.toByte(), '7'.code.toByte())

    fun parseReadBlocksToDecrypted64(readBlocks: Map<String, String>): Map<Int, ByteArray> {
        val out = linkedMapOf<Int, ByteArray>()
        for ((k, v) in readBlocks) {
            if (k.length != 6) continue
            val addr = k.hexToBytes()
            if (addr.size != 3) continue
            if (addr[2] != 0x80.toByte()) continue
            val hi = addr[0].toInt() and 0xFF
            val lo = addr[1].toInt() and 0xFF
            val payload128 = v.hexToBytes()
            if (payload128.size != 128) continue
            val p1 = payload128.copyOfRange(0, 64)
            val p2 = payload128.copyOfRange(64, 128)
            when (lo) {
                0x00 -> {
                    out[(hi shl 8) or 0x00] = cryptPayload(p1)
                    out[(hi shl 8) or 0x40] = cryptPayload(p2)
                }
                0x80 -> {
                    out[(hi shl 8) or 0x80] = cryptPayload(p1)
                    out[(hi shl 8) or 0xC0] = cryptPayload(p2)
                }
            }
        }
        return out
    }

    fun sparseFromBlocks64(blocks64: Map<Int, ByteArray>): ByteArray {
        if (blocks64.isEmpty()) return ByteArray(0)
        val maxEnd = blocks64.maxOf { it.key + it.value.size }
        val img = ByteArray(maxEnd) { 0xFF.toByte() }
        for ((addr, data) in blocks64) {
            System.arraycopy(data, 0, img, addr, data.size)
        }
        return img
    }

    fun buildWriteImageFromSparse(img: ByteArray): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (addr3 in protocolAddresses()) {
            val hi = addr3[0].toInt() and 0xFF
            val lo = addr3[1].toInt() and 0xFF
            val a16 = if (lo == 0x00) ((hi shl 8) or 0x00) else ((hi shl 8) or 0x80)
            val b16 = if (lo == 0x00) ((hi shl 8) or 0x40) else ((hi shl 8) or 0xC0)
            val a = cryptPayload(slice64(img, a16))
            val b = cryptPayload(slice64(img, b16))
            out[addr3.toHex()] = (a + b).toHex()
        }
        return out
    }

    private fun slice64(img: ByteArray, addr: Int): ByteArray {
        val out = ByteArray(64) { 0xFF.toByte() }
        if (addr >= img.size) return out
        val n = minOf(64, img.size - addr)
        System.arraycopy(img, addr, out, 0, n)
        return out
    }

    private fun maybeXor(v: Int, k: Int): Int {
        if (k == 0x20) return v
        if (v == 0x00 || v == 0xFF || v == k || v == (k xor 0xFF)) return v
        return v xor k
    }

    private fun cryptPayload(payload: ByteArray): ByteArray {
        val out = payload.copyOf()
        for (i in out.indices) {
            val v = out[i].toInt() and 0xFF
            val k = defaultKey[i % 4].toInt() and 0xFF
            out[i] = maybeXor(v, k).toByte()
        }
        return out
    }

    private fun protocolAddresses(): List<ByteArray> {
        val list = ArrayList<ByteArray>(260)
        for (hi in 0x00..0x7C) {
            list += byteArrayOf(hi.toByte(), 0x00, 0x80.toByte())
            list += byteArrayOf(hi.toByte(), 0x80.toByte(), 0x80.toByte())
        }
        list += byteArrayOf(0x80.toByte(), 0x00, 0x80.toByte())
        list += byteArrayOf(0x90.toByte(), 0x00, 0x80.toByte())
        list += byteArrayOf(0xA0.toByte(), 0x00, 0x80.toByte())
        list += byteArrayOf(0xA0.toByte(), 0x80.toByte(), 0x80.toByte())
        list += byteArrayOf(0xA1.toByte(), 0x00, 0x80.toByte())
        list += byteArrayOf(0xA1.toByte(), 0x80.toByte(), 0x80.toByte())
        return list
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    val s = trim()
    require(s.length % 2 == 0)
    val out = ByteArray(s.length / 2)
    var i = 0
    while (i < s.length) {
        out[i / 2] = s.substring(i, i + 2).toInt(16).toByte()
        i += 2
    }
    return out
}
