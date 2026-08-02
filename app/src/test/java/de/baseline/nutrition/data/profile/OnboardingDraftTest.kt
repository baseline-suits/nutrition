package de.baseline.nutrition.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingDraftTest {
    @Test
    fun newDraftDoesNotExposePersonalOrNutritionDemoValues() {
        val draft = OnboardingDraft()

        assertEquals("", draft.birthDate)
        assertEquals("", draft.height)
        assertEquals("", draft.weight)
        assertEquals("", draft.biologicalInput)
        assertEquals("", draft.activity)
        assertEquals("", draft.direction)
        assertEquals("", draft.kcal)
        assertEquals("", draft.protein)
        assertEquals("", draft.carbs)
        assertEquals("", draft.fat)
    }
}
