package com.byf3332.uv5rminicps

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.byf3332.uv5rminicps.core.SettingItem
import com.byf3332.uv5rminicps.core.VfoState
import com.byf3332.uv5rminicps.databinding.FragmentVfoBinding
import kotlin.math.floor
import kotlin.math.max
import java.util.Locale

class VfoFragment : Fragment() {
    private var _binding: FragmentVfoBinding? = null
    private val binding get() = _binding!!
    private val vm: CpsViewModel by activityViewModels()

    private val toneOptions: List<String> by lazy { buildToneOptions() }
    private val sqModeOptions = listOf("QT/DQT", "QT/DQT*DTMF", "QT/DQT+DTMF")
    private val powerOptions by lazy { listOf(getString(R.string.option_power_high), getString(R.string.option_power_low)) }
    private val bandwidthOptions by lazy { listOf(getString(R.string.option_bandwidth_wide), getString(R.string.option_bandwidth_narrow)) }
    private val stepOptions = listOf("2.5KHz", "5.0KHz", "6.25KHz", "10.0KHz", "12.5KHz", "25.0KHz")
    private val signalOptions = (1..20).map { it.toString() }
    private val offsetDirOptions by lazy { listOf(getString(R.string.option_off), "+", "-") }
    private val onOffOptions by lazy { listOf(getString(R.string.option_off), getString(R.string.option_on)) }
    private val vfoPttOptions by lazy {
        listOf(
            getString(R.string.option_none),
            getString(R.string.option_ptt_press),
            getString(R.string.option_ptt_release),
            getString(R.string.option_ptt_press_release_full),
        )
    }
    private val manualCtcssLabel by lazy { getString(R.string.tone_manual_ctcss) }
    private val tonePickerOptions: List<String> by lazy { listOf("OFF", manualCtcssLabel) + toneOptions.filter { it != "OFF" } }
    private var suppressAutoSave = false
    private val lastToneValue = mutableMapOf<Int, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSpinners()
        val baseBottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = baseBottomPadding + max(imeBottom, sysBottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        vm.vfoA.observe(viewLifecycleOwner) { a -> bindVfoA(a) }
        vm.vfoB.observe(viewLifecycleOwner) { b -> bindVfoB(b) }
        vm.settings.observe(viewLifecycleOwner) { rows -> bindGlobal(rows) }
        vm.channels.observe(viewLifecycleOwner) { channels ->
            setLoadedState(channels.isNotEmpty())
        }
        installAutoSaveHandlers()
    }

    private fun setLoadedState(loaded: Boolean) {
        binding.textVfoEmpty.visibility = if (loaded) View.GONE else View.VISIBLE
        val contentVisibility = if (loaded) View.VISIBLE else View.GONE
        val childCount = binding.layoutVfoContent.childCount
        for (i in 0 until childCount) {
            val child = binding.layoutVfoContent.getChildAt(i)
            if (child.id == binding.textVfoEmpty.id) continue
            child.visibility = contentVisibility
        }
    }

    private fun initSpinners() {
        setupSpinner(binding.spinnerVfoARxtone, tonePickerOptions)
        setupSpinner(binding.spinnerVfoATxtone, tonePickerOptions)
        setupSpinner(binding.spinnerVfoASqMode, sqModeOptions)
        setupSpinner(binding.spinnerVfoAPower, powerOptions)
        setupSpinner(binding.spinnerVfoABandwidth, bandwidthOptions)
        setupSpinner(binding.spinnerVfoAStep, stepOptions)
        setupSpinner(binding.spinnerVfoASignal, signalOptions)
        setupSpinner(binding.spinnerVfoAOffsetDir, offsetDirOptions)
        setupSpinner(binding.spinnerVfoAFhss, onOffOptions)

        setupSpinner(binding.spinnerVfoBRxtone, tonePickerOptions)
        setupSpinner(binding.spinnerVfoBTxtone, tonePickerOptions)
        setupSpinner(binding.spinnerVfoBSqMode, sqModeOptions)
        setupSpinner(binding.spinnerVfoBPower, powerOptions)
        setupSpinner(binding.spinnerVfoBBandwidth, bandwidthOptions)
        setupSpinner(binding.spinnerVfoBStep, stepOptions)
        setupSpinner(binding.spinnerVfoBSignal, signalOptions)
        setupSpinner(binding.spinnerVfoBOffsetDir, offsetDirOptions)
        setupSpinner(binding.spinnerVfoBFhss, onOffOptions)

        setupSpinner(binding.spinnerVfoBusyLock, onOffOptions)
        setupSpinner(binding.spinnerVfoPttid, vfoPttOptions)
    }

    private fun installAutoSaveHandlers() {
        installToneManualCtcssHandler(binding.spinnerVfoARxtone)
        installToneManualCtcssHandler(binding.spinnerVfoATxtone)
        installToneManualCtcssHandler(binding.spinnerVfoBRxtone)
        installToneManualCtcssHandler(binding.spinnerVfoBTxtone)

        val spinners = listOf(
            binding.spinnerVfoASqMode, binding.spinnerVfoAPower, binding.spinnerVfoABandwidth, binding.spinnerVfoAStep,
            binding.spinnerVfoASignal, binding.spinnerVfoAOffsetDir, binding.spinnerVfoAFhss, binding.spinnerVfoBSqMode,
            binding.spinnerVfoBPower, binding.spinnerVfoBBandwidth, binding.spinnerVfoBStep, binding.spinnerVfoBSignal,
            binding.spinnerVfoBOffsetDir, binding.spinnerVfoBFhss, binding.spinnerVfoBusyLock, binding.spinnerVfoPttid
        )
        spinners.forEach { spinner ->
            var initialized = false
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (suppressAutoSave) return
                    if (!initialized) {
                        initialized = true
                        return
                    }
                    autoSaveFromUi(showErrors = false)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val edits = listOf(
            binding.editVfoAFreq, binding.editVfoAOffset, binding.editVfoBFreq,
            binding.editVfoBOffset, binding.editVfoScanLow, binding.editVfoScanHigh
        )
        edits.forEach { edit ->
            attachEditAutoSave(edit)
        }
    }

    private fun installToneManualCtcssHandler(spinner: Spinner) {
        var initialized = false
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressAutoSave) return
                if (!initialized) {
                    initialized = true
                    return
                }
                val selected = spinnerSelected(spinner)
                if (selected == manualCtcssLabel) {
                    showManualCtcssInput(spinner)
                } else {
                    lastToneValue[spinner.id] = selected
                    autoSaveFromUi(showErrors = false)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showManualCtcssInput(spinner: Spinner) {
        val editor = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.ctcss_input_hint_range)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ctcss_input_title))
            .setView(editor)
            .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                val fallback = lastToneValue[spinner.id] ?: "OFF"
                selectToneValue(spinner, fallback)
            }
            .setPositiveButton(getString(R.string.action_confirm)) { _, _ ->
                val v = editor.text.toString().trim().toDoubleOrNull()
                if (v == null || v < 60.0 || v > 260.0) {
                    Toast.makeText(requireContext(), getString(R.string.err_ctcss_range), Toast.LENGTH_SHORT).show()
                    val fallback = lastToneValue[spinner.id] ?: "OFF"
                    selectToneValue(spinner, fallback)
                    return@setPositiveButton
                }
                val formatted = String.format(Locale.US, "%.1f", v)
                selectToneValue(spinner, formatted)
                lastToneValue[spinner.id] = formatted
                autoSaveFromUi(showErrors = false)
            }
            .show()
    }

    private fun attachEditAutoSave(edit: EditText) {
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) autoSaveFromUi(showErrors = false)
        }
        edit.setOnEditorActionListener { _, _, _ ->
            autoSaveFromUi(showErrors = false)
            false
        }
    }

    private fun autoSaveFromUi(showErrors: Boolean): Boolean {
        if (suppressAutoSave || binding.textVfoEmpty.visibility == View.VISIBLE) return false

        val lowText = binding.editVfoScanLow.text.toString().trim()
        val highText = binding.editVfoScanHigh.text.toString().trim()
        val range = validateVfoScanRange(lowText, highText, showErrors) ?: return false

        val a = collectVfoA()
        val b = collectVfoB()
        if (!isAllowedFreqMhz(a.freqMhz) || !isAllowedFreqMhz(b.freqMhz)) {
            if (showErrors) {
                Toast.makeText(requireContext(), getString(R.string.err_freq_allowed_ranges), Toast.LENGTH_SHORT).show()
            }
            return false
        }
        normalizeFreqText(binding.editVfoAFreq, a.freqMhz)
        normalizeFreqText(binding.editVfoBFreq, b.freqMhz)
        vm.updateVfo(a, b)
        vm.updateSetting("vfo_scan_range_l", range.first.toString())
        vm.updateSetting("vfo_scan_range_h", range.second.toString())
        vm.updateSetting("vfo_busy_lock", spinnerSelected(binding.spinnerVfoBusyLock))
        vm.updateSetting("vfo_pttid", spinnerSelected(binding.spinnerVfoPttid))
        return true
    }

    private fun bindVfoA(a: VfoState) {
        suppressAutoSave = true
        try {
            binding.editVfoAFreq.setText(a.freqMhz?.let { String.format(Locale.US, "%.5f", it) }.orEmpty())
            selectToneValue(binding.spinnerVfoARxtone, bestToneValue(a.rxTone))
            selectToneValue(binding.spinnerVfoATxtone, bestToneValue(a.txTone))
            selectIndex(binding.spinnerVfoASqMode, a.sqModeIndex)
            selectValue(
                binding.spinnerVfoAPower,
                if (a.power.equals("low", true)) getString(R.string.option_power_low) else getString(R.string.option_power_high)
            )
            selectValue(
                binding.spinnerVfoABandwidth,
                if (a.bandwidth.equals("narrow", true)) getString(R.string.option_bandwidth_narrow) else getString(R.string.option_bandwidth_wide)
            )
            selectIndex(binding.spinnerVfoAStep, a.stepIndex)
            selectValue(binding.spinnerVfoASignal, a.signalGroupUi.toString())
            selectValue(
                binding.spinnerVfoAOffsetDir,
                when (a.offsetDir) { "+" -> "+"; "-" -> "-"; else -> getString(R.string.option_off) }
            )
            binding.editVfoAOffset.setText(String.format(Locale.US, "%.4f", a.offsetMhz))
            selectValue(binding.spinnerVfoAFhss, if (a.fhss == 1) getString(R.string.option_on) else getString(R.string.option_off))
        } finally {
            suppressAutoSave = false
        }
    }

    private fun bindVfoB(b: VfoState) {
        suppressAutoSave = true
        try {
            binding.editVfoBFreq.setText(b.freqMhz?.let { String.format(Locale.US, "%.5f", it) }.orEmpty())
            selectToneValue(binding.spinnerVfoBRxtone, bestToneValue(b.rxTone))
            selectToneValue(binding.spinnerVfoBTxtone, bestToneValue(b.txTone))
            selectIndex(binding.spinnerVfoBSqMode, b.sqModeIndex)
            selectValue(
                binding.spinnerVfoBPower,
                if (b.power.equals("low", true)) getString(R.string.option_power_low) else getString(R.string.option_power_high)
            )
            selectValue(
                binding.spinnerVfoBBandwidth,
                if (b.bandwidth.equals("narrow", true)) getString(R.string.option_bandwidth_narrow) else getString(R.string.option_bandwidth_wide)
            )
            selectIndex(binding.spinnerVfoBStep, b.stepIndex)
            selectValue(binding.spinnerVfoBSignal, b.signalGroupUi.toString())
            selectValue(
                binding.spinnerVfoBOffsetDir,
                when (b.offsetDir) { "+" -> "+"; "-" -> "-"; else -> getString(R.string.option_off) }
            )
            binding.editVfoBOffset.setText(String.format(Locale.US, "%.4f", b.offsetMhz))
            selectValue(binding.spinnerVfoBFhss, if (b.fhss == 1) getString(R.string.option_on) else getString(R.string.option_off))
        } finally {
            suppressAutoSave = false
        }
    }

    private fun bindGlobal(rows: List<SettingItem>) {
        fun get(key: String): String = rows.firstOrNull { it.key == key }?.value.orEmpty()
        suppressAutoSave = true
        try {
            binding.editVfoScanLow.setText(get("vfo_scan_range_l"))
            binding.editVfoScanHigh.setText(get("vfo_scan_range_h"))
            selectValue(binding.spinnerVfoBusyLock, get("vfo_busy_lock"))
            selectValue(binding.spinnerVfoPttid, get("vfo_pttid"))
        } finally {
            suppressAutoSave = false
        }
    }

    private fun collectVfoA(): VfoState {
        return VfoState(
            freqMhz = parseAllowedFreqMhz(binding.editVfoAFreq.text.toString()),
            rxTone = spinnerSelected(binding.spinnerVfoARxtone),
            txTone = spinnerSelected(binding.spinnerVfoATxtone),
            sqModeIndex = binding.spinnerVfoASqMode.selectedItemPosition,
            power = if (spinnerSelected(binding.spinnerVfoAPower) == getString(R.string.option_power_low)) "low" else "high",
            bandwidth = if (spinnerSelected(binding.spinnerVfoABandwidth) == getString(R.string.option_bandwidth_narrow)) "narrow" else "wide",
            stepIndex = binding.spinnerVfoAStep.selectedItemPosition,
            signalGroupUi = spinnerSelected(binding.spinnerVfoASignal).toIntOrNull() ?: 1,
            offsetDir = when (spinnerSelected(binding.spinnerVfoAOffsetDir)) { "+" -> "+"; "-" -> "-"; else -> "off" },
            offsetMhz = binding.editVfoAOffset.text.toString().trim().toDoubleOrNull() ?: 0.0,
            fhss = if (spinnerSelected(binding.spinnerVfoAFhss) == getString(R.string.option_on)) 1 else 0,
        )
    }

    private fun collectVfoB(): VfoState {
        return VfoState(
            freqMhz = parseAllowedFreqMhz(binding.editVfoBFreq.text.toString()),
            rxTone = spinnerSelected(binding.spinnerVfoBRxtone),
            txTone = spinnerSelected(binding.spinnerVfoBTxtone),
            sqModeIndex = binding.spinnerVfoBSqMode.selectedItemPosition,
            power = if (spinnerSelected(binding.spinnerVfoBPower) == getString(R.string.option_power_low)) "low" else "high",
            bandwidth = if (spinnerSelected(binding.spinnerVfoBBandwidth) == getString(R.string.option_bandwidth_narrow)) "narrow" else "wide",
            stepIndex = binding.spinnerVfoBStep.selectedItemPosition,
            signalGroupUi = spinnerSelected(binding.spinnerVfoBSignal).toIntOrNull() ?: 1,
            offsetDir = when (spinnerSelected(binding.spinnerVfoBOffsetDir)) { "+" -> "+"; "-" -> "-"; else -> "off" },
            offsetMhz = binding.editVfoBOffset.text.toString().trim().toDoubleOrNull() ?: 0.0,
            fhss = if (spinnerSelected(binding.spinnerVfoBFhss) == getString(R.string.option_on)) 1 else 0,
        )
    }

    private fun setupSpinner(spinner: Spinner, options: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun selectToneValue(spinner: Spinner, value: String) {
        val adapter = spinner.adapter as? ArrayAdapter<String>
        val safe = if (value.isBlank()) "OFF" else value
        if (adapter != null) {
            val exists = (0 until adapter.count).any { adapter.getItem(it) == safe }
            if (!exists) adapter.add(safe)
        }
        selectValue(spinner, safe)
        lastToneValue[spinner.id] = safe
    }

    private fun selectValue(spinner: Spinner, value: String) {
        val items = (0 until spinner.count).map { spinner.getItemAtPosition(it).toString() }
        val index = items.indexOf(value)
        if (index >= 0) spinner.setSelection(index, false)
    }

    private fun selectIndex(spinner: Spinner, index: Int) {
        spinner.setSelection(index.coerceIn(0, spinner.count - 1), false)
    }

    private fun spinnerSelected(spinner: Spinner): String = spinner.selectedItem?.toString().orEmpty()

    private fun validateVfoScanRange(lowText: String, highText: String, showErrors: Boolean = true): Pair<Int, Int>? {
        val low = lowText.toIntOrNull()
        val high = highText.toIntOrNull()
        if (low == null || high == null) {
            if (showErrors) Toast.makeText(requireContext(), getString(R.string.err_vfo_scan_integer), Toast.LENGTH_SHORT).show()
            return null
        }
        if (low > high) {
            if (showErrors) Toast.makeText(requireContext(), getString(R.string.err_vfo_scan_low_gt_high), Toast.LENGTH_SHORT).show()
            return null
        }
        val lowBand = scanBand(low)
        val highBand = scanBand(high)
        if (lowBand == null || highBand == null) {
            if (showErrors) Toast.makeText(requireContext(), getString(R.string.err_vfo_scan_bands), Toast.LENGTH_SHORT).show()
            return null
        }
        if (lowBand != highBand) {
            if (showErrors) Toast.makeText(requireContext(), getString(R.string.err_vfo_scan_same_band), Toast.LENGTH_SHORT).show()
            return null
        }
        return low to high
    }

    private fun scanBand(v: Int): Int? {
        return when (v) {
            in 136..174 -> 1
            in 400..520 -> 2
            else -> null
        }
    }

    private fun isAllowedFreqMhz(v: Double?): Boolean {
        val f = v ?: return false
        return (f >= 108.0 && f < 174.0) ||
            (f >= 350.0 && f < 390.0) ||
            (f >= 400.0 && f < 520.0)
    }

    private fun isAirbandMhz(v: Double): Boolean {
        return v >= 108.0 && v <= 135.99875
    }

    private fun parseAllowedFreqMhz(text: String): Double? {
        val v = text.trim().toDoubleOrNull() ?: return null
        if (!isAllowedFreqMhz(v)) return null
        if (isAirbandMhz(v)) return v
        val snapped = snapDownToMinRaster(v)
        return if (isAllowedFreqMhz(snapped)) snapped else null
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

    private fun normalizeFreqText(edit: EditText, value: Double?) {
        if (value == null) return
        val normalized = String.format(Locale.US, "%.5f", value)
        val current = edit.text?.toString().orEmpty().trim()
        if (current != normalized) {
            edit.setText(normalized)
        }
    }

    private fun bestToneValue(raw: String): String {
        return toneOptions.firstOrNull { it.equals(raw, ignoreCase = true) } ?: "OFF"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
