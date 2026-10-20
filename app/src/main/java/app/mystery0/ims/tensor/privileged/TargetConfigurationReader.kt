package app.mystery0.ims.tensor.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import app.mystery0.ims.tensor.model.ChinaBroadnetProfile
import app.mystery0.ims.tensor.model.TargetConfigMapper
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.model.TargetConfigSnapshot

/** 必须在统一 shell 权限委托内调用；只读合并后的实际配置，不使用通用默认配置补缺项。 */
internal class TargetConfigurationReader(private val context: Context, val subId: Int) {
    val manager: CarrierConfigManager = context.getSystemService(CarrierConfigManager::class.java)

    private data class ActiveSim(val identity: String, val homePlmn: String?)

    fun identity(): String = activeSim().identity

    @SuppressLint("MissingPermission")
    private fun activeSim(): ActiveSim {
        require(subId >= 0) { "An active SIM is required" }
        val info = context.getSystemService(SubscriptionManager::class.java)
            .activeSubscriptionInfoList.orEmpty().firstOrNull { it.subscriptionId == subId }
            ?: error("SIM is no longer active")
        // subId 会被复用，ICCID 仅用于摘要校验，不保存或打印原始卡号。
        check(info.iccId.isNotBlank()) { "SIM identity is unavailable" }
        return ActiveSim(PersistentVolteBackup.identityDigest(info.iccId),
            ChinaBroadnetProfile.homePlmn(info.mccString, info.mncString))
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun raw(): Map<String, Any> {
        // 无 keys 的公开重载兼容 Android 13，且保留私有键；空/未加载结果不能当作默认配置。
        val values = manager.getConfigForSubId(subId) ?: error("Carrier configuration is unavailable")
        check(values.getBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, false)) {
            "Carrier configuration is not loaded"
        }
        return values.keySet().mapNotNull { key -> values[key]?.let { key to it } }.toMap()
    }

    fun snapshot(expected: Map<String, Any>? = null): TargetConfigSnapshot {
        val sim = activeSim()
        val raw = raw()
        check(expected == null || TargetConfigMapper.matches(raw, expected)) { "Configuration changed during verification" }
        val values = TargetConfigMapper.read(raw, Build.VERSION.SDK_INT)
        // 归属 PLMN 也在读取前后核对，避免将切卡过程中另一张卡的运营商信息混入快照。
        check(activeSim() == sim) { "SIM changed while reading configuration" }
        check(values.isNotEmpty()) { "Carrier configuration contains no supported keys" }
        val defaults = defaults()
        return TargetConfigSnapshot(subId, sim.identity, values,
            recovery = values.keys.associateWith { TargetConfigMapper.recovery(it, defaults) },
            wfcNameFormatConsistent = TargetConfigMapper.isWfcNameFormatConsistent(raw),
            homePlmn = sim.homePlmn)
    }

    @Suppress("DEPRECATION")
    fun defaults(): Map<String, Any> = try {
        // 隐藏 API 在 Android 13+ 以反射读取；它是框架默认参数，不是该卡运营商默认。
        // 反射失败只取消参数项的单项恢复，不影响普通布尔开关和当前配置读取。
        val values = CarrierConfigManager::class.java.getMethod("getDefaultConfig")
            .invoke(null) as PersistableBundle
        values.keySet().mapNotNull { key -> values[key]?.let { key to it } }.toMap()
    } catch (e: Exception) {
        android.util.Log.w("TargetConfiguration", "Default parameters unavailable", e)
        emptyMap()
    }

    companion object {
        fun encode(snapshot: TargetConfigSnapshot): Bundle = Bundle().apply {
            putBoolean(TargetConfigProtocol.COMPLETE, true)
            putInt(TargetConfigProtocol.SUB_ID, snapshot.subId)
            putString(TargetConfigProtocol.IDENTITY, snapshot.identity)
            putBundle(TargetConfigProtocol.VALUES, TargetConfigProtocol.encode(snapshot.values))
            putString(TargetConfigProtocol.ERROR, snapshot.error)
            putBoolean(TargetConfigProtocol.WFC_NAME_FORMAT_CONSISTENT, snapshot.wfcNameFormatConsistent)
            putString(TargetConfigProtocol.HOME_PLMN, snapshot.homePlmn)
            putBundle(TargetConfigProtocol.RECOVERY, Bundle().apply {
                snapshot.recovery.forEach { (feature, policy) -> putString(feature.name, policy.name) }
            })
        }
    }
}
