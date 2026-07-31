import argparse
import json
import time
from collections import defaultdict
from decimal import Decimal
from pathlib import Path

from baseline_api import main


def evaluate_cases(document: dict, outputs: dict[str, dict]) -> dict:
    results = []
    language_totals: dict[str, dict[str, int]] = defaultdict(
        lambda: {"cases": 0, "schema_valid": 0, "required_hits": 0, "required_total": 0}
    )
    for case in document["cases"]:
        output = outputs.get(case["id"])
        schema_valid = False
        required_hits = 0
        forbidden_hits = 0
        ranges_ok = True
        warning_ok = not case.get("require_warning", False)
        error = None
        if output is not None:
            try:
                meal = main.AnalysisMeal.model_validate(output)
                schema_valid = True
                names = " ".join(item.original_name.lower() for item in meal.ingredients)
                required_hits = sum(
                    token.lower() in names for token in case.get("required_ingredients", [])
                )
                forbidden_hits = sum(
                    token.lower() in names for token in case.get("forbidden_ingredients", [])
                )
                warning_ok = warning_ok or bool(meal.warnings)
                totals: dict[str, Decimal] = defaultdict(Decimal)
                for nutrient in meal.nutrients:
                    if nutrient.basis == "portion":
                        totals[nutrient.key] += nutrient.value
                for ingredient in meal.ingredients:
                    for nutrient in ingredient.nutrients:
                        if nutrient.basis == "portion":
                            totals[nutrient.key] += nutrient.value
                for key, bounds in case.get("nutrient_ranges", {}).items():
                    ranges_ok = (
                        ranges_ok
                        and key in totals
                        and (Decimal(str(bounds[0])) <= totals[key] <= Decimal(str(bounds[1])))
                    )
            except ValueError as exc:
                error = str(exc).splitlines()[0]
        else:
            error = "missing output"
        required_total = len(case.get("required_ingredients", []))
        language = language_totals[case["language"]]
        language["cases"] += 1
        language["schema_valid"] += int(schema_valid)
        language["required_hits"] += required_hits
        language["required_total"] += required_total
        results.append(
            {
                "id": case["id"],
                "language": case["language"],
                "category": case["category"],
                "schema_valid": schema_valid,
                "required_hits": required_hits,
                "required_total": required_total,
                "forbidden_hits": forbidden_hits,
                "ranges_ok": ranges_ok,
                "warning_ok": warning_ok,
                "error": error,
            }
        )
    total_required = sum(item["required_total"] for item in results)
    total_hits = sum(item["required_hits"] for item in results)
    summary = {
        "suite_version": document["suite_version"],
        "cases": len(results),
        "schema_valid_rate": sum(item["schema_valid"] for item in results) / len(results),
        "required_ingredient_rate": total_hits / total_required if total_required else 1.0,
        "forbidden_ingredient_hits": sum(item["forbidden_hits"] for item in results),
        "range_failures": sum(not item["ranges_ok"] for item in results),
        "warning_failures": sum(not item["warning_ok"] for item in results),
        "languages": dict(language_totals),
        "results": results,
    }
    summary["passed"] = (
        summary["schema_valid_rate"] == 1
        and summary["required_ingredient_rate"] >= 0.9
        and summary["forbidden_ingredient_hits"] == 0
        and summary["range_failures"] == 0
        and summary["warning_failures"] == 0
    )
    return summary


def live_outputs(document: dict, suite_root: Path) -> tuple[dict[str, dict], dict]:
    outputs = {}
    latencies = []
    tokens = 0
    for case in document["cases"]:
        item = case["input"]
        image_bytes = None
        image_type = None
        text = item.get("text")
        if item["type"] == "image":
            image_bytes = (suite_root / item["file"]).read_bytes()
            image_type = "image/jpeg"
        started = time.monotonic()
        output, usage = main.call_analysis_provider(
            text=text,
            locale=case["language"],
            meal_type=None,
            image_bytes=image_bytes,
            image_media_type=image_type,
        )
        latencies.append(round((time.monotonic() - started) * 1000))
        tokens += int(usage.get("prompt_tokens") or 0) + int(usage.get("completion_tokens") or 0)
        outputs[case["id"]] = output
    latencies.sort()
    telemetry = {
        "model": main.settings.analysis_model,
        "prompt_version": main.ANALYSIS_PROMPT_VERSION,
        "schema_version": main.ANALYSIS_SCHEMA_VERSION,
        "requests": len(outputs),
        "tokens": tokens,
        "p50_latency_ms": latencies[len(latencies) // 2],
        "p95_latency_ms": latencies[min(len(latencies) - 1, round(len(latencies) * 0.95))],
    }
    return outputs, telemetry


def markdown(summary: dict, mode: str, telemetry: dict) -> str:
    status = "BESTANDEN" if summary["passed"] else "NICHT BESTANDEN"
    lines = [
        "# Baseline Meal Analysis Eval",
        "",
        f"- Status: **{status}**",
        f"- Modus: `{mode}`",
        f"- Suite: `{summary['suite_version']}`",
        f"- Schema-gültig: {summary['schema_valid_rate']:.1%}",
        f"- Pflichtzutaten: {summary['required_ingredient_rate']:.1%}",
        f"- Verbotene Zutaten: {summary['forbidden_ingredient_hits']}",
        f"- Mengen-/Kalorienbereiche fehlgeschlagen: {summary['range_failures']}",
        f"- Unsicherheitswarnungen fehlgeschlagen: {summary['warning_failures']}",
        "",
        "## Sprachen",
        "",
    ]
    for language, values in summary["languages"].items():
        lines.append(
            f"- {language}: {values['schema_valid']}/{values['cases']} schema-gültig, "
            f"{values['required_hits']}/{values['required_total']} Pflichtzutaten"
        )
    if telemetry:
        lines += ["", "## Technische Metadaten", ""]
        lines += [f"- {key}: {value}" for key, value in telemetry.items()]
    lines += ["", "## Fälle", ""]
    for result in summary["results"]:
        marker = (
            "✓"
            if all(
                [
                    result["schema_valid"],
                    result["forbidden_hits"] == 0,
                    result["ranges_ok"],
                    result["warning_ok"],
                ]
            )
            else "✗"
        )
        lines.append(f"- {marker} `{result['id']}` ({result['language']}, {result['category']})")
    return "\n".join(lines) + "\n"


def main_cli() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=Path("artifacts/eval"))
    args = parser.parse_args()
    suite_root = Path(__file__).parents[1] / "evals"
    document = json.loads((suite_root / "cases.json").read_text(encoding="utf-8"))
    if args.live:
        outputs, telemetry = live_outputs(document, suite_root)
        mode = "live"
    else:
        outputs = {case["id"]: case["fixture_output"] for case in document["cases"]}
        telemetry = {
            "model": main.settings.analysis_model,
            "prompt_version": main.ANALYSIS_PROMPT_VERSION,
            "schema_version": main.ANALYSIS_SCHEMA_VERSION,
        }
        mode = "fixture"
    summary = evaluate_cases(document, outputs)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "eval.json").write_text(
        json.dumps(summary | {"mode": mode, "telemetry": telemetry}, indent=2, ensure_ascii=False)
        + "\n",
        encoding="utf-8",
    )
    (args.output_dir / "eval.md").write_text(
        markdown(summary, mode, telemetry),
        encoding="utf-8",
    )
    if not summary["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main_cli()
