"""Summarise ZAP findings and observed API coverage without request values."""

import html
import json
import os
import re
from collections import Counter
from pathlib import Path
from urllib.parse import parse_qsl, urlsplit


WORK = Path("build/zap")
RISKS = (("3", "High"), ("2", "Medium"), ("1", "Low"), ("0", "Info"))
ID_SEGMENT = re.compile(
    r"^(?:\d+|[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})$"
)


def cell(value):
    return html.escape(str(value)).replace("|", "&#124;").replace("\n", " ")


def normalized_path(url):
    # ZAP appends "()(params)" to some Sites Tree node URLs.
    path = re.sub(r"\(\).*$", "", urlsplit(url).path) or "/"
    return "/".join("{id}" if ID_SEGMENT.fullmatch(part) else part for part in path.split("/"))


def site_nodes(tree):
    stack = list(tree)
    while stack:
        node = stack.pop()
        if not isinstance(node, dict):
            continue
        # Ancestor nodes can have URLs and methods without ever receiving a request.
        if node.get("url") and node.get("method") and node.get("statusCode") is not None:
            yield node
        stack.extend(node.get("children") or [])


def coverage(tree):
    endpoints = set()
    statuses = Counter()
    patterns = Counter()
    for node in site_nodes(tree):
        method = node["method"]
        path = normalized_path(node["url"])
        endpoints.add(f"{method} {path}")
        statuses[str(node["statusCode"])] += 1
        names = tuple(sorted({
            re.sub(r"[^A-Za-z0-9_.-]", "_", name)[:100] or "(unnamed)"
            for name, _ in parse_qsl(urlsplit(node["url"]).query, keep_blank_values=True)
        }))
        if names:
            patterns[(method, path, names)] += 1
    return sorted(endpoints), statuses, patterns


def alert_rows(report):
    rows = []
    for site in report.get("site", []):
        for alert in site.get("alerts", []):
            count = alert.get("count")
            rows.append({
                "risk": str(alert["riskcode"]),
                "plugin": alert["pluginid"],
                "name": alert.get("alert") or alert.get("name", ""),
                "instances": int(count) if count is not None else len(alert.get("instances", [])),
            })
    order = {code: index for index, (code, _) in enumerate(RISKS)}
    return sorted(rows, key=lambda row: (
        order.get(row["risk"], len(order)), -row["instances"], row["name"]
    ))


def pattern_table(patterns):
    lines = [
        "| Method | Path | Query parameter names | Sites Tree nodes |",
        "|---|---|---|---:|",
    ]
    for (method, path, names), count in sorted(
        patterns.items(), key=lambda item: (-item[1], item[0])
    ):
        lines.append(f"| {cell(method)} | {cell(path)} | {cell(', '.join(names))} | {count} |")
    if not patterns:
        lines.append("| - | - | No query parameters found | 0 |")
    return lines


def markdown(rows, cov, auth_status, mode="read"):
    lines = [f"## ZAP active scan ({cell(mode)})", ""]
    check = "anonymous denial" if mode == "anonymous" else "authenticated endpoint"
    lines.append(f"Post-scan {check} check: **{cell(auth_status)}**.")
    if auth_status != "success":
        lines.append("**Authentication was not verified after scanning; interpret results with caution.**"
                     if mode != "anonymous" else
                     "**Anonymous access denial was not verified after scanning; interpret results with caution.**")
    lines += ["", "| Risk | Alert instances | Alert types |", "|---|---:|---:|"]
    for code, name in RISKS:
        matching = [row for row in rows if row["risk"] == code]
        lines.append(f"| {name} | {sum(row['instances'] for row in matching)} | {len(matching)} |")
    if rows:
        lines += ["", "### Findings", "", "| Risk | Rule | Finding | Instances |", "|---|---|---|---:|"]
        names = dict(RISKS)
        for row in rows:
            lines.append(
                f"| {cell(names.get(row['risk'], row['risk']))} | {cell(row['plugin'])} | "
                f"{cell(row['name'])} | {row['instances']} |"
            )
    else:
        lines += ["", "No alerts were reported. This does not establish complete API coverage."]
    lines += ["", "### Coverage", ""]
    if cov is None:
        lines.append("Sites Tree export not found; endpoint coverage is unknown.")
        return "\n".join(lines) + "\n"
    endpoints, statuses, patterns = cov
    lines.append(
        f"Observed **{sum(statuses.values())} Sites Tree nodes** covering "
        f"**{len(endpoints)} normalized endpoints**."
    )
    lines.append(
        "These are observed requests, not proof that every endpoint received every active rule. "
        "Queries are removed and numeric/UUID path segments are shown as `{id}`."
    )
    lines += ["", "| Response code | Sites Tree nodes |", "|---|---:|"]
    lines += [f"| {cell(code)} | {count} |" for code, count in sorted(statuses.items())]
    lines += ["", "<details><summary>Observed endpoints</summary>", "", "```text"]
    lines += [endpoint.replace("```", "'''") for endpoint in endpoints[:400]]
    if len(endpoints) > 400:
        lines.append(f"... {len(endpoints) - 400} more in zap-active-endpoints.txt")
    lines += ["```", "", "</details>", "", "### Sanitized query patterns", ""]
    lines.append("Parameter values are discarded. Counts refer to Sites Tree nodes, not all scan requests.")
    lines += [""] + pattern_table(patterns)[:22]
    if len(patterns) > 20:
        lines.append(f"\n{len(patterns) - 20} more patterns in zap-active-query-patterns.md.")
    return "\n".join(lines) + "\n"


def main():
    rows = alert_rows(json.loads((WORK / "zap-active-report.json").read_text()))
    tree_path = WORK / "sites-tree.json"
    cov = coverage(json.loads(tree_path.read_text())) if tree_path.exists() else None
    if cov is None:
        print("::warning::Sites Tree export not found; endpoint coverage is unknown.")
    summary = markdown(rows, cov, os.environ.get("AUTH_STATUS") or "unknown",
                       os.environ.get("SCAN_MODE", "read"))
    (WORK / "zap-active-summary.md").write_text(summary)
    if cov is not None:
        endpoints, _, patterns = cov
        (WORK / "zap-active-endpoints.txt").write_text("\n".join(endpoints) + "\n")
        (WORK / "zap-active-query-patterns.md").write_text(
            "Query parameter values are omitted; numeric/UUID path segments are normalized.\n\n"
            + "\n".join(pattern_table(patterns)) + "\n"
        )
    with Path(os.environ["GITHUB_ENV"]).open("a") as env:
        for code, name in RISKS:
            matching = [row for row in rows if row["risk"] == code]
            env.write(f"ZAP_{name.upper()}_INSTANCES={sum(row['instances'] for row in matching)}\n")
            env.write(f"ZAP_{name.upper()}_TYPES={len(matching)}\n")
    print(summary)


if __name__ == "__main__":
    main()
