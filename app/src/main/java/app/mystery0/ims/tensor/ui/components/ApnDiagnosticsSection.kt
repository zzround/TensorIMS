package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.ApnAssessment
import app.mystery0.ims.tensor.model.ApnDiagnosticRow
import app.mystery0.ims.tensor.model.ApnDiagnostics
import app.mystery0.ims.tensor.model.ApnReadError
import app.mystery0.ims.tensor.model.ApnReadState
import app.mystery0.ims.tensor.model.ApnTypeKind
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.ui.openApnSettings

/** 仅展示系统匹配的候选配置和人工核对入口，不把数据库条目当作实际网络承载。 */
@Composable
internal fun ApnDiagnosticsSection(
    target: SimSelection,
    result: ApnDiagnostics?,
    loading: Boolean,
    canAct: Boolean,
    onCheck: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var settingsUnavailable by remember { mutableStateOf(false) }
    Text(stringResource(R.string.apn_diagnostics_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.apn_diagnostics_intro), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.apn_diagnostics_loading))
        }
    } else if (result != null) {
        Text(stringResource(when (result.assessment) {
            ApnAssessment.UNAVAILABLE -> R.string.apn_diagnostics_unavailable
            ApnAssessment.NOT_BROADNET -> R.string.apn_diagnostics_not_broadnet
            ApnAssessment.REFERENCE_IMS_FOUND -> R.string.apn_diagnostics_reference_found
            ApnAssessment.REVIEW_REQUIRED -> R.string.apn_diagnostics_review_required
            ApnAssessment.NO_IMS_OBSERVED -> R.string.apn_diagnostics_no_ims
        }), style = MaterialTheme.typography.bodyMedium)
        if (result.state == ApnReadState.UNAVAILABLE) {
            Text(stringResource(when (result.error) {
                ApnReadError.PERMISSION_DENIED -> R.string.apn_diagnostics_permission_denied
                ApnReadError.UNKNOWN_HOME_PLMN -> R.string.apn_diagnostics_unknown_home
                ApnReadError.SIM_CHANGED, ApnReadError.SIM_UNAVAILABLE -> R.string.apn_diagnostics_sim_changed
                else -> R.string.apn_diagnostics_read_failed
            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (result.state == ApnReadState.READ_OK) {
            val rows = result.rows.sortedBy {
                when {
                    it.typeKind == ApnTypeKind.OTHER -> 2
                    it.isReferenceIms -> 1
                    else -> 0
                }
            }
            Text(stringResource(R.string.apn_diagnostics_candidates, rows.size), style = MaterialTheme.typography.labelLarge)
            rows.take(8).forEach { row -> ApnRow(row) }
            if (rows.size > 8) Text(stringResource(R.string.apn_diagnostics_more_rows, rows.size - 8),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.apn_diagnostics_result_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(stringResource(R.string.apn_diagnostics_reference), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.apn_diagnostics_settings_note, target.showTitle),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { settingsUnavailable = false; onCheck() }, enabled = canAct) {
            Text(stringResource(R.string.apn_diagnostics_check))
        }
        TextButton(onClick = {
            onOpenSettings()
            settingsUnavailable = !openApnSettings(context, target.subId)
        }, enabled = canAct && result?.state != ApnReadState.NOT_BROADNET &&
            result?.error !in setOf(ApnReadError.SIM_CHANGED, ApnReadError.SIM_UNAVAILABLE,
                ApnReadError.UNKNOWN_HOME_PLMN)) {
            Text(stringResource(R.string.apn_diagnostics_open_settings))
        }
    }
    if (settingsUnavailable) Text(stringResource(R.string.apn_diagnostics_settings_unavailable),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ApnRow(row: ApnDiagnosticRow) {
    val empty = stringResource(R.string.apn_diagnostics_empty)
    val kind = stringResource(when (row.typeKind) {
        ApnTypeKind.IMS_ONLY -> R.string.apn_diagnostics_kind_ims
        ApnTypeKind.IMS_MIXED -> R.string.apn_diagnostics_kind_mixed
        ApnTypeKind.ALL -> R.string.apn_diagnostics_kind_all
        ApnTypeKind.OTHER -> R.string.apn_diagnostics_kind_other
        ApnTypeKind.UNRECOGNIZED -> R.string.apn_diagnostics_kind_unrecognized
    })
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.apn_diagnostics_row_apn, row.apn?.takeIf { it.isNotEmpty() } ?: empty),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.apn_diagnostics_row_types,
                row.types?.takeIf { it.isNotEmpty() } ?: empty, kind), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.apn_diagnostics_row_enabled, stringResource(when (row.carrierEnabled) {
                true -> R.string.apn_diagnostics_enabled
                false -> R.string.apn_diagnostics_disabled
                null -> R.string.nr_diagnostics_unknown
            })), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.apn_diagnostics_row_protocol,
                row.protocol?.takeIf { it.isNotEmpty() } ?: empty,
                row.roamingProtocol?.takeIf { it.isNotEmpty() } ?: empty), style = MaterialTheme.typography.bodySmall)
        }
    }
}
