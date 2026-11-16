"""Combine the independent ZAP scan statuses without merging their findings."""

import json
import os
import re
import sys
from pathlib import Path

from zap_active_summary import RISKS, alert_rows


MODES = ("read", "write", "anonymous")
WORK = Path("build/zap")
STATUS_DIR = Path("build/zap-status")


def collect():
    mode = os.environ["SCAN_MODE"]
    if mode not in MODES:
        raise ValueError(f"Unknown scan mode: {mode}")

    status = {
        "mode": mode,
        "job": os.environ["JOB_STATUS"],
        "scan": os.environ["SCAN_STATUS"] or "skipped",
        "access_check": os.environ["AUTH_STATUS"] or "skipped",
        "summary": os.environ["SUMMARY_STATUS"] or "skipped",
        "counts": None,
    }
    if status["summary"] == "success":
        rows = alert_rows(json.loads((WORK / "zap-active-report.json").read_text()))
        status["counts"] = {
            name: {
                "instances": sum(row["instances"] for row in rows if row["risk"] == code),
                "types": sum(1 for row in rows if row["risk"] == code),
            }
            for code, name in RISKS
        }
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "scan-status.json").write_text(json.dumps(status) + "\n")


def combined_text(statuses, matrix_result, run_url, repository):
    lines = [
        f"ZAP Active Scan: {repository.split('/')[-1]} - DEV",
        f"Overall scan jobs: {matrix_result}",
        "",
    ]
    for mode in MODES:
        status = statuses.get(mode)
        if status is None:
            lines.append(f"{mode.upper()}: status unavailable for this run attempt.")
            continue
        if status["mode"] != mode:
            raise ValueError(f"Scan status does not match {mode}")
        lines.append(
            f"{mode.upper()}: job {status['job']}; scan {status['scan']}; "
            f"post-scan access check {status['access_check']}; summary {status['summary']}"
        )
        if status["counts"] is None:
            lines.append("  Findings unavailable.")
        else:
            for _, name in RISKS:
                counts = status["counts"][name]
                lines.append(f"  {name}: {counts['instances']} instances ({counts['types']} types)")
    lines += [
        "",
        "Counts are per scan and may overlap; they are not unique combined findings.",
        f"Reports and coverage: {run_url}",
    ]
    return "\n".join(lines) + "\n"


def combine():
    raw_attempt = os.environ["GITHUB_RUN_ATTEMPT"]
    if not re.fullmatch(r"[1-9][0-9]*", raw_attempt):
        raise ValueError("GITHUB_RUN_ATTEMPT must be a positive integer")
    attempt = int(raw_attempt)
    statuses = {}
    # download-artifact extracts a single pattern match directly into the destination.
    single_path = STATUS_DIR / "scan-status.json"
    if single_path.exists():
        status = json.loads(single_path.read_text())
        if status["mode"] not in MODES:
            raise ValueError(f"Unknown scan mode: {status['mode']}")
        statuses[status["mode"]] = status
    for mode in MODES:
        path = STATUS_DIR / f"zap-active-status-{attempt}-{mode}" / "scan-status.json"
        if path.exists():
            if mode in statuses:
                raise ValueError(f"Duplicate scan status: {mode}")
            statuses[mode] = json.loads(path.read_text())
    run_url = (
        f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}"
        f"/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    )
    text = combined_text(statuses, os.environ["MATRIX_RESULT"], run_url, os.environ["GITHUB_REPOSITORY"])
    Path("build").mkdir(parents=True, exist_ok=True)
    Path("build/zap-combined-summary.md").write_text("## ZAP active scan\n\n```\n" + text + "```\n")
    payload = {"text": text}
    Path("build/zap-combined-slack.json").write_text(json.dumps(payload) + "\n")
    print(text)


if __name__ == "__main__":
    if sys.argv[1:] == ["collect"]:
        collect()
    elif sys.argv[1:] == ["combine"]:
        combine()
    else:
        raise SystemExit("Usage: zap_combined_summary.py collect|combine")
