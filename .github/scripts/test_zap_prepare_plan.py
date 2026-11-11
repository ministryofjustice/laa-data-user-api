import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


REPO = Path(__file__).resolve().parents[2]


class ZapPreparePlanTest(unittest.TestCase):
    def prepare(self, directory, mode, token=None):
        work = Path(directory)
        (work / ".github/zap").mkdir(parents=True, exist_ok=True)
        (work / "build/zap").mkdir(parents=True, exist_ok=True)
        shutil.copy(REPO / ".github/zap/active-scan-plan.yaml", work / ".github/zap")
        shutil.copy(REPO / "laa-data-user.yml", work)
        env = {
            **os.environ, "SCAN_MODE": mode,
            "ZAP_AUTH_TOKEN": ("" if mode == "anonymous" else "synthetic-token") if token is None else token,
            "ZAP_TEST_OID": "00000000-0000-0000-0000-000000000001",
            "ZAP_TEST_FIRM_CODE": "900001",
        }
        return subprocess.run(
            ["ruby", str(REPO / ".github/scripts/zap_prepare_plan.rb")],
            cwd=work, env=env, text=True, capture_output=True,
        )

    def plan(self, directory):
        return json.loads(subprocess.check_output([
            "ruby", "-r", "yaml", "-r", "json", "-e",
            "puts JSON.generate(YAML.load_file(ARGV[0]))", str(Path(directory) / "build/zap/plan.yaml"),
        ]))

    def test_read_preserves_get_only_import_and_fixture_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.prepare(directory, "read")
            self.assertEqual(result.returncode, 0, result.stderr)
            spec = json.loads((Path(directory) / "build/zap/openapi-read-only.json").read_text())
            for item in spec["paths"].values():
                self.assertIn("get", item)
                self.assertNotIn("post", item)
            parameters = spec["components"]["parameters"]
            self.assertEqual(parameters["FirmId"]["schema"]["default"], "900001")
            self.assertEqual(parameters["Sort"]["schema"]["items"]["default"], "lastName,asc")
            self.assertNotIn("default", parameters["Sort"]["schema"])

    def test_write_only_seeds_allowlisted_commands_and_never_targets_actor(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.prepare(directory, "write")
            self.assertEqual(result.returncode, 0, result.stderr)
            plan = self.plan(directory)
            self.assertNotIn("openapi", [job["type"] for job in plan["jobs"]])
            requests = next(job["requests"] for job in plan["jobs"] if job["type"] == "requestor")
            self.assertEqual(len(requests), 3)
            for request in requests:
                self.assertEqual(request["method"], "POST")
                self.assertNotIn("00000000-0000-0000-0000-000000000001", request["data"])
                self.assertTrue(request["url"].endswith(("/convert-to-multi-firm", "/update-offices")))
            baseline = json.loads((Path(directory) / "build/zap/read-baseline-requests.json").read_text())
            self.assertEqual(len(baseline), 14)

    def test_anonymous_removes_authorization_and_covers_reads_and_local_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.prepare(directory, "anonymous")
            self.assertEqual(result.returncode, 0, result.stderr)
            plan = self.plan(directory)
            self.assertNotIn("replacer", [job["type"] for job in plan["jobs"]])
            self.assertNotIn("Authorization", json.dumps(plan))
            spec = json.loads((Path(directory) / "build/zap/openapi-read-only.json").read_text())
            self.assertEqual(spec["security"], [])
            posts = [path for path, item in spec["paths"].items() if "post" in item]
            self.assertEqual(len(posts), 2)
            for item in spec["paths"].values():
                for method in ("get", "post"):
                    if method in item:
                        self.assertEqual(item[method]["security"], [])

    def test_anonymous_rejects_accidentally_supplied_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.prepare(directory, "anonymous", "must-not-be-used")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("must not receive a token", result.stderr)


if __name__ == "__main__":
    unittest.main()
