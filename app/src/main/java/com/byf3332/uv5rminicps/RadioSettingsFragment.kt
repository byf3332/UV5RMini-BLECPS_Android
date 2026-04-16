package com.byf3332.uv5rminicps

import android.os.Bundle
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
import com.byf3332.uv5rminicps.core.RadioSettingsCodec
import com.byf3332.uv5rminicps.core.SettingItem
import com.byf3332.uv5rminicps.databinding.FragmentSettingsBinding

class RadioSettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: CpsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm.settings.observe(viewLifecycleOwner) { renderRows(it) }
    }

    private fun renderRows(rows: List<SettingItem>) {
        val box = binding.layoutSettingsList
        box.removeAllViews()
        val visibleRows = rows.filterNot { it.key == "vfo_scan_range_l" || it.key == "vfo_scan_range_h" }

        if (visibleRows.isEmpty()) {
            val density = requireContext().resources.displayMetrics.density
            val pad = (20 * density).toInt()

            box.addView(TextView(requireContext()).apply {
                text = getString(R.string.settings_empty)
                gravity = android.view.Gravity.CENTER
                setPadding(pad, pad, pad, pad)
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            return
        }

        visibleRows.forEach { row ->
            box.addView(makeSettingRow(row))
        }
    }

    private fun makeSettingRow(row: SettingItem): View {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(requireContext()).apply {
            text = row.label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(label)

        val options = RadioSettingsCodec.optionsFor(row.key, resources)
        if (options.isNotEmpty()) {
            val spinner = Spinner(requireContext())
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.adapter = adapter
            spinner.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val index = options.indexOf(row.value).let { if (it < 0) 0 else it }
            var initialized = false
            spinner.setSelection(index, false)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!initialized) {
                        initialized = true
                        return
                    }
                    vm.updateSetting(row.key, options[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            container.addView(spinner)
        } else {
            val editor = EditText(requireContext()).apply {
                setText(row.value)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        vm.updateSetting(row.key, text.toString().trim())
                        true
                    } else {
                        false
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) vm.updateSetting(row.key, text.toString().trim())
                }
            }
            container.addView(editor)
        }

        return container
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
