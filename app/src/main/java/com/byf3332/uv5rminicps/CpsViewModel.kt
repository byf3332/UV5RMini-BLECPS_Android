package com.byf3332.uv5rminicps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.byf3332.uv5rminicps.ble.Uv5rminiBleClient
import com.byf3332.uv5rminicps.core.Channel
import com.byf3332.uv5rminicps.core.DtmfCodec
import com.byf3332.uv5rminicps.core.DtmfState
import com.byf3332.uv5rminicps.core.RadioSettingsCodec
import com.byf3332.uv5rminicps.core.SettingItem
import com.byf3332.uv5rminicps.core.Uv5rminiImageEditor
import com.byf3332.uv5rminicps.core.Uv5rminiProtocolCodec
import com.byf3332.uv5rminicps.core.Uv5rminiSpec
import com.byf3332.uv5rminicps.core.VfoCodec
import com.byf3332.uv5rminicps.core.VfoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CpsViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app
    private val bleClient = Uv5rminiBleClient(app.applicationContext)
    private var baseImage: ByteArray = ByteArray(0)
    private var readBlocks: Map<String, String> = emptyMap()
    private val settingsEdits = linkedMapOf<String, String>()
    private var vfoAEdit: VfoState? = null
    private var vfoBEdit: VfoState? = null
    private var dtmfEdit: DtmfState? = null

    private val channelsState = mutableListOf<Channel>()
    private val deletedChannels = sortedSetOf<Int>()

    private val _summary = MutableLiveData(appCtx.getString(R.string.summary_not_read))
    val summary: LiveData<String> = _summary
    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy
    private val _channels = MutableLiveData<List<Channel>>(emptyList())
    val channels: LiveData<List<Channel>> = _channels
    private val _settings = MutableLiveData<List<SettingItem>>(emptyList())
    val settings: LiveData<List<SettingItem>> = _settings
    private val _vfoA = MutableLiveData(VfoState())
    val vfoA: LiveData<VfoState> = _vfoA
    private val _vfoB = MutableLiveData(VfoState())
    val vfoB: LiveData<VfoState> = _vfoB
    private val _dtmf = MutableLiveData(DtmfState())
    val dtmf: LiveData<DtmfState> = _dtmf

    fun readFromDevice(mac: String) {
        if (mac.isBlank()) {
            _summary.value = appCtx.getString(R.string.summary_enter_mac_first)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                _summary.value = appCtx.getString(R.string.summary_reading)
                val result = bleClient.readImage(mac) { c, t ->
                    if (c % 16 == 0 || c == t) {
                        _summary.postValue(appCtx.getString(R.string.summary_reading_progress, c, t))
                    }
                }
                readBlocks = result.blocks
                val blocks64 = Uv5rminiProtocolCodec.parseReadBlocksToDecrypted64(result.blocks)
                baseImage = Uv5rminiProtocolCodec.sparseFromBlocks64(blocks64)

                channelsState.clear()
                channelsState += Uv5rminiImageEditor.extractChannels(baseImage)
                deletedChannels.clear()
                settingsEdits.clear()
                vfoAEdit = null
                vfoBEdit = null
                dtmfEdit = null

                val (va, vb) = VfoCodec.decode(baseImage)
                _vfoA.value = va
                _vfoB.value = vb
                _dtmf.value = DtmfCodec.decode(baseImage)
                _settings.value = RadioSettingsCodec.decode(baseImage, appCtx.resources)
                _channels.value = channelsState.sortedBy { it.channel }
                _summary.value = appCtx.getString(R.string.summary_read_done, result.modelAscii)
            } catch (t: Throwable) {
                _summary.value = appCtx.getString(R.string.summary_read_failed, t.message ?: "")
            } finally {
                _busy.value = false
            }
        }
    }

    fun addChannel() {
        val used = channelsState.map { it.channel }.toSet()
        val next = (Uv5rminiSpec.MIN_CHANNEL..Uv5rminiSpec.MAX_CHANNEL).firstOrNull { it !in used }
        if (next == null) {
            _summary.value = appCtx.getString(R.string.summary_no_channel_to_add)
            return
        }
        deletedChannels.remove(next)
        channelsState += Channel(channel = next, bandwidth = "wide")
        _channels.value = channelsState.sortedBy { it.channel }
    }

    fun deleteChannel(ch: Int): Boolean {
        if (ch !in Uv5rminiSpec.MIN_CHANNEL..Uv5rminiSpec.MAX_CHANNEL) {
            _summary.value = appCtx.getString(R.string.summary_channel_range)
            return false
        }
        channelsState.removeAll { it.channel == ch }
        deletedChannels += ch
        _channels.value = channelsState.sortedBy { it.channel }
        return true
    }

    fun updateChannel(chNo: Int, patch: Channel) {
        val idx = channelsState.indexOfFirst { it.channel == chNo }
        if (idx < 0) return
        channelsState[idx] = patch
        _channels.value = channelsState.sortedBy { it.channel }
    }

    fun updateSetting(key: String, value: String) {
        settingsEdits[key] = value
        val now = _settings.value.orEmpty().map {
            if (it.key == key) it.copy(value = value) else it
        }
        _settings.value = now
    }

    fun updateVfo(a: VfoState, b: VfoState) {
        vfoAEdit = a
        vfoBEdit = b
        _vfoA.value = a
        _vfoB.value = b
    }

    fun updateDtmf(state: DtmfState) {
        dtmfEdit = state
        _dtmf.value = state
    }

    fun writeToDevice(mac: String) {
        if (mac.isBlank()) {
            _summary.value = appCtx.getString(R.string.summary_enter_mac_first)
            return
        }
        if (baseImage.isEmpty() || readBlocks.isEmpty()) {
            _summary.value = appCtx.getString(R.string.summary_read_first)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                var img = baseImage.copyOf()
                img = Uv5rminiImageEditor.applyStateOnImage(
                    img,
                    com.byf3332.uv5rminicps.core.RadioState(
                        channelsState.toMutableList(),
                        deletedChannels.toMutableSet()
                    )
                )
                img = RadioSettingsCodec.applyEdits(img, settingsEdits, appCtx.resources)
                val va = vfoAEdit ?: _vfoA.value ?: VfoState()
                val vb = vfoBEdit ?: _vfoB.value ?: VfoState()
                img = VfoCodec.apply(img, va, vb)
                val dt = dtmfEdit ?: _dtmf.value ?: DtmfState()
                img = DtmfCodec.apply(img, dt)
                val writeImage = Uv5rminiProtocolCodec.buildWriteImageFromSparse(img)
                writePending(writeImage)
                bleClient.writeImage(mac, writeImage) { c, t ->
                    if (c % 16 == 0 || c == t) {
                        _summary.postValue(appCtx.getString(R.string.summary_writing_progress, c, t))
                    }
                }
                _summary.value = appCtx.getString(R.string.summary_write_done)
            } catch (t: Throwable) {
                _summary.value = appCtx.getString(R.string.summary_write_failed, t.message ?: "")
            } finally {
                _busy.value = false
            }
        }
    }

    fun buildPendingOnly() {
        if (baseImage.isEmpty()) {
            _summary.value = appCtx.getString(R.string.summary_read_first)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                var img = baseImage.copyOf()
                img = Uv5rminiImageEditor.applyStateOnImage(
                    img,
                    com.byf3332.uv5rminicps.core.RadioState(
                        channelsState.toMutableList(),
                        deletedChannels.toMutableSet()
                    )
                )
                img = RadioSettingsCodec.applyEdits(img, settingsEdits, appCtx.resources)
                val va = vfoAEdit ?: _vfoA.value ?: VfoState()
                val vb = vfoBEdit ?: _vfoB.value ?: VfoState()
                img = VfoCodec.apply(img, va, vb)
                val dt = dtmfEdit ?: _dtmf.value ?: DtmfState()
                img = DtmfCodec.apply(img, dt)
                val writeImage = Uv5rminiProtocolCodec.buildWriteImageFromSparse(img)
                val f = writePending(writeImage)
                _summary.value = appCtx.getString(R.string.summary_pending_done, f.absolutePath)
            } catch (t: Throwable) {
                _summary.value = appCtx.getString(R.string.summary_pending_failed, t.message ?: "")
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun writePending(writeImage: Map<String, String>): File = withContext(Dispatchers.IO) {
        val f = File(getApplication<Application>().filesDir, "uv5rmini_write_pending.json")
        val body = buildString {
            append("{\n  \"blocks\": {\n")
            writeImage.entries.forEachIndexed { i, e ->
                append("    \"${e.key}\": \"${e.value}\"")
                append(if (i == writeImage.size - 1) "\n" else ",\n")
            }
            append("  }\n}\n")
        }
        f.writeText(body)
        f
    }
}

