#!/usr/bin/env python3
"""
Resumen de cobertura de la capa de dominio y de servicio (QA-22).

    python3 qa/ci/coverage-summary.py        (después de `mvn verify`)

Lee el jacoco.csv que deja cada módulo en target/site/jacoco/ y suma solo los paquetes
`*.domain` y `*.service` de los cuatro artefactos, que es lo que mide el umbral del eje B
(80 % de líneas, 70 % de ramas). Los simuladores no entran: no son Ecopedia.

Imprime una tabla en la consola y, en GitHub Actions, la escribe también en el resumen del run
($GITHUB_STEP_SUMMARY), así se ve sin bajar nada.
"""

import csv
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LAYER = re.compile(r"^com\.ecopedia\.(core|charging|integration|async)\.[a-z]+\.(domain|service)$")
LINES_MIN, BRANCHES_MIN = 0.80, 0.70


def pct(covered, missed):
    total = covered + missed
    return covered / total if total else None


def fmt(ratio):
    return "—" if ratio is None else f"{ratio * 100:.1f} %"


def main():
    rows, totals = [], [0, 0, 0, 0]  # líneas cubiertas, perdidas, ramas cubiertas, perdidas
    for module in ("core", "charging", "integration", "async"):
        report = ROOT / "backend" / f"ecopedia-{module}" / "target" / "site" / "jacoco" / "jacoco.csv"
        if not report.exists():
            rows.append((module, None, None, "sin reporte"))
            continue
        lc = lm = bc = bm = 0
        with report.open(encoding="utf-8") as f:
            for r in csv.DictReader(f):
                if LAYER.match(r["PACKAGE"]):
                    lc += int(r["LINE_COVERED"])
                    lm += int(r["LINE_MISSED"])
                    bc += int(r["BRANCH_COVERED"])
                    bm += int(r["BRANCH_MISSED"])
        totals = [totals[0] + lc, totals[1] + lm, totals[2] + bc, totals[3] + bm]
        rows.append((module, pct(lc, lm), pct(bc, bm), f"{lc + lm} líneas"))

    lines, branches = pct(totals[0], totals[1]), pct(totals[2], totals[3])
    ok = lines is not None and lines >= LINES_MIN and branches is not None and branches >= BRANCHES_MIN

    out = [
        "## Cobertura · dominio y servicio (QA-22)",
        "",
        "| Módulo | Líneas | Ramas | Tamaño |",
        "| --- | --- | --- | --- |",
        *[f"| {m} | {fmt(l)} | {fmt(b)} | {n} |" for m, l, b, n in rows],
        f"| **Total** | **{fmt(lines)}** | **{fmt(branches)}** | |",
        "",
        f"Umbral del eje B: {LINES_MIN:.0%} de líneas y {BRANCHES_MIN:.0%} de ramas → "
        + ("**cumple**." if ok else "**todavía no cumple**."),
        "",
        "Reporte completo: artefacto `jacoco` (abrí `index.html` de cada módulo).",
    ]
    text = "\n".join(out)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
