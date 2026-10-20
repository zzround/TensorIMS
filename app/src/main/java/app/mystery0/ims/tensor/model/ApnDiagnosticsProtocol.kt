package app.mystery0.ims.tensor.model

import android.os.Bundle

/** 固定只读协议不传 URI、selection、写入 action 或凭据；真实 SQL NULL 与缺字段严格区分。 */
object ApnDiagnosticsProtocol {
    const val SUB_ID = "apn_diagnostics_sub_id"
    const val COMPLETE = "apn_diagnostics_complete"
    const val HOME_PLMN = "apn_diagnostics_home_plmn"
    const val STATE = "apn_diagnostics_state"
    const val ROWS = "apn_diagnostics_rows"
    const val ERROR = "apn_diagnostics_error"
    private const val APN = "apn"
    private const val TYPES = "types"
    private const val ENABLED = "enabled"
    private const val PROTOCOL = "protocol"
    private const val ROAMING_PROTOCOL = "roaming_protocol"
    private val rowKeys = setOf(APN, TYPES, ENABLED, PROTOCOL, ROAMING_PROTOCOL)

    fun encode(result: ApnDiagnostics): Bundle = Bundle().apply {
        require(ApnDiagnosticsLimits.valid(result.rows)) { "APN diagnostic result exceeds limit" }
        putBoolean(COMPLETE, true)
        putInt(SUB_ID, result.subId)
        putString(HOME_PLMN, result.homePlmn)
        putString(STATE, result.state.name)
        putString(ERROR, result.error?.name)
        putParcelableArrayList(ROWS, ArrayList(result.rows.map { row -> Bundle().apply {
            putString(APN, row.apn)
            putString(TYPES, row.types)
            if (row.carrierEnabled == null) putString(ENABLED, null) else putBoolean(ENABLED, row.carrierEnabled)
            putString(PROTOCOL, row.protocol)
            putString(ROAMING_PROTOCOL, row.roamingProtocol)
        } }))
    }

    @Suppress("DEPRECATION")
    fun decode(bundle: Bundle?, expectedSubId: Int): ApnDiagnostics {
        fun invalid(error: ApnReadError = ApnReadError.INVALID_RESPONSE) = ApnDiagnostics(expectedSubId, error = error)
        if (bundle == null) return invalid(ApnReadError.SESSION_FAILED)
        return try {
            if (bundle.get(COMPLETE) != true || bundle.get(SUB_ID) != expectedSubId || expectedSubId < 0) return invalid()
            if (!bundle.containsKey(HOME_PLMN) || !bundle.containsKey(ERROR)) return invalid()
            val home = bundle.get(HOME_PLMN)
            if (home != null && (home !is String || !home.matches(Regex("[0-9]{5,6}")))) return invalid()
            val state = ApnReadState.entries.firstOrNull { it.name == bundle.get(STATE) } ?: return invalid()
            val rawError = bundle.get(ERROR)
            val error = if (rawError == null) null else ApnReadError.entries.firstOrNull { it.name == rawError } ?: return invalid()
            val rawRows = bundle.get(ROWS) as? ArrayList<*> ?: return invalid()
            if (rawRows.size > ApnDiagnosticsLimits.MAX_ROWS) return invalid(ApnReadError.RESPONSE_TOO_LARGE)
            val rows = rawRows.map { item ->
                val row = item as? Bundle ?: return invalid()
                if (row.keySet() != rowKeys) return invalid()
                for (key in rowKeys - ENABLED) if (row.get(key) != null && row.get(key) !is String) return invalid()
                val enabled = row.get(ENABLED)
                if (enabled != null && enabled !is Boolean) return invalid()
                ApnDiagnosticRow(row.get(APN) as String?, row.get(TYPES) as String?, enabled as Boolean?,
                    row.get(PROTOCOL) as String?, row.get(ROAMING_PROTOCOL) as String?)
            }
            if (!ApnDiagnosticsLimits.valid(rows)) return invalid(ApnReadError.RESPONSE_TOO_LARGE)
            val homePlmn = home as String?
            val scope = apnTargetScope(expectedSubId, homePlmn)
            val valid = when (state) {
                ApnReadState.READ_OK -> scope == ApnTargetScope.BROADNET && error == null
                ApnReadState.NOT_BROADNET -> scope == ApnTargetScope.OTHER_CARRIER && error == null && rows.isEmpty()
                ApnReadState.UNAVAILABLE -> error != null && rows.isEmpty()
            }
            if (!valid) invalid() else ApnDiagnostics(expectedSubId, homePlmn, state, rows, error)
        } catch (_: RuntimeException) {
            invalid()
        }
    }
}
