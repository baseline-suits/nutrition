package de.baseline.nutrition.ui.history

import de.baseline.nutrition.data.diary.HistoryAggregateDto
import de.baseline.nutrition.data.diary.HistoryDataSource
import de.baseline.nutrition.data.diary.HistoryLoad
import de.baseline.nutrition.data.diary.HistoryResponseDto
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun changesRangeAndExposesCachedFallback() = runTest(dispatcher) {
        val source = FakeHistoryDataSource()
        val viewModel = HistoryViewModel(source, dispatcher)
        advanceUntilIdle()

        assertEquals(listOf(7), source.requests)
        assertFalse(viewModel.state.value.cached)

        source.cached = true
        viewModel.setRange(30)
        advanceUntilIdle()

        assertEquals(listOf(7, 30), source.requests)
        assertEquals(30, viewModel.state.value.rangeDays)
        assertTrue(viewModel.state.value.cached)
        assertFalse(viewModel.state.value.error)
    }

    @Test
    fun keepsFailureVisibleWhenNoCachedRangeExists() = runTest(dispatcher) {
        val source = FakeHistoryDataSource(fail = true)
        val viewModel = HistoryViewModel(source, dispatcher)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.error)
        assertFalse(viewModel.state.value.loading)
    }
}

private class FakeHistoryDataSource(
    var cached: Boolean = false,
    private val fail: Boolean = false,
) : HistoryDataSource {
    val requests = mutableListOf<Int>()

    override suspend fun history(days: Int): HistoryLoad {
        requests += days
        if (fail) error("offline")
        return HistoryLoad(response(days), cached)
    }

    private fun response(days: Int) = HistoryResponseDto(
        start = "2026-07-01",
        end = "2026-07-07",
        totalDays = days,
        days = emptyList(),
        summary = HistoryAggregateDto(
            trackedDays = 0,
            completeDays = 0,
            partialDays = 0,
            averages = emptyMap(),
            averageDenominators = emptyMap(),
            targetAverages = emptyMap(),
            targetDenominators = emptyMap(),
            goalPercentages = emptyMap(),
            goalDenominators = emptyMap(),
        ),
        weeks = emptyList(),
    )
}
