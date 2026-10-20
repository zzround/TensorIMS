package app.mystery0.ims.tensor.model

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

    /** 不具备明确单卡身份时保留原草稿；成功时仍只准备目标，实际写入走统一应用流程。 */
    fun stage(state: ImsEditorState): TargetEdits? =
        if (isEligible(state)) stageTargetEdits(state, imsConfigPreset(ImsConfigPreset.CHINA_BROADNET_5G))
        else null
}
