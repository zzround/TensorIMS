package app.mystery0.ims.tensor.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.ims.tensor.ConfigurationOperations
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.ShizukuProvider
import app.mystery0.ims.tensor.model.BroadnetApnResult
import app.mystery0.ims.tensor.model.BroadnetApnState
import app.mystery0.ims.tensor.model.ChinaBroadnetProfile
import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.FeatureValue
import app.mystery0.ims.tensor.model.ImsEditorState
import app.mystery0.ims.tensor.model.ImsConfigPreset
import app.mystery0.ims.tensor.model.TargetConfigSnapshot
import app.mystery0.ims.tensor.model.stageTargetEdits
import app.mystery0.ims.tensor.model.commonValues
import app.mystery0.ims.tensor.model.finishedTargetApplyState
import app.mystery0.ims.tensor.model.refreshedTargetState
import app.mystery0.ims.tensor.model.retainBroadnetApnResult
import app.mystery0.ims.tensor.model.imsConfigPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 当前快照与待应用目标分离；切卡后忽略旧结果，但完成已发出的写入和历史记录。 */
class ImsConfigViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repository = (app as app.mystery0.ims.tensor.Application).autoRestore.repository
    private val _state = MutableStateFlow(ImsEditorState())
    val state = _state.asStateFlow()
    private var generation = 0
    private var shizukuReady = false

    /** 只清理成功重置的当前目标，迟到结果不能覆盖用户切换后的草稿。 */
    fun reloadAfterReset(subId: Int, success: Boolean) {
        if (_state.value.subId != subId) return
        if (success) {
            load(subId, shizukuReady, discardEdits = true)
        } else {
            // 失败可能发生在部分写入之后：保留草稿，但旧快照不能继续作为写入依据。
            ++generation
            _state.value = _state.value.copy(loading = false, applying = false, applied = false,
                applyingApns = false,
                snapshots = _state.value.snapshots.map { it.copy(error = "Reset did not complete") },
                error = app.getString(R.string.nav_reset_failed_snapshot))
        }
    }

    fun load(subId: Int?, ready: Boolean, discardEdits: Boolean = false) {
        shizukuReady = ready
        val previous = if (!discardEdits && _state.value.subId == subId) _state.value else ImsEditorState(subId = subId)
        val request = ++generation
        _state.value = previous.copy(loading = true, applying = false, applyingApns = false, applied = false, error = null)
        if (!ready || subId == null) return
        viewModelScope.launch {
            try {
                val snapshots = ConfigurationOperations.run {
                    if (request != generation || !shizukuReady) return@run emptyList()
                    val ids = if (subId == -1) ShizukuProvider.readSimInfoList(app).map { it.subId }
                        else listOf(subId)
                    ids.map { ShizukuProvider.readTargetConfig(app, it) }
                }
                if (request != generation) return@launch
                _state.value = refreshedTargetState(previous, snapshots).copy(
                    error = if (snapshots.isEmpty()) app.getString(R.string.ims_no_active_sim)
                        else snapshots.filter { it.error != null }.joinToString("\n") {
                            "SIM ${it.subId}: ${it.error}"
                        }.ifBlank { null })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ImsConfigViewModel", "Configuration read failed", e)
                if (request == generation) _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun edit(feature: Feature, value: FeatureValue) = editAll(mapOf(feature to value))

    fun preset(preset: ImsConfigPreset) {
        val current = _state.value
        if (!current.ready || current.applying || ConfigurationOperations.busy.value || !shizukuReady) return
        if (preset != ImsConfigPreset.CHINA_BROADNET_5G) {
            editAll(imsConfigPreset(preset), clearBroadnetApn = true)
            return
        }
        // UI 禁用之外在业务入口复核同一卡快照，迟到点击不能把广电预设套给其他 SIM。
        val staged = ChinaBroadnetProfile.stage(current)
        _state.value = if (staged == null) current.copy(notice = R.string.broadnet_5g_scope_hint)
        else current.copy(edits = staged.edits, applied = false, error = null,
            broadnetApnIntent = staged.apnIntent, broadnetApnResult = null, historySaveFailed = false,
            notice = if (staged.skipped) R.string.ims_some_targets_skipped else null)
    }

    fun editAll(values: Map<Feature, FeatureValue>, clearBroadnetApn: Boolean = false) {
        val current = _state.value
        if (!current.ready || current.applying || ConfigurationOperations.busy.value || !shizukuReady) return
        val staged = stageTargetEdits(current, values)
        _state.value = current.copy(edits = staged.edits, applied = false, error = null, historySaveFailed = false,
            broadnetApnIntent = if (clearBroadnetApn) null else current.broadnetApnIntent,
            broadnetApnResult = if (clearBroadnetApn) null else current.broadnetApnResult,
            notice = if (staged.skipped) R.string.ims_some_targets_skipped else null)
    }

    fun loadHistory() {
        val current = _state.value
        if (current.subId == null || !current.ready || current.applying ||
            ConfigurationOperations.busy.value || !shizukuReady) return
        val histories = current.snapshots.map { repository.loadTargetsForSim(it.subId, it.identity).orEmpty() }
        val targets = commonValues(histories)
        if (targets.isNullOrEmpty()) {
            _state.value = current.copy(broadnetApnIntent = null, broadnetApnResult = null, notice = R.string.ims_no_history)
        } else editAll(targets, clearBroadnetApn = true)
    }

    fun apply() {
        val current = _state.value
        if (!current.ready || current.applying || !current.hasPendingChanges || !shizukuReady ||
            ConfigurationOperations.busy.value) return
        val apnIntent = current.broadnetApnIntent
        if (apnIntent != null && !ChinaBroadnetProfile.matches(current, apnIntent)) {
            _state.value = current.copy(error = app.getString(R.string.broadnet_5g_scope_hint))
            return
        }
        val request = generation
        val targets = current.edits.toMap()
        val canStart = {
            request == generation && shizukuReady && _state.value.subId == current.subId &&
                (apnIntent == null || ChinaBroadnetProfile.matches(_state.value, apnIntent))
        }
        _state.value = current.copy(applying = true, applyingApns = apnIntent != null,
            error = null, notice = null, applied = false, historySaveFailed = false, broadnetApnResult = null)
        viewModelScope.launch {
            val results = mutableListOf<TargetConfigSnapshot>()
            var apnResult: BroadnetApnResult? = null
            var historySaveFailed = false
            var failureMessage: String? = null
            try {
                ConfigurationOperations.run {
                    if (!canStart()) return@run
                    if (current.subId == -1) {
                        val activeIds = ShizukuProvider.readSimInfoList(app).map { it.subId }.toSet()
                        check(activeIds == current.snapshots.map { it.subId }.toSet()) {
                            app.getString(R.string.ims_sim_list_changed)
                        }
                    }
                    if (apnIntent != null) {
                        val result = ShizukuProvider.ensureBroadnetApns(app, apnIntent.subId, apnIntent.identity, canStart)
                        apnResult = if (result.subId == apnIntent.subId && result.identity == apnIntent.identity) result
                            else BroadnetApnResult(apnIntent.subId, apnIntent.identity, BroadnetApnState.UNKNOWN,
                                error = "TARGET_RESPONSE_MISMATCH")
                        if (request == generation) _state.value = _state.value.copy(
                            broadnetApnResult = apnResult, applyingApns = false)
                        if (apnResult?.success != true || !canStart()) return@run
                    }
                    // APN 可单独成为待办；没有蜂窝目标时不构造空 CarrierConfig 写入。
                    if (targets.isEmpty()) return@run
                    for (snapshot in current.snapshots) {
                        if (!canStart()) break
                        val response = ShizukuProvider.applyTargetConfig(app, snapshot.subId, snapshot.identity, targets, canStart)
                        val result = if (response.error != null ||
                            (response.subId == snapshot.subId && response.identity == snapshot.identity)) response
                            else response.copy(error = "SIM identity changed during configuration apply")
                        results += result
                        if (result.error == null) {
                            try {
                                repository.saveTargets(snapshot.subId, targets, result.identity, result.historyValues)
                                repository.markManualApply(listOf(snapshot.subId))
                            } catch (failure: Exception) {
                                historySaveFailed = true
                                Log.e("ImsConfigViewModel", "Verified configuration history save failed for subId=${snapshot.subId}", failure)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("ImsConfigViewModel", "Configuration apply failed", e)
                failureMessage = e.message
                if (apnIntent != null && apnResult == null) {
                    apnResult = BroadnetApnResult(apnIntent.subId, apnIntent.identity, BroadnetApnState.UNKNOWN,
                        error = "CLIENT_OPERATION_FAILED")
                }
            } finally {
                // 已派发的操作继续清理与记录；异常或取消也必须收束当前界面的忙碌状态。
                if (request == generation) {
                    val finished = finishedTargetApplyState(current, results, apnResult, historySaveFailed)
                    val errors = buildList {
                        if (!finished.applied) add(app.getString(when {
                            apnIntent != null && apnResult?.success != true -> when (apnResult?.state) {
                                BroadnetApnState.CONFLICT -> R.string.broadnet_apn_conflict
                                BroadnetApnState.FAILED -> R.string.broadnet_apn_failed
                                else -> R.string.broadnet_apn_unknown
                            }
                            apnIntent != null -> R.string.broadnet_apply_partial
                            else -> R.string.ims_apply_incomplete
                        }))
                        // 保留普通配置的逐卡错误；APN 的内部错误码不直接面向用户。
                        results.filter { it.error != null }.forEach { add("SIM ${it.subId}: ${it.error}") }
                        if (apnIntent == null || apnResult?.success == true) {
                            failureMessage?.takeIf { it.isNotBlank() }?.let(::add)
                        }
                        if (historySaveFailed) add(app.getString(R.string.ims_history_save_failed))
                    }
                    _state.value = finished.copy(error = errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                } else if (apnIntent != null) {
                    apnResult?.let { _state.value = retainBroadnetApnResult(_state.value, apnIntent, it) }
                }
            }
        }
    }
}
