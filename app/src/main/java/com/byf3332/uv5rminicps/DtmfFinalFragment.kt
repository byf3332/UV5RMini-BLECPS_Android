package com.byf3332.uv5rminicps

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.byf3332.uv5rminicps.core.DtmfCodec
import com.byf3332.uv5rminicps.core.DtmfContact
import com.byf3332.uv5rminicps.core.DtmfState
import com.byf3332.uv5rminicps.core.SettingItem
import com.byf3332.uv5rminicps.databinding.FragmentDtmfBinding

class DtmfFinalFragment : Fragment() {
    private var _binding: FragmentDtmfBinding? = null
    private val binding get() = _binding!!
    private val vm: CpsViewModel by activityViewModels()
    private val txtLocalId by lazy { getString(R.string.dtmf_local_id) }
    private val txtDtmfDuration by lazy { getString(R.string.dtmf_duration) }
    private val txtDtmfGap by lazy { getString(R.string.dtmf_gap) }
    private val txtHangup by lazy { getString(R.string.dtmf_hangup) }
    private val txtSeparator by lazy { getString(R.string.dtmf_separator) }
    private val txtGroupCall by lazy { getString(R.string.dtmf_group_call) }
    private val txtOnline by lazy { getString(R.string.dtmf_online) }
    private val txtOffline by lazy { getString(R.string.dtmf_offline) }
    private val txtColIndex by lazy { getString(R.string.dtmf_col_index) }
    private val txtColCode by lazy { getString(R.string.dtmf_col_code) }
    private val txtColName by lazy { getString(R.string.dtmf_col_name) }

    private val codeEdits = mutableListOf<EditText>()
    private val nameEdits = mutableListOf<EditText>()
    private lateinit var editLocalId: EditText
    private lateinit var spinnerDuration: Spinner
    private lateinit var spinnerGap: Spinner
    private lateinit var spinnerHangup: Spinner
    private lateinit var spinnerSeparator: Spinner
    private lateinit var spinnerGroupCall: Spinner
    private lateinit var editOnline: EditText
    private lateinit var editOffline: EditText

    private var suppressUi = false
    private var suppressVmEcho = false
    private var uiReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDtmfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textDtmfEmpty.text = getString(R.string.dtmf_empty)
        buildUi()
        vm.channels.observe(viewLifecycleOwner) { setLoadedState(it.isNotEmpty()) }
        vm.settings.observe(viewLifecycleOwner) { bindHangupSetting(it) }
        vm.dtmf.observe(viewLifecycleOwner) { state ->
            if (suppressVmEcho) {
                suppressVmEcho = false
            } else {
                bindState(state)
            }
        }
    }

    private fun setLoadedState(loaded: Boolean) {
        binding.layoutDtmfContent.visibility = if (loaded) View.VISIBLE else View.GONE
        binding.textDtmfEmpty.visibility = if (loaded) View.GONE else View.VISIBLE
    }

    private fun buildUi() {
        if (uiReady) return
        codeEdits.clear()
        nameEdits.clear()

        val contactsBox = binding.layoutDtmfContacts
        contactsBox.addView(makeContactsHeader())
        repeat(20) { idx ->
            contactsBox.addView(makeContactRow(idx + 1))
        }

        val bottom = binding.layoutDtmfBottom
        editLocalId = makeBottomEdit(bottom, txtLocalId, InputType.TYPE_CLASS_NUMBER)
        spinnerDuration = makeBottomSpinner(bottom, txtDtmfDuration, DtmfCodec.codeDurationOptions())
        spinnerGap = makeBottomSpinner(bottom, txtDtmfGap, DtmfCodec.codeGapOptions())
        spinnerHangup = makeBottomSpinner(bottom, txtHangup, DtmfCodec.hangupOptions())
        spinnerSeparator = makeBottomSpinner(bottom, txtSeparator, DtmfCodec.separatorOptions())
        spinnerGroupCall = makeBottomSpinner(bottom, txtGroupCall, DtmfCodec.groupCallOptions())
        editOnline = makeBottomEdit(bottom, txtOnline, InputType.TYPE_CLASS_TEXT)
        editOffline = makeBottomEdit(bottom, txtOffline, InputType.TYPE_CLASS_TEXT)

        uiReady = true
    }

    private fun makeContactsHeader(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(4))
            addView(TextView(requireContext()).apply {
                text = txtColIndex
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(requireContext()).apply {
                text = txtColCode
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
            })
            addView(TextView(requireContext()).apply {
                text = txtColName
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun makeContactRow(index1Based: Int): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val tvIdx = TextView(requireContext()).apply {
            text = index1Based.toString()
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val code = EditText(requireContext()).apply {
            hint = txtColCode
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(8)
            }
        }
        val name = EditText(requireContext()).apply {
            hint = txtColName
            maxLines = 1
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        attachEditAutoSave(code)
        attachEditAutoSave(name)
        codeEdits += code
        nameEdits += name
        row.addView(tvIdx)
        row.addView(code)
        row.addView(name)
        return row
    }

    private fun bindState(state: DtmfState) {
        if (!uiReady) return
        suppressUi = true
        try {
            val contacts = state.contacts.sortedBy { it.index }
            repeat(20) { i ->
                val c = contacts.getOrNull(i) ?: DtmfContact(i + 1)
                codeEdits[i].setText(c.code)
                nameEdits[i].setText(c.name)
            }
            editLocalId.setText(state.localId)
            setSpinnerValue(spinnerDuration, state.codeDurationMs)
            setSpinnerValue(spinnerGap, state.codeGapMs)
            setSpinnerValue(spinnerHangup, state.hangup)
            setSpinnerValue(spinnerSeparator, state.separator)
            setSpinnerValue(spinnerGroupCall, state.groupCall)
            editOnline.setText(state.onlineCode)
            editOffline.setText(state.offlineCode)
        } finally {
            suppressUi = false
        }
    }

    private fun persist() {
        if (suppressUi || !uiReady) return
        val contacts = mutableListOf<DtmfContact>()
        repeat(20) { i ->
            contacts += DtmfContact(
                index = i + 1,
                code = codeEdits[i].text?.toString()?.trim().orEmpty(),
                name = nameEdits[i].text?.toString()?.trim().orEmpty(),
            )
        }
        val next = DtmfState(
            contacts = contacts,
            localId = editLocalId.text?.toString()?.trim().orEmpty(),
            codeDurationMs = selected(spinnerDuration),
            codeGapMs = selected(spinnerGap),
            hangup = selected(spinnerHangup),
            separator = selected(spinnerSeparator),
            groupCall = selected(spinnerGroupCall),
            onlineCode = editOnline.text?.toString()?.trim().orEmpty(),
            offlineCode = editOffline.text?.toString()?.trim().orEmpty(),
        )
        suppressVmEcho = true
        vm.updateDtmf(next)
        vm.updateSetting("dtmf_hangup", next.hangup)
    }

    private fun bindHangupSetting(rows: List<SettingItem>) {
        val v = rows.firstOrNull { it.key == "dtmf_hangup" }?.value ?: return
        suppressUi = true
        try {
            setSpinnerValue(spinnerHangup, v)
        } finally {
            suppressUi = false
        }
    }

    private fun makeBottomEdit(parent: LinearLayout, label: String, inputType: Int): EditText {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val tv = TextView(requireContext()).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val edit = EditText(requireContext()).apply {
            this.inputType = inputType
            maxLines = 1
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        attachEditAutoSave(edit)
        row.addView(tv)
        row.addView(edit)
        parent.addView(row)
        return edit
    }

    private fun makeBottomSpinner(parent: LinearLayout, label: String, options: List<String>): Spinner {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        val tv = TextView(requireContext()).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val spinner = Spinner(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        var initialized = false
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUi) return
                if (!initialized) {
                    initialized = true
                    return
                }
                persist()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        row.addView(tv)
        row.addView(spinner)
        parent.addView(row)
        return spinner
    }

    private fun setSpinnerValue(spinner: Spinner, value: String) {
        val idx =
            (0 until spinner.count).firstOrNull { spinner.getItemAtPosition(it).toString() == value } ?: 0
        spinner.setSelection(idx, false)
    }

    private fun selected(spinner: Spinner): String = spinner.selectedItem?.toString().orEmpty()

    private fun attachEditAutoSave(edit: EditText) {
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                persist()
                true
            } else {
                false
            }
        }
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persist()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

