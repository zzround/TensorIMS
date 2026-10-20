package app.mystery0.ims.tensor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApnDiagnosticsTest {
    private fun row(apn: String? = "ims", types: String? = "ims", enabled: Boolean? = true,
                    protocol: String? = "IPV4V6", roaming: String? = "IPV4V6") =
        ApnDiagnosticRow(apn, types, enabled, protocol, roaming)

    private fun result(vararg rows: ApnDiagnosticRow) =
        ApnDiagnostics(14, "46015", ApnReadState.READ_OK, rows.toList())

    @Test
    fun `实际空值与通配类型包含 IMS 但不算独立 IMS 条目`() {
        listOf(null, "", "*", "default,*").forEach { types ->
            assertEquals(ApnTypeKind.ALL, classifyApnTypes(types))
            assertFalse(row(types = types).isReferenceIms)
            assertEquals(ApnAssessment.REVIEW_REQUIRED, result(row(types = types)).assessment)
        }
    }

    @Test
    fun `类型按 AOSP 逗号及大小写语义识别 不通过 trim 制造有效 IMS`() {
        assertEquals(ApnTypeKind.IMS_ONLY, classifyApnTypes("IMS"))
        assertEquals(ApnTypeKind.IMS_ONLY, classifyApnTypes("ims,ims,"))
        assertEquals(ApnTypeKind.IMS_MIXED, classifyApnTypes("default,supl,ims"))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes(" ims"))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes("ims, default"))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes(" "))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes(",,"))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes("xims"))
        assertEquals(ApnTypeKind.UNRECOGNIZED, classifyApnTypes("future_type"))
    }

    @Test
    fun `实测 internet 混合条目提示核对 修正的独立 ims 只匹配参考字段`() {
        val baseline = row("internet", "default,supl,ims")
        assertEquals(ApnTypeKind.IMS_MIXED, baseline.typeKind)
        assertEquals(ApnAssessment.REVIEW_REQUIRED, result(baseline).assessment)
        assertEquals(ApnAssessment.REFERENCE_IMS_FOUND, result(row()).assessment)
        assertEquals(ApnAssessment.REVIEW_REQUIRED, result(row("operator.ims")).assessment)
        assertEquals(ApnAssessment.REVIEW_REQUIRED, result(row(apn = null)).assessment)
    }

    @Test
    fun `禁用及未知启用状态不能当作可用参考配置`() {
        listOf(false, null).forEach { enabled ->
            assertFalse(row(enabled = enabled).isReferenceIms)
            assertEquals(ApnAssessment.REVIEW_REQUIRED, result(row(enabled = enabled)).assessment)
        }
    }

    @Test
    fun `参考条目不能掩盖另一条混合或不可识别候选项`() {
        assertEquals(ApnAssessment.REVIEW_REQUIRED,
            result(row(), row("internet", "default,ims")).assessment)
        assertEquals(ApnAssessment.REVIEW_REQUIRED,
            result(row(), row("internet", "future_type")).assessment)
        assertEquals(ApnAssessment.REFERENCE_IMS_FOUND,
            result(row(), row("cbnet", "default,ia,supl,xcap")).assessment)
    }

    @Test
    fun `协议差异保留原值供核对 不据此宣布注册失败`() {
        val entry = row(protocol = "IP", roaming = null)
        assertEquals("IP", entry.protocol)
        assertEquals(null, entry.roamingProtocol)
        assertEquals(ApnAssessment.REFERENCE_IMS_FOUND, result(entry).assessment)
    }

    @Test
    fun `仅完整读取可报告未观察到 IMS 查询失败不是缺失`() {
        assertEquals(ApnAssessment.NO_IMS_OBSERVED, result().assessment)
        assertEquals(ApnAssessment.NO_IMS_OBSERVED,
            result(row("cbnet", "default,ia,supl,xcap")).assessment)
        for (error in ApnReadError.entries) {
            assertEquals(ApnAssessment.UNAVAILABLE, ApnDiagnostics(14, error = error).assessment)
        }
        assertEquals(ApnAssessment.UNAVAILABLE,
            result(row()).copy(state = ApnReadState.UNAVAILABLE, error = ApnReadError.SIM_CHANGED).assessment)
    }

    @Test
    fun `只允许当前单张46015归属 不能使用驻留或漫游网络替代`() {
        assertEquals(ApnTargetScope.BROADNET, apnTargetScope(14, "46015"))
        for (home in listOf("46013", "45403", "45412", "460150")) {
            assertEquals(ApnTargetScope.OTHER_CARRIER, apnTargetScope(14, home))
            assertEquals(ApnAssessment.UNAVAILABLE, result(row()).copy(homePlmn = home).assessment)
        }
        assertEquals(ApnTargetScope.UNAVAILABLE, apnTargetScope(-1, "46015"))
        assertEquals(ApnTargetScope.UNAVAILABLE, apnTargetScope(-2, "46015"))
        for (home in listOf(null, "", " 46015", "广电", "4601")) {
            assertEquals(ApnTargetScope.UNAVAILABLE, apnTargetScope(14, home))
        }
    }

    @Test
    fun `行数和文本总量有界 超限不能以部分结果推断没有 IMS`() {
        assertTrue(ApnDiagnosticsLimits.valid(List(64) { row() }))
        assertFalse(ApnDiagnosticsLimits.valid(List(65) { row() }))
        assertFalse(ApnDiagnosticsLimits.valid(listOf(row(apn = "x".repeat(257)))))
        val full = row("a".repeat(256), "b".repeat(256), true, "c".repeat(256), "d".repeat(256))
        assertTrue(ApnDiagnosticsLimits.valid(List(16) { full }))
        assertFalse(ApnDiagnosticsLimits.valid(List(17) { full }))
    }
}
