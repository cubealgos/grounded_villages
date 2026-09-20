#!/usr/bin/env python3
"""Aggregate GV-10's per-seed seed-sweep JSON files into a table and a combined JSON report.

Each argument is one seed's JSON file, as written by `grounded_villages.harness.SeedSweepRunner`
(26.2-fabric only, `src/seedsweep/java`) -- one village's start position, piece count, height
spread and water fraction, sampled under its footprint exactly as
`docs/spec/domains/site.md` §3 defines those two metrics. `./gradlew :26.2-fabric:seedSweep` calls
this once per invocation, after collecting every seed's own run; `just sweep <N>` is the same thing
through the justfile.

Prints a plain-text table to stdout (so `just sweep`'s own log is the report) and writes the same
data, plus each seed's raw record, as one combined JSON document next to the per-seed files
(`build/seedsweep/report.json`) -- what `docs/baseline/*.json` is a checked-in copy of, for the 10
vanilla-baseline seeds.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


def load_results(paths: list[str]) -> list[dict]:
    results = []
    for raw_path in paths:
        path = Path(raw_path)
        if not path.exists():
            raise SystemExit(f"seed_sweep_report: missing per-seed file {path}")
        data = json.loads(path.read_text())
        data["_source"] = str(path)
        results.append(data)
    return results


def render_table(results: list[dict]) -> str:
    headers = [
        "seed", "structure", "start", "pieces", "height spread", "water fraction", "tier",
        "rejected (w/h)", "outcome", "ms",
    ]
    rows = []
    for r in results:
        start = f"({r['startX']}, {r['startY']}, {r['startZ']})"
        rows.append([
            str(r["seed"]),
            r["structureId"],
            start,
            str(r["pieceCount"]),
            f"{r['heightSpread']:.1f}",
            f"{r['waterFraction'] * 100:.1f}%",
            r.get("tier") or "-",
            f"{r.get('rejectedWater', 0)}/{r.get('rejectedHeight', 0)}",
            r.get("ladderOutcome") or "-",
            str(r["runtimeMillis"]),
        ])

    widths = [max(len(headers[i]), *(len(row[i]) for row in rows)) if rows else len(headers[i])
              for i in range(len(headers))]

    def fmt_row(cells: list[str]) -> str:
        return "  ".join(cell.ljust(widths[i]) for i, cell in enumerate(cells))

    lines = [fmt_row(headers), fmt_row(["-" * w for w in widths])]
    lines.extend(fmt_row(row) for row in rows)
    return "\n".join(lines)


def summary(results: list[dict]) -> dict:
    if not results:
        return {"seeds": 0}
    spreads = [r["heightSpread"] for r in results]
    waters = [r["waterFraction"] for r in results]
    pieces = [r["pieceCount"] for r in results]
    rejected_water = sum(r.get("rejectedWater", 0) for r in results)
    rejected_height = sum(r.get("rejectedHeight", 0) for r in results)
    outcomes: dict[str, int] = {}
    for r in results:
        outcome = r.get("ladderOutcome")
        if outcome:
            outcomes[outcome] = outcomes.get(outcome, 0) + 1
    return {
        "seeds": len(results),
        "mean_height_spread": sum(spreads) / len(spreads),
        "max_height_spread": max(spreads),
        "mean_water_fraction": sum(waters) / len(waters),
        "max_water_fraction": max(waters),
        "mean_piece_count": sum(pieces) / len(pieces),
        "total_rejected_water": rejected_water,
        "total_rejected_height": rejected_height,
        "ladder_outcomes": outcomes,
    }


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("usage: seed_sweep_report.py <seed-result.json> [...]")

    results = load_results(sys.argv[1:])
    results.sort(key=lambda r: r["seed"])

    print(render_table(results))
    print()
    stats = summary(results)
    print(
        f"{stats['seeds']} seeds -- mean height spread {stats['mean_height_spread']:.1f} "
        f"(max {stats['max_height_spread']:.1f}), mean water fraction "
        f"{stats['mean_water_fraction'] * 100:.1f}% (max {stats['max_water_fraction'] * 100:.1f}%), "
        f"mean piece count {stats['mean_piece_count']:.1f}"
    )
    print(
        f"rejected pieces (total): {stats['total_rejected_water']} water, "
        f"{stats['total_rejected_height']} height deviation -- ladder outcomes: {stats['ladder_outcomes']}"
    )

    report_path = Path(sys.argv[1]).resolve().parent / "report.json"
    report_path.write_text(json.dumps({"summary": stats, "villages": results}, indent=2))
    print(f"\nWrote {report_path}")


if __name__ == "__main__":
    main()
