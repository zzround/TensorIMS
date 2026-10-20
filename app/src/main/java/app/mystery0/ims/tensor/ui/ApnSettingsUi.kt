package app.mystery0.ims.tensor.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 仅打开系统配置界面；APN 的变更由用户在系统中明确完成。 */
internal fun openApnSettings(context: Context, subId: Int): Boolean {
    if (subId < 0) return false
    val intent = Intent(Settings.ACTION_APN_SETTINGS).apply {
        putExtra(Settings.EXTRA_SUB_ID, subId)
        // AOSP ApnSettings 实际读取 sub_id；同时传公开 extra 兼容其他 Settings 实现。
        // 此 action 不承诺选卡，界面仍须提醒用户核对目标 SIM。
        putExtra("sub_id", subId)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
