package de.baseline.nutrition.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.baseline.nutrition.R
import de.baseline.nutrition.ui.theme.BaselineSpacing
import de.baseline.nutrition.ui.theme.GlassSurface

@Composable
fun LoadingScreen() {
    val loadingDescription = stringResource(R.string.loading_description)
    StateScreen(
        title = R.string.loading_title,
        description = R.string.loading_description,
        testTag = "loading_state",
        leading = {
            CircularProgressIndicator(
                modifier = Modifier.semantics {
                    contentDescription = loadingDescription
                },
            )
        },
    )
}

@Composable
fun LoggedOutScreen() {
    StateScreen(
        title = R.string.logged_out_title,
        description = R.string.logged_out_description,
        testTag = "logged_out_state",
    )
}

@Composable
fun OnboardingScreen() {
    StateScreen(
        title = R.string.continue_setup_title,
        description = R.string.continue_setup_description,
        testTag = "onboarding_state",
    )
}

@Composable
fun AuthenticatedScreen() {
    StateScreen(
        title = R.string.diary_title,
        description = R.string.diary_description,
        testTag = "authenticated_state",
    )
}

@Composable
fun TechnicalErrorScreen(onRetry: () -> Unit) {
    StateScreen(
        title = R.string.technical_error_title,
        description = R.string.technical_error_description,
        testTag = "technical_error_state",
        action = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        },
    )
}

@Composable
private fun StateScreen(
    @StringRes title: Int,
    @StringRes description: Int,
    testTag: String,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(BaselineSpacing.large)
                .testTag(testTag),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BaselineSpacing.medium),
                horizontalAlignment = Alignment.Start,
            ) {
                leading?.invoke()
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                action?.invoke()
            }
        }
    }
}
