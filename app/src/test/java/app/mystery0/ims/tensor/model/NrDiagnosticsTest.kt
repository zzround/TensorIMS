package app.mystery0.ims.tensor.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NrDiagnosticsTest {
    @Test
    fun `未知或失败的网络策略掩码不能变成允许或禁止`() {
        assertEquals(null, nrPolicyAllows(null))
        assertEquals(null, nrPolicyAllows(0L))
        assertEquals(null, nrPolicyAllows(-1L))
        assertEquals(false, nrPolicyAllows(1L shl 12)) // 只有 LTE
        assertEquals(true, nrPolicyAllows((1L shl 12) or (1L shl 19))) // LTE + NR
    }

    @Test
    fun `配置未加载时不能把框架默认 NSA SA 数组显示为当前 SIM 允许`() {
        assertEquals(null, nrConfigAllows(false, intArrayOf(1, 2), 2))
        assertEquals(null, nrConfigAllows(null, intArrayOf(1, 2), 1))
        assertEquals(null, nrConfigAllows(true, null, 2))
        assertEquals(true, nrConfigAllows(true, intArrayOf(1, 2), 2))
        assertEquals(false, nrConfigAllows(true, intArrayOf(1), 2))
        assertEquals(false, nrConfigAllows(true, intArrayOf(), 1))
    }

    @Test
    fun `NSA 允许但未连接不能显示已连接`() {
        assertEquals(NrRegistration.NSA_NOT_RESTRICTED, classifyNrRegistration(true, 13, 2))
        assertEquals(NrRegistration.NSA_CONNECTED, classifyNrRegistration(true, 13, 3))
    }

    @Test
    fun `SA 需要分组域已注册且使用 NR`() {
        assertEquals(NrRegistration.SA_REGISTERED, classifyNrRegistration(true, 20, 0))
        assertEquals(NrRegistration.NOT_REGISTERED, classifyNrRegistration(false, 20, 3))
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(null, 20, 3))
    }

    @Test
    fun `不可读记录和未知 NR 状态不能被补为关闭`() {
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(null, null, null))
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(true, 13, null))
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(true, 13, 99))
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(true, 0, 3))
        assertEquals(NrRegistration.UNKNOWN, classifyNrRegistration(true, 99, 3))
    }

    @Test
    fun `配置全部允许也不能制造已连接证据`() {
        val diagnostic = NrDiagnostics(carrierConfigAllowsNsa = true, carrierConfigAllowsSa = true,
            userPolicyAllowsNr = true, carrierPolicyAllowsNr = true)
        assertEquals(NrRegistration.UNKNOWN, diagnostic.registration)
        assertEquals(NrRegistration.NSA_NOT_RESTRICTED,
            diagnostic.copy(packetRegistered = true, packetNetworkType = 13, nrState = 2).registration)
    }

    @Test
    fun `LTE CA 支持 NSA 而其他已注册 RAT 不产生 NSA 假阳性`() {
        assertEquals(NrRegistration.NSA_CONNECTED, classifyNrRegistration(true, 19, 3))
        assertEquals(NrRegistration.NO_NR, classifyNrRegistration(true, 3, 3))
        assertEquals(NrRegistration.NSA_RESTRICTED, classifyNrRegistration(true, 13, 1))
        assertEquals(NrRegistration.NO_NR, classifyNrRegistration(true, 13, 0))
    }
}
