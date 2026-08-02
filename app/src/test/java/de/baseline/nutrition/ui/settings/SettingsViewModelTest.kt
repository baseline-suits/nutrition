package de.baseline.nutrition.ui.settings

import de.baseline.nutrition.data.network.ApiException
import de.baseline.nutrition.data.profile.ProfileRequest
import de.baseline.nutrition.data.settings.SettingsAccountDto
import de.baseline.nutrition.data.settings.SettingsDataSource
import de.baseline.nutrition.data.settings.SettingsHealthAggregateDto
import de.baseline.nutrition.data.settings.SettingsHealthStateDto
import de.baseline.nutrition.data.settings.SettingsLocalDataSource
import de.baseline.nutrition.data.settings.SettingsProfileDto
import de.baseline.nutrition.data.sync.MealSyncUiState
import de.baseline.nutrition.domain.auth.AccountDeletionStatus
import de.baseline.nutrition.domain.auth.AuthRepository
import de.baseline.nutrition.domain.health.HealthAvailability
import de.baseline.nutrition.domain.health.HealthCapability
import de.baseline.nutrition.domain.health.HealthConnectionSnapshot
import de.baseline.nutrition.domain.health.HealthDataType
import de.baseline.nutrition.domain.health.HealthPermissionRequest
import de.baseline.nutrition.domain.health.HealthPermissionState
import de.baseline.nutrition.domain.health.HealthReadResult
import de.baseline.nutrition.domain.health.HealthReadWindow
import de.baseline.nutrition.domain.health.HealthRepository
import de.baseline.nutrition.domain.health.HealthSyncResult
import de.baseline.nutrition.domain.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsAccountProfileHealthAndSyncState() = runTest(dispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()

        advanceUntilIdle()

        assertEquals("settings-user", viewModel.state.value.account?.username)
        assertEquals("2000", viewModel.state.value.profileDraft.targetKcal)
        assertEquals(HealthAvailability.Unavailable, viewModel.state.value.healthConnection?.availability)
        assertNull(viewModel.state.value.operation)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf("de"), fixture.appliedLocales)
    }

    @Test
    fun localeChangesOnlyAfterServerConfirmation() = runTest(dispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        fixture.appliedLocales.clear()

        viewModel.setLocale("ru")
        advanceUntilIdle()

        assertEquals(listOf("ru"), fixture.remote.localeRequests)
        assertEquals("ru", viewModel.state.value.account?.locale)
        assertEquals(listOf("ru"), fixture.appliedLocales)

        fixture.remote.localeFailure = ApiException(503, "network_error")
        viewModel.setLocale("de")
        advanceUntilIdle()

        assertEquals("ru", viewModel.state.value.account?.locale)
        assertEquals(listOf("ru"), fixture.appliedLocales)
        assertEquals(SettingsError.Network, viewModel.state.value.error)
    }

    @Test
    fun profileSaveUsesExpectedVersionAndPreservesDraftOnConflict() = runTest(dispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        viewModel.updateProfileDraft(viewModel.state.value.profileDraft.copy(targetKcal = "2100"))
        fixture.remote.confirmedProfile = fixture.remote.confirmedProfile.copy(
            targetKcal = "2100",
            updatedAt = "2026-08-01T10:01:00Z",
        )
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("2026-08-01T10:00:00Z", fixture.remote.profileRequests.single().expectedUpdatedAt)
        assertEquals("2100", viewModel.state.value.profile?.targetKcal)
        assertEquals("2026-08-01T10:01:00Z", viewModel.state.value.profileDraft.expectedUpdatedAt)

        viewModel.updateProfileDraft(viewModel.state.value.profileDraft.copy(targetKcal = "2200"))
        fixture.remote.profileFailure = ApiException(409, "profile_conflict")
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("2100", viewModel.state.value.profile?.targetKcal)
        assertEquals("2200", viewModel.state.value.profileDraft.targetKcal)
        assertEquals(SettingsError.Conflict, viewModel.state.value.error)
    }

    @Test
    fun manualSyncRunsMealsBeforeHealthAndRefreshesServerState() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val fixture = Fixture(events)
        fixture.health.connectionSnapshot = connection(HealthPermissionState.Granted)
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        events.clear()

        viewModel.syncNow()
        advanceUntilIdle()

        assertEquals(
            listOf("meals", "connection", "health-Steps", "meal-state", "health-state", "aggregates"),
            events,
        )
        assertEquals(SettingsNotice.Synced, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.healthSyncFailed)
    }

    @Test
    fun sourcePreferenceLogoutAndAccountDeletionUseExplicitOperations() = runTest(dispatcher) {
        val fixture = Fixture()
        val viewModel = fixture.viewModel()
        advanceUntilIdle()

        viewModel.saveSourcePreference("steps", "com.example.health")
        advanceUntilIdle()
        assertEquals(listOf("steps" to "com.example.health"), fixture.remote.sourceRequests)
        assertEquals(SettingsNotice.SourceSaved, viewModel.state.value.notice)

        viewModel.logout(true)
        advanceUntilIdle()
        assertEquals(listOf(true), fixture.auth.logoutRequests)
        assertTrue(viewModel.state.value.loggedOut)

        val second = fixture.viewModel()
        advanceUntilIdle()
        second.deleteAccount("a-secure-password")
        advanceUntilIdle()
        assertEquals(listOf("a-secure-password"), fixture.auth.deleteRequests)
        assertEquals(AccountDeletionStatus.Completed, second.state.value.accountDeletionStatus)
    }

    private class Fixture(private val events: MutableList<String> = mutableListOf()) {
        val remote = FakeSettingsDataSource(events)
        val local = FakeSettingsLocalDataSource(events)
        val health = FakeHealthRepository(events)
        val auth = FakeAuthRepository()
        val appliedLocales = mutableListOf<String>()

        fun viewModel() = SettingsViewModel(
            remote = remote,
            local = local,
            healthRepository = health,
            authRepository = auth,
            ioDispatcher = StandardTestDispatcher(),
            applyLocale = appliedLocales::add,
            appDetails = SettingsAppDetails("0.1.0-debug", 1, "local"),
        )
    }

    private companion object {
        fun connection(permission: HealthPermissionState? = null): HealthConnectionSnapshot =
            HealthConnectionSnapshot(
                availability = if (permission == null) HealthAvailability.Unavailable else HealthAvailability.Available,
                capabilities = if (permission == null) emptyMap() else mapOf(
                    HealthDataType.Steps to HealthCapability(HealthDataType.Steps, permission),
                ),
            )
    }
}

private class FakeSettingsDataSource(private val events: MutableList<String>) : SettingsDataSource {
    val localeRequests = mutableListOf<String>()
    val profileRequests = mutableListOf<ProfileRequest>()
    val sourceRequests = mutableListOf<Pair<String, String?>>()
    var localeFailure: Exception? = null
    var profileFailure: Exception? = null
    var confirmedProfile = fixtureProfile()

    override suspend fun account() = SettingsAccountDto(
        id = "user-1",
        username = "settings-user",
        locale = "de",
        timezone = "Europe/Berlin",
        onboardingComplete = true,
    )

    override suspend fun profile(): SettingsProfileDto = confirmedProfile

    override suspend fun saveProfile(profile: ProfileRequest): SettingsProfileDto {
        profileRequests += profile
        profileFailure?.let { throw it }
        return confirmedProfile
    }

    override suspend fun saveLocale(locale: String): String {
        localeRequests += locale
        localeFailure?.let { throw it }
        return locale
    }

    override suspend fun healthState(): SettingsHealthStateDto {
        events += "health-state"
        return SettingsHealthStateDto()
    }

    override suspend fun healthAggregates(start: String, end: String): List<SettingsHealthAggregateDto> {
        events += "aggregates"
        return emptyList()
    }

    override suspend fun saveSourcePreference(dataType: String, originPackage: String) {
        sourceRequests += dataType to originPackage
    }

    override suspend fun clearSourcePreference(dataType: String) {
        sourceRequests += dataType to null
    }

    companion object {
        private fun fixtureProfile() = SettingsProfileDto(
            userId = "user-1",
            targetKcal = "2000",
            targetProtein = "120",
            targetCarbs = "220",
            targetFat = "70",
            targetsManual = 1,
            updatedAt = "2026-08-01T10:00:00Z",
            locale = "de",
            timezone = "Europe/Berlin",
        )
    }
}

private class FakeSettingsLocalDataSource(private val events: MutableList<String>) : SettingsLocalDataSource {
    override suspend fun mealSyncState(): MealSyncUiState {
        events += "meal-state"
        return MealSyncUiState()
    }

    override suspend fun syncMeals() {
        events += "meals"
    }

    override suspend fun reloadCaches() = Unit
}

private class FakeHealthRepository(private val events: MutableList<String>) : HealthRepository {
    var connectionSnapshot = HealthConnectionSnapshot(HealthAvailability.Unavailable, emptyMap())

    override suspend fun connection(): HealthConnectionSnapshot {
        events += "connection"
        return connectionSnapshot
    }

    override fun permissionRequest(type: HealthDataType) = HealthPermissionRequest(type, emptySet())
    override suspend fun recordPermissionResult(request: HealthPermissionRequest, granted: Set<String>) = Unit
    override suspend fun setEnabled(type: HealthDataType, enabled: Boolean) = Unit
    override suspend fun read(type: HealthDataType, window: HealthReadWindow) =
        HealthReadResult(type, emptyList(), emptyList(), false)

    override suspend fun sync(type: HealthDataType, window: HealthReadWindow): HealthSyncResult {
        events += "health-${type.name}"
        return HealthSyncResult(type, 0, 0, false, true, 0, 0)
    }

    override suspend fun disconnect() = Unit
}

private class FakeAuthRepository : AuthRepository {
    val logoutRequests = mutableListOf<Boolean>()
    val deleteRequests = mutableListOf<String>()

    override suspend fun restore(): SessionState = SessionState.Authenticated
    override suspend fun checkServer() = Unit
    override suspend fun login(username: String, password: String) = Unit
    override suspend fun register(accessCode: String, username: String, password: String, locale: String) = Unit

    override suspend fun logout(allDevices: Boolean) {
        logoutRequests += allDevices
    }

    override suspend fun deleteAccount(password: String): AccountDeletionStatus {
        deleteRequests += password
        return AccountDeletionStatus.Completed
    }
}
