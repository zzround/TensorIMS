package app.mystery0.ims.tensor.privileged

import android.app.Activity
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import app.mystery0.ims.tensor.model.ApnDiagnosticRow
import app.mystery0.ims.tensor.model.ApnDiagnostics
import app.mystery0.ims.tensor.model.ApnDiagnosticsLimits
import app.mystery0.ims.tensor.model.ApnDiagnosticsProtocol
import app.mystery0.ims.tensor.model.ApnReadError
import app.mystery0.ims.tensor.model.ApnReadState
import app.mystery0.ims.tensor.model.ApnTargetScope
import app.mystery0.ims.tensor.model.apnTargetScope

/** 仅查询指定活动广电订阅的非凭据字段，不提供写入、重置、首选 APN 或任意 URI 接口。 */
class ApnDiagnosticsReader : SessionInstrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val subId = arguments?.getInt(ApnDiagnosticsProtocol.SUB_ID, -1) ?: -1
        var diagnostic = ApnDiagnostics(subId, error = ApnReadError.SESSION_FAILED)
        if (subId >= 0) {
            val failure = runWithShellPermissionDelegation(TAG) {
                diagnostic = readDiagnostic(subId)
            }
            // 委托建立或清理失败使完整结果失效，不能保留此前已读到的 APN 行。
            if (failure != null) {
                Log.w(TAG, "APN diagnostics session failed", failure)
                diagnostic = ApnDiagnostics(subId, error = if (failure is SecurityException)
                    ApnReadError.PERMISSION_DENIED else ApnReadError.SESSION_FAILED)
            }
        }
        finish(Activity.RESULT_OK, ApnDiagnosticsProtocol.encode(diagnostic))
    }

    private fun readDiagnostic(subId: Int): ApnDiagnostics {
        val targetReader = TargetConfigurationReader(context, subId)
        val before = try {
            targetReader.currentSim()
        } catch (failure: Exception) {
            return unavailable(subId, failure, if (failure is SecurityException)
                ApnReadError.PERMISSION_DENIED else ApnReadError.SIM_UNAVAILABLE)
        }
        val result = when (apnTargetScope(subId, before.homePlmn)) {
            ApnTargetScope.UNAVAILABLE -> ApnDiagnostics(subId, error = ApnReadError.UNKNOWN_HOME_PLMN)
            ApnTargetScope.OTHER_CARRIER -> ApnDiagnostics(subId, before.homePlmn, ApnReadState.NOT_BROADNET)
            ApnTargetScope.BROADNET -> try {
                ApnDiagnostics(subId, before.homePlmn, ApnReadState.READ_OK, queryRows(subId))
            } catch (failure: ApnQueryException) {
                unavailable(subId, failure, failure.error, before.homePlmn)
            } catch (failure: SecurityException) {
                unavailable(subId, failure, ApnReadError.PERMISSION_DENIED, before.homePlmn)
            } catch (failure: Exception) {
                unavailable(subId, failure, ApnReadError.QUERY_FAILED, before.homePlmn)
            } catch (failure: LinkageError) {
                unavailable(subId, failure, ApnReadError.QUERY_FAILED, before.homePlmn)
            }
        }
        val after = try {
            targetReader.currentSim()
        } catch (failure: Exception) {
            return unavailable(subId, failure, if (failure is SecurityException)
                ApnReadError.PERMISSION_DENIED else ApnReadError.SIM_UNAVAILABLE)
        }
        return if (after == before) result else ApnDiagnostics(subId, error = ApnReadError.SIM_CHANGED)
    }

    private fun queryRows(subId: Int): List<ApnDiagnosticRow> {
        // API 29+ 由 Provider 按指定订阅做 SIM/MVNO 匹配；不退回默认卡或全表 numeric 查询。
        val uri = Uri.withAppendedPath(Telephony.Carriers.SIM_APN_URI, subId.toString())
        val cursor = context.contentResolver.query(uri, PROJECTION, null, null, null)
            ?: throw ApnQueryException(ApnReadError.QUERY_FAILED)
        return cursor.use {
            val columns = PROJECTION.map { name -> cursor.getColumnIndex(name).also { index ->
                if (index < 0) throw ApnQueryException(ApnReadError.INVALID_RESPONSE)
            } }
            val rows = mutableListOf<ApnDiagnosticRow>()
            while (cursor.moveToNext()) {
                if (rows.size >= ApnDiagnosticsLimits.MAX_ROWS) throw ApnQueryException(ApnReadError.RESPONSE_TOO_LARGE)
                rows += ApnDiagnosticRow(
                    cursor.nullableText(columns[0]), cursor.nullableText(columns[1]),
                    cursor.nullableBoolean(columns[2]), cursor.nullableText(columns[3]),
                    cursor.nullableText(columns[4]),
                )
                if (!ApnDiagnosticsLimits.valid(rows)) throw ApnQueryException(ApnReadError.RESPONSE_TOO_LARGE)
            }
            rows.toList()
        }
    }

    private fun Cursor.nullableText(column: Int): String? = when (getType(column)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_STRING -> getString(column).also {
            if (it.length > ApnDiagnosticsLimits.MAX_FIELD_CHARS) throw ApnQueryException(ApnReadError.RESPONSE_TOO_LARGE)
        }
        else -> throw ApnQueryException(ApnReadError.INVALID_RESPONSE)
    }

    private fun Cursor.nullableBoolean(column: Int): Boolean? = when (getType(column)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> when (getLong(column)) {
            0L -> false
            1L -> true
            else -> throw ApnQueryException(ApnReadError.INVALID_RESPONSE)
        }
        // SIM_APN_URI 的 AOSP 实现会用 getString 构造 MatrixCursor，布尔列可能返回文本。
        Cursor.FIELD_TYPE_STRING -> when (getString(column)) {
            "0" -> false
            "1" -> true
            else -> throw ApnQueryException(ApnReadError.INVALID_RESPONSE)
        }
        else -> throw ApnQueryException(ApnReadError.INVALID_RESPONSE)
    }

    private fun unavailable(subId: Int, failure: Throwable, error: ApnReadError, homePlmn: String? = null): ApnDiagnostics {
        Log.w(TAG, "APN diagnostics unavailable: ${error.name}", failure)
        return ApnDiagnostics(subId, homePlmn = homePlmn, error = error)
    }

    private class ApnQueryException(val error: ApnReadError) : Exception(error.name)

    companion object {
        private const val TAG = "ApnDiagnosticsReader"
        private val PROJECTION = arrayOf(Telephony.Carriers.APN, Telephony.Carriers.TYPE,
            Telephony.Carriers.CARRIER_ENABLED, Telephony.Carriers.PROTOCOL, Telephony.Carriers.ROAMING_PROTOCOL)
    }
}
