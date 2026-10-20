package app.mystery0.ims.tensor.model

data class BroadnetApnPlan(
    val missing: List<BroadnetApnTemplate> = emptyList(),
    val reused: Int = 0,
    val error: String? = null,
)

/** APN 数据库按运营商共享；SIM URI 用于匹配验证，不能充当写入隔离。 */
object BroadnetApnPlanner {
    fun plan(snapshot: BroadnetApnSnapshot, expectedIdentity: String): BroadnetApnPlan {
        fun conflict(code: String) = BroadnetApnPlan(error = code)
        if (snapshot.identity != expectedIdentity || snapshot.homePlmn != "46015") return conflict("SIM_SCOPE_CHANGED")
        if (snapshot.preferredSetId != 0) return conflict("UNSUPPORTED_APN_SET")
        if (!validRows(snapshot.simRows) || !validRows(snapshot.carrierRows) ||
            snapshot.carrierRows.any { it.numeric != "46015" }) return conflict("INVALID_APN_RESPONSE")
        val carrierById = snapshot.carrierRows.associateBy { it.id }
        if (snapshot.simRows.any { !it.isMnoScope || carrierById[it.id] != it ||
            it.carrierId != -1 && it.carrierId != snapshot.simCarrierId }) return conflict("UNSUPPORTED_APN_SCOPE")
        if (snapshot.preferred != null && snapshot.simRows.none { it == snapshot.preferred }) return conflict("PREFERRED_APN_CONFLICT")
        if (snapshot.preferred != null && snapshot.preferred.apnSetId != snapshot.preferredSetId) return conflict("PREFERRED_APN_CONFLICT")

        // 混合、通配、禁用 IMS 及未知类型不能被额外创建的正确条目掩盖。
        if (snapshot.simRows.any { it.typeKind != ApnTypeKind.OTHER && !it.matches(BroadnetApnTemplate.IMS) }) {
            return conflict("EXISTING_IMS_CONFLICT")
        }
        for (template in BroadnetApnTemplate.entries) {
            val sameName = snapshot.carrierRows.filter { it.apn.equals(template.apn, true) }
            // 全局查询只排除可见冲突；Provider 隐藏的删除标记仍可能导致 insert 合并后返回 null。
            val usable = sameName.singleOrNull()?.let { row -> row in snapshot.simRows &&
                if (template == BroadnetApnTemplate.DATA) row.usableData else row.matches(template) } == true
            if (sameName.isNotEmpty() && !usable) {
                return conflict("EXISTING_APN_CONFLICT")
            }
        }
        if (snapshot.preferred != null && !snapshot.preferred.usableData) return conflict("PREFERRED_APN_CONFLICT")
        val data = snapshot.preferred ?: snapshot.simRows.firstOrNull { it.usableData }
        if (data == null && snapshot.simRows.any { "default" in normalizedApnTypes(it.types) }) {
            return conflict("EXISTING_DATA_CONFLICT")
        }
        val ims = snapshot.simRows.firstOrNull { it.matches(BroadnetApnTemplate.IMS) }
        return BroadnetApnPlan(buildList {
            if (data == null) add(BroadnetApnTemplate.DATA)
            if (ims == null) add(BroadnetApnTemplate.IMS)
        }, reused = (if (data != null) 1 else 0) + (if (ims != null) 1 else 0))
    }

    private fun validRows(rows: List<BroadnetApnRow>): Boolean = rows.size <= ApnDiagnosticsLimits.MAX_ROWS &&
        rows.all { it.id > 0 } && rows.map { it.id }.toSet().size == rows.size
}

/** 验证实际行集合；只允许增加本次固定模板，不能用行数或返回 URI 单独判断成功。 */
object BroadnetApnVerification {
    fun unchanged(before: BroadnetApnSnapshot, after: BroadnetApnSnapshot, newDataIds: Set<Long>): Boolean =
        sameScope(before, after) && before.simRows.size == after.simRows.size && before.carrierRows.size == after.carrierRows.size &&
            before.simRows.toSet() == after.simRows.toSet() && before.carrierRows.toSet() == after.carrierRows.toSet() &&
            preferredPreserved(before, after, newDataIds)

    fun inserted(before: BroadnetApnSnapshot, after: BroadnetApnSnapshot, template: BroadnetApnTemplate,
                 rowId: Long, byId: BroadnetApnRow?, newDataIds: Set<Long>): Boolean {
        if (rowId <= 0 || !sameScope(before, after) || before.carrierRows.any { it.id == rowId }) return false
        val row = after.carrierRows.singleOrNull { it.id == rowId } ?: return false
        if (byId != row || row.name != template.displayName || !row.matches(template) || row.edited != 1 ||
            row.apnSetId != 0 || row.carrierId != -1) return false
        if (after.simRows.singleOrNull { it.id == rowId } != row) return false
        if (after.carrierRows.size != before.carrierRows.size + 1 || after.simRows.size != before.simRows.size + 1) return false
        if (after.carrierRows.filterNot { it.id == rowId }.toSet() != before.carrierRows.toSet() ||
            after.simRows.filterNot { it.id == rowId }.toSet() != before.simRows.toSet()) return false
        return preferredPreserved(before, after, newDataIds + if (template == BroadnetApnTemplate.DATA) setOf(rowId) else emptySet())
    }

    private fun sameScope(before: BroadnetApnSnapshot, after: BroadnetApnSnapshot): Boolean =
        before.identity == after.identity && before.homePlmn == "46015" && after.homePlmn == "46015" &&
            before.simCarrierId == after.simCarrierId && before.preferredSetId == 0 && after.preferredSetId == 0

    private fun preferredPreserved(before: BroadnetApnSnapshot, after: BroadnetApnSnapshot, newDataIds: Set<Long>): Boolean =
        if (before.preferred != null) before.preferred == after.preferred
        else after.preferred == null || after.preferred.id in newDataIds && after.preferred.matches(BroadnetApnTemplate.DATA)
}

interface BroadnetApnAdapter {
    fun read(): BroadnetApnSnapshot
    fun insert(template: BroadnetApnTemplate): Long?
    fun readRow(id: Long): BroadnetApnRow?
}

/** 每条写入分别闭环；Provider 的 insert(null) 可能已合并旧行，故不批量、不盲重试。 */
object BroadnetApnEngine {
    fun run(subId: Int, identity: String, write: Boolean, adapter: BroadnetApnAdapter,
            onFailure: (Throwable) -> Unit = {},
            onWrite: () -> Unit = {}): BroadnetApnResult {
        var created = 0
        var reused = 0
        var attempted = false
        var pendingInsert = false
        var readComplete = false
        fun result(state: BroadnetApnState, error: String? = null) =
            BroadnetApnResult(subId, identity, state, created, reused, error, readComplete)
        fun read(): BroadnetApnSnapshot {
            readComplete = false
            return adapter.read().also {
                check(it.identity == identity && it.homePlmn == "46015") { "SIM scope changed" }
                readComplete = true
            }
        }
        try {
            require(subId >= 0 && identity.matches(Regex("[a-fA-F0-9]{64}"))) { "INVALID_TARGET" }
            var expected = read()
            val plan = BroadnetApnPlanner.plan(expected, identity)
            if (plan.error != null) return result(BroadnetApnState.CONFLICT, plan.error)
            reused = plan.reused
            if (plan.missing.isEmpty()) return result(BroadnetApnState.COMPLETE)
            if (!write) return result(BroadnetApnState.FAILED, "APN_MISSING")
            val newDataIds = mutableSetOf<Long>()
            for (template in plan.missing) {
                val before = read()
                if (!BroadnetApnVerification.unchanged(expected, before, newDataIds)) {
                    return result(BroadnetApnState.CONFLICT, "APN_STATE_CHANGED")
                }
                onWrite()
                attempted = true
                pendingInsert = true
                readComplete = false
                val id = adapter.insert(template) ?: return result(BroadnetApnState.UNKNOWN, "INSERT_RESULT_UNKNOWN")
                val after = read()
                readComplete = false
                val row = adapter.readRow(id)
                readComplete = true
                if (row?.subscriptionId != subId || !BroadnetApnVerification.inserted(before, after, template, id, row, newDataIds)) {
                    return result(BroadnetApnState.UNKNOWN, "INSERT_READBACK_MISMATCH")
                }
                created++
                pendingInsert = false
                if (template == BroadnetApnTemplate.DATA) newDataIds += id
                expected = after
            }
            val after = read()
            if (!BroadnetApnVerification.unchanged(expected, after, newDataIds) ||
                BroadnetApnPlanner.plan(after, identity).let { it.error != null || it.missing.isNotEmpty() }) {
                return result(BroadnetApnState.UNKNOWN, "FINAL_READBACK_MISMATCH")
            }
            return result(BroadnetApnState.COMPLETE)
        } catch (failure: Exception) {
            onFailure(failure)
            return result(if (attempted || pendingInsert) BroadnetApnState.UNKNOWN else BroadnetApnState.FAILED,
                if (attempted || pendingInsert) "APN_OPERATION_UNCONFIRMED" else "APN_READ_FAILED")
        }
    }
}
