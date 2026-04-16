package com.byf3332.uv5rminicps.core

object Uv5rminiImageEditor {
    private fun channelOffset(ch: Int): Int = (ch - 1) * Uv5rminiSpec.CHANNEL_RECORD_SIZE

    fun extractChannels(image: ByteArray): List<Channel> {
        val out = mutableListOf<Channel>()
        for (ch in Uv5rminiSpec.MIN_CHANNEL..Uv5rminiSpec.MAX_CHANNEL) {
            val off = channelOffset(ch)
            if (off + Uv5rminiSpec.CHANNEL_RECORD_SIZE > image.size) break
            val rec = image.copyOfRange(off, off + Uv5rminiSpec.CHANNEL_RECORD_SIZE)
            val rx = Uv5rminiCodec.bcdLe4ToMhz(rec, 0)
            val tx = Uv5rminiCodec.bcdLe4ToMhz(rec, 4)
            if (rx == null && tx == null) continue
            out += Uv5rminiCodec.decodeChannel(rec, ch)
        }
        return out
    }

    fun applyStateOnImage(baseImage: ByteArray, state: RadioState): ByteArray {
        val out = baseImage.copyOf()

        for (ch in state.channels) {
            if (ch.channel !in Uv5rminiSpec.MIN_CHANNEL..Uv5rminiSpec.MAX_CHANNEL) continue
            val off = channelOffset(ch.channel)
            if (off + Uv5rminiSpec.CHANNEL_RECORD_SIZE > out.size) continue
            val baseRec = out.copyOfRange(off, off + Uv5rminiSpec.CHANNEL_RECORD_SIZE)
            val patched = Uv5rminiCodec.encodeChannel(baseRec, ch)
            patched.copyInto(out, off)
        }

        for (ch in state.deletedChannels) {
            if (ch !in Uv5rminiSpec.MIN_CHANNEL..Uv5rminiSpec.MAX_CHANNEL) continue
            val off = channelOffset(ch)
            if (off + Uv5rminiSpec.CHANNEL_RECORD_SIZE > out.size) continue
            Uv5rminiSpec.EMPTY_CHANNEL_RECORD.copyInto(out, off)
        }

        return out
    }
}
