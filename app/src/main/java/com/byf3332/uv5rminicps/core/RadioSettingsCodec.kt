package com.byf3332.uv5rminicps.core

import android.content.res.Resources
import com.byf3332.uv5rminicps.R
import java.util.Locale

data class SettingItem(val key: String, val label: String, val value: String)

object RadioSettingsCodec {
    private fun s(res: Resources, id: Int): String = res.getString(id)

    private fun onOff(res: Resources): List<String> = listOf(
        s(res, R.string.option_off),
        s(res, R.string.option_on),
    )

    private fun pttIdOpts(res: Resources): List<String> = listOf(
        s(res, R.string.option_none),
        s(res, R.string.option_ptt_press),
        s(res, R.string.option_ptt_release),
        s(res, R.string.option_ptt_press_release_full),
    )

    private fun sideToneOpts(res: Resources): List<String> = listOf(
        s(res, R.string.option_off),
        s(res, R.string.rs_side_tone_key),
        s(res, R.string.rs_side_tone_id),
        s(res, R.string.rs_side_tone_key_id),
    )

    private fun displayTypeOpts(res: Resources): List<String> = listOf(
        s(res, R.string.rs_display_name),
        s(res, R.string.rs_display_freq),
        s(res, R.string.rs_display_channel_no),
    )

    private fun key1ShortOpts(res: Resources): List<String> = listOf(
        s(res, R.string.rs_key1_reserved_0),
        s(res, R.string.rs_key1_reserved_1),
        s(res, R.string.rs_key1_reserved_2),
        s(res, R.string.rs_key1_reserved_3),
        s(res, R.string.rs_key1_scan),
        s(res, R.string.rs_key1_sweep),
        s(res, R.string.rs_key1_vox),
        s(res, R.string.rs_key1_fm),
        s(res, R.string.rs_key1_flashlight),
        s(res, R.string.rs_key1_alarm),
    )

    fun labelForKey(key: String, res: Resources): String = when (key) {
        "sql" -> s(res, R.string.rs_label_sql)
        "save_mode" -> s(res, R.string.rs_label_save_mode)
        "vox" -> s(res, R.string.rs_label_vox)
        "backlight" -> s(res, R.string.rs_label_backlight)
        "dual_standby" -> s(res, R.string.rs_label_dual_standby)
        "tot" -> s(res, R.string.rs_label_tot)
        "beep" -> s(res, R.string.rs_label_beep)
        "voice" -> s(res, R.string.rs_label_voice)
        "language" -> s(res, R.string.rs_label_language)
        "side_tone" -> s(res, R.string.rs_label_side_tone)
        "scan_mode" -> s(res, R.string.rs_label_scan_mode)
        "vfo_pttid" -> s(res, R.string.rs_label_vfo_pttid)
        "ptt_delay" -> s(res, R.string.rs_label_ptt_delay)
        "ch_a_display_type" -> s(res, R.string.rs_label_ch_a_display_type)
        "ch_b_display_type" -> s(res, R.string.rs_label_ch_b_display_type)
        "vfo_busy_lock" -> s(res, R.string.rs_label_vfo_busy_lock)
        "auto_lock" -> s(res, R.string.rs_label_auto_lock)
        "alarm_mode" -> s(res, R.string.rs_label_alarm_mode)
        "alarm_tone" -> s(res, R.string.rs_label_alarm_tone)
        "tail_clear" -> s(res, R.string.rs_label_tail_clear)
        "rpt_tail_clear" -> s(res, R.string.rs_label_rpt_tail_clear)
        "rpt_tail_det" -> s(res, R.string.rs_label_rpt_tail_det)
        "roger" -> s(res, R.string.rs_label_roger)
        "fm_enable" -> s(res, R.string.rs_label_fm_enable)
        "ch_a_workmode" -> s(res, R.string.rs_label_ch_a_workmode)
        "ch_b_workmode" -> s(res, R.string.rs_label_ch_b_workmode)
        "key_lock" -> s(res, R.string.rs_label_key_lock)
        "power_on_display_type" -> s(res, R.string.rs_label_power_on_display_type)
        "tone" -> s(res, R.string.rs_label_tone)
        "vox_delay_time" -> s(res, R.string.rs_label_vox_delay_time)
        "menu_quit_time" -> s(res, R.string.rs_label_menu_quit_time)
        "tot_alarm" -> s(res, R.string.rs_label_tot_alarm)
        "cts_dcs_scan_type" -> s(res, R.string.rs_label_cts_dcs_scan_type)
        "vfo_scan_range_l" -> s(res, R.string.rs_label_vfo_scan_range_l)
        "vfo_scan_range_h" -> s(res, R.string.rs_label_vfo_scan_range_h)
        "key1_short" -> s(res, R.string.rs_label_key1_short)
        "rst_menu" -> s(res, R.string.rs_label_rst_menu)
        "dtmf_hangup" -> s(res, R.string.rs_label_dtmf_hangup)
        "vox_sw" -> s(res, R.string.rs_label_vox_sw)
        else -> key
    }

    fun optionsFor(key: String, res: Resources): List<String> = when (key) {
        "sql" -> listOf(s(res, R.string.option_off)) + (1..9).map { it.toString() }
        "save_mode", "dual_standby", "beep", "voice", "vfo_busy_lock", "auto_lock",
        "alarm_tone", "tail_clear", "roger", "fm_enable", "key_lock", "rst_menu", "vox_sw" -> onOff(res)
        "vox" -> (1..10).map { it.toString() }
        "backlight" -> listOf(
            s(res, R.string.rs_backlight_always),
            "5s",
            "10s",
            "15s",
            "20s",
        )
        "tot" -> listOf(s(res, R.string.option_off)) + (1..12).map { "${it * 15}s" }
        "language" -> listOf(s(res, R.string.rs_language_english), s(res, R.string.rs_language_chinese))
        "side_tone" -> sideToneOpts(res)
        "scan_mode" -> listOf(
            s(res, R.string.rs_scan_mode_time),
            s(res, R.string.rs_scan_mode_carrier),
            s(res, R.string.rs_scan_mode_search),
        )
        "vfo_pttid" -> pttIdOpts(res)
        "ptt_delay" -> (1..30).map { "${it * 100}ms" }
        "ch_a_display_type", "ch_b_display_type" -> displayTypeOpts(res)
        "alarm_mode" -> listOf(
            s(res, R.string.rs_alarm_mode_site),
            s(res, R.string.rs_alarm_mode_tone),
            s(res, R.string.rs_alarm_mode_code),
        )
        "ch_a_workmode", "ch_b_workmode" -> listOf(
            s(res, R.string.rs_workmode_vfo),
            s(res, R.string.rs_workmode_channel),
        )
        "power_on_display_type" -> listOf(
            s(res, R.string.rs_power_on_logo),
            s(res, R.string.rs_power_on_voltage),
        )
        "tone" -> listOf("1000Hz", "1450Hz", "1750Hz", "2100Hz")
        "vox_delay_time" -> (0..15).map { String.format(Locale.US, "%.1fs", 0.5 + it * 0.1) }
        "menu_quit_time" -> (1..12).map { "${it * 5}s" }
        "tot_alarm" -> listOf(s(res, R.string.option_off)) + (1..10).map { "${it}s" }
        "cts_dcs_scan_type" -> listOf(
            s(res, R.string.rs_cts_scan_all),
            s(res, R.string.rs_cts_scan_rx),
            s(res, R.string.rs_cts_scan_tx),
        )
        "rpt_tail_clear", "rpt_tail_det" -> (0..10).map { "${it * 100}ms" }
        "dtmf_hangup" -> (3..10).map { "${it}s" }
        "key1_short" -> key1ShortOpts(res)
        else -> emptyList()
    }

    fun decode(image: ByteArray, res: Resources): List<SettingItem> {
        if (image.size < 0x9040) return emptyList()
        val b = image.copyOfRange(0x9000, 0x9040)

        val sql = (b[0x00].toInt() and 0xFF) % 10
        val vox = (b[0x02].toInt() and 0xFF) % 10
        val backlight = (b[0x03].toInt() and 0xFF) % 9
        val tot = (b[0x05].toInt() and 0xFF) % 13
        val sideTone = (b[0x09].toInt() and 0xFF) % 4
        val scanMode = (b[0x0A].toInt() and 0xFF) % 3
        val pttid = (b[0x0B].toInt() and 0xFF) % 4
        val pttDelay = (b[0x0C].toInt() and 0xFF) % 30
        val aDisp = (b[0x0D].toInt() and 0xFF) % 3
        val bDisp = (b[0x0E].toInt() and 0xFF) % 3
        val alarmMode = (b[0x11].toInt() and 0xFF) % 3
        val poDisp = (b[0x1C].toInt() and 0xFF) % 2
        val tone = (b[0x1D].toInt() and 0xFF) % 4
        val voxDelay = (b[0x20].toInt() and 0xFF) % 16
        val menuQuit = (b[0x21].toInt() and 0xFF) % 12
        val totAlarm = (b[0x28].toInt() and 0xFF) % 11
        val ctsScan = (b[0x2B].toInt() and 0xFF) % 3
        val key1 = b[0x32].toInt() and 0xFF
        val rangeL = ((b[0x2D].toInt() and 0xFF) shl 8) or (b[0x2C].toInt() and 0xFF)
        val rangeH = ((b[0x2F].toInt() and 0xFF) shl 8) or (b[0x2E].toInt() and 0xFF)

        fun bool(value: Int): String = onOff(res)[value.coerceIn(0, 1)]
        fun item(key: String, value: String): SettingItem = SettingItem(key, labelForKey(key, res), value)

        return listOf(
            item("sql", if (sql == 0) s(res, R.string.option_off) else sql.toString()),
            item("save_mode", bool((b[0x01].toInt() and 0xFF) % 2)),
            item("vox", (vox + 1).toString()),
            item("backlight", optionsFor("backlight", res).getOrElse(backlight) { optionsFor("backlight", res).first() }),
            item("dual_standby", bool((b[0x04].toInt() and 0xFF) % 2)),
            item("tot", optionsFor("tot", res).getOrElse(tot) { optionsFor("tot", res).first() }),
            item("beep", bool((b[0x06].toInt() and 0xFF) % 2)),
            item("voice", bool((b[0x07].toInt() and 0xFF) % 2)),
            item("language", optionsFor("language", res).getOrElse((b[0x08].toInt() and 0xFF) % 2) { optionsFor("language", res).first() }),
            item("side_tone", optionsFor("side_tone", res).getOrElse(sideTone) { "RAW($sideTone)" }),
            item("scan_mode", optionsFor("scan_mode", res).getOrElse(scanMode) { optionsFor("scan_mode", res).first() }),
            item("vfo_pttid", optionsFor("vfo_pttid", res).getOrElse(pttid) { optionsFor("vfo_pttid", res).first() }),
            item("ptt_delay", optionsFor("ptt_delay", res).getOrElse(pttDelay) { "RAW($pttDelay)" }),
            item("ch_a_display_type", optionsFor("ch_a_display_type", res).getOrElse(aDisp) { "RAW($aDisp)" }),
            item("ch_b_display_type", optionsFor("ch_b_display_type", res).getOrElse(bDisp) { "RAW($bDisp)" }),
            item("vfo_busy_lock", bool((b[0x0F].toInt() and 0xFF) % 2)),
            item("auto_lock", bool((b[0x10].toInt() and 0xFF) % 2)),
            item("alarm_mode", optionsFor("alarm_mode", res).getOrElse(alarmMode) { optionsFor("alarm_mode", res).first() }),
            item("alarm_tone", bool((b[0x12].toInt() and 0xFF) % 2)),
            item("tail_clear", bool((b[0x14].toInt() and 0xFF) % 2)),
            item("rpt_tail_clear", optionsFor("rpt_tail_clear", res).getOrElse((b[0x15].toInt() and 0xFF) % 11) { "0ms" }),
            item("rpt_tail_det", optionsFor("rpt_tail_det", res).getOrElse((b[0x16].toInt() and 0xFF) % 11) { "0ms" }),
            item("roger", bool((b[0x17].toInt() and 0xFF) % 2)),
            item("fm_enable", if (((b[0x19].toInt() and 0xFF) % 2) == 0) s(res, R.string.option_on) else s(res, R.string.option_off)),
            item("ch_a_workmode", optionsFor("ch_a_workmode", res).getOrElse((b[0x1A].toInt() and 0x0F) % 2) { optionsFor("ch_a_workmode", res).first() }),
            item("ch_b_workmode", optionsFor("ch_b_workmode", res).getOrElse(((b[0x1A].toInt() shr 4) and 0x0F) % 2) { optionsFor("ch_b_workmode", res).first() }),
            item("key_lock", bool(b[0x1B].toInt() and 1)),
            item("power_on_display_type", optionsFor("power_on_display_type", res).getOrElse(poDisp) { "RAW($poDisp)" }),
            item("tone", optionsFor("tone", res).getOrElse(tone) { optionsFor("tone", res).first() }),
            item("vox_delay_time", optionsFor("vox_delay_time", res).getOrElse(voxDelay) { optionsFor("vox_delay_time", res).first() }),
            item("menu_quit_time", optionsFor("menu_quit_time", res).getOrElse(menuQuit) { optionsFor("menu_quit_time", res).first() }),
            item("tot_alarm", optionsFor("tot_alarm", res).getOrElse(totAlarm) { optionsFor("tot_alarm", res).first() }),
            item("cts_dcs_scan_type", optionsFor("cts_dcs_scan_type", res).getOrElse(ctsScan) { optionsFor("cts_dcs_scan_type", res).first() }),
            item("vfo_scan_range_l", rangeL.toString()),
            item("vfo_scan_range_h", rangeH.toString()),
            item("key1_short", optionsFor("key1_short", res).getOrElse(key1) { "RAW($key1)" }),
            item("rst_menu", bool((b[0x37].toInt() and 0xFF) % 2)),
            item("dtmf_hangup", optionsFor("dtmf_hangup", res).getOrElse((b[0x39].toInt() and 0xFF) % 8) { "3s" }),
            item("vox_sw", bool((b[0x3A].toInt() and 0xFF) % 2)),
        )
    }

    fun applyEdits(image: ByteArray, edits: Map<String, String>, res: Resources): ByteArray {
        if (image.size < 0x9040) return image
        val out = image.copyOf()
        val b = out.copyOfRange(0x9000, 0x9040)

        fun digits(v: String): Int = Regex("""-?\d+""").find(v)?.value?.toIntOrNull() ?: 0
        fun isOn(v: String): Int = if (v == s(res, R.string.option_on) || v.equals("on", true) || v == "1") 1 else 0
        fun setByOptions(key: String, raw: String): Int {
            val options = optionsFor(key, res)
            val idx = options.indexOf(raw)
            return if (idx >= 0) idx else digits(raw)
        }

        edits.forEach { (k, raw) ->
            when (k) {
                "sql" -> b[0x00] = if (raw == s(res, R.string.option_off)) 0 else digits(raw).coerceIn(1, 9).toByte()
                "save_mode" -> b[0x01] = isOn(raw).toByte()
                "vox" -> b[0x02] = (digits(raw) - 1).coerceIn(0, 9).toByte()
                "backlight" -> b[0x03] = setByOptions("backlight", raw).coerceIn(0, 8).toByte()
                "dual_standby" -> b[0x04] = isOn(raw).toByte()
                "tot" -> b[0x05] = setByOptions("tot", raw).coerceIn(0, 12).toByte()
                "beep" -> b[0x06] = isOn(raw).toByte()
                "voice" -> b[0x07] = isOn(raw).toByte()
                "language" -> b[0x08] = setByOptions("language", raw).coerceIn(0, 1).toByte()
                "side_tone" -> b[0x09] = setByOptions("side_tone", raw).coerceIn(0, 3).toByte()
                "scan_mode" -> b[0x0A] = setByOptions("scan_mode", raw).coerceIn(0, 2).toByte()
                "vfo_pttid" -> b[0x0B] = setByOptions("vfo_pttid", raw).coerceIn(0, 3).toByte()
                "ptt_delay" -> b[0x0C] = setByOptions("ptt_delay", raw).coerceIn(0, 29).toByte()
                "ch_a_display_type" -> b[0x0D] = setByOptions("ch_a_display_type", raw).coerceIn(0, 2).toByte()
                "ch_b_display_type" -> b[0x0E] = setByOptions("ch_b_display_type", raw).coerceIn(0, 2).toByte()
                "vfo_busy_lock" -> b[0x0F] = isOn(raw).toByte()
                "auto_lock" -> b[0x10] = isOn(raw).toByte()
                "alarm_mode" -> b[0x11] = setByOptions("alarm_mode", raw).coerceIn(0, 2).toByte()
                "alarm_tone" -> b[0x12] = isOn(raw).toByte()
                "tail_clear" -> b[0x14] = isOn(raw).toByte()
                "rpt_tail_clear" -> b[0x15] = setByOptions("rpt_tail_clear", raw).coerceIn(0, 10).toByte()
                "rpt_tail_det" -> b[0x16] = setByOptions("rpt_tail_det", raw).coerceIn(0, 10).toByte()
                "roger" -> b[0x17] = isOn(raw).toByte()
                "fm_enable" -> b[0x19] = if (isOn(raw) == 1) 0 else 1
                "ch_a_workmode" -> {
                    val mode = setByOptions("ch_a_workmode", raw).coerceIn(0, 1)
                    b[0x1A] = ((b[0x1A].toInt() and 0xF0) or mode).toByte()
                }
                "ch_b_workmode" -> {
                    val mode = setByOptions("ch_b_workmode", raw).coerceIn(0, 1)
                    b[0x1A] = ((b[0x1A].toInt() and 0x0F) or (mode shl 4)).toByte()
                }
                "key_lock" -> b[0x1B] = ((b[0x1B].toInt() and 0xFE) or isOn(raw)).toByte()
                "power_on_display_type" -> b[0x1C] = setByOptions("power_on_display_type", raw).coerceIn(0, 1).toByte()
                "tone" -> b[0x1D] = setByOptions("tone", raw).coerceIn(0, 3).toByte()
                "vox_delay_time" -> b[0x20] = setByOptions("vox_delay_time", raw).coerceIn(0, 15).toByte()
                "menu_quit_time" -> b[0x21] = setByOptions("menu_quit_time", raw).coerceIn(0, 11).toByte()
                "tot_alarm" -> b[0x28] = setByOptions("tot_alarm", raw).coerceIn(0, 10).toByte()
                "cts_dcs_scan_type" -> b[0x2B] = setByOptions("cts_dcs_scan_type", raw).coerceIn(0, 2).toByte()
                "vfo_scan_range_l" -> {
                    val v = digits(raw).coerceIn(0, 9999)
                    b[0x2C] = (v and 0xFF).toByte()
                    b[0x2D] = ((v shr 8) and 0xFF).toByte()
                }
                "vfo_scan_range_h" -> {
                    val v = digits(raw).coerceIn(0, 9999)
                    b[0x2E] = (v and 0xFF).toByte()
                    b[0x2F] = ((v shr 8) and 0xFF).toByte()
                }
                "key1_short" -> b[0x32] = setByOptions("key1_short", raw).coerceIn(0, 255).toByte()
                "rst_menu" -> b[0x37] = isOn(raw).toByte()
                "dtmf_hangup" -> b[0x39] = setByOptions("dtmf_hangup", raw).coerceIn(0, 7).toByte()
                "vox_sw" -> b[0x3A] = isOn(raw).toByte()
            }
        }

        System.arraycopy(b, 0, out, 0x9000, 64)
        return out
    }
}

