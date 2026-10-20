package app.mystery0.ims.tensor.model

data class ChinaBroadnetDraft(
    val edits: Map<Feature, FeatureValue>,
    val skipped: Boolean,
    val apnIntent: BroadnetApnIntent,
)

/** 广电身份以当前活动 SIM 的归属 PLMN 为准，不使用拜访网络、显示名称或手机号段猜测。 */
object ChinaBroadnetProfile {
    const val HOME_PLMN = "46015"

    /** MNC 的位数是身份的一部分，不能把 015 截短为 15，也不为缺失信息补值。 */
    fun homePlmn(mcc: String?, mnc: String?): String? =
        if (mcc?.matches(Regex("[0-9]{3}")) == true && mnc?.matches(Regex("[0-9]{2,3}")) == true)
            mcc + mnc else null

    fun isEligible(state: ImsEditorState): Boolean {
        val selected = state.subId?.takeIf { it >= 0 } ?: return false
        val snapshot = state.snapshots.singleOrNull() ?: return false
        return state.ready && snapshot.subId == selected && snapshot.identity.isNotBlank() &&
            snapshot.homePlmn == HOME_PLMN
    }

    fun matches(state: ImsEditorState, intent: BroadnetApnIntent): Boolean = isEligible(state) &&
        state.subId == intent.subId && state.snapshots.single().identity == intent.identity

    /** 同时准备蜂窝目标与 APN 补齐，点预设不写系统；实际操作统一由“应用更改”执行。 */
    fun stage(state: ImsEditorState): ChinaBroadnetDraft? {
        if (!isEligible(state)) return null
        val snapshot = state.snapshots.single()
        val staged = stageTargetEdits(state, imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G))
        return ChinaBroadnetDraft(staged.edits, staged.skipped, BroadnetApnIntent(snapshot.subId, snapshot.identity))
    }
}
