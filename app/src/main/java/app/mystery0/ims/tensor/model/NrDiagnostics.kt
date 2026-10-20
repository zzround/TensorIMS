package app.mystery0.ims.tensor.model

/** 系统无线状态和本地允许策略分开保存；缺失值不能解释为关闭或未注册。 */
data class NrDiagnostics(
    val packetRegistered: Boolean? = null,
    val packetNetworkType: Int? = null,
    val nrState: Int? = null,
    val carrierConfigAllowsNsa: Boolean? = null,
    val carrierConfigAllowsSa: Boolean? = null,
    val userPolicyAllowsNr: Boolean? = null,
    val carrierPolicyAllowsNr: Boolean? = null,
) {
    val registration: NrRegistration
        get() = classifyNrRegistration(packetRegistered, packetNetworkType, nrState)
}

enum class NrRegistration {
    UNKNOWN,
    NOT_REGISTERED,
    SA_REGISTERED,
    NSA_CONNECTED,
    NSA_NOT_RESTRICTED,
    NSA_RESTRICTED,
    NO_NR,
}

/** 0 是 NETWORK_TYPE_BITMASK_UNKNOWN，负数可来自旧系统的失败兜底，均不能解释为允许/禁止。 */
internal fun nrPolicyAllows(mask: Long?): Boolean? =
    mask?.takeIf { it > 0L }?.let { it and (1L shl 19) != 0L } // NETWORK_TYPE_BITMASK_NR

/** 未加载的 CarrierConfig 可能携带框架默认数组，不能当作当前 SIM 的配置。 */
internal fun nrConfigAllows(configApplied: Boolean?, modes: IntArray?, mode: Int): Boolean? =
    if (configApplied == true) modes?.contains(mode) else null

/**
 * 仅根据 WWAN 分组域的注册记录判定，不使用状态栏图标、IMS 语音能力或配置允许值。
 * 数值来自 Android TelephonyManager 与 NetworkRegistrationInfo 的稳定常量，
 * 在纯 Kotlin 层保留显式映射以覆盖“允许 NSA，但尚未连接”的回归场景。
 */
internal fun classifyNrRegistration(
    packetRegistered: Boolean?,
    packetNetworkType: Int?,
    nrState: Int?,
): NrRegistration {
    if (packetRegistered == false) return NrRegistration.NOT_REGISTERED
    if (packetRegistered != true) return NrRegistration.UNKNOWN
    return when (packetNetworkType) {
        20 -> NrRegistration.SA_REGISTERED // NETWORK_TYPE_NR
        13, 19 -> when (nrState) { // NETWORK_TYPE_LTE / NETWORK_TYPE_LTE_CA
            3 -> NrRegistration.NSA_CONNECTED // NR_STATE_CONNECTED
            2 -> NrRegistration.NSA_NOT_RESTRICTED // NR_STATE_NOT_RESTRICTED
            1 -> NrRegistration.NSA_RESTRICTED // NR_STATE_RESTRICTED
            0 -> NrRegistration.NO_NR // NR_STATE_NONE
            else -> NrRegistration.UNKNOWN
        }
        in 1..18 -> NrRegistration.NO_NR // 其余已知 2G/3G/IWLAN RAT
        else -> NrRegistration.UNKNOWN // 缺失、NETWORK_TYPE_UNKNOWN 或未来新增 RAT
    }
}
