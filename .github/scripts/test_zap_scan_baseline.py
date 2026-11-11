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
            with patch.object(baseline.json, "load", return_value={"success": False}), \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(baseline.check_routes([request], "token", "write")[0][1],
                                 "command failed")
            sent = build.return_value.open.call_args.args[0]
            self.assertEqual(sent.method, "POST")
            self.assertEqual(sent.data, request["data"].encode())


if __name__ == "__main__":
    unittest.main()
