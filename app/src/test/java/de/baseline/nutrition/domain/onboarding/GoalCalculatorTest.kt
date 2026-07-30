package de.baseline.nutrition.domain.onboarding

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalCalculatorTest {
    @Test
    fun `berechnung ist reproduzierbar und versioniert`() {
        val result = GoalCalculator.calculate(
            birthDate = LocalDate.of(1990, 1, 1),
            biologicalInput = "female",
            heightCm = 170.0,
            weightKg = 65.0,
            activityLevel = "active",
            goalDirection = "deficit",
            today = LocalDate.of(2026, 7, 31),
        )

        assertEquals("mifflin-st-jeor-v1", result.formulaVersion)
        assertEquals(1827, result.targetKcal)
        assertEquals(104, result.proteinGrams)
    }

    @Test
    fun `minderjaehrige erhalten kein automatisches defizit`() {
        val maintain = GoalCalculator.calculate(
            LocalDate.of(2010, 1, 1), "male", 170.0, 65.0, "sometimes", "maintain",
            LocalDate.of(2026, 7, 31),
        )
        val deficit = GoalCalculator.calculate(
            LocalDate.of(2010, 1, 1), "male", 170.0, 65.0, "sometimes", "deficit",
            LocalDate.of(2026, 7, 31),
        )

        assertEquals(maintain.targetKcal, deficit.targetKcal)
    }
}
