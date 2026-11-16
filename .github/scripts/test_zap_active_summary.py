import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import zap_active_summary as summary


class ZapActiveSummaryTest(unittest.TestCase):
    def test_findings_use_risk_order_and_instance_counts(self):
        rows = summary.alert_rows({"site": [{"alerts": [
            {"riskcode": 1, "pluginid": "low", "name": "Low", "count": "4"},
            {"riskcode": "2", "pluginid": "medium", "alert": "Medium", "instances": [{}, {}]},
            {"riskcode": "3", "pluginid": "high", "alert": "High", "count": "0"},
        ]}]})
        self.assertEqual([(row["risk"], row["instances"]) for row in rows],
                         [("3", 0), ("2", 2), ("1", 4)])

    def test_coverage_normalizes_ids_and_omits_query_values(self):
        tree = [{"method": "GET", "url": "http://localhost/api", "children": [
            {"method": "GET", "url": "http://localhost/api/v1/queries/users/123?email=private",
             "statusCode": 200},
            {"method": "GET", "url": "http://localhost/api/v1/queries/users/"
             "00000000-0000-0000-0000-000000000001?email=other", "statusCode": 403},
            {"method": "GET", "url": "http://localhost/api/v1/queries/users/456()(email)",
             "statusCode": 400},
        ]}]
        endpoints, statuses, patterns = summary.coverage(tree)
        self.assertEqual(endpoints, ["GET /api/v1/queries/users/{id}"])
        self.assertEqual(statuses, {"200": 1, "403": 1, "400": 1})
        self.assertEqual(patterns, {("GET", "/api/v1/queries/users/{id}", ("email",)): 2})
        rendered = summary.markdown([], (endpoints, statuses, patterns), "success")
        for value in ("private", "other", "00000000-0000", "456"):
            self.assertNotIn(value, rendered)
        self.assertIn("| 403 | 1 |", rendered)
        self.assertIn("not proof", rendered)

    def test_missing_coverage_and_failed_authentication_are_explicit(self):
        rendered = summary.markdown([], None, "failure")
        self.assertIn("coverage is unknown", rendered)
        self.assertIn("Authentication was not verified", rendered)
        self.assertNotIn("0 normalized endpoints", rendered)

    def test_anonymous_summary_does_not_claim_authenticated_coverage(self):
        rendered = summary.markdown([], None, "success", "anonymous")
        self.assertIn("anonymous denial check: **success**", rendered)
        self.assertNotIn("authenticated endpoint check", rendered)

    def test_summary_escapes_findings_and_parameter_names(self):
        cov = summary.coverage([{
            "method": "GET", "url": "http://localhost/api/v1/queries/users?bad%7C%0Aname=secret",
            "statusCode": 200,
        }])
        rows = [{"risk": "2", "plugin": "123", "name": "<script>|finding\nnext", "instances": 2}]
        rendered = summary.markdown(rows, cov, "success")
        self.assertIn("&lt;script&gt;&#124;finding next", rendered)
        self.assertIn("bad__name", rendered)
        self.assertNotIn("secret", rendered)

    def test_main_writes_artifacts_and_preserves_slack_totals(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work / "zap-active-report.json").write_text(json.dumps({"site": [{"alerts": [
                {"riskcode": "2", "pluginid": "123", "alert": "Example", "count": "3"},
            ]}]}))
            (work / "sites-tree.json").write_text(json.dumps([{
                "method": "GET", "url": "http://localhost/api/v1/me?query=private", "statusCode": 200,
            }]))
            env_file = work / "env"
            with patch.object(summary, "WORK", work), patch.dict(
                os.environ, {"GITHUB_ENV": str(env_file), "AUTH_STATUS": "success"}
            ), contextlib.redirect_stdout(io.StringIO()):
                summary.main()
            self.assertIn("ZAP_MEDIUM_INSTANCES=3\nZAP_MEDIUM_TYPES=1\n", env_file.read_text())
            self.assertIn("ZAP_HIGH_INSTANCES=0\nZAP_HIGH_TYPES=0\n", env_file.read_text())
            self.assertIn("| Medium | 3 | 1 |", (work / "zap-active-summary.md").read_text())
            self.assertEqual((work / "zap-active-endpoints.txt").read_text(), "GET /api/v1/me\n")
            self.assertNotIn("private", (work / "zap-active-query-patterns.md").read_text())
            (work / "zap-active-report.json").write_text("{invalid")
            with patch.object(summary, "WORK", work), self.assertRaises(json.JSONDecodeError):
                summary.main()


if __name__ == "__main__":
    unittest.main()
