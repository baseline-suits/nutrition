# Baseline Meal Analysis Eval

- Status: **BESTANDEN**
- Modus: `fixture`
- Suite: `baseline-meal-eval/1.0.0`
- Schema-gültig: 100.0%
- Pflichtzutaten: 100.0%
- Verbotene Zutaten: 0
- Mengen-/Kalorienbereiche fehlgeschlagen: 0
- Unsicherheitswarnungen fehlgeschlagen: 0

## Sprachen

- de: 4/4 schema-gültig, 9/9 Pflichtzutaten
- ru: 2/2 schema-gültig, 4/4 Pflichtzutaten

## Technische Metadaten

- model: gpt-5.6-luna
- prompt_version: baseline-meal-analysis/1.0.0
- schema_version: nutrition-analysis-model-output/1.1.0

## Fälle

- ✓ `de-single-oats` (de, single_food)
- ✓ `ru-tea-milk` (ru, beverage)
- ✓ `de-bread-topping` (de, bread_topping)
- ✓ `image-mixed-plate` (de, mixed_plate)
- ✓ `image-packaged-bar` (de, packaged_product)
- ✓ `ru-ambiguous-portion` (ru, ambiguous_portion)
