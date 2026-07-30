package de.baseline.nutrition.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.baseline.nutrition.R
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.ui.theme.BaselineSpacing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountHomeScreen(
    repository: AuthRepository,
    ioDispatcher: CoroutineDispatcher,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun logout(allDevices: Boolean) {
        scope.launch {
            withContext(ioDispatcher) { repository.logout(allDevices) }
            onLoggedOut()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(BaselineSpacing.large),
        verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
    ) {
        Text(stringResource(R.string.diary_title))
        Text(stringResource(R.string.diary_description))
        Button(onClick = { logout(false) }) {
            Text(stringResource(R.string.logout_this_device))
        }
        TextButton(onClick = { logout(true) }) {
            Text(stringResource(R.string.logout_all_devices))
        }
    }
}

