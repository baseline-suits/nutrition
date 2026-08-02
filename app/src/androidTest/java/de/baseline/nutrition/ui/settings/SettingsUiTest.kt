package de.baseline.nutrition.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import de.baseline.nutrition.data.settings.SettingsAccountDto
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.data.settings.SettingsHealthSourceDto
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthCapability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.ui.LocaleController
import de.baseline.nutrition.ui.theme.BaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsAllCentralSectionsAndConfirmsDangerousActions() {
        var logoutAll: Boolean? = null
        compose.setContent {
            BaselineTheme {
                SettingsContent(
                    state = readyState(),
                    onDraft = {},
                    onCalculate = {},
                    onSaveProfile = {},
                    onLocale = {},
                    onOpenBudget = {},
                    onOpenHealth = {},
                    onSync = {},
                    onDisconnectHealth = {},
                    onSource = { _, _ -> },
                    onReloadCaches = {},
                    onLogout = { logoutAll = it },
                    onDeleteAccount = {},
                    onRefresh = {},
                    onDismissMessage = {},
                    onClose = {},
                    onAccountDeletionFinished = {},
                )
            }
        }

        listOf(
            "settings-language",
            "settings-profile",
            "settings-health-sync",
            "settings-sync-section",
            "settings-privacy",
            "settings-account",
        ).forEach { compose.onNodeWithTag(it).assertExists() }

        compose.onNodeWithTag("settings-account-open").performScrollTo().performClick()
        compose.onNodeWithTag("settings-danger").assertExists()
        compose.onNodeWithTag("settings-logout-all").performScrollTo().performClick()
        compose.onNodeWithTag("settings-confirm-logout-all").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, logoutAll) }

        compose.onNodeWithTag("settings-delete-account").performScrollTo().performClick()
        compose.onNodeWithTag("confirm-delete-account").assertIsDisplayed()
    }

    @Test
    fun runningOperationDisablesProfileAndDangerChoices() {
        compose.setContent {
            BaselineTheme {
                SettingsContent(
                    state = readyState().copy(operation = SettingsOperation.Profile),
                    onDraft = {},
                    onCalculate = {},
                    onSaveProfile = {},
                    onLocale = {},
                    onOpenBudget = {},
                    onOpenHealth = {},
                    onSync = {},
                    onDisconnectHealth = {},
                    onSource = { _, _ -> },
                    onReloadCaches = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onRefresh = {},
                    onDismissMessage = {},
                    onClose = {},
                    onAccountDeletionFinished = {},
                )
            }
        }

        compose.onNodeWithTag("settings-open-goals").assertIsNotEnabled()
        compose.onNodeWithTag("settings-open-health").assertIsNotEnabled()
        compose.onNodeWithTag("settings-sync-section").assertIsNotEnabled()
        compose.onNodeWithTag("settings-account-open").assertIsNotEnabled()
        compose.onNodeWithTag("settings-logout-device").assertIsNotEnabled()
    }

    @Test
    fun healthConflictOffersExplicitSourceChoice() {
        compose.setContent {
            BaselineTheme {
                SettingsContent(
                    state = readyState().copy(
                        healthConnection = HealthConnectionSnapshot(
                            HealthAvailability.Available,
                            mapOf(
                                HealthDataType.Steps to HealthCapability(
                                    HealthDataType.Steps,
                                    HealthPermissionState.Granted,
                                ),
                            ),
                        ),
                        healthAggregates = listOf(
                            SettingsHealthAggregateDto(
                                dataType = "steps",
                                localDay = "2026-08-01",
                                status = "conflict",
                                unit = "count",
                                sources = listOf(
                                    source("com.example.watch"),
                                    source("com.example.phone"),
                                ),
                            ),
                        ),
                    ),
                    onDraft = {},
                    onCalculate = {},
                    onSaveProfile = {},
                    onLocale = {},
                    onOpenBudget = {},
                    onOpenHealth = {},
                    onSync = {},
                    onDisconnectHealth = {},
                    onSource = { _, _ -> },
                    onReloadCaches = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onRefresh = {},
                    onDismissMessage = {},
                    onClose = {},
                    onAccountDeletionFinished = {},
                )
            }
        }

        compose.onNodeWithTag("settings-sync-section").performScrollTo().performClick()
        compose.onNodeWithTag("settings-health-status").assertExists()
        compose.onNodeWithTag("settings-source-steps-automatic").assertExists()
        compose.onNodeWithTag("settings-source-steps-com.example.watch").assertExists()
    }

    @Test
    fun completedAccountDeletionLeavesAuthenticatedArea() {
        var finished = false
        compose.setContent {
            BaselineTheme {
                SettingsContent(
                    state = readyState().copy(accountDeletionStatus = AccountDeletionStatus.Completed),
                    onDraft = {},
                    onCalculate = {},
                    onSaveProfile = {},
                    onLocale = {},
                    onOpenBudget = {},
                    onOpenHealth = {},
                    onSync = {},
                    onDisconnectHealth = {},
                    onSource = { _, _ -> },
                    onReloadCaches = {},
                    onLogout = {},
                    onDeleteAccount = {},
                    onRefresh = {},
                    onDismissMessage = {},
                    onClose = {},
                    onAccountDeletionFinished = { finished = true },
                )
            }
        }

        compose.onNodeWithTag("account-deletion-finished").performClick()
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test
    fun languageChangeUsesPersistentAppLocaleStore() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, "androidx.appcompat.app.AppLocalesMetadataHolderService"),
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertTrue(service.metaData.getBoolean("autoStoreLocales"))

        ActivityScenario.launch(LocaleTestActivity::class.java).use { scenario ->
            instrumentation.runOnMainSync { LocaleController.apply("ru") }
            compose.waitUntil(5_000) {
                AppCompatDelegate.getApplicationLocales().toLanguageTags() == "ru"
            }

            scenario.recreate()
            compose.waitUntil(5_000) {
                AppCompatDelegate.getApplicationLocales().toLanguageTags() == "ru"
            }
            assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())

            instrumentation.runOnMainSync { LocaleController.apply("de") }
            compose.waitUntil(5_000) {
                AppCompatDelegate.getApplicationLocales().toLanguageTags() == "de"
            }
        }
    }

    private fun readyState() = SettingsUiState(
        account = SettingsAccountDto(
            id = "user-1",
            username = "settings-user",
            locale = "de",
            timezone = "Europe/Berlin",
            onboardingComplete = true,
        ),
        profileDraft = SettingsProfileDraft(
            manual = true,
            targetKcal = "2000",
            targetProtein = "120",
            targetCarbs = "220",
            targetFat = "70",
            timezone = "Europe/Berlin",
            expectedUpdatedAt = "2026-08-01T10:00:00Z",
        ),
        operation = null,
        appDetails = SettingsAppDetails("0.1.0-debug", 1, "local"),
    )

    private fun source(packageName: String) = SettingsHealthSourceDto(
        originPackage = packageName,
        recordCount = 1,
        overlapDetected = false,
        selected = false,
    )
}
