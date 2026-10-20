package app.mystery0.ims.tensor.model

import androidx.compose.runtime.Immutable

@Immutable
data class ImsCapabilityStatus(
    val isRegistered: Boolean? = null,
    val isVolteAvailable: Boolean? = null,
    val isVoWifiAvailable: Boolean? = null,
    val isVoNrAvailable: Boolean? = null,
    val isVtAvailable: Boolean? = null,
    val nr: NrDiagnostics = NrDiagnostics(),
    val homePlmn: String? = null,
)
