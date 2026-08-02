package de.baseline.nutrition.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import de.baseline.nutrition.R
import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.network.ServerSettingsStore
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.ui.theme.BaselineSpacing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    repository: AuthRepository,
    serverSettings: ServerSettingsStore,
    ioDispatcher: CoroutineDispatcher,
    onAuthenticated: () -> Unit,
) {
    var registration by rememberSaveable { mutableStateOf(false) }
    var accessCode by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var serverUrl by rememberSaveable { mutableStateOf(serverSettings.currentUrl()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverConnected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val genericError = stringResource(R.string.auth_error_generic)
    val invalidError = stringResource(R.string.auth_error_invalid)
    val invalidServerError = stringResource(R.string.server_url_invalid)
    val unreachableServerError = stringResource(R.string.server_unreachable)
    val locale = LocalConfiguration.current.locales[0].language.let { if (it == "ru") "ru" else "de" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Text(stringResource(if (registration) R.string.register_title else R.string.login_title))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; serverConnected = false },
            label = { Text(stringResource(R.string.server_url)) },
            supportingText = { Text(stringResource(R.string.server_url_hint)) },
            enabled = !loading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Button(
            enabled = !loading,
            onClick = {
                val saved = runCatching { serverSettings.save(serverUrl) }
                    .onFailure { error = invalidServerError }
                    .getOrNull() ?: return@Button
                serverUrl = saved
                loading = true
                error = null
                scope.launch {
                    runCatching { withContext(ioDispatcher) { repository.checkServer() } }
                        .onSuccess { serverConnected = true }
                        .onFailure { error = unreachableServerError }
                    loading = false
                }
            },
        ) {
            Text(stringResource(R.string.test_server_connection))
        }
        if (serverConnected) Text(stringResource(R.string.server_connected))
        if (registration) {
            OutlinedTextField(
                value = accessCode,
                onValueChange = { accessCode = it },
                label = { Text(stringResource(R.string.access_code)) },
                enabled = !loading,
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username)) },
            enabled = !loading,
            singleLine = true,
            modifier = Modifier.semantics { contentType = ContentType.Username },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            enabled = !loading,
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.semantics { contentType = ContentType.Password },
        )
        TextButton(onClick = { passwordVisible = !passwordVisible }) {
            Text(stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password))
        }
        error?.let { Text(it) }
        Button(
            enabled = !loading && username.length >= 3 && password.length >= 10 &&
                (!registration || accessCode.length >= 16),
            onClick = {
                val saved = runCatching { serverSettings.save(serverUrl) }
                    .onFailure { error = invalidServerError }
                    .getOrNull() ?: return@Button
                serverUrl = saved
                loading = true
                error = null
                scope.launch {
                    runCatching {
                        withContext(ioDispatcher) {
                            if (registration) repository.register(accessCode, username, password, locale)
                            else repository.login(username, password)
                        }
                    }.onSuccess {
                        onAuthenticated()
                    }.onFailure {
                        error = if (it is ApiException && it.status in 400..499) invalidError else genericError
                    }
                    loading = false
                }
            },
        ) {
            if (loading) CircularProgressIndicator()
            else Text(stringResource(if (registration) R.string.register_action else R.string.login_action))
        }
        TextButton(onClick = { registration = !registration; error = null }, enabled = !loading) {
            Text(stringResource(if (registration) R.string.switch_to_login else R.string.switch_to_register))
        }
    }
}
