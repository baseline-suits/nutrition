package de.baseline.nutrition.ui.health

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import de.baseline.nutrition.R
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.theme.BaselineSpacing

@Composable
fun HealthConnectScreen(
    viewModel: HealthConnectViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val pendingRequest = state.pendingPermissionRequest
    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        pendingRequest?.let { viewModel.onPermissionResult(it, granted) }
    }
    LaunchedEffect(pendingRequest) {
        pendingRequest?.let { launcher.launch(it.permissions) }
    }
    HealthConnectContent(
        state = state,
        onRequest = viewModel::requestPermission,
        onSetEnabled = viewModel::setEnabled,
        onRead = viewModel::read,
        onRefresh = viewModel::refresh,
        onDisconnect = viewModel::disconnect,
        onClose = onClose,
    )
}

@Composable
internal fun HealthConnectContent(
    state: HealthConnectUiState,
    onRequest: (HealthDataType) -> Unit,
    onSetEnabled: (HealthDataType, Boolean) -> Unit,
    onRead: (HealthDataType) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.health_connect_title), fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(BaselineSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
            ) {
                Text(stringResource(R.string.health_connect_explanation), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.health_connect_privacy))
                Text(stringResource(R.string.health_connect_optional))
            }
        }
        if (state.loading && state.permissions.isEmpty()) {
            Text(stringResource(R.string.health_connect_checking))
        } else when (state.availability) {
            HealthAvailability.NotInstalled -> AvailabilityCard(
                text = stringResource(R.string.health_connect_not_installed),
                action = stringResource(R.string.health_connect_install),
                onAction = { openProviderStore(context) },
            )
            HealthAvailability.UpdateRequired -> AvailabilityCard(
                text = stringResource(R.string.health_connect_update_required),
                action = stringResource(R.string.health_connect_update),
                onAction = { openProviderStore(context) },
            )
            HealthAvailability.Unavailable -> AvailabilityCard(
                text = stringResource(R.string.health_connect_unavailable),
                action = null,
                onAction = {},
            )
            HealthAvailability.Available -> HealthDataType.entries.forEach { type ->
                HealthCapabilityCard(
                    type = type,
                    permission = state.permissions[type] ?: HealthPermissionState.NotRequested,
                    read = state.reads[type],
                    onRequest = { onRequest(type) },
                    onEnable = { onSetEnabled(type, true) },
                    onDisable = { onSetEnabled(type, false) },
                    onRead = { onRead(type) },
                    onOpenSettings = {
                        context.startActivity(HealthConnectClient.getHealthConnectManageDataIntent(context))
                    },
                )
            }
        }
        if (state.error) Text(stringResource(R.string.health_connect_error))
        Row(horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small)) {
            OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) }
            if (state.availability == HealthAvailability.Available) {
                OutlinedButton(onClick = onDisconnect, enabled = !state.disconnecting) {
                    Text(stringResource(R.string.health_connect_disconnect))
                }
            }
        }
    }
}

@Composable
private fun HealthCapabilityCard(
    type: HealthDataType,
    permission: HealthPermissionState,
    read: HealthTypeReadState?,
    onRequest: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRead: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("health-${type.name}")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(stringResource(healthTypeLabel(type)), fontWeight = FontWeight.Bold)
            Text(stringResource(healthTypeDescription(type)))
            Text(stringResource(permissionLabel(permission)))
            when (permission) {
                HealthPermissionState.Granted -> Row(
                    horizontalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
                ) {
                    Button(
                        onClick = onRead,
                        enabled = read?.loading != true,
                        modifier = Modifier.testTag("health-read-${type.name}"),
                    ) {
                        Text(stringResource(R.string.health_connect_read_now))
                    }
                    OutlinedButton(
                        onClick = onDisable,
                        modifier = Modifier.testTag("health-disable-${type.name}"),
                    ) {
                        Text(stringResource(R.string.health_connect_disable))
                    }
                }
                HealthPermissionState.Disabled -> Button(
                    onClick = onEnable,
                    modifier = Modifier.testTag("health-enable-${type.name}"),
                ) {
                    Text(stringResource(R.string.health_connect_enable))
                }
                HealthPermissionState.PermanentlyDenied -> Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("health-settings-${type.name}"),
                ) {
                    Text(stringResource(R.string.health_connect_open_settings))
                }
                else -> Button(
                    onClick = onRequest,
                    modifier = Modifier.testTag("health-request-${type.name}"),
                ) {
                    Text(
                        stringResource(
                            if (permission == HealthPermissionState.NotRequested) {
                                R.string.health_connect_allow
                            } else {
                                R.string.retry
                            },
                        ),
                    )
                }
            }
            read?.let { HealthReadStatus(it) }
        }
    }
}

@Composable
private fun HealthReadStatus(read: HealthTypeReadState) {
    HorizontalDivider()
    val message = when {
        read.loading -> stringResource(R.string.health_connect_reading)
        read.error -> stringResource(R.string.health_connect_read_error)
        read.truncated -> stringResource(R.string.health_connect_too_many_records)
        !read.cursorCommitted -> stringResource(
            R.string.health_connect_sync_partial,
            read.rejectedCount,
        )
        read.recordCount == 0 -> stringResource(R.string.health_connect_empty)
        else -> stringResource(
            R.string.health_connect_data_ready,
            read.recordCount ?: 0,
            read.sourceCount ?: 0,
        )
    }
    Text(message, modifier = Modifier.testTag("health-read-status"))
}

@Composable
private fun AvailabilityCard(text: String, action: String?, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("health-availability")) {
        Column(
            Modifier.padding(BaselineSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(BaselineSpacing.small),
        ) {
            Text(text)
            action?.let { Button(onClick = onAction) { Text(it) } }
        }
    }
}

private fun healthTypeLabel(type: HealthDataType): Int = when (type) {
    HealthDataType.Steps -> R.string.health_type_steps
    HealthDataType.Sleep -> R.string.health_type_sleep
    HealthDataType.ActiveCalories -> R.string.health_type_active_calories
    HealthDataType.Exercise -> R.string.health_type_exercise
    HealthDataType.Weight -> R.string.health_type_weight
}

private fun healthTypeDescription(type: HealthDataType): Int = when (type) {
    HealthDataType.Steps -> R.string.health_type_steps_description
    HealthDataType.Sleep -> R.string.health_type_sleep_description
    HealthDataType.ActiveCalories -> R.string.health_type_active_calories_description
    HealthDataType.Exercise -> R.string.health_type_exercise_description
    HealthDataType.Weight -> R.string.health_type_weight_description
}

private fun permissionLabel(state: HealthPermissionState): Int = when (state) {
    HealthPermissionState.NotRequested -> R.string.health_permission_not_requested
    HealthPermissionState.Granted -> R.string.health_permission_granted
    HealthPermissionState.Disabled -> R.string.health_permission_disabled
    HealthPermissionState.Denied -> R.string.health_permission_denied
    HealthPermissionState.PermanentlyDenied -> R.string.health_permission_permanently_denied
    HealthPermissionState.Revoked -> R.string.health_permission_revoked
}

private fun openProviderStore(context: android.content.Context) {
    val packageName = "com.google.android.apps.healthdata"
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val fallback = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
    )
    context.startActivity(if (market.resolveActivity(context.packageManager) != null) market else fallback)
}
