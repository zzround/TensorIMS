package app.mystery0.ims.tensor.privileged

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import app.mystery0.ims.tensor.model.ApnDiagnosticsLimits
import app.mystery0.ims.tensor.model.BroadnetApnAdapter
import app.mystery0.ims.tensor.model.BroadnetApnEngine
import app.mystery0.ims.tensor.model.BroadnetApnProtocol
import app.mystery0.ims.tensor.model.BroadnetApnResult
import app.mystery0.ims.tensor.model.BroadnetApnRow
import app.mystery0.ims.tensor.model.BroadnetApnSnapshot
import app.mystery0.ims.tensor.model.BroadnetApnState
import app.mystery0.ims.tensor.model.BroadnetApnTemplate
import app.mystery0.ims.tensor.model.broadnetApnInteger

/** 固定创建两个广电模板；没有 update/delete、首选项修改或批量事务假设。 */
open class BroadnetApnProvisioner : SessionInstrumentation() {
    protected open val writesEnabled: Boolean = true
    private var request = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        request = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val subId = request.getInt(BroadnetApnProtocol.SUB_ID, -1)
        val identity = request.getString(BroadnetApnProtocol.IDENTITY).orEmpty()
        var writeStarted = false
        var result = BroadnetApnResult(subId, identity, BroadnetApnState.FAILED, error = "APN_SESSION_FAILED")
        synchronized(operationLock) {
            val failure = runWithShellPermissionDelegation(TAG) {
                require(subId >= 0 && identity.matches(Regex("[a-fA-F0-9]{64}"))) { "INVALID_TARGET" }
                if (writesEnabled) check(requireExpectedIdentity(subId) == identity) { "SIM identity changed" }
                result = BroadnetApnEngine.run(subId, identity, writesEnabled,
                    BroadnetApnProvider(targetContext, subId, identity),
                    onFailure = { Log.w(TAG, "Broadnet APN operation could not be verified", it) }) { writeStarted = true }
            }
            if (failure != null) {
                // 清理是否完成仍由后端权威标记；不得把已有成功行包装成整项成功。
                Log.w(TAG, "Broadnet APN session did not complete", failure)
                result = result.copy(state = if (writeStarted || failure.suppressed.isNotEmpty())
                    BroadnetApnState.UNKNOWN else BroadnetApnState.FAILED,
                    error = "APN_SESSION_FAILED", readComplete = false)
            }
        }
        finish(Activity.RESULT_OK, BroadnetApnProtocol.encode(result))
    }

    companion object {
        private const val TAG = "BroadnetApnProvisioner"
        private val operationLock = Any()
    }
}

/** 恢复只读取当前结构状态，绝不重新派发补齐。组件选择不接受调用方的 action。 */
class BroadnetApnStateReader : BroadnetApnProvisioner() {
    override val writesEnabled: Boolean = false
}

private class BroadnetApnProvider(private val context: Context, private val subId: Int,
                                  private val expectedIdentity: String) : BroadnetApnAdapter {
    private val resolver = context.contentResolver
    private val simReader = TargetConfigurationReader(context, subId)

    private fun verifyIdentity() {
        val sim = simReader.currentSim()
        check(sim.identity == expectedIdentity && sim.homePlmn == "46015") { "SIM scope changed" }
    }

    override fun read(): BroadnetApnSnapshot {
        verifyIdentity()
        val telephony = context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(subId)
        val carrierId = telephony.simSpecificCarrierId
        val simRows = query(Uri.withAppendedPath(Telephony.Carriers.SIM_APN_URI, subId.toString()))
        // 对照实际受管理策略过滤后的列表；不将被策略排除的普通 APN 宣称可用。
        val filteredRows = query(Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "sim_apn_list/filtered/subId/$subId"))
        check(filteredRows.toSet() == simRows.toSet()) { "Managed APN scope is unsupported" }
        // 创建需要额外排除共享数据库中的可见同 APN 冲突；两类查询均会隐藏删除标记。
        // 此固定 numeric 条件只存在于补齐/恢复操作，普通只读诊断仍仅查询 SIM_APN_URI。
        val carrierRows = query(Telephony.Carriers.CONTENT_URI, "${Telephony.Carriers.NUMERIC}=?", arrayOf("46015"))
        val preferred = query(Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "preferapn/subId/$subId"))
        check(preferred.size <= 1) { "Ambiguous preferred APN" }
        val preferredSetId = preferredSetId()
        check(telephony.simSpecificCarrierId == carrierId) { "Carrier identity changed" }
        verifyIdentity()
        return BroadnetApnSnapshot(expectedIdentity, "46015", simRows, carrierRows, preferred.singleOrNull(), carrierId, preferredSetId)
    }

    private fun preferredSetId(): Int {
        val uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "preferapnset/subId/$subId")
        // AOSP 此专属接口把 null 明确定义为 NO_APN_SET_ID(0)，普通 APN 查询仍不接受 null。
        // 非 null 空游标、权限/列异常均不能按 0 处理；另与真实 preferred 行交叉核对。
        val cursor = resolver.query(uri, arrayOf(BroadnetApnColumns.APN_SET_ID), null, null, null) ?: return 0
        return cursor.use {
            val column = cursor.getColumnIndex(BroadnetApnColumns.APN_SET_ID)
            check(column >= 0 && cursor.count in 1..ApnDiagnosticsLimits.MAX_ROWS) { "Preferred APN set unavailable" }
            var value: Int? = null
            var count = 0
            while (cursor.moveToNext()) {
                check(++count <= ApnDiagnosticsLimits.MAX_ROWS) { "Too many preferred APN set rows" }
                val number = when (cursor.getType(column)) {
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                    Cursor.FIELD_TYPE_STRING -> broadnetApnInteger(cursor.getString(column))
                    else -> null
                }
                check(number != null && number in -1L..Int.MAX_VALUE.toLong()) { "Invalid preferred APN set" }
                check(value == null || value == number.toInt()) { "Ambiguous preferred APN set" }
                value = number.toInt()
            }
            checkNotNull(value) { "Empty preferred APN set" }
        }
    }

    override fun insert(template: BroadnetApnTemplate): Long? {
        verifyIdentity()
        val values = ContentValues().apply {
            put(Telephony.Carriers.NAME, template.displayName)
            put(Telephony.Carriers.APN, template.apn)
            put(Telephony.Carriers.TYPE, template.types)
            put(Telephony.Carriers.NUMERIC, "46015")
            put(Telephony.Carriers.MCC, "460")
            put(Telephony.Carriers.MNC, "15")
            put(Telephony.Carriers.CARRIER_ENABLED, 1)
            put(Telephony.Carriers.PROTOCOL, "IPV4V6")
            put(Telephony.Carriers.ROAMING_PROTOCOL, "IPV4V6")
            put(Telephony.Carriers.NETWORK_TYPE_BITMASK, 0L)
            put(Telephony.Carriers.MVNO_TYPE, "")
            put(Telephony.Carriers.MVNO_MATCH_DATA, "")
            put(BroadnetApnColumns.EDITED_STATUS, 1)
            put(BroadnetApnColumns.APN_SET_ID, 0)
            put(Telephony.Carriers.CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID)
            put(Telephony.Carriers.SUBSCRIPTION_ID, subId)
        }
        // AOSP carriers/subId/* 的写入会退化为全局 carriers，不能声称逐订阅数据库隔离。
        // AOSP insert 冲突后可合并旧行并返回 null；调用方必须进入未知结果，不得重试。
        val uri = resolver.insert(Telephony.Carriers.CONTENT_URI, values) ?: return null
        check(uri.scheme == "content" && uri.authority == Telephony.Carriers.CONTENT_URI.authority &&
            uri.pathSegments.size == 2 && uri.pathSegments[0] == "carriers" && uri.query == null && uri.fragment == null) {
            "Unexpected inserted APN URI"
        }
        return ContentUris.parseId(uri).also { check(it > 0) { "Invalid inserted APN id" } }
    }

    override fun readRow(id: Long): BroadnetApnRow? {
        require(id > 0)
        verifyIdentity()
        val rows = query(ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI, id))
        check(rows.size <= 1) { "Ambiguous inserted APN" }
        verifyIdentity()
        return rows.singleOrNull()
    }

    private fun query(uri: Uri, selection: String? = null, selectionArgs: Array<String>? = null): List<BroadnetApnRow> {
        val cursor = resolver.query(uri, PROJECTION, selection, selectionArgs, null) ?: error("APN query unavailable")
        return cursor.use {
            val columns = PROJECTION.map { name -> cursor.getColumnIndex(name).also { check(it >= 0) { "Missing APN column" } } }
            val rows = mutableListOf<BroadnetApnRow>()
            var total = 0
            fun text(index: Int): String? = when (cursor.getType(columns[index])) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_STRING -> cursor.getString(columns[index]).also { value ->
                    total += value.length
                    check(value.length <= ApnDiagnosticsLimits.MAX_FIELD_CHARS && total <= ApnDiagnosticsLimits.MAX_TOTAL_CHARS) { "APN response exceeds limit" }
                }
                else -> error("Invalid APN text column")
            }
            fun integer(index: Int): Long? = when (cursor.getType(columns[index])) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columns[index])
                Cursor.FIELD_TYPE_STRING -> broadnetApnInteger(cursor.getString(columns[index])).also {
                    check(it != null) { "Invalid APN integer column" }
                }
                else -> error("Invalid APN integer column")
            }
            while (cursor.moveToNext()) {
                check(rows.size < ApnDiagnosticsLimits.MAX_ROWS) { "Too many APN rows" }
                val id = integer(0)
                check(id != null && id > 0) { "Invalid APN id" }
                val enabled = when (integer(7)) { null -> null; 0L -> false; 1L -> true; else -> error("Invalid APN enabled value") }
                val networkTypes = integer(10)
                check(networkTypes == null || networkTypes >= 0) { "Invalid APN network types" }
                val edited = integer(12)
                check(edited == null || edited in 0L..6L) { "Invalid APN edited status" }
                val setId = integer(13)
                check(setId != null && setId in -1L..Int.MAX_VALUE.toLong()) { "Invalid APN set" }
                val ownedBy = integer(14)
                check(ownedBy in setOf(0L, 1L)) { "Invalid APN owner" }
                val carrierId = integer(15)
                check(carrierId != null && carrierId in -1L..Int.MAX_VALUE.toLong()) { "Invalid APN carrier id" }
                val subscriptionId = integer(16)
                check(subscriptionId != null && subscriptionId in -1L..Int.MAX_VALUE.toLong()) { "Invalid APN subscription metadata" }
                rows += BroadnetApnRow(id, text(1), text(2), text(3), text(4), text(5), text(6), enabled,
                    text(8), text(9), networkTypes, text(11), edited?.toInt(), setId.toInt(), ownedBy?.toInt(),
                    carrierId.toInt(), subscriptionId.toInt())
            }
            check(rows.map { it.id }.toSet().size == rows.size) { "Duplicate APN ids" }
            rows.toList()
        }
    }

    companion object {
        private val PROJECTION = arrayOf(Telephony.Carriers._ID, Telephony.Carriers.NAME,
            Telephony.Carriers.NUMERIC, Telephony.Carriers.MCC, Telephony.Carriers.MNC,
            Telephony.Carriers.APN, Telephony.Carriers.TYPE, Telephony.Carriers.CARRIER_ENABLED,
            Telephony.Carriers.PROTOCOL, Telephony.Carriers.ROAMING_PROTOCOL, Telephony.Carriers.NETWORK_TYPE_BITMASK,
            Telephony.Carriers.MVNO_TYPE, BroadnetApnColumns.EDITED_STATUS, BroadnetApnColumns.APN_SET_ID,
            BroadnetApnColumns.OWNED_BY, Telephony.Carriers.CARRIER_ID, Telephony.Carriers.SUBSCRIPTION_ID)
    }
}

/** AOSP Telephony.Carriers 的系统字段未进入 public SDK；仅集中使用已核对的 Provider 列名。 */
private object BroadnetApnColumns {
    const val EDITED_STATUS = "edited"
    const val APN_SET_ID = "apn_set_id"
    const val OWNED_BY = "owned_by"
}
