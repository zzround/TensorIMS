package app.mystery0.ims.tensor.model

enum class BroadnetApnState { COMPLETE, CONFLICT, FAILED, UNKNOWN }

/** 配置补齐结果与网络注册分开；readComplete 只说明本次关键查询完整。 */
data class BroadnetApnResult(
    val subId: Int,
    val identity: String,
    val state: BroadnetApnState,
    val created: Int = 0,
    val reused: Int = 0,
    val error: String? = null,
    val readComplete: Boolean = false,
) {
    val success: Boolean get() = state == BroadnetApnState.COMPLETE
    val requiresRecovery: Boolean get() = state == BroadnetApnState.UNKNOWN
}

/** 只允许这两个模板；调用方不能传入 APN、类型、SQL 或字段值。 */
enum class BroadnetApnTemplate(val displayName: String, val apn: String, val types: String) {
    DATA("CBNET", "cbnet", "default,ia,supl,xcap"),
    IMS("IMS", "ims", "ims"),
}

/** 固定查询的非凭据字段；原始 IMSI/ICCID、MVNO 匹配值及认证信息不会进入结果。 */
data class BroadnetApnRow(
    val id: Long,
    val name: String?,
    val numeric: String?,
    val mcc: String?,
    val mnc: String?,
    val apn: String?,
    val types: String?,
    val enabled: Boolean?,
    val protocol: String?,
    val roamingProtocol: String?,
    val networkTypeBitmask: Long?,
    val mvnoType: String?,
    val edited: Int?,
    val apnSetId: Int?,
    val ownedBy: Int?,
    val carrierId: Int?,
    val subscriptionId: Int?,
) {
    val typeKind: ApnTypeKind get() = classifyApnTypes(types)
    val isMnoScope: Boolean get() = numeric == "46015" && mcc == "460" && mnc == "15" &&
        mvnoType.isNullOrEmpty() && ownedBy == 1 && apnSetId in setOf(0, -1)
    val active: Boolean get() = edited in setOf(0, 1, 4)

    fun matches(template: BroadnetApnTemplate): Boolean = isMnoScope && active && enabled == true &&
        apn.equals(template.apn, true) && normalizedApnTypes(types) == normalizedApnTypes(template.types) &&
        protocol == "IPV4V6" && roamingProtocol == "IPV4V6" && networkTypeBitmask == 0L

    /** 名称、协议及制式限制不被改写；已有合法首选数据条目可继续由系统使用。 */
    val usableData: Boolean get() = isMnoScope && active && enabled == true && !apn.isNullOrBlank() &&
        typeKind == ApnTypeKind.OTHER && "default" in normalizedApnTypes(types) &&
        protocol in setOf("IP", "IPV6", "IPV4V6") && roamingProtocol in setOf("IP", "IPV6", "IPV4V6") &&
        networkTypeBitmask != null && networkTypeBitmask >= 0L
}

data class BroadnetApnSnapshot(
    val identity: String,
    val homePlmn: String?,
    val simRows: List<BroadnetApnRow>,
    val carrierRows: List<BroadnetApnRow>,
    val preferred: BroadnetApnRow?,
    val simCarrierId: Int = -1,
    val preferredSetId: Int = 0,
)

internal fun normalizedApnTypes(types: String?): Set<String> = types.orEmpty().split(',')
    .map { it.lowercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()

/** Cursor 的整型列也可能来自 AOSP MatrixCursor 的字符串；不接受浮点、空白或溢出。 */
fun broadnetApnInteger(value: Any?): Long? = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is String -> if (value.length in 1..20 && value.matches(Regex("-?(0|[1-9][0-9]*)")))
        value.toLongOrNull()?.takeIf { it.toString() == value } else null
    else -> null
}
