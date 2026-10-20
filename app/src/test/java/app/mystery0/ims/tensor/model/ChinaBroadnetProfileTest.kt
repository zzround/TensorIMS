package app.mystery0.ims.tensor.model

import org.junit.Assert.*
import org.junit.Test

class ChinaBroadnetProfileTest {
    private fun bool(value: Boolean) = FeatureValue(value, FeatureValueType.BOOLEAN)

    private fun snapshot(subId: Int = 1, homePlmn: String? = "46015"): TargetConfigSnapshot {
        val values = Feature.entries.associateWith { FeatureValue(it.defaultValue, it.valueType) }
        return TargetConfigSnapshot(subId, "sim-$subId", values,
            recovery = values.keys.associateWith { feature ->
                if (feature.valueType == FeatureValueType.INTEGER) ConfigRecovery.CHOICE else ConfigRecovery.SWITCH
            }, homePlmn = homePlmn)
    }

    @Test
    fun `归属 PLMN 保留 MNC 位数且不补齐缺失值`() {
        assertEquals("46015", ChinaBroadnetProfile.homePlmn("460", "15"))
        assertEquals("460015", ChinaBroadnetProfile.homePlmn("460", "015"))
        for ((mcc, mnc) in listOf(null to "15", "460" to null, "" to "15",
            "460" to "", "46" to "15", "460" to "1", "460" to "15 ", "460" to "1A")) {
            assertNull(ChinaBroadnetProfile.homePlmn(mcc, mnc))
        }
    }

    @Test
    fun `仅识别广电归属卡而不把共建共享或漫游网络当作归属`() {
        assertTrue(ChinaBroadnetProfile.isEligible(ImsEditorState(subId = 1, snapshots = listOf(snapshot()))))
        // 46000 和 46013 属于移动；国际漫游卡不能因当前接入广电或移动而套用此预设。
        for (homePlmn in listOf("46000", "46013", "46001", "46011", "460015", "310260", "52501", null, "")) {
            assertFalse(ChinaBroadnetProfile.isEligible(ImsEditorState(subId = 1,
                snapshots = listOf(snapshot(homePlmn = homePlmn)))))
        }
    }

    @Test
    fun `全卡选择身份不明或读取失败均不能使用广电预设`() {
        val selected = ImsEditorState(subId = 1, snapshots = listOf(snapshot()))
        val rejected = listOf(
            selected.copy(subId = -1), selected.copy(subId = null), selected.copy(subId = 2),
            selected.copy(loading = true), selected.copy(snapshots = emptyList()),
            selected.copy(snapshots = listOf(snapshot(), snapshot(2))),
            selected.copy(snapshots = listOf(snapshot().copy(identity = ""))),
            selected.copy(snapshots = listOf(snapshot().copy(error = "Unavailable"))),
        )
        for (state in rejected) {
            assertFalse(ChinaBroadnetProfile.isEligible(state))
            assertNull(ChinaBroadnetProfile.stage(state))
        }
    }

    @Test
    fun `广电预设只写七个蜂窝配置键且不改 Wi-Fi 通话或业务鉴权`() {
        val profile = imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G)
        assertEquals(setOf(Feature.VOLTE, Feature.FIVE_G_NR, Feature.VONR,
            Feature.ENHANCED_4G_LTE), profile.keys)
        val writes = profile.flatMap { (feature, value) ->
            TargetConfigMapper.writes(feature, value, emptyMap()).entries.map { it.key to it.value }
        }.toMap()
        assertArrayEquals(intArrayOf(1, 2), writes[TargetConfigMapper.NR_KEY] as IntArray)
        assertEquals(mapOf(
            "carrier_volte_available_bool" to true,
            "vonr_enabled_bool" to true,
            "vonr_setting_visibility_bool" to true,
            "editable_enhanced_4g_lte_bool" to true,
            "enhanced_4g_lte_on_by_default_bool" to true,
            "hide_enhanced_4g_lte_bool" to false,
        ), writes - TargetConfigMapper.NR_KEY)
        assertFalse(writes.keys.any { it.contains("provision") || it.contains("entitlement") ||
            it.contains("wfc") || it.contains("threshold") || it.contains("icon") ||
            it.contains("roaming_network") || it.contains("sa_disable") })
        assertFalse(TargetConfigMapper.NAME in writes)
        assertFalse(TargetConfigMapper.USER_AGENT in writes)
        assertFalse(TargetConfigMapper.USSD_METHOD in writes)
    }

    @Test
    fun `已有 Wi-Fi 通话开关无论开启关闭或未知都不被预设添加到草稿`() {
        for (wifi in listOf(true, false, null)) {
            for (roaming in listOf(true, false, null)) {
                val initial = snapshot()
                val values = (initial.values - setOf(Feature.VOWIFI, Feature.VOWIFI_ROAMING)).toMutableMap()
                wifi?.let { values[Feature.VOWIFI] = bool(it) }
                roaming?.let { values[Feature.VOWIFI_ROAMING] = bool(it) }
                val state = ImsEditorState(subId = 1, snapshots = listOf(initial.copy(values = values)))
                val staged = requireNotNull(ChinaBroadnetProfile.stage(state))
                assertFalse(staged.skipped)
                assertFalse(Feature.VOWIFI in staged.edits)
                assertFalse(Feature.VOWIFI_ROAMING in staged.edits)
                val displayed = state.copy(edits = staged.edits).values
                assertEquals(wifi?.let(::bool), displayed[Feature.VOWIFI])
                assertEquals(roaming?.let(::bool), displayed[Feature.VOWIFI_ROAMING])
            }
        }
    }

    @Test
    fun `广电预设保留用户明确开启或关闭 Wi-Fi 通话的手工草稿`() {
        for (wifi in listOf(true, false)) {
            for (roaming in listOf(true, false)) {
                val manual = mapOf(Feature.VOWIFI to bool(wifi), Feature.VOWIFI_ROAMING to bool(roaming))
                val state = ImsEditorState(subId = 1, snapshots = listOf(snapshot()), edits = manual)
                val staged = requireNotNull(ChinaBroadnetProfile.stage(state))
                assertFalse(staged.skipped)
                assertEquals(manual + imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G), staged.edits)
                assertEquals(manual, state.edits)
            }
        }
    }

    @Test
    fun `新广电预设不迁移仍与当前值一致的旧 Wi-Fi 通话历史`() {
        for (enabled in listOf(true, false)) {
            val history = mapOf(Feature.VOWIFI to bool(enabled), Feature.VOWIFI_ROAMING to bool(enabled))
            val profile = imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G)
            assertEquals(history + profile, confirmedTargetHistory(history, profile, history + profile))
        }
    }

    @Test
    fun `广电预设保留无关手工草稿并保留相同显示值的精确 NR 目标`() {
        val manual = mapOf(Feature.CARRIER_NAME to FeatureValue("My SIM", FeatureValueType.STRING),
            Feature.USSD_METHOD to FeatureValue(1, FeatureValueType.INTEGER),
            Feature.FIVE_G_PLUS_ICON to bool(false), Feature.CROSS_SIM to bool(false))
        val state = ImsEditorState(subId = 1, snapshots = listOf(snapshot()), edits = manual)
        val staged = requireNotNull(ChinaBroadnetProfile.stage(state))
        assertFalse(staged.skipped)
        assertEquals(manual + imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G), staged.edits)
        assertEquals(bool(true), staged.edits[Feature.FIVE_G_NR])
        assertEquals(manual, state.edits)
    }

    @Test
    fun `缺少平台键时明确跳过而不构造未知的运营商值`() {
        val state = ImsEditorState(subId = 1, snapshots = listOf(snapshot().copy(
            values = mapOf(Feature.FIVE_G_NR to bool(false)),
            recovery = mapOf(Feature.FIVE_G_NR to ConfigRecovery.SWITCH))))
        val staged = requireNotNull(ChinaBroadnetProfile.stage(state))
        assertTrue(staged.skipped)
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true)), staged.edits)
    }
}
