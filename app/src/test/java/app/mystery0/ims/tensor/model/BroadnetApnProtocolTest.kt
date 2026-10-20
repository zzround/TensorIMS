package app.mystery0.ims.tensor.model

import org.junit.Assert.*
import org.junit.Test

class BroadnetApnProtocolTest {
    private val identity = "a".repeat(64)
    private fun fields() = mapOf<String, Any?>(BroadnetApnProtocol.SUB_ID to 14,
        BroadnetApnProtocol.IDENTITY to identity, BroadnetApnProtocol.COMPLETE to true,
        BroadnetApnProtocol.STATE to "COMPLETE", BroadnetApnProtocol.CREATED to 1,
        BroadnetApnProtocol.REUSED to 1, BroadnetApnProtocol.ERROR to null,
        BroadnetApnProtocol.READ_COMPLETE to true)
    private fun decode(values: Map<String, Any?> = fields(), error: String? = null, clean: Any? = true) =
        BroadnetApnProtocol.decodeFields(values, 14, identity, error, clean)

    @Test fun `只有同卡身份完整字段清理和两个已核对配置才可成功`() {
        assertTrue(decode().success)
        for (change in listOf(mapOf(BroadnetApnProtocol.SUB_ID to 12),
            mapOf(BroadnetApnProtocol.IDENTITY to "b".repeat(64)),
            mapOf(BroadnetApnProtocol.CREATED to 1L), mapOf(BroadnetApnProtocol.REUSED to 0),
            mapOf(BroadnetApnProtocol.CREATED to 2), mapOf(BroadnetApnProtocol.READ_COMPLETE to false))) {
            assertEquals(BroadnetApnState.UNKNOWN, decode(fields() + change).state)
        }
        assertEquals(BroadnetApnState.UNKNOWN, decode(fields() - BroadnetApnProtocol.ERROR).state)
        assertEquals(BroadnetApnState.UNKNOWN, decode(clean = null).state)
        assertEquals(BroadnetApnState.UNKNOWN, decode(clean = false).state)
        assertEquals(BroadnetApnState.UNKNOWN, decode(clean = "true").state)
    }

    @Test fun `明确未启动错误可以失败 未知后端和无结构返回保持未知`() {
        for (error in listOf("NOT_STARTED", "INVALID_ARGUMENTS", "NOT_READY", "REQUEST_CANCELLED")) {
            assertEquals(BroadnetApnState.FAILED,
                BroadnetApnProtocol.decodeFields(null, 14, identity, error).state)
        }
        for (error in listOf(null, "OPERATION_INDETERMINATE", "JOURNAL_FAILED", "CLEANUP_FAILED")) {
            assertEquals(BroadnetApnState.UNKNOWN,
                BroadnetApnProtocol.decodeFields(null, 14, identity, error).state)
        }
        assertEquals(BroadnetApnState.UNKNOWN, decode(error = "OPERATION_INDETERMINATE").state)
    }

    @Test fun `部分未知结果保留已创建数量及恢复标记不变成整项成功`() {
        val result = decode(fields() + mapOf(BroadnetApnProtocol.STATE to "UNKNOWN",
            BroadnetApnProtocol.ERROR to "INSERT_RESULT_UNKNOWN", BroadnetApnProtocol.REUSED to 0,
            BroadnetApnProtocol.READ_COMPLETE to false), "OPERATION_INDETERMINATE")
        assertEquals(1, result.created)
        assertTrue(result.requiresRecovery)
        assertFalse(result.success)
        assertFalse(result.readComplete)
    }

    @Test fun `后端未启动错误不能覆盖业务未知或部分写入证据`() {
        for (state in listOf("UNKNOWN", "FAILED", "COMPLETE")) {
            val result = decode(fields() + mapOf(BroadnetApnProtocol.STATE to state,
                BroadnetApnProtocol.ERROR to if (state == "COMPLETE") null else "INSERT_RESULT_UNKNOWN"),
                "PERMISSION_DENIED")
            assertEquals(BroadnetApnState.UNKNOWN, result.state)
            assertEquals(1, result.created)
            assertTrue(result.requiresRecovery)
        }
        val notStarted = decode(fields() + mapOf(BroadnetApnProtocol.STATE to "FAILED",
            BroadnetApnProtocol.CREATED to 0, BroadnetApnProtocol.REUSED to 0,
            BroadnetApnProtocol.READ_COMPLETE to false, BroadnetApnProtocol.ERROR to "NOT_STARTED"),
            "NOT_STARTED", clean = null)
        assertEquals(BroadnetApnState.FAILED, notStarted.state)
    }

    @Test fun `恢复完整缺失与查询失败由readComplete区分且都不是成功`() {
        val result = decode(fields() + mapOf(BroadnetApnProtocol.STATE to "FAILED",
            BroadnetApnProtocol.CREATED to 0, BroadnetApnProtocol.REUSED to 0,
            BroadnetApnProtocol.ERROR to "APN_MISSING"))
        assertEquals(BroadnetApnState.FAILED, result.state)
        assertTrue(result.readComplete)
        assertFalse(result.success)
        val unavailable = decode(fields() + mapOf(BroadnetApnProtocol.STATE to "FAILED",
            BroadnetApnProtocol.CREATED to 0, BroadnetApnProtocol.REUSED to 0,
            BroadnetApnProtocol.ERROR to "APN_READ_FAILED", BroadnetApnProtocol.READ_COMPLETE to false))
        assertFalse(unavailable.readComplete)
    }
}
