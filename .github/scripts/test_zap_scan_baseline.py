import contextlib
import http.server
import io
import json
import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

import zap_scan_baseline as baseline


class ZapScanBaselineTest(unittest.TestCase):
    def test_requests_are_authenticated_and_failures_do_not_hide_other_routes(self):
        seen = []

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                seen.append((self.path, self.headers.get("Authorization")))
                code = 200 if self.path == "/api/v1/ok" else 302 if self.path.endswith("redirect") else 403
                self.send_response(code)
                if code == 302:
                    self.send_header("Location", "/api/v1/ok")
                self.end_headers()

            def log_message(self, *args):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever)
        thread.start()
        try:
            requests = [
                {"method": "GET", "url": f"http://127.0.0.1:{server.server_port}/api/v1/{path}"}
                for path in ("forbidden", "redirect", "ok")
            ]
            with contextlib.redirect_stdout(io.StringIO()):
                results = baseline.check_routes(requests, "test-token")
            self.assertEqual([status for _, status in results], [403, 302, 200])
            self.assertEqual(len(seen), 3)
            self.assertTrue(all(auth == "Bearer test-token" for _, auth in seen))
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_rejects_nonlocal_or_write_requests_before_sending_anything(self):
        with patch.object(baseline.urllib.request, "build_opener") as opener:
            for request in (
                {"method": "POST", "url": "http://localhost/api/v1/example"},
                {"method": "GET", "url": "https://example.invalid/api/v1/example"},
                {"method": "GET", "url": "http://localhost/actuator/health"},
            ):
                with self.assertRaises(ValueError):
                    baseline.check_routes([request], "test-token")
            with self.assertRaises(ValueError):
                baseline.check_routes([], "test-token")
            opener.assert_not_called()

    def test_main_fails_on_bad_baseline_and_persists_response_table(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work / "read-baseline-requests.json").write_text(json.dumps([
                {"method": "GET", "url": "http://localhost/api/v1/me"},
            ]))
            summary_path = work / "summary"
            with patch.object(baseline, "WORK", work), patch.object(
                baseline, "check_routes", return_value=[("/api/v1/me", 500)]
            ), patch.dict(os.environ, {
                "ZAP_AUTH_TOKEN": "test-token", "GITHUB_STEP_SUMMARY": str(summary_path),
            }), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(baseline.main(), 1)
            self.assertIn("| `/api/v1/me` | 500 |", summary_path.read_text())
            self.assertEqual(summary_path.read_text(), (work / "zap-baseline.md").read_text())

    def test_anonymous_sends_no_authorization_and_refuses_a_token(self):
        request = {"method": "GET", "url": "http://localhost/api/v1/me"}
        with patch.object(baseline.urllib.request, "build_opener") as build:
            response = build.return_value.open.return_value.__enter__.return_value
            response.status = 401
            result = baseline.check_routes([request], "", "anonymous")
            self.assertEqual(result, [("GET /api/v1/me", 401)])
            sent = build.return_value.open.call_args.args[0]
            self.assertNotIn("Authorization", sent.headers)
        with self.assertRaises(ValueError):
            baseline.check_routes([request], "unexpected-token", "anonymous")

    def test_write_requires_command_success_not_just_http_200(self):
        request = {
            "method": "POST",
            "url": "http://localhost/api/v1/commands/users/convert-to-multi-firm",
            "data": '{"userEntraObjectId":"00000000-0000-0000-0000-000000000006"}',
        }
        with patch.object(baseline.urllib.request, "build_opener") as build:
            response = build.return_value.open.return_value.__enter__.return_value
            response.status = 200
            response.read.return_value = b'{"success": false}'
            captured = []
            with contextlib.redirect_stdout(io.StringIO()):
                results = baseline.check_routes(
                    [request], "token", "write", captured_writes=captured
                )
                self.assertEqual(results[0][1], "command failed")
            self.assertEqual(captured, [])
            sent = build.return_value.open.call_args.args[0]
            self.assertEqual(sent.method, "POST")
            self.assertEqual(sent.data, request["data"].encode())

    def test_write_baseline_captures_successful_traffic_without_replaying(self):
        seen = []

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                seen.append(self.rfile.read(int(self.headers["Content-Length"])).decode())
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"success":true}')

            def log_message(self, *args):
                pass

        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                request = {
                    "method": "POST",
                    "url": f"http://127.0.0.1:{server.server_port}/api/v1/commands/users/convert-to-multi-firm",
                    "data": '{"userEntraObjectId":"00000000-0000-0000-0000-000000000006"}',
                }
                (work / "read-baseline-requests.json").write_text(json.dumps([request]))
                with patch.object(baseline, "WORK", work), patch.dict(os.environ, {
                    "SCAN_MODE": "write", "BASELINE_PHASE": "pre",
                    "ZAP_AUTH_TOKEN": "synthetic-token",
                    "GITHUB_STEP_SUMMARY": str(work / "summary"),
                }), contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(baseline.main(), 0)
                entries = json.loads((work / "write-baseline.har").read_text())["log"]["entries"]
                self.assertEqual(len(entries), 1)
                self.assertEqual(entries[0]["request"]["postData"]["text"], request["data"])
                self.assertIn(
                    {"name": "Authorization", "value": "Bearer synthetic-token"},
                    entries[0]["request"]["headers"],
                )
                self.assertEqual(entries[0]["response"]["status"], 200)
                self.assertEqual(json.loads(entries[0]["response"]["content"]["text"]), {"success": True})
                self.assertEqual(seen, [request["data"]])
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
