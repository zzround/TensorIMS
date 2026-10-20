package app.mystery0.ims.tensor.model

import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol

/** 请求只包含目标卡和已准备草稿的身份；读写能力由固定 OperationType 与组件决定。 */
object BroadnetApnProtocol {
    const val SUB_ID = "broadnet_apn_sub_id"
    const val IDENTITY = "broadnet_apn_identity"
    const val COMPLETE = "broadnet_apn_complete"
    const val STATE = "broadnet_apn_state"
    const val CREATED = "broadnet_apn_created"
    const val REUSED = "broadnet_apn_reused"
    const val ERROR = "broadnet_apn_error"
    const val READ_COMPLETE = "broadnet_apn_read_complete"
    private val keys = setOf(SUB_ID, IDENTITY, COMPLETE, STATE, CREATED, REUSED, ERROR, READ_COMPLETE)

    fun encode(result: BroadnetApnResult): Bundle = Bundle().apply {
        putInt(SUB_ID, result.subId)
        putString(IDENTITY, result.identity)
        putBoolean(COMPLETE, true)
        putString(STATE, result.state.name)
        putInt(CREATED, result.created)
        putInt(REUSED, result.reused)
        putString(ERROR, result.error)
        putBoolean(READ_COMPLETE, result.readComplete)
        // Runtime 必须保留未知写入的 journal；业务枚举不能绕过既有恢复门禁。
        if (result.requiresRecovery) putString(BridgeProtocol.ERROR_CODE, "OPERATION_INDETERMINATE")
    }

    @Suppress("DEPRECATION")
    fun decode(bundle: Bundle?, expectedSubId: Int, expectedIdentity: String): BroadnetApnResult = try {
        decodeFields(bundle?.let { value -> keys.filter { value.containsKey(it) }.associateWith { value.get(it) } },
            expectedSubId, expectedIdentity, bundle?.getString(BridgeProtocol.ERROR_CODE),
            if (bundle?.containsKey(BridgeProtocol.CLEANUP_CONFIRMED) == true) bundle.get(BridgeProtocol.CLEANUP_CONFIRMED) else null)
    } catch (_: RuntimeException) {
        BroadnetApnResult(expectedSubId, expectedIdentity, BroadnetApnState.UNKNOWN, error = "INVALID_APN_RESULT")
    }

    /** 纯字段校验支持 JVM 回归测试，结构缺失与已知未执行的后端错误严格区分。 */
    fun decodeFields(fields: Map<String, Any?>?, expectedSubId: Int, expectedIdentity: String,
                     backendError: String? = null, cleanupConfirmed: Any? = null): BroadnetApnResult {
        fun invalid(error: String = "INVALID_APN_RESULT") =
            BroadnetApnResult(expectedSubId, expectedIdentity, BroadnetApnState.UNKNOWN, error = error)
        if (cleanupConfirmed != null && cleanupConfirmed != true) return invalid("CLEANUP_UNCONFIRMED")
        val knownNotStarted = setOf("NOT_STARTED", "INVALID_ARGUMENTS", "NOT_READY", "NOT_RUNNING", "REQUEST_CANCELLED", "BUSY",
            "PERMISSION_REQUIRED", "PERMISSION_DENIED", "AUTH_FAILED", "VERSION_MISMATCH", "DELEGATION_BUSY")
        // 没有业务结果时可接受明确未启动码；已有部分写入/未知业务证据不能被它覆盖。
        if (fields.isNullOrEmpty() && backendError in knownNotStarted) return BroadnetApnResult(expectedSubId, expectedIdentity,
            BroadnetApnState.FAILED, error = backendError)
        val validIdentity = expectedIdentity.matches(Regex("[a-fA-F0-9]{64}"))
        if (fields == null || fields.keys != keys || fields[COMPLETE] != true || expectedSubId < 0 || !validIdentity ||
            fields[SUB_ID] != expectedSubId || fields[IDENTITY] != expectedIdentity) return invalid()
        val state = BroadnetApnState.entries.firstOrNull { it.name == fields[STATE] } ?: return invalid()
        val created = fields[CREATED] as? Int ?: return invalid()
        val reused = fields[REUSED] as? Int ?: return invalid()
        val readComplete = fields[READ_COMPLETE] as? Boolean ?: return invalid()
        val error = fields[ERROR]
        if (created !in 0..2 || reused !in 0..2 || created + reused > 2 || error != null &&
            (error !is String || error.isBlank() || error.length > 256)) return invalid()
        if (state == BroadnetApnState.COMPLETE && (!readComplete || created + reused != 2 || error != null)) return invalid()
        if (state != BroadnetApnState.COMPLETE && error == null || state == BroadnetApnState.CONFLICT && !readComplete) return invalid()
        val result = BroadnetApnResult(expectedSubId, expectedIdentity, state, created, reused, error as String?, readComplete)
        if (backendError in knownNotStarted && state == BroadnetApnState.FAILED && created == 0 && !readComplete) {
            return result.copy(error = backendError)
        }
        if (cleanupConfirmed != true) return result.copy(state = BroadnetApnState.UNKNOWN,
            error = "CLEANUP_UNCONFIRMED", readComplete = false)
        return if (backendError != null && state != BroadnetApnState.UNKNOWN) result.copy(
            state = BroadnetApnState.UNKNOWN, error = backendError.take(256), readComplete = false) else result
    }
}
