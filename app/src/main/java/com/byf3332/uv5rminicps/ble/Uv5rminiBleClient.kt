package com.byf3332.uv5rminicps.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

object Uv5rminiBleSpec {
    const val FFE1_UUID = "0000FFE1-0000-1000-8000-00805F9B34FB"
    const val FF31_UUID = "0000FF31-0000-1000-8000-00805F9B34FB"
    val SERVICE_UUID: UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    val FFE1: UUID = UUID.fromString(FFE1_UUID)
    val FF31: UUID = UUID.fromString(FF31_UUID)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    val CONTROL_INIT: ByteArray = byteArrayOf(
        0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )
    val PROGRAM_TOKEN = "PROGRAMCOLORPROU".encodeToByteArray()
    val FEATURE_QUERY = "F".encodeToByteArray()
    val MODEL_QUERY = "M".encodeToByteArray()
    val SEND_TOKEN = byteArrayOf(
        0x53, 0x45, 0x4E, 0x44, 0x21, 0x05, 0x0D, 0x01, 0x01, 0x01,
        0x04, 0x11, 0x08, 0x05, 0x0D, 0x0D, 0x01, 0x11, 0x0F, 0x09,
        0x12, 0x09, 0x10, 0x04, 0x00,
    )
    val EXIT_TOKEN = "E".encodeToByteArray()
    const val ACK: Byte = 0x06
}

/**
 * BLE protocol client for UV5R Mini-style radios.
 * Mirrors the validated Python transaction flow.
 */
class Uv5rminiBleClient(private val context: Context) {
    data class ScannedDevice(
        val address: String,
        val name: String?,
        val rssi: Int,
    )

    data class ReadResult(
        val bannerHex: String,
        val featuresHex: String,
        val modelAscii: String,
        val blocks: Map<String, String>,
    )

    @SuppressLint("MissingPermission")
    suspend fun readImage(
        address: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): ReadResult = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter ?: error("Bluetooth adapter unavailable")
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        val session = BleSession(context, device)
        session.start()
        try {
            val features = session.handshake()
            val blocks = session.readImage(onProgress)
            session.finish()
            ReadResult(
                bannerHex = session.bannerHex,
                featuresHex = features.first,
                modelAscii = features.second,
                blocks = blocks,
            )
        } finally {
            session.stop()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun writeImage(
        address: String,
        blocks: Map<String, String>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter ?: error("Bluetooth adapter unavailable")
        val device: BluetoothDevice = adapter.getRemoteDevice(address)
        val session = BleSession(context, device)
        session.start()
        try {
            session.handshake()
            session.replayWriteImage(blocks, onProgress)
            try {
                session.finish()
            } catch (_: Throwable) {
                // Device may reboot before final ACK.
            }
        } finally {
            session.stop()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun scanDevices(
        durationMs: Long = 5_000L,
        onUpdate: ((List<ScannedDevice>) -> Unit)? = null,
    ): List<ScannedDevice> = withContext(Dispatchers.Main) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter ?: error("Bluetooth adapter unavailable")
        if (!adapter.isEnabled) error("Bluetooth disabled")
        val scanner = adapter.bluetoothLeScanner ?: error("BLE scanner unavailable")
        val mainHandler = Handler(Looper.getMainLooper())

        val found = linkedMapOf<String, ScannedDevice>()
        fun emitSnapshot() {
            val snapshot = found.values.sortedByDescending { it.rssi }
            if (onUpdate != null) {
                mainHandler.post { onUpdate.invoke(snapshot) }
            }
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
                if (name.isNullOrBlank()) return
                val rssi = result.rssi
                val current = found[address]
                if (current == null || rssi > current.rssi) {
                    found[address] = ScannedDevice(
                        address = address,
                        name = name,
                        rssi = rssi,
                    )
                    emitSnapshot()
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
        }

        val filters = emptyList<android.bluetooth.le.ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, callback)
            delay(durationMs)
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        found.values.sortedByDescending { it.rssi }
    }
}

private class BleSession(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val notifyQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val writeQueue = Channel<Boolean>(Channel.UNLIMITED)
    private val connectedDeferred = CompletableDeferred<BluetoothGatt>()
    private val servicesDeferred = CompletableDeferred<Unit>()
    private val cccdDeferred = CompletableDeferred<Unit>()
    private val mtuDeferred = CompletableDeferred<Int>()
    private val failedDeferred = CompletableDeferred<Throwable>()

    private var gatt: BluetoothGatt? = null
    private lateinit var ffe1: BluetoothGattCharacteristic
    private lateinit var ff31: BluetoothGattCharacteristic
    var bannerHex: String = ""
        private set

    private val callback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failedDeferred.complete(RuntimeException("GATT connect failed: status=$status"))
                return
            }
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDeferred.complete(g)
                g.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                if (!failedDeferred.isCompleted) {
                    failedDeferred.complete(RuntimeException("GATT disconnected"))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDeferred.complete(Unit)
            } else {
                failedDeferred.complete(RuntimeException("discoverServices failed: $status"))
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == Uv5rminiBleSpec.FFE1) {
                notifyQueue.trySend(value.copyOf())
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == Uv5rminiBleSpec.FFE1) {
                notifyQueue.trySend(characteristic.value?.copyOf() ?: ByteArray(0))
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeQueue.trySend(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == Uv5rminiBleSpec.CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                cccdDeferred.complete(Unit)
            } else if (descriptor.uuid == Uv5rminiBleSpec.CCCD_UUID) {
                failedDeferred.complete(RuntimeException("CCCD write failed: $status"))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtuDeferred.complete(mtu)
            } else {
                failedDeferred.complete(RuntimeException("requestMtu failed: status=$status"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun start() {
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        val g = awaitEither(connectedDeferred, failedDeferred, 10_000L)
        awaitEither(servicesDeferred, failedDeferred, 10_000L)
        val service = g.getService(Uv5rminiBleSpec.SERVICE_UUID)
            ?: error("FFE0 service not found")
        ffe1 = service.getCharacteristic(Uv5rminiBleSpec.FFE1)
            ?: error("FFE1 characteristic not found")
        ff31 = service.getCharacteristic(Uv5rminiBleSpec.FF31)
            ?: error("FF31 characteristic not found")
        try {
            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } catch (_: Throwable) {
        }
        try {
            g.requestMtu(247)
            awaitEither(mtuDeferred, failedDeferred, 6_000L)
        } catch (_: Throwable) {
            // Some stacks reject explicit MTU request; fallback to default MTU.
        }

        g.setCharacteristicNotification(ffe1, true)
        val cccd = ffe1.getDescriptor(Uv5rminiBleSpec.CCCD_UUID) ?: error("CCCD missing on FFE1")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        writeDescriptorCompat(g, cccd)
        awaitEither(cccdDeferred, failedDeferred, 8_000L)

        writeWithResponse(ff31, Uv5rminiBleSpec.CONTROL_INIT)
        val banner = recv()
        bannerHex = banner.joinToString(" ") { "%02x".format(it) }
    }

    @SuppressLint("MissingPermission")
    suspend fun stop() {
        try {
            gatt?.setCharacteristicNotification(ffe1, false)
        } catch (_: Throwable) {
        }
        try {
            gatt?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            gatt?.close()
        } catch (_: Throwable) {
        }
    }

    suspend fun handshake(): Pair<String, String> {
        writeWithResponse(ffe1, Uv5rminiBleSpec.PROGRAM_TOKEN)
        expectAck()

        writeWithResponse(ffe1, Uv5rminiBleSpec.FEATURE_QUERY)
        val features = recv()

        writeWithResponse(ffe1, Uv5rminiBleSpec.MODEL_QUERY)
        val model = recv()

        writeWithResponse(ffe1, Uv5rminiBleSpec.SEND_TOKEN)
        expectAck()

        return features.toHex() to model.toString(Charsets.US_ASCII)
    }

    suspend fun readImage(onProgress: (Int, Int) -> Unit): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val addrs = protocolAddresses()
        val total = addrs.size
        addrs.forEachIndexed { index, addr ->
            val cmd = byteArrayOf('R'.code.toByte(), addr[0], addr[1], addr[2])
            writeWithResponse(ffe1, cmd)
            val rsp = recv()
            if (rsp.size < 4 || !rsp.copyOfRange(0, 4).contentEquals(cmd)) {
                error("Unexpected read response at ${addr.toHex()}: ${rsp.toHex()}")
            }
            result[addr.toHex()] = rsp.copyOfRange(4, rsp.size).toHex()
            onProgress(index + 1, total)
        }
        return result
    }

    suspend fun replayWriteImage(blocks: Map<String, String>, onProgress: (Int, Int) -> Unit) {
        val addrs = protocolAddresses()
        val total = addrs.size
        addrs.forEachIndexed { index, addr ->
            val key = addr.toHex()
            val block = (blocks[key] ?: error("Missing block $key")).hexToBytes()
            require(block.size == 128) { "Block $key size=${block.size}, expected 128" }
            val cmd = byteArrayOf('W'.code.toByte(), addr[0], addr[1], addr[2]) + block
            writeWithResponse(ffe1, cmd)
            expectAck()
            onProgress(index + 1, total)
        }
    }

    suspend fun finish() {
        writeWithResponse(ffe1, Uv5rminiBleSpec.EXIT_TOKEN)
        expectAck()
    }

    private suspend fun expectAck() {
        val rsp = recv()
        if (!(rsp.size == 1 && rsp[0] == Uv5rminiBleSpec.ACK)) {
            error("Expected ACK 06, got ${rsp.toHex()}")
        }
    }

    private suspend fun recv(timeoutMs: Long = 5_000L): ByteArray {
        return withTimeout(timeoutMs) { notifyQueue.receive() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeWithResponse(ch: BluetoothGattCharacteristic, data: ByteArray) {
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writeCharacteristicCompat(gatt ?: error("gatt null"), ch, data)
        val ok = withTimeout(5_000L) { writeQueue.receive() }
        if (!ok) error("Write failed (with response)")
    }

}

private suspend fun <T> awaitEither(ok: CompletableDeferred<T>, err: CompletableDeferred<Throwable>, timeoutMs: Long): T {
    return withTimeout(timeoutMs) {
        while (true) {
            if (ok.isCompleted) return@withTimeout ok.await()
            if (err.isCompleted) throw err.await()
            delay(20)
        }
        @Suppress("UNREACHABLE_CODE")
        ok.await()
    }
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun writeCharacteristicCompat(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val status = gatt.writeCharacteristic(ch, value, ch.writeType)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            error("writeCharacteristic failed immediately: $status")
        }
    } else {
        ch.value = value
        if (!gatt.writeCharacteristic(ch)) {
            error("writeCharacteristic returned false")
        }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
private fun writeDescriptorCompat(gatt: BluetoothGatt, d: BluetoothGattDescriptor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val status = gatt.writeDescriptor(d, d.value)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            error("writeDescriptor failed immediately: $status")
        }
    } else {
        if (!gatt.writeDescriptor(d)) {
            error("writeDescriptor returned false")
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    val clean = trim()
    require(clean.length % 2 == 0) { "invalid hex length" }
    val out = ByteArray(clean.length / 2)
    var i = 0
    while (i < clean.length) {
        out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        i += 2
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
