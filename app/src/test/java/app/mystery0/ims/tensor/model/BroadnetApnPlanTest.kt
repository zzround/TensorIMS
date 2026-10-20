package app.mystery0.ims.tensor.model

import org.junit.Assert.*
import org.junit.Test

class BroadnetApnPlanTest {
    private val identity = "a".repeat(64)
    private fun row(id: Long, template: BroadnetApnTemplate) = BroadnetApnRow(id, template.displayName,
        "46015", "460", "15", template.apn, template.types, true, "IPV4V6", "IPV4V6", 0L, "", 1,
        0, 1, -1, 14)
    private fun snapshot(vararg rows: BroadnetApnRow, preferred: BroadnetApnRow? = null) =
        BroadnetApnSnapshot(identity, "46015", rows.toList(), rows.toList(), preferred)

    private inner class Adapter(var state: BroadnetApnSnapshot = snapshot()) : BroadnetApnAdapter {
        val writes = mutableListOf<BroadnetApnTemplate>()
        var onRead: ((Int) -> Unit)? = null
        var onInsert: ((BroadnetApnTemplate) -> Long?)? = null
        var readCount = 0
        override fun read(): BroadnetApnSnapshot { onRead?.invoke(++readCount); return state }
        fun add(template: BroadnetApnTemplate): Long {
            val id = (state.carrierRows.maxOfOrNull { it.id } ?: 99L) + 1
            val entry = row(id, template)
            state = state.copy(simRows = state.simRows + entry, carrierRows = state.carrierRows + entry,
                preferred = state.preferred ?: entry.takeIf { template == BroadnetApnTemplate.DATA })
            return id
        }
        override fun insert(template: BroadnetApnTemplate): Long? {
            writes += template
            return onInsert?.invoke(template) ?: if (onInsert == null) add(template) else null
        }
        override fun readRow(id: Long) = state.carrierRows.singleOrNull { it.id == id }
        fun replace(entry: BroadnetApnRow) {
            state = state.copy(simRows = state.simRows.map { if (it.id == entry.id) entry else it },
                carrierRows = state.carrierRows.map { if (it.id == entry.id) entry else it },
                preferred = if (state.preferred?.id == entry.id) entry else state.preferred)
        }
    }

    private fun ensure(adapter: Adapter, write: Boolean = true) = BroadnetApnEngine.run(14, identity, write, adapter)

    @Test fun `空库只补两条且第二次操作完整复用不重复写入`() {
        val adapter = Adapter()
        val first = ensure(adapter)
        assertTrue(first.success)
        assertTrue(first.readComplete)
        assertEquals(2, first.created)
        assertEquals(listOf(BroadnetApnTemplate.DATA, BroadnetApnTemplate.IMS), adapter.writes)
        assertEquals("cbnet", adapter.state.preferred?.apn)
        val second = ensure(adapter)
        assertTrue(second.success)
        assertEquals(0, second.created)
        assertEquals(2, second.reused)
        assertEquals(2, adapter.writes.size)
    }

    @Test fun `缺一项只创建该项并保留已有首选数据APN原字段`() {
        val original = row(7, BroadnetApnTemplate.DATA).copy(name = "已选数据", apn = "internet",
            types = "default,supl", protocol = "IP", networkTypeBitmask = 123L)
        val adapter = Adapter(snapshot(original, preferred = original))
        val result = ensure(adapter)
        assertTrue(result.success)
        assertEquals(1, result.created)
        assertEquals(1, result.reused)
        assertEquals(listOf(BroadnetApnTemplate.IMS), adapter.writes)
        assertEquals(original, adapter.state.preferred)
    }

    @Test fun `唯一可用cbnet无需强制改成模板类型或协议`() {
        val original = row(7, BroadnetApnTemplate.DATA).copy(types = "default,supl", protocol = "IP")
        val adapter = Adapter(snapshot(original, preferred = original))
        assertTrue(ensure(adapter).success)
        assertEquals(listOf(BroadnetApnTemplate.IMS), adapter.writes)
        assertEquals(original, adapter.state.preferred)
    }

    @Test fun `原有IMS但无数据项仅补cbnet`() {
        val adapter = Adapter(snapshot(row(4, BroadnetApnTemplate.IMS)))
        assertTrue(ensure(adapter).success)
        assertEquals(listOf(BroadnetApnTemplate.DATA), adapter.writes)
    }

    @Test fun `混合通配未知及禁用IMS不被新正确记录掩盖`() {
        val ims = row(5, BroadnetApnTemplate.IMS)
        for (bad in listOf(ims.copy(apn = "internet", types = "default,supl,ims"),
            ims.copy(types = null), ims.copy(types = "*"), ims.copy(types = " ims"),
            ims.copy(enabled = false), ims.copy(enabled = null))) {
            val adapter = Adapter(snapshot(bad))
            assertEquals(BroadnetApnState.CONFLICT, ensure(adapter).state)
            assertTrue(adapter.writes.isEmpty())
        }
    }

    @Test fun `可见同名重复和未匹配MVNO全局冲突禁止新增`() {
        val ims = row(5, BroadnetApnTemplate.IMS)
        val duplicate = Adapter(snapshot(ims, ims.copy(id = 6)))
        assertEquals(BroadnetApnState.CONFLICT, ensure(duplicate).state)
        val mvno = ims.copy(mvnoType = "spn")
        val separateScope = Adapter(snapshot().copy(carrierRows = listOf(mvno)))
        assertEquals(BroadnetApnState.CONFLICT, ensure(separateScope).state)
        assertTrue(duplicate.writes.isEmpty() && separateScope.writes.isEmpty())
    }

    @Test fun `受管理非普通set及不同carrierId不判为可用普通广电项`() {
        val ims = row(5, BroadnetApnTemplate.IMS)
        for (bad in listOf(ims.copy(ownedBy = 0), ims.copy(apnSetId = 4), ims.copy(carrierId = 99))) {
            assertEquals(BroadnetApnState.CONFLICT, ensure(Adapter(snapshot(bad))).state)
        }
        assertEquals(BroadnetApnState.CONFLICT,
            ensure(Adapter(snapshot().copy(preferredSetId = 4))).state)
        assertEquals(null, BroadnetApnPlanner.plan(snapshot(ims.copy(apnSetId = -1)), identity).error)
    }

    @Test fun `SIM身份或归属变化不算完整核对且不会写到另一张卡`() {
        for (state in listOf(snapshot().copy(identity = "b".repeat(64)), snapshot().copy(homePlmn = "45403"))) {
            val adapter = Adapter(state)
            val result = ensure(adapter)
            assertEquals(BroadnetApnState.FAILED, result.state)
            assertFalse(result.readComplete)
            assertTrue(adapter.writes.isEmpty())
        }
    }

    @Test fun `插入可能已合并但返回null时未知停止第二条且保留恢复语义`() {
        val adapter = Adapter()
        adapter.onInsert = { template ->
            // 模拟不可见删除项或并发插入触发 Provider merge，数据库已变化但没有返回 URI。
            val id = adapter.add(template)
            adapter.replace(requireNotNull(adapter.readRow(id)).copy(types = "default,ia,supl,xcap,mms"))
            null
        }
        val result = ensure(adapter)
        assertEquals(BroadnetApnState.UNKNOWN, result.state)
        assertEquals("INSERT_RESULT_UNKNOWN", result.error)
        assertTrue(result.requiresRecovery)
        assertFalse(result.readComplete)
        assertEquals(1, adapter.writes.size)
        assertEquals(1, adapter.state.carrierRows.size)
        assertEquals(0, result.created)
    }

    @Test fun `新增后旧行变化不能只按新增行数报告成功`() {
        val existing = row(5, BroadnetApnTemplate.DATA).copy(apn = "mms", types = "mms")
        val adapter = Adapter(snapshot(existing))
        adapter.onInsert = { template -> adapter.add(template).also { adapter.replace(existing.copy(name = "changed")) } }
        assertEquals(BroadnetApnState.UNKNOWN, ensure(adapter).state)
        assertEquals(1, adapter.writes.size)
    }

    @Test fun `原空首选只允许系统选本次新增cbnet不得接受其他漂移`() {
        val existing = row(5, BroadnetApnTemplate.DATA).copy(apn = "mms", types = "mms")
        val adapter = Adapter(snapshot(existing))
        adapter.onInsert = { template -> adapter.add(template).also { adapter.state = adapter.state.copy(preferred = existing) } }
        assertEquals(BroadnetApnState.UNKNOWN, ensure(adapter).state)
        assertEquals(1, adapter.writes.size)
    }

    @Test fun `第一条确认后第二条异常保留已创建计数不重试或删除`() {
        val adapter = Adapter()
        adapter.onInsert = { template ->
            if (template == BroadnetApnTemplate.IMS) throw SecurityException("permission revoked")
            adapter.add(template)
        }
        val result = ensure(adapter)
        assertEquals(BroadnetApnState.UNKNOWN, result.state)
        assertEquals(1, result.created)
        assertEquals(1, adapter.state.carrierRows.size)
        assertEquals(2, adapter.writes.size)
    }

    @Test fun `第二步前观察到改变停止执行并保留已确认部分结果`() {
        val adapter = Adapter()
        adapter.onRead = { count -> if (count == 4) {
            adapter.replace(adapter.state.carrierRows.single().copy(name = "external edit"))
        } }
        val result = ensure(adapter)
        assertEquals(BroadnetApnState.CONFLICT, result.state)
        assertEquals(1, result.created)
        assertTrue(result.readComplete)
        assertEquals(1, adapter.writes.size)
    }

    @Test fun `恢复只读可报告完整缺失但绝不补齐或宣称原操作成功`() {
        val adapter = Adapter()
        val result = ensure(adapter, write = false)
        assertEquals(BroadnetApnState.FAILED, result.state)
        assertEquals("APN_MISSING", result.error)
        assertTrue(result.readComplete)
        assertFalse(result.success)
        assertTrue(adapter.writes.isEmpty())
    }

    @Test fun `整数列接受AOSP字符串但拒绝SQLnull浮点非规范文本及溢出`() {
        assertEquals(14L, broadnetApnInteger(14))
        assertEquals(14L, broadnetApnInteger(14L))
        assertEquals(14L, broadnetApnInteger("14"))
        assertEquals(0L, broadnetApnInteger("0"))
        assertEquals(-1L, broadnetApnInteger("-1"))
        for (value in listOf(null, 14.0, "", " 14", "14 ", "01", "-0", "+1", "9223372036854775808")) {
            assertNull(broadnetApnInteger(value))
        }
    }
}
