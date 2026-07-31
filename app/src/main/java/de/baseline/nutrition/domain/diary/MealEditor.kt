package de.baseline.nutrition.domain.diary

import de.baseline.nutrition.data.diary.IngredientDto
import de.baseline.nutrition.data.diary.MealDto
import de.baseline.nutrition.data.diary.MealPayload
import de.baseline.nutrition.data.diary.NutrientDto
import de.baseline.nutrition.data.diary.PrivateFoodDto
import de.baseline.nutrition.data.diary.PrivateFoodPayload
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class NutrientFields(
    val energy: String = "",
    val protein: String = "",
    val carbohydrates: String = "",
    val fat: String = "",
    val additional: List<NutrientDto> = emptyList(),
    val sources: Map<String, String> = emptyMap(),
    val locks: Map<String, Boolean> = emptyMap(),
    val originalValues: Map<String, String> = emptyMap(),
) {
    fun value(key: String): String = when (key) {
        "energy" -> energy
        "protein" -> protein
        "carbohydrates" -> carbohydrates
        else -> fat
    }

    fun with(key: String, value: String): NutrientFields = when (key) {
        "energy" -> copy(energy = value)
        "protein" -> copy(protein = value)
        "carbohydrates" -> copy(carbohydrates = value)
        else -> copy(fat = value)
    }
}

data class IngredientDraft(
    val key: String = UUID.randomUUID().toString(),
    val name: String = "",
    val preparation: String = "",
    val amount: String = "100",
    val unit: String = "g",
    val nutrients: NutrientFields = NutrientFields(),
) {
    fun withScaledAmount(newAmount: String): IngredientDraft {
        val old = parseLocalizedDecimal(amount)
        val new = parseLocalizedDecimal(newAmount)
        if (old == null || new == null || old <= BigDecimal.ZERO || new <= BigDecimal.ZERO) {
            return copy(amount = newAmount)
        }
        val factor = new.divide(old, 12, RoundingMode.HALF_UP)
        fun scaled(value: String): String = parseLocalizedDecimal(value)?.multiply(factor)
            ?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString().orEmpty()
        return copy(
            amount = newAmount,
            nutrients = nutrients.copy(
                energy = scaled(nutrients.energy), protein = scaled(nutrients.protein),
                carbohydrates = scaled(nutrients.carbohydrates), fat = scaled(nutrients.fat),
                additional = nutrients.additional.map { nutrient ->
                    nutrient.copy(
                        value = scaled(nutrient.value),
                        source = "user",
                        locked = true,
                    )
                },
            ),
        )
    }
}

data class PrivateFoodEditorDraft(
    val id: String? = null,
    val version: Int? = null,
    val name: String = "",
    val brand: String = "",
    val amount: String = "100",
    val unit: String = "g",
    val basis: String = "100g",
    val nutrients: NutrientFields = NutrientFields(),
) {
    fun toPayload(): PrivateFoodPayload {
        val parsedAmount = parseLocalizedDecimal(amount)
        require(name.isNotBlank() && parsedAmount != null && parsedAmount > BigDecimal.ZERO)
        return PrivateFoodPayload(
            name = name.trim(), brand = brand.trim().ifBlank { null },
            defaultAmount = parsedAmount.canonical(), unit = unit, basis = basis,
            nutrients = nutrients.toDtos().map { it.copy(basis = basis) }, version = version,
        )
    }

    companion object {
        fun from(food: PrivateFoodDto) = PrivateFoodEditorDraft(
            id = food.id, version = food.version, name = food.name, brand = food.brand.orEmpty(),
            amount = food.defaultAmount, unit = food.unit, basis = food.basis,
            nutrients = food.nutrients.toFields(),
        )

        fun from(ingredient: IngredientDraft) = PrivateFoodEditorDraft(
            name = ingredient.name, amount = ingredient.amount, unit = ingredient.unit,
            basis = "portion", nutrients = ingredient.nutrients,
        )
    }
}

data class MealEditorDraft(
    val mealId: String? = null,
    val version: Int? = null,
    val clientId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val mealType: String = "snack",
    val day: String = LocalDate.now().toString(),
    val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val note: String = "",
    val nutrients: NutrientFields = NutrientFields(),
    val ingredients: List<IngredientDraft> = emptyList(),
    val captureMethod: String = "manual",
    val provenanceSource: String = "user",
    val externalReference: String? = null,
    val attachmentId: String? = null,
    val analysisWarnings: List<String> = emptyList(),
    val dirty: Boolean = false,
) {
    fun totals(): Map<String, BigDecimal?> = nutrientKeys.associateWith { key ->
        val values = buildList {
            add(nutrients.value(key))
            ingredients.forEach { add(it.nutrients.value(key)) }
        }.mapNotNull(::parseLocalizedDecimal)
        values.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    fun toPayload(zoneId: ZoneId = ZoneId.systemDefault()): MealPayload {
        require(name.isNotBlank()) { "name" }
        val parsedDay = LocalDate.parse(day)
        val parsedTime = LocalTime.parse(time)
        val ingredientDtos = ingredients.map { ingredient ->
            val amount = parseLocalizedDecimal(ingredient.amount)
            require(ingredient.name.isNotBlank() && amount != null && amount > BigDecimal.ZERO) { "ingredient" }
            IngredientDto(
                name = ingredient.name.trim(),
                preparation = ingredient.preparation.trim().ifBlank { null },
                amount = amount.canonical(),
                unit = ingredient.unit,
                nutrients = ingredient.nutrients.toDtos(),
            )
        }
        val directNutrients = nutrients.toDtos()
        require(ingredientDtos.isNotEmpty() || directNutrients.isNotEmpty()) { "nutrients" }
        return MealPayload(
            clientId = clientId,
            localDay = parsedDay.toString(),
            eatenAt = LocalDateTime.of(parsedDay, parsedTime).atZone(zoneId).toOffsetDateTime().toString(),
            timezone = zoneId.id,
            mealType = mealType,
            name = name.trim(),
            note = note.trim().ifBlank { null },
            ingredients = ingredientDtos,
            nutrients = directNutrients,
            captureMethod = captureMethod,
            provenanceSource = provenanceSource,
            externalReference = externalReference,
            attachmentId = attachmentId,
            version = version,
        )
    }

    companion object {
        fun from(meal: MealDto): MealEditorDraft {
            val eaten = java.time.OffsetDateTime.parse(meal.eatenAt)
            return MealEditorDraft(
                mealId = meal.id,
                version = meal.version,
                clientId = meal.clientId,
                name = meal.name,
                mealType = meal.mealType,
                day = meal.localDay,
                time = eaten.toLocalTime().withSecond(0).withNano(0).toString(),
                note = meal.note.orEmpty(),
                nutrients = meal.nutrients.toFields(),
                ingredients = meal.ingredients.map { ingredient ->
                    IngredientDraft(
                        key = ingredient.id ?: UUID.randomUUID().toString(),
                        name = ingredient.name,
                        preparation = ingredient.preparation.orEmpty(),
                        amount = ingredient.amount,
                        unit = ingredient.unit,
                        nutrients = ingredient.nutrients.toFields(),
                    )
                },
                captureMethod = meal.captureMethod,
                provenanceSource = meal.provenanceSource ?: "user",
                externalReference = meal.externalReference,
                attachmentId = meal.attachmentId,
            )
        }
    }
}

fun PrivateFoodDto.toIngredient(amountText: String = defaultAmount): IngredientDraft {
    val amount = parseLocalizedDecimal(amountText) ?: error("amount")
    require(amount > BigDecimal.ZERO)
    val reference = when (basis) {
        "100g", "100ml" -> BigDecimal("100")
        else -> parseLocalizedDecimal(defaultAmount) ?: error("default_amount")
    }
    val scaled = nutrients.map { nutrient ->
        nutrient.copy(
            value = ((parseLocalizedDecimal(nutrient.value) ?: BigDecimal.ZERO) * amount / reference)
                .setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
            basis = "portion",
            source = "user",
            locked = true,
        )
    }
    return IngredientDraft(
        name = listOfNotNull(name, brand?.takeIf { it.isNotBlank() }).joinToString(" · "),
        amount = amount.canonical(),
        unit = unit,
        nutrients = scaled.toFields(),
    )
}

fun parseLocalizedDecimal(input: String): BigDecimal? {
    var value = input.trim().replace("\u00a0", "").replace(" ", "")
    if (value.isEmpty()) return null
    if (',' in value && '.' in value) {
        val commaIsDecimal = value.lastIndexOf(',') > value.lastIndexOf('.')
        value = if (commaIsDecimal) value.replace(".", "").replace(',', '.') else value.replace(",", "")
    } else if (',' in value) {
        value = value.replace(',', '.')
    }
    return value.toBigDecimalOrNull()
}

private val nutrientKeys = listOf("energy", "protein", "carbohydrates", "fat")

private fun NutrientFields.toDtos(): List<NutrientDto> {
    val core = nutrientKeys.mapNotNull { key ->
        val value = parseLocalizedDecimal(value(key)) ?: return@mapNotNull null
        require(value >= BigDecimal.ZERO) { key }
        NutrientDto(
            key = key,
            value = value.canonical(),
            unit = if (key == "energy") "kcal" else "g",
            source = if (originalValues[key]?.let(::parseLocalizedDecimal) == value) {
                sources[key] ?: "user"
            } else {
                "user"
            },
            locked = if (originalValues[key]?.let(::parseLocalizedDecimal) == value) {
                locks[key] ?: true
            } else {
                true
            },
        )
    }
    val preserved = additional.filterNot { it.key in nutrientKeys }.map { nutrient ->
        val value = requireNotNull(parseLocalizedDecimal(nutrient.value))
        require(value >= BigDecimal.ZERO) { nutrient.key }
        nutrient.copy(value = value.canonical())
    }
    return core + preserved
}

private fun List<NutrientDto>.toFields(): NutrientFields {
    val values = filter { it.key in nutrientKeys }.associate { it.key to it.value }
    val sources = filter { it.key in nutrientKeys }.associate { it.key to it.source }
    val locks = filter { it.key in nutrientKeys }.associate { it.key to it.locked }
    return fold(
        NutrientFields(
            additional = filterNot { it.key in nutrientKeys },
            sources = sources,
            locks = locks,
            originalValues = values,
        ),
    ) { fields, nutrient ->
        if (nutrient.key in nutrientKeys) fields.with(nutrient.key, nutrient.value) else fields
    }
}

private fun BigDecimal.canonical(): String = stripTrailingZeros().toPlainString()
