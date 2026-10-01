#!/usr/bin/env python3

import argparse
import csv
import json
import math
import statistics
import sys
from datetime import datetime
from pathlib import Path


THRESHOLD_KIB = 500 * 1024
REQUIRED_SAMPLE_COUNT = 864
REQUIRED_COLUMNS = [
    "timestamp",
    "mem_total_kib",
    "mem_available_kib",
    "swap_total_kib",
    "swap_free_kib",
    "umoweb_frontend_bytes",
    "umoweb_backend_bytes",
    "umoweb_mysql_bytes",
    "umoweb_health",
    "openclaw_service_state",
]


def _parse_timestamp(value: str, line_number: int) -> datetime:
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError as exc:
        raise ValueError(
            f"line {line_number}: timestamp must be ISO 8601 UTC"
        ) from exc


def _parse_integer(row: dict[str, str], key: str, line_number: int) -> int:
    raw_value = row.get(key)
    try:
        value = int(raw_value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"line {line_number}: {key} must be an integer") from exc
    if value < 0:
        raise ValueError(f"line {line_number}: {key} must not be negative")
    return value


def analyze_rows(rows: list[dict[str, str]]) -> dict[str, object]:
    if not rows:
        raise ValueError("CSV contains no memory samples")

    mem_available_values = []
    for line_number, row in enumerate(rows, start=2):
        if len(row) != len(REQUIRED_COLUMNS) or None in row:
            raise ValueError(f"line {line_number}: unexpected field count")
        if None in row.values():
            raise ValueError(f"line {line_number}: unexpected field count")
        _parse_timestamp(row["timestamp"], line_number)
        mem_available_values.append(
            _parse_integer(row, "mem_available_kib", line_number)
        )

    sorted_values = sorted(mem_available_values)
    p05_index = math.ceil(0.05 * len(sorted_values)) - 1
    minimum = sorted_values[0]
    below_threshold_count = sum(
        value <= THRESHOLD_KIB for value in mem_available_values
    )

    return {
        "sample_count": len(mem_available_values),
        "min_mem_available_kib": minimum,
        "p05_mem_available_kib": sorted_values[p05_index],
        "median_mem_available_kib": statistics.median(mem_available_values),
        "below_threshold_count": below_threshold_count,
        "threshold_kib": THRESHOLD_KIB,
        "required_sample_count": REQUIRED_SAMPLE_COUNT,
        "pass": (
            len(mem_available_values) >= REQUIRED_SAMPLE_COUNT
            and minimum > THRESHOLD_KIB
        ),
    }


def analyze_csv(path: Path) -> dict[str, object]:
    try:
        handle = path.open("r", encoding="utf-8", newline="")
    except OSError as exc:
        raise ValueError(f"cannot read CSV: {exc}") from exc

    with handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames is None:
            raise ValueError("CSV is empty")
        if reader.fieldnames != REQUIRED_COLUMNS:
            raise ValueError("CSV header does not match the required schema")
        rows = list(reader)

    return analyze_rows(rows)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Analyze OpenClaw memory-preflight CSV samples."
    )
    parser.add_argument("csv_path", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        report = analyze_csv(args.csv_path)
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
