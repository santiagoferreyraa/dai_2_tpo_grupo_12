#!/usr/bin/env python3
"""
Resumen del mutation score (QA-23).

    python3 qa/ci/mutation-summary.py        (después de correr PIT con -Pmutation)

Lee target/pit-reports/mutations.xml de cada módulo. Un mutante cuenta como DETECTADO cuando PIT
lo marca detected="true": algún test falló (KILLED) o se colgó (TIMED_OUT). Los que SOBREVIVEN
son cambios al código que ningún test notó; los SIN COBERTURA, código que ningún test ejecuta.

    mutation score = detectados / total

Umbral del eje B: 60 %. Imprime una tabla y, en GitHub Actions, la suma al resumen del run.
"""

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
THRESHOLD = 0.60


def main():
    rows, total_all, detected_all = [], 0, 0
    for module in ("core", "charging", "integration", "async"):
        report = ROOT / "backend" / f"ecopedia-{module}" / "target" / "pit-reports" / "mutations.xml"
        if not report.exists():
            rows.append(f"| {module} | — | — | — | — | sin reporte |")
            continue
        mutations = ET.parse(report).getroot().findall("mutation")
        total = len(mutations)
        detected = sum(1 for m in mutations if m.get("detected") == "true")
        survived = sum(1 for m in mutations if m.get("status") == "SURVIVED")
        no_coverage = sum(1 for m in mutations if m.get("status") == "NO_COVERAGE")
        score = f"{detected / total * 100:.1f} %" if total else "—"
        rows.append(f"| {module} | {total} | {detected} | {survived} | {no_coverage} | {score} |")
        total_all += total
        detected_all += detected

    overall = detected_all / total_all if total_all else None
    verdict = (
        "sin datos" if overall is None
        else ("**cumple**" if overall >= THRESHOLD else "**todavía no cumple**")
    )
    text = "\n".join([
        "## Mutation score · dominio y servicio (QA-23)",
        "",
        "| Módulo | Mutantes | Detectados | Sobrevivieron | Sin cobertura | Score |",
        "| --- | --- | --- | --- | --- | --- |",
        *rows,
        f"| **Total** | **{total_all}** | **{detected_all}** | | | **{'—' if overall is None else f'{overall * 100:.1f} %'}** |",
        "",
        f"Umbral del eje B: {THRESHOLD:.0%} → {verdict}.",
        "",
        "Qué mutante sobrevivió y en qué línea: artefacto `pit` (index.html de cada módulo).",
    ])
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
