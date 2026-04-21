package com.byf3332.uv5rminicps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.byf3332.uv5rminicps.ble.Uv5rminiBleClient
import com.byf3332.uv5rminicps.core.Channel
import com.byf3332.uv5rminicps.databinding.FragmentFirstBinding
import kotlin.math.floor
import kotlinx.coroutines.launch
import android.provider.Settings
import java.util.Locale

class ChannelsFragment : Fragment() {
    private companion object {
        const val PREFS_NAME = "device"
        const val KEY_LAST_MAC = "last_mac"
        const val KEY_LAST_DEVICE_DISPLAY = "last_device_display"
    }

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val vm: CpsViewModel by activityViewModels()
    private val bleClient by lazy { Uv5rminiBleClient(requireContext().applicationContext) }

    private val toneOptions: List<String> by lazy { buildToneOptions() }
    private val powerOptions = listOf("high", "low")
    private val bandwidthOptions = listOf("wide", "narrow")
    private val onOffOptions = listOf("off", "on")
    private val pttOptions = listOf("off", "press", "release", "both")
    private val signalOptions = (1..20).map { it.toString() }
    private val sqModeOptions = listOf("QT/DQT", "QT/DQT*DTMF", "QT/DQT+DTMF")
    private val onOffCnOptions by lazy { listOf(getString(R.string.option_off), getString(R.string.option_on)) }
    private val powerCnOptions by lazy { listOf(getString(R.string.option_power_high), getString(R.string.option_power_low)) }
    private val bandwidthCnOptions by lazy { listOf(getString(R.string.option_bandwidth_wide), getString(R.string.option_bandwidth_narrow)) }
    private val pttCnOptions by lazy {
        listOf(
            getString(R.string.option_ptt_off),
            getString(R.string.option_ptt_press),
            getString(R.string.option_ptt_release),
            getString(R.string.option_ptt_press_release),
        )
    }
    private val fixedColumnWidthDp = 56
    private val bodyColumnWidthsDp = listOf(150, 150, 150, 150, 110, 110, 120, 120, 160, 130, 110, 120, 90, 200)
    private var syncingVertical = false
    private var scanningDevices = false
    private val macRegex = Regex("([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
    private var pendingScanPrefs: android.content.SharedPreferences? = null
    private val scanPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.err_missing_ble_permission, denied.joinToString()),
                Toast.LENGTH_LONG
            ).show()
            pendingScanPrefs = null
            return@registerForActivityResult
        }
        val prefs = pendingScanPrefs
        pendingScanPrefs = null
        if (prefs != null) {
            scanAndSelectDevice(prefs)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val savedDisplay = prefs.getString(KEY_LAST_DEVICE_DISPLAY, "") ?: ""
        val savedMac = prefs.getString(KEY_LAST_MAC, "") ?: ""
        if (savedDisplay.isNotBlank()) {
            binding.editMacAddress.setText(savedDisplay)
        } else if (savedMac.isNotBlank()) {
            binding.editMacAddress.setText(savedMac)
        }
        vm.summary.observe(viewLifecycleOwner) { binding.textStatus.text = it }
        vm.channels.observe(viewLifecycleOwner) { renderChannelTable(it) }
        binding.hscrollChannelsHeader.setPeer(binding.hscrollChannelsBody)
        binding.hscrollChannelsBody.setPeer(binding.hscrollChannelsHeader)
        binding.scrollChannelsBody.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (syncingVertical) return@setOnScrollChangeListener
            syncingVertical = true
            binding.scrollChannelsFixedBody.scrollTo(0, scrollY)
            syncingVertical = false
        }
        binding.scrollChannelsFixedBody.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (syncingVertical) return@setOnScrollChangeListener
            syncingVertical = true
            binding.scrollChannelsBody.scrollTo(0, scrollY)
            syncingVertical = false
        }
        vm.busy.observe(viewLifecycleOwner) { busy ->
            binding.buttonReadReal.isEnabled = !busy
            binding.buttonDeleteChannel.isEnabled = !busy
            binding.buttonWriteReal.isEnabled = !busy
            binding.buttonScanDevices.isEnabled = !busy && !scanningDevices
        }

        binding.buttonScanDevices.setOnClickListener {
            if (ensureScanPermissions(prefs)) {
                scanAndSelectDevice(prefs)
            }
        }
        binding.buttonReadReal.setOnClickListener {
            val mac = resolveMacAddressFromInput()
            persistSelectedDevice(prefs, mac, binding.editMacAddress.text.toString())
            vm.readFromDevice(mac)
        }
        binding.buttonDeleteChannel.setOnClickListener {
            val ch = binding.editChannelDelete.text.toString().trim().toIntOrNull()
            if (ch == null) {
                Toast.makeText(requireContext(), getString(R.string.err_input_valid_channel), Toast.LENGTH_SHORT).show()
            } else if (vm.deleteChannel(ch)) {
                binding.editChannelDelete.setText("")
            }
        }
        binding.buttonWriteReal.setOnClickListener {
            val mac = resolveMacAddressFromInput()
            persistSelectedDevice(prefs, mac, binding.editMacAddress.text.toString())
            vm.writeToDevice(mac)
        }
    }

    private fun renderChannelTable(channels: List<Channel>) {
        val tableHeaderFixed = binding.tableChannelsHeaderFixed
        val tableBodyFixed = binding.tableChannelsFixed
        val tableHeader = binding.tableChannelsHeader
        val tableBody = binding.tableChannels
        val tableContainer = binding.layoutChannelsTableContainer
        val emptyText = binding.textChannelsEmptyBig
        tableHeaderFixed.removeAllViews()
        tableBodyFixed.removeAllViews()
        tableHeader.removeAllViews()
        tableBody.removeAllViews()

        val bodyHeaders = listOf(
            getString(R.string.col_rx_freq),
            getString(R.string.col_rx_tone),
            getString(R.string.col_tx_freq),
            getString(R.string.col_tx_tone),
            getString(R.string.col_power),
            getString(R.string.col_bandwidth),
            getString(R.string.col_scan_add),
            getString(R.string.col_dtmf_decode),
            getString(R.string.col_sq_mode),
            getString(R.string.col_ptt_id),
            getString(R.string.col_signal),
            getString(R.string.col_busy_lock),
            getString(R.string.col_fhss),
            getString(R.string.col_channel_name),
        )
        tableHeaderFixed.addView(makeHeaderRow(listOf(getString(R.string.col_channel)), listOf(fixedColumnWidthDp)))
        tableHeader.addView(makeHeaderRow(bodyHeaders, bodyColumnWidthsDp))

        if (channels.isEmpty()) {
            tableContainer.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            return
        }
        tableContainer.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        channels.sortedBy { it.channel }.forEach { ch ->
            val (fixedRow, bodyRow) = makeInteractiveRows(ch)
            tableBodyFixed.addView(fixedRow)
            tableBody.addView(bodyRow)
        }
    }

    private fun makeHeaderRow(values: List<String>, widthsDp: List<Int>): TableRow {
        val row = TableRow(requireContext())
        values.forEachIndexed { i, text ->
            val tv = makeCell(text, widthsDp.getOrElse(i) { 140 }, clickable = false, style = CellStyle.HEADER)
            tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
            row.addView(tv)
        }
        return row
    }

    private fun makePlainRow(values: List<String>, widthDp: Int = 220): TableRow {
        val row = TableRow(requireContext())
        values.forEach { text ->
            row.addView(makeCell(text, widthDp, clickable = false))
        }
        return row
    }

    private fun makeInteractiveRows(ch: Channel): Pair<TableRow, TableRow> {
        val fixedRow = TableRow(requireContext())
        val row = TableRow(requireContext())
        fixedRow.addView(makeCell(ch.channel.toString(), fixedColumnWidthDp, clickable = false, style = CellStyle.FIXED))

        row.addView(makeCell(fmtFreq(ch.rxFreqMhz), 150, clickable = true) {
            showInputEditor(getString(R.string.title_edit_rx_freq, ch.channel), fmtFreq(ch.rxFreqMhz), "MHz") { input ->
                val freq = parseAllowedFreqMhz(input)
                if (freq == null) {
                    Toast.makeText(requireContext(), getString(R.string.err_freq_allowed_ranges), Toast.LENGTH_SHORT).show()
                    return@showInputEditor
                }
                val patch = if (ch.txFreqMhz == null) {
                    ch.copy(rxFreqMhz = freq, txFreqMhz = freq)
                } else {
                    ch.copy(rxFreqMhz = freq)
                }
                vm.updateChannel(ch.channel, patch)
            }
        })
        row.addView(makeCell(bestToneValue(ch.rxTone), 150, clickable = true) {
            showToneEditor(getString(R.string.title_edit_rx_tone, ch.channel), bestToneValue(ch.rxTone)) {
                vm.updateChannel(ch.channel, ch.copy(rxTone = it))
            }
        })
        row.addView(makeCell(fmtFreq(ch.txFreqMhz), 150, clickable = true) {
            showInputEditor(getString(R.string.title_edit_tx_freq, ch.channel), fmtFreq(ch.txFreqMhz), "MHz") { input ->
                val freq = parseAllowedFreqMhz(input)
                if (freq == null) {
                    Toast.makeText(requireContext(), getString(R.string.err_freq_allowed_ranges), Toast.LENGTH_SHORT).show()
                    return@showInputEditor
                }
                val patch = if (ch.rxFreqMhz == null) {
                    ch.copy(txFreqMhz = freq, rxFreqMhz = freq)
                } else {
                    ch.copy(txFreqMhz = freq)
                }
                vm.updateChannel(ch.channel, patch)
            }
        })
        row.addView(makeCell(bestToneValue(ch.txTone), 150, clickable = true) {
            showToneEditor(getString(R.string.title_edit_tx_tone, ch.channel), bestToneValue(ch.txTone)) {
                vm.updateChannel(ch.channel, ch.copy(txTone = it))
            }
        })
        row.addView(makeCell(powerLabel(ch.txPower), 110, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_power, ch.channel), powerCnOptions, powerLabel(ch.txPower)) {
                vm.updateChannel(ch.channel, ch.copy(txPower = if (it == getString(R.string.option_power_low)) "low" else "high"))
            }
        })
        row.addView(makeCell(bandwidthLabel(ch.bandwidth), 110, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_bandwidth, ch.channel), bandwidthCnOptions, bandwidthLabel(ch.bandwidth)) {
                vm.updateChannel(ch.channel, ch.copy(bandwidth = if (it == getString(R.string.option_bandwidth_narrow)) "narrow" else "wide"))
            }
        })
        row.addView(makeCell(onOffCn(ch.scanAdd == 1), 120, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_scan_add, ch.channel), onOffCnOptions, onOffCn(ch.scanAdd == 1)) {
                vm.updateChannel(ch.channel, ch.copy(scanAdd = if (it == getString(R.string.option_on)) 1 else 0))
            }
        })
        row.addView(makeCell(onOffCn(ch.sqModeIndex > 0), 120, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_dtmf_decode, ch.channel), onOffCnOptions, onOffCn(ch.sqModeIndex > 0)) {
                val nextSq = when (it) {
                    getString(R.string.option_on) -> if (ch.sqModeIndex == 2) 2 else 1
                    else -> 0
                }
                vm.updateChannel(ch.channel, ch.copy(sqModeIndex = nextSq))
            }
        })
        row.addView(makeCell(sqModeLabel(ch.sqModeIndex), 160, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_sq_mode, ch.channel), sqModeOptions, sqModeLabel(ch.sqModeIndex)) {
                vm.updateChannel(ch.channel, ch.copy(sqModeIndex = sqModeOptions.indexOf(it).coerceAtLeast(0)))
            }
        })
        row.addView(makeCell(pttLabel(ch.pttIdModeIndex), 130, clickable = true) {
            val opts = pttCnOptions
            showSelectEditor(getString(R.string.title_edit_ptt_id, ch.channel), opts, pttLabel(ch.pttIdModeIndex)) {
                vm.updateChannel(ch.channel, ch.copy(pttIdModeIndex = opts.indexOf(it).coerceAtLeast(0)))
            }
        })
        row.addView(makeCell(ch.signalGroupUi.toString(), 110, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_signal, ch.channel), signalOptions, ch.signalGroupUi.toString()) {
                vm.updateChannel(ch.channel, ch.copy(signalGroupUi = it.toIntOrNull() ?: ch.signalGroupUi))
            }
        })
        row.addView(makeCell(onOffCn(ch.busyLock == 1), 120, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_busy_lock, ch.channel), onOffCnOptions, onOffCn(ch.busyLock == 1)) {
                vm.updateChannel(ch.channel, ch.copy(busyLock = if (it == getString(R.string.option_on)) 1 else 0))
            }
        })
        row.addView(makeCell(onOffCn(ch.fhss == 1), 90, clickable = true) {
            showSelectEditor(getString(R.string.title_edit_fhss, ch.channel), onOffCnOptions, onOffCn(ch.fhss == 1)) {
                vm.updateChannel(ch.channel, ch.copy(fhss = if (it == getString(R.string.option_on)) 1 else 0))
            }
        })
        row.addView(makeCell(ch.name, 200, clickable = true) {
            showInputEditor(getString(R.string.title_edit_name, ch.channel), ch.name, getString(R.string.hint_channel_name)) { input ->
                vm.updateChannel(ch.channel, ch.copy(name = input))
            }
        })
        return fixedRow to row
    }

    private enum class CellStyle { NORMAL, HEADER, FIXED }

    private fun makeCell(
        text: String,
        widthDp: Int,
        clickable: Boolean,
        style: CellStyle = CellStyle.NORMAL,
        onClick: (() -> Unit)? = null
    ): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 13f
            isClickable = clickable
            isFocusable = clickable
            setPadding(10, 8, 10, 8)
            background = cellBackground(style)
            layoutParams = TableRow.LayoutParams(dp(widthDp), TableRow.LayoutParams.WRAP_CONTENT)
            if (clickable && onClick != null) {
                setOnClickListener { onClick() }
            }
        }
    }

    private fun cellBackground(style: CellStyle): GradientDrawable {
        val bg = when (style) {
            CellStyle.HEADER -> Color.parseColor("#DCEBFF")
            CellStyle.FIXED -> Color.parseColor("#EEF4FF")
            CellStyle.NORMAL -> Color.parseColor("#F7F7FA")
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(4).toFloat()
            setColor(bg)
            setStroke(dp(1), Color.parseColor("#CDD4DF"))
        }
    }

    private fun showSelectEditor(title: String, options: List<String>, current: String, onSelect: (String) -> Unit) {
        val cur = options.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), cur) { dialog, which ->
                onSelect(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showInputEditor(title: String, value: String, hint: String, onCommit: (String) -> Unit) {
        val editor = EditText(requireContext()).apply {
            setText(value)
            this.hint = hint
        }
        val wrap = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
            addView(editor)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(wrap)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ -> onCommit(editor.text.toString().trim()) }
            .show()
    }

    private fun showToneEditor(title: String, current: String, onCommit: (String) -> Unit) {
        val customItem = getString(R.string.tone_manual_ctcss)
        val options = buildList {
            add("OFF")
            add(customItem)
            addAll(toneOptions.filter { it != "OFF" })
        }
        val cur = options.indexOf(current).let { if (it < 0) 0 else it }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), cur) { dialog, which ->
                val sel = options[which]
                if (sel == customItem) {
                    dialog.dismiss()
                    showInputEditor(getString(R.string.title_manual_ctcss, title), "", getString(R.string.hint_ctcss_example)) { raw ->
                        val f = raw.toDoubleOrNull()
                        if (f == null || f < 60.0 || f > 260.0) {
                            Toast.makeText(requireContext(), getString(R.string.err_ctcss_range), Toast.LENGTH_SHORT).show()
                            return@showInputEditor
                        }
                        onCommit(String.format(Locale.US, "%.1f", f))
                    }
                } else {
                    onCommit(sel)
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun bestToneValue(raw: String): String {
        return toneOptions.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "OFF"
    }

    private fun onOffCn(value: Boolean): String = if (value) getString(R.string.option_on) else getString(R.string.option_off)
    private fun powerLabel(value: String): String = if (value.equals("low", true)) getString(R.string.option_power_low) else getString(R.string.option_power_high)
    private fun bandwidthLabel(value: String): String = if (value.equals("narrow", true)) getString(R.string.option_bandwidth_narrow) else getString(R.string.option_bandwidth_wide)
    private fun pttLabel(index: Int): String = when (index) {
        1 -> getString(R.string.option_ptt_press)
        2 -> getString(R.string.option_ptt_release)
        3 -> getString(R.string.option_ptt_press_release)
        else -> getString(R.string.option_none)
    }
    private fun sqModeLabel(index: Int): String {
        return sqModeOptions.getOrElse(index.coerceIn(0, sqModeOptions.size - 1)) { sqModeOptions.first() }
    }

    private fun buildToneOptions(): List<String> {
        val ctcss = listOf(
            "67.0", "69.3", "71.9", "74.4", "77.0", "79.7", "82.5", "85.4", "88.5", "91.5", "94.8", "97.4",
            "100.0", "103.5", "107.2", "110.9", "114.8", "118.8", "123.0", "127.3", "131.8", "136.5",
            "141.3", "146.2", "151.4", "156.7", "159.8", "162.2", "165.5", "167.9", "171.3", "173.8",
            "177.3", "179.9", "183.5", "186.2", "189.9", "192.8", "196.6", "199.5", "203.5", "206.5",
            "210.7", "218.1", "225.7", "229.1", "233.6", "241.8", "250.3", "254.1"
        )
        val dcsCodes = listOf(
            23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74,
            114, 115, 116, 125, 131, 132, 134, 143, 145, 152, 155, 156, 162, 165, 172, 174,
            205, 212, 223, 225, 226, 243, 244, 245, 246, 251, 252, 255, 261, 263, 265, 266, 271, 274,
            306, 311, 315, 325, 331, 332, 343, 346, 351, 356, 364, 365, 371,
            411, 412, 413, 423, 431, 432, 445, 446, 452, 454, 455, 462, 464, 465, 466,
            503, 506, 516, 523, 526, 532, 546, 565,
            606, 612, 624, 627, 631, 632, 654, 662, 664, 703, 712, 723, 731, 732, 734, 743, 754
        )
        val dcsN = dcsCodes.map { "D%03dN".format(it) }
        val dcsI = dcsCodes.map { "D%03dI".format(it) }
        return listOf("OFF") + ctcss + dcsN + dcsI
    }

    private fun fmtFreq(v: Double?): String = if (v == null) "" else String.format(Locale.US, "%.5f", v)

    private fun parseAllowedFreqMhz(text: String): Double? {
        val v = text.trim().toDoubleOrNull() ?: return null
        if (!isAllowedFreqMhz(v)) return null
        if (isAirbandMhz(v)) return v
        val snapped = snapDownToMinRaster(v)
        return if (isAllowedFreqMhz(snapped)) snapped else null
    }

    private fun isAllowedFreqMhz(v: Double): Boolean {
        return (v >= 108.0 && v < 174.0) ||
            (v >= 350.0 && v < 390.0) ||
            (v >= 400.0 && v < 520.0)
    }

    private fun isAirbandMhz(v: Double): Boolean {
        return v >= 108.0 && v <= 135.99875
    }

    private fun snapDownToMinRaster(v: Double): Double {
        val step = 0.00125

        val (low, high) = when {
            v >= 136.0 && v <= 173.99875 -> 136.0 to 173.99875
            v >= 350.0 && v <= 399.99875 -> 350.0 to 399.99875
            v >= 400.0 && v <= 519.99875 -> 400.0 to 519.99875
            else -> return v
        }

        val units = kotlin.math.floor((v - low) / step + 1e-9).toLong()
        val snapped = low + units * step

        return snapped.coerceIn(low, high)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun scanAndSelectDevice(prefs: android.content.SharedPreferences) {
        if (scanningDevices) return
        if (!isLocationServiceEnabled()) {
            showEnableLocationDialog()
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_devices, null)
        val listView = dialogView.findViewById<ListView>(R.id.list_scan_devices)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_scan_devices)
        val statusText = dialogView.findViewById<TextView>(R.id.text_scan_status)

        val labels = mutableListOf<String>()
        var foundDevices: List<Uv5rminiBleClient.ScannedDevice> = emptyList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        listView.adapter = adapter

        var scanJob: kotlinx.coroutines.Job? = null

        fun startScan() {
            scanJob?.cancel()
            scanningDevices = true
            binding.buttonScanDevices.isEnabled = false
            labels.clear()
            adapter.notifyDataSetChanged()
            foundDevices = emptyList()
            progressBar.visibility = View.VISIBLE
            statusText.text = getString(R.string.status_scanning_devices)

            scanJob = lifecycleScope.launch {
                try {
                    val result = bleClient.scanDevices(durationMs = 30_000L) { updates ->
                        foundDevices = updates
                        labels.clear()
                        labels.addAll(updates.map { formatDeviceDisplay(it) })
                        adapter.notifyDataSetChanged()
                        statusText.text = getString(R.string.status_scanning_count, updates.size)
                    }
                    foundDevices = result
                    if (result.isEmpty()) {
                        statusText.text = getString(R.string.msg_no_devices_found)
                    } else {
                        statusText.text = getString(R.string.status_scan_done_count, result.size)
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // ignored
                } catch (t: Throwable) {
                    statusText.text = getString(R.string.msg_scan_failed, (t.message ?: t.javaClass.simpleName))
                } finally {
                    progressBar.visibility = View.GONE
                    scanningDevices = false
                    val busy = vm.busy.value ?: false
                    binding.buttonScanDevices.isEnabled = !busy
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_select_device))
            .setView(dialogView)
            .setNeutralButton(getString(R.string.action_rescan), null)
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = foundDevices.getOrNull(position) ?: return@setOnItemClickListener
            val display = formatDeviceDisplayLine(selected)
            binding.editMacAddress.setText(display)
            persistSelectedDevice(prefs, selected.address, display)
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                startScan()
            }
        }
        dialog.setOnDismissListener {
            scanJob?.cancel()
            scanningDevices = false
            val busy = vm.busy.value ?: false
            binding.buttonScanDevices.isEnabled = !busy
        }

        dialog.show()
        startScan()
    }

    private fun resolveMacAddressFromInput(): String {
        val raw = binding.editMacAddress.text.toString().trim()
        val matched = macRegex.find(raw)?.groupValues?.getOrNull(1)
        return matched?.uppercase(Locale.US).orEmpty()
    }

    private fun persistSelectedDevice(
        prefs: android.content.SharedPreferences,
        mac: String,
        displayText: String,
    ) {
        if (mac.isBlank()) return
        val display = displayText.trim().ifBlank { mac }
        prefs.edit()
            .putString(KEY_LAST_MAC, mac)
            .putString(KEY_LAST_DEVICE_DISPLAY, display)
            .apply()
    }

    private fun isLocationServiceEnabled(): Boolean {
        val lm = requireContext().getSystemService(LocationManager::class.java) ?: return false
        return lm.isLocationEnabled
    }

    private fun ensureScanPermissions(prefs: android.content.SharedPreferences): Boolean {
        val perms = requiredScanPermissions()
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        pendingScanPrefs = prefs
        scanPermissionLauncher.launch(missing.toTypedArray())
        return false
    }

    private fun requiredScanPermissions(): List<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showEnableLocationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_location_required))
            .setMessage(getString(R.string.msg_location_required_for_scan))
            .setNegativeButton(getString(R.string.action_cancel), null)
            .setPositiveButton(getString(R.string.action_go_to_settings)) { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            .show()
    }

    private fun formatDeviceDisplay(device: Uv5rminiBleClient.ScannedDevice): String {
        val name = device.name?.takeIf { it.isNotBlank() } ?: getString(R.string.device_unknown_name)
        return "$name\n${device.address}  RSSI ${device.rssi}"
    }

    private fun formatDeviceDisplayLine(device: Uv5rminiBleClient.ScannedDevice): String {
        val name = device.name?.takeIf { it.isNotBlank() } ?: getString(R.string.device_unknown_name)
        return "$name (${device.address})"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
