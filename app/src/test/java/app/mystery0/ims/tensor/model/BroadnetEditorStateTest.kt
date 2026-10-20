package app.mystery0.ims.tensor.model

import org.junit.Assert.*
import org.junit.Test

class BroadnetEditorStateTest {
    private fun bool(value: Boolean) = FeatureValue(value, FeatureValueType.BOOLEAN)

    private fun staged(): ImsEditorState {
        val values = imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G)
        val snapshot = TargetConfigSnapshot(14, "same-sim", values,
            recovery = values.keys.associateWith { ConfigRecovery.SWITCH }, homePlmn = "46015")
        val state = ImsEditorState(subId = 14, snapshots = listOf(snapshot))
        val draft = requireNotNull(ChinaBroadnetProfile.stage(state))
        return state.copy(edits = draft.edits, broadnetApnIntent = draft.apnIntent)
    }

    private fun apns(state: BroadnetApnState = BroadnetApnState.COMPLETE, created: Int = 0, reused: Int = 2) =
        BroadnetApnResult(14, "same-sim", state, created, reused)

    @Test
    fun `只有 APN 待办也属于待应用而普通快照不自动生成待办`() {
        val state = staged().copy(edits = emptyMap())
        assertTrue(state.hasPendingChanges)
        assertTrue(state.ready)
        assertFalse(state.copy(broadnetApnIntent = null).hasPendingChanges)
    }

    @Test
    fun `同卡刷新保留 APN 意图和手工草稿但清除旧 APN 结果`() {
        val original = staged()
        val snapshot = original.snapshots.single().copy(
            values = original.snapshots.single().values + (Feature.VOWIFI to bool(false)),
            recovery = original.snapshots.single().recovery + (Feature.VOWIFI to ConfigRecovery.SWITCH))
        val previous = original.copy(snapshots = listOf(snapshot),
            edits = original.edits + (Feature.VOWIFI to bool(true)), broadnetApnResult = apns())
        val fresh = refreshedTargetState(previous, listOf(snapshot))
        assertEquals(previous.broadnetApnIntent, fresh.broadnetApnIntent)
        assertEquals(previous.edits, fresh.edits)
        assertNull(fresh.broadnetApnResult)
        assertFalse(fresh.applied)
    }

    @Test
    fun `身份或归属变化清除 APN 意图而临时失败保留并禁止应用`() {
        val previous = staged()
        val snapshot = previous.snapshots.single()
        for (home in listOf("46000", "46013", "45403", null)) {
            assertNull(refreshedTargetState(previous, listOf(snapshot.copy(homePlmn = home))).broadnetApnIntent)
        }
        assertNull(refreshedTargetState(previous, listOf(snapshot.copy(identity = "replacement"))).broadnetApnIntent)
        val failed = refreshedTargetState(previous, listOf(snapshot.copy(error = "UNAVAILABLE")))
        assertFalse(failed.ready)
        assertEquals(previous.broadnetApnIntent, failed.broadnetApnIntent)
        assertEquals(previous.edits, failed.edits)
        assertEquals(previous.broadnetApnIntent, refreshedTargetState(failed, listOf(snapshot)).broadnetApnIntent)
    }

    @Test
    fun `APN 失败冲突或未知均保留组合草稿并结束忙碌`() {
        val previous = staged().copy(applying = true, applyingApns = true)
        for (status in listOf(BroadnetApnState.CONFLICT, BroadnetApnState.FAILED, BroadnetApnState.UNKNOWN)) {
            val result = finishedTargetApplyState(previous, emptyList(), apns(status, reused = 0))
            assertFalse(result.applied)
            assertFalse(result.applying)
            assertFalse(result.applyingApns)
            assertTrue(result.hasPendingChanges)
            assertEquals(previous.edits, result.edits)
            assertEquals(previous.broadnetApnIntent, result.broadnetApnIntent)
        }
    }

    @Test
    fun `后端失联后保留同卡最后 APN 结果而不把旧快照变为可用`() {
        val previous = staged()
        val intent = requireNotNull(previous.broadnetApnIntent)
        val unavailable = previous.copy(loading = true, applying = false,
            snapshots = previous.snapshots.map { it.copy(error = "BACKEND_UNAVAILABLE") })
        val unknown = apns(BroadnetApnState.UNKNOWN, created = 1, reused = 0)
        val result = retainBroadnetApnResult(unavailable, intent, unknown)
        assertEquals(unknown, result.broadnetApnResult)
        assertEquals(unavailable.snapshots, result.snapshots)
        assertTrue(result.loading)
        assertFalse(result.ready)
        assertFalse(result.applying)
        assertFalse(result.applied)
    }

    @Test
    fun `迟到 APN 结果不能覆盖已换卡丢弃或身份变化后的草稿`() {
        val previous = staged()
        val intent = requireNotNull(previous.broadnetApnIntent)
        val changed = listOf(
            previous.copy(subId = 12),
            previous.copy(broadnetApnIntent = null),
            previous.copy(broadnetApnIntent = intent.copy()),
            previous.copy(broadnetApnIntent = intent.copy(identity = "replacement")),
            previous.copy(snapshots = previous.snapshots.map { it.copy(identity = "replacement") }),
        )
        for (state in changed) assertEquals(state, retainBroadnetApnResult(state, intent, apns()))
        assertEquals(previous, retainBroadnetApnResult(previous, intent, apns().copy(subId = 12)))
        assertEquals(previous, retainBroadnetApnResult(previous, intent, apns().copy(identity = "replacement")))
    }

    @Test
    fun `APN 完成而蜂窝失败保留待办且重试接受复用结果`() {
        val previous = staged()
        val failedConfig = previous.snapshots.single().copy(error = "READBACK_FAILED")
        val partial = finishedTargetApplyState(previous, listOf(failedConfig), apns(created = 2, reused = 0))
        assertFalse(partial.applied)
        assertEquals(previous.edits, partial.edits)
        assertEquals(previous.broadnetApnIntent, partial.broadnetApnIntent)
        assertEquals(2, partial.broadnetApnResult?.created)
        val retried = finishedTargetApplyState(partial, previous.snapshots, apns())
        assertTrue(retried.applied)
        assertFalse(retried.hasPendingChanges)
        assertNull(retried.broadnetApnIntent)
        assertEquals(0, retried.broadnetApnResult?.created)
        assertEquals(2, retried.broadnetApnResult?.reused)
    }

    @Test
    fun `跨卡 APN 或配置响应不能完成当前组合草稿`() {
        val previous = staged()
        for (bad in listOf(apns().copy(subId = 12), apns().copy(identity = "replacement"))) {
            val result = finishedTargetApplyState(previous, previous.snapshots, bad)
            assertFalse(result.applied)
            assertNull(result.broadnetApnResult)
            assertTrue(result.hasPendingChanges)
        }
        val badConfig = previous.snapshots.single().copy(identity = "replacement")
        val result = finishedTargetApplyState(previous, listOf(badConfig), apns())
        assertFalse(result.applied)
        assertEquals(previous.snapshots, result.snapshots)
    }

    @Test
    fun `系统成功但历史失败保持已回读事实及可重试草稿`() {
        val previous = staged()
        val result = finishedTargetApplyState(previous, previous.snapshots, apns(), historySaveFailed = true)
        assertTrue(result.applied)
        assertTrue(result.historySaveFailed)
        assertTrue(result.hasPendingChanges)
        assertEquals(previous.broadnetApnIntent, result.broadnetApnIntent)
        val saved = finishedTargetApplyState(result, result.snapshots, apns())
        assertTrue(saved.applied)
        assertFalse(saved.historySaveFailed)
        assertFalse(saved.hasPendingChanges)
    }

    @Test
    fun `APN 单项待办成功不需要伪造空的 CarrierConfig 写入`() {
        val previous = staged().copy(edits = emptyMap())
        val result = finishedTargetApplyState(previous, emptyList(), apns())
        assertTrue(result.applied)
        assertFalse(result.hasPendingChanges)
        assertEquals(previous.snapshots, result.snapshots)
        assertEquals(emptyMap<Feature, FeatureValue>(), result.edits)
    }

    @Test
    fun `普通多卡应用仍保留失败草稿并只更新已核对卡的快照`() {
        val original = staged().snapshots.single()
        val second = original.copy(subId = 12, identity = "other-sim", homePlmn = "45403")
        val previous = ImsEditorState(subId = -1, snapshots = listOf(original, second),
            edits = mapOf(Feature.VOLTE to bool(false)))
        val verified = original.copy(values = original.values + (Feature.VOLTE to bool(false)))
        val partial = finishedTargetApplyState(previous, listOf(verified, second.copy(error = "FAILED")), null)
        assertFalse(partial.applied)
        assertEquals(previous.edits, partial.edits)
        assertEquals(listOf(verified, second), partial.snapshots)
        assertNull(partial.broadnetApnIntent)
        val completed = finishedTargetApplyState(partial, listOf(verified,
            second.copy(values = second.values + (Feature.VOLTE to bool(false)))), null)
        assertTrue(completed.applied)
        assertFalse(completed.hasPendingChanges)
        assertNull(completed.broadnetApnResult)
    }
}
