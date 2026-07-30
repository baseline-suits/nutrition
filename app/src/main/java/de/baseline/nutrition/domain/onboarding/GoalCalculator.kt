package de.baseline.nutrition.domain.onboarding

import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

data class GoalResult(
    val basalKcal: Int,
    val maintenanceKcal: Int,
    val targetKcal: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val formulaVersion: String = "mifflin-st-jeor-v1",
)

object GoalCalculator {
    fun calculate(
        birthDate: LocalDate,
        biologicalInput: String,
        heightCm: Double,
        weightKg: Double,
        activityLevel: String,
        goalDirection: String,
        today: LocalDate = LocalDate.now(),
    ): GoalResult {
        require(heightCm in 80.0..260.0 && weightKg in 25.0..400.0)
        val age = Period.between(birthDate, today).years
        require(age in 13..120)
        val basal = (10 * weightKg + 6.25 * heightCm - 5 * age +
            if (biologicalInput == "male") 5 else -161).roundToInt()
        val activity = mapOf(
            "inactive" to 1.2,
            "sometimes" to 1.375,
            "active" to 1.55,
            "very_active" to 1.725,
        ).getValue(activityLevel)
        val maintenance = (basal * activity).roundToInt()
        val adjustment = when {
            age < 18 -> 0
            goalDirection == "deficit" -> -300
            goalDirection == "surplus" -> 250
            else -> 0
        }
        val target = (maintenance + adjustment).coerceAtLeast(1200)
        val protein = (weightKg * 1.6).roundToInt()
        val fat = (weightKg * 0.8).roundToInt()
        val carbs = ((target - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return GoalResult(basal, maintenance, target, protein, carbs, fat)
    }
}

