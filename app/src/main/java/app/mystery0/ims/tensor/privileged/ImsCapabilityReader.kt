package app.mystery0.ims.tensor.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.telephony.AccessNetworkConstants
import android.telephony.CarrierConfigManager
import android.telephony.NetworkRegistrationInfo
import android.telephony.TelephonyManager
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE
import android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR
import android.util.Log
import app.mystery0.ims.tensor.model.nrConfigAllows
import app.mystery0.ims.tensor.model.nrPolicyAllows

class ImsCapabilityReader : SessionInstrumentation() {
    companion object {
        private const val TAG = "ImsCapabilityReader"
        const val BUNDLE_SELECT_SIM_ID = "select_sim_id"
        const val BUNDLE_IMS_REGISTERED = "ims_registered"
        const val BUNDLE_VOLTE = "volte"
        const val BUNDLE_VOWIFI = "vowifi"
        const val BUNDLE_VONR = "vonr"
        const val BUNDLE_VT = "vt"
        const val BUNDLE_SNAPSHOT_COMPLETE = "snapshot_complete"
        const val BUNDLE_PACKET_REGISTERED = "packet_registered"
        const val BUNDLE_PACKET_NETWORK_TYPE = "packet_network_type"
        const val BUNDLE_NR_STATE = "nr_state"
        const val BUNDLE_CONFIG_NSA = "config_nr_nsa"
        const val BUNDLE_CONFIG_SA = "config_nr_sa"
        const val BUNDLE_USER_NR = "user_policy_nr"
        const val BUNDLE_CARRIER_NR = "carrier_policy_nr"
        const val BUNDLE_RESULT_MSG = "result_msg"
        const val BUNDLE_HOME_PLMN = "home_plmn"

        // MmTelCapabilities 能力类型常量（来自 MmTelFeature.MmTelCapabilities，值稳定）
        private const val CAPABILITY_TYPE_VOICE = 1
        private const val CAPABILITY_TYPE_VIDEO = 2
    }

    // serviceState 由 shell permission delegation 授权，委托失败会转换为结果错误。
    @SuppressLint("MissingPermission")
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }

        val result = Bundle()
        val subId = arguments.getInt(BUNDLE_SELECT_SIM_ID, -1)
        if (subId < 0) {
            result.putString(BUNDLE_RESULT_MSG, "invalid subId")
            finish(Activity.RESULT_OK, result)
            return
        }

        val failure = runWithShellPermissionDelegation(TAG) {
            // 只读操作不携带写入身份令牌，必须在本次委托内独立校验快照前后的 SIM 身份。
            val targetReader = TargetConfigurationReader(context, subId)
            val snapshotSim = targetReader.currentSim()

            // 1. 整体 IMS 注册状态（隐藏 API，通过已有 stub 调用）
            readOptional(BUNDLE_IMS_REGISTERED) {
                privilegeSession.isImsRegistered(operationId, subId)
            }?.let { result.putBoolean(BUNDLE_IMS_REGISTERED, it) }

            // 2. 逐项 IMS 能力（@SystemApi ImsMmTelManager，通过反射调用，与 *#*#4636#*#* 相同）
            readOptional("IMS manager") {
                val mmTelClass = Class.forName("android.telephony.ims.ImsMmTelManager")
                // 这两个 @SystemApi 不在公开 SDK，沿用反射；接口失败不伪造不可用状态。
                val manager = mmTelClass.getMethod("createForSubscriptionId", Int::class.javaPrimitiveType)
                    .invoke(null, subId)
                manager to mmTelClass.getMethod("isAvailable",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            }?.let { (manager, method) ->
                listOf(
                    Triple(BUNDLE_VOLTE, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE),
                    Triple(BUNDLE_VOWIFI, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN),
                    Triple(BUNDLE_VONR, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR),
                    Triple(BUNDLE_VT, CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE),
                ).forEach { (key, capability, technology) ->
                    readOptional(key) { method.invoke(manager, capability, technology) as Boolean }
                        ?.let { result.putBoolean(key, it) }
                }
            }

            // 3. 按所选 subId 读取 WWAN 分组域快照，不能把 Wi-Fi 或另一张卡算作 NR。
            readOptional("TelephonyManager") {
                context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(subId)
            }?.let { tm ->
                readOptional("WWAN packet registration") {
                    tm.serviceState?.networkRegistrationInfoList?.filter {
                        it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN &&
                            (it.domain and NetworkRegistrationInfo.DOMAIN_PS) != 0
                    }?.singleOrNull()
                }?.let { registration ->
                    readOptional(BUNDLE_PACKET_REGISTERED) {
                        // Android 14+ 的公开 API 不受 CarrierConfig 注册状态覆盖影响。
                        if (Build.VERSION.SDK_INT >= 34) registration.isNetworkRegistered
                        else @Suppress("DEPRECATION") registration.isRegistered
                    }?.let { result.putBoolean(BUNDLE_PACKET_REGISTERED, it) }
                    readOptional(BUNDLE_PACKET_NETWORK_TYPE) { registration.accessNetworkTechnology }
                        ?.let { result.putInt(BUNDLE_PACKET_NETWORK_TYPE, it) }
                    // getNrState 仍是隐藏 API，沿用现有反射；缺失/失败保持未知，不补 NR_STATE_NONE。
                    readOptional(BUNDLE_NR_STATE) {
                        registration.javaClass.getMethod("getNrState").invoke(registration) as Int
                    }?.let { result.putInt(BUNDLE_NR_STATE, it) }
                }

                // API 33+ 的只读接口，复用现有 READ_PRIVILEGED_PHONE_STATE，不修改首选网络。
                listOf(
                    BUNDLE_USER_NR to TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
                    BUNDLE_CARRIER_NR to TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER,
                ).forEach { (key, reason) ->
                    readOptional(key) { tm.getAllowedNetworkTypesForReason(reason) }
                        ?.let(::nrPolicyAllows)?.let { result.putBoolean(key, it) }
                }
            }

            // 本地 CarrierConfig 的允许值不代表网络准入，单独展示且缺键保持未知。
            readOptional("CarrierConfig NR availability") {
                context.getSystemService(CarrierConfigManager::class.java).getConfigForSubId(subId)
            }?.let { config ->
                // 未加载时 getConfigForSubId 可能返回框架默认值；类型不符也必须保持未知。
                @Suppress("DEPRECATION")
                val applied = config[CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL] as? Boolean
                @Suppress("DEPRECATION")
                val modes = config[CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY] as? IntArray
                nrConfigAllows(applied, modes, CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA)
                    ?.let { result.putBoolean(BUNDLE_CONFIG_NSA, it) }
                nrConfigAllows(applied, modes, CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA)
                    ?.let { result.putBoolean(BUNDLE_CONFIG_SA, it) }
            }
            check(targetReader.currentSim() == snapshotSim) { "SIM changed while reading IMS capabilities" }
            verifyTargetIdentities()
            result.putString(BUNDLE_HOME_PLMN, snapshotSim.homePlmn)
            result.putBoolean(BUNDLE_SNAPSHOT_COMPLETE, true)
        }
        if (failure != null) {
            Log.e(TAG, "read IMS capabilities failed", failure)
            result.putString(BUNDLE_RESULT_MSG, failure.toPrivilegedErrorMessage())
        }
        finish(Activity.RESULT_OK, result)
    }

    /** 单项遥测失败只使该项未知；会话、SIM 身份和权限清理失败仍由外层使整个快照失效。 */
    private inline fun <T> readOptional(field: String, block: () -> T): T? = try {
        block()
    } catch (failure: Exception) {
        Log.w(TAG, "Cannot read $field", failure)
        null
    } catch (failure: LinkageError) {
        Log.w(TAG, "Unavailable runtime API for $field", failure)
        null
    }
}
