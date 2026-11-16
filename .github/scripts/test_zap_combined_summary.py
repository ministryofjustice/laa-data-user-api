import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import zap_combined_summary as combined


class ZapCombinedSummaryTest(unittest.TestCase):
    def test_collect_counts_findings_and_preserves_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work / "zap-active-report.json").write_text(json.dumps({"site": [{"alerts": [
                {"riskcode": "2", "pluginid": "123", "count": "4"},
                {"riskcode": "2", "pluginid": "456", "instances": [{}, {}]},
            ]}]}))
            env = {"SCAN_MODE": "write", "JOB_STATUS": "failure", "SCAN_STATUS": "failure",
                   "AUTH_STATUS": "success", "SUMMARY_STATUS": "success"}
            with patch.object(combined, "WORK", work), patch.dict(os.environ, env, clear=True):
                combined.collect()
            status = json.loads((work / "scan-status.json").read_text())
            self.assertEqual(status["counts"]["Medium"], {"instances": 6, "types": 2})
            self.assertEqual(status["job"], "failure")
            self.assertEqual(status["scan"], "failure")

    def test_missing_and_failed_passes_are_not_reported_as_zero(self):
        status = {"mode": "read", "job": "failure", "scan": "skipped",
                  "access_check": "skipped", "summary": "skipped", "counts": None}
        text = combined.combined_text({"read": status}, "failure", "https://example.test/run", "example/repo")
        self.assertIn("READ: job failure; scan skipped", text)
        self.assertIn("Findings unavailable.", text)
        self.assertIn("WRITE: status unavailable for this run attempt.", text)
        self.assertIn("ANONYMOUS: status unavailable", text)
        self.assertNotIn("High: 0", text)
        self.assertIn("may overlap", text)

    def test_collect_missing_summary_does_not_read_report(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            env = {"SCAN_MODE": "anonymous", "JOB_STATUS": "failure", "SCAN_STATUS": "",
                   "AUTH_STATUS": "", "SUMMARY_STATUS": ""}
            with patch.object(combined, "WORK", work), patch.dict(os.environ, env, clear=True):
                combined.collect()
            status = json.loads((work / "scan-status.json").read_text())
            self.assertIsNone(status["counts"])
            self.assertEqual(status["scan"], "skipped")

    def test_incorrect_mode_is_rejected(self):
        with self.assertRaises(ValueError):
            combined.combined_text({"read": {"mode": "write"}}, "failure", "https://example.test", "example/repo")

    def test_combine_writes_one_payload_with_three_separate_passes(self):
        counts = {name: {"instances": 2, "types": 1} for _, name in combined.RISKS}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for mode in combined.MODES:
                status_dir = root / "statuses" / f"zap-active-status-2-{mode}"
                status_dir.mkdir(parents=True)
                (status_dir / "scan-status.json").write_text(json.dumps({
                    "mode": mode, "job": "success", "scan": "success",
                    "access_check": "success", "summary": "success", "counts": counts,
                }))
            env = {"GITHUB_RUN_ATTEMPT": "2", "GITHUB_SERVER_URL": "https://github.com",
                   "GITHUB_REPOSITORY": "example/repo", "GITHUB_RUN_ID": "123",
                   "MATRIX_RESULT": "success"}
            original = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(combined, "STATUS_DIR", root / "statuses"), \
                        patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(io.StringIO()):
                    combined.combine()
            finally:
                os.chdir(original)
            payload = json.loads((root / "build/zap-combined-slack.json").read_text())
            self.assertIn("ZAP Active Scan: repo - DEV", payload["text"])
            for mode in ("READ", "WRITE", "ANONYMOUS"):
                self.assertIn(f"{mode}: job success", payload["text"])
            self.assertEqual(payload["text"].count("High: 2 instances (1 types)"), 3)
            self.assertIn("https://github.com/example/repo/actions/runs/123", payload["text"])
            self.assertIn(payload["text"], (root / "build/zap-combined-summary.md").read_text())

    def test_combine_handles_single_flattened_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "scan-status.json").write_text(json.dumps({
                "mode": "write", "job": "failure", "scan": "failure",
                "access_check": "skipped", "summary": "skipped", "counts": None,
            }))
            env = {"GITHUB_RUN_ATTEMPT": "1", "GITHUB_SERVER_URL": "https://github.com",
                   "GITHUB_REPOSITORY": "example/repo", "GITHUB_RUN_ID": "123",
                   "MATRIX_RESULT": "failure"}
            original = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(combined, "STATUS_DIR", root), \
                        patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(io.StringIO()):
                    combined.combine()
            finally:
                os.chdir(original)
            payload = json.loads((root / "build/zap-combined-slack.json").read_text())
            self.assertIn("WRITE: job failure; scan failure", payload["text"])
            self.assertIn("READ: status unavailable", payload["text"])


if __name__ == "__main__":
    unittest.main()
