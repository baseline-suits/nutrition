import json
from pathlib import Path

from scripts.run_evals import evaluate_cases


def test_fixture_eval_passes_and_separates_languages():
    root = Path(__file__).parents[1] / "evals"
    document = json.loads((root / "cases.json").read_text(encoding="utf-8"))
    outputs = {case["id"]: case["fixture_output"] for case in document["cases"]}

    result = evaluate_cases(document, outputs)

    assert result["passed"] is True
    assert result["schema_valid_rate"] == 1
    assert result["forbidden_ingredient_hits"] == 0
    assert result["languages"]["de"]["cases"] == 4
    assert result["languages"]["ru"]["cases"] == 2


def test_eval_rejects_unknown_nutrient_and_hallucinated_ingredient():
    root = Path(__file__).parents[1] / "evals"
    document = json.loads((root / "cases.json").read_text(encoding="utf-8"))
    case = document["cases"][0]
    invalid = case["fixture_output"] | {
        "ingredients": case["fixture_output"]["ingredients"]
        + [
            {
                "original_name": "Zucker",
                "amount": "10",
                "unit": "g",
                "nutrients": [
                    {"key": "invented", "value": "1", "unit": "g", "source": "ai_estimate"}
                ],
            }
        ]
    }

    result = evaluate_cases(
        {"suite_version": "test", "cases": [case]},
        {case["id"]: invalid},
    )

    assert result["passed"] is False
    assert result["schema_valid_rate"] == 0
