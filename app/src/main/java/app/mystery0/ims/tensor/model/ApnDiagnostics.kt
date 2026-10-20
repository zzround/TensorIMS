package app.mystery0.ims.tensor.model

import java.util.Locale

/** 仅保存所选订阅可查询的 APN 配置线索；不能据此判断 IMS 注册或正在使用的 APN。 */
data class ApnDiagnostics(
    val subId: Int,
    val homePlmn: String? = null,
    val state: ApnReadState = ApnReadState.UNAVAILABLE,
    val rows: List<ApnDiagnosticRow> = emptyList(),
    val error: ApnReadError? = null,
) {
    val assessment: ApnAssessment
        get() = when {
            state == ApnReadState.NOT_BROADNET -> ApnAssessment.NOT_BROADNET
            state != ApnReadState.READ_OK || error != null ||
                apnTargetScope(subId, homePlmn) != ApnTargetScope.BROADNET -> ApnAssessment.UNAVAILABLE
            // 即使另有参考条目，也不能掩盖混合、通配、禁用或不可识别的 IMS 候选项。
            rows.any { it.typeKind != ApnTypeKind.OTHER && !it.isReferenceIms } -> ApnAssessment.REVIEW_REQUIRED
            rows.any { it.isReferenceIms } -> ApnAssessment.REFERENCE_IMS_FOUND
            else -> ApnAssessment.NO_IMS_OBSERVED
        }
}

data class ApnDiagnosticRow(
    val apn: String?,
    val types: String?,
    val carrierEnabled: Boolean?,
    val protocol: String?,
    val roamingProtocol: String?,
) {
    val typeKind: ApnTypeKind get() = classifyApnTypes(types)

    // 协议差异保留给用户核对；这里仅说明发现已启用的 ims / ims 条目，不作联网保证。
    val isReferenceIms: Boolean get() = carrierEnabled == true &&
        apn.equals("ims", ignoreCase = true) && typeKind == ApnTypeKind.IMS_ONLY
}

enum class ApnReadState { READ_OK, UNAVAILABLE, NOT_BROADNET }
enum class ApnTypeKind { IMS_ONLY, IMS_MIXED, ALL, OTHER, UNRECOGNIZED }
enum class ApnAssessment { UNAVAILABLE, NOT_BROADNET, REFERENCE_IMS_FOUND, REVIEW_REQUIRED, NO_IMS_OBSERVED }
enum class ApnReadError {
    UNKNOWN_HOME_PLMN, PERMISSION_DENIED, QUERY_FAILED, INVALID_RESPONSE, RESPONSE_TOO_LARGE,
    SIM_CHANGED, SIM_UNAVAILABLE, SESSION_FAILED,
}
enum class ApnTargetScope { BROADNET, OTHER_CARRIER, UNAVAILABLE }

/** 归属来自本次活动 SubscriptionInfo，不能传入驻留网络或显示名称。 */
fun apnTargetScope(subId: Int, homePlmn: String?): ApnTargetScope = when {
    subId < 0 || homePlmn == null || !homePlmn.matches(Regex("[0-9]{5,6}")) -> ApnTargetScope.UNAVAILABLE
    homePlmn == ChinaBroadnetProfile.HOME_PLMN -> ApnTargetScope.BROADNET
    else -> ApnTargetScope.OTHER_CARRIER
}

/**
 * 对照 AOSP ApnSetting.getApnTypesBitmaskFromString：空/null 与 * 包含 IMS；按逗号分隔、
 * Locale.ROOT 小写且不 trim。未知 token 保留为待核对，不能被当作没有 IMS 的完整证据。
 */
fun classifyApnTypes(types: String?): ApnTypeKind {
    if (types.isNullOrEmpty()) return ApnTypeKind.ALL
    val tokens = types.split(',').map { it.lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.toSet()
    if ("*" in tokens) return ApnTypeKind.ALL
    if (tokens.isEmpty() || tokens.any { it !in knownApnTypes }) return ApnTypeKind.UNRECOGNIZED
    return when {
        tokens == setOf("ims") -> ApnTypeKind.IMS_ONLY
        "ims" in tokens -> ApnTypeKind.IMS_MIXED
        else -> ApnTypeKind.OTHER
    }
}

private val knownApnTypes = setOf("default", "hipri", "mms", "supl", "dun", "fota", "ims", "cbs",
    "ia", "emergency", "mcx", "xcap", "enterprise", "vsim", "bip", "rcs", "oem_paid", "oem_private")

/** 有界结果不能截断后继续给出“未发现”结论；超限必须返回整项未知。 */
object ApnDiagnosticsLimits {
    const val MAX_ROWS = 64
    const val MAX_FIELD_CHARS = 256
    const val MAX_TOTAL_CHARS = 16_384

    fun valid(rows: List<ApnDiagnosticRow>): Boolean {
        if (rows.size > MAX_ROWS) return false
        var total = 0
        for (row in rows) {
            for (field in listOf(row.apn, row.types, row.protocol, row.roamingProtocol)) {
                if (field != null && field.length > MAX_FIELD_CHARS) return false
                total += field?.length ?: 0
                if (total > MAX_TOTAL_CHARS) return false
            }
        }
        return true
    }
}
