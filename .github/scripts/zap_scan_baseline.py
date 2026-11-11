"""Verify authenticated fixtures or anonymous denial inside the isolated namespace."""

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit


WORK = Path(os.environ.get("ZAP_WORK_DIR", "build/zap"))
WRITE_PATHS = {
    "/api/v1/commands/users/convert-to-multi-firm",
    "/api/v1/commands/user-profiles/update-offices",
}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check_routes(requests, token, mode="read"):
    if mode not in ("read", "write", "anonymous"):
        raise ValueError(f"Unknown scan mode: {mode}")
    if (mode == "anonymous" and token) or (mode != "anonymous" and not token):
        raise ValueError("Anonymous scans require no token; authenticated scans require a token")
    if not requests:
        raise ValueError("No baseline requests configured")
    for request in requests:
        url = urlsplit(request["url"])
        allowed_method = request["method"] == "GET" or (
            mode in ("write", "anonymous") and request["method"] == "POST"
            and url.path in WRITE_PATHS
        )
        if (not allowed_method or url.scheme != "http"
                or url.hostname not in ("localhost", "127.0.0.1")
                or url.username is not None or url.password is not None
                or not url.path.startswith("/api/v1/")):
            raise ValueError(f"Baseline request is outside the local scan scope: {request['url']}")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    results = []
    for request in requests:
        path = request["method"] + " " + urlsplit(request["url"]).path
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        data = request.get("data")
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(
            request["url"], headers=headers, method=request["method"],
            data=data.encode() if data is not None else None,
        )
        try:
            with opener.open(req, timeout=30) as response:
                status = response.status
                if request["method"] == "POST" and mode == "write":
                    if json.load(response).get("success") is not True:
                        print(f"::error::Baseline {path} returned an unsuccessful command result")
                        status = "command failed"
        except urllib.error.HTTPError as error:
            status = error.code
        except (urllib.error.URLError, TimeoutError) as error:
            print(f"::error::Baseline {path} failed: {error}")
            status = "connection error"
        results.append((path, status))
        expected = 401 if mode == "anonymous" else 200
        if status != expected:
            print(f"::error::Baseline {path}: {status}; expected HTTP {expected}")
    return results


def main():
    mode = os.environ.get("SCAN_MODE", "read")
    phase = os.environ.get("BASELINE_PHASE", "pre")
    requests = json.loads((WORK / "read-baseline-requests.json").read_text())
    if phase == "post":
        requests = [request for request in requests
                    if request["method"] == "GET" and urlsplit(request["url"]).path == "/api/v1/me"]
    results = check_routes(requests, os.environ.get("ZAP_AUTH_TOKEN", ""), mode)
    expected = 401 if mode == "anonymous" else 200
    lines = [
        f"### {mode.capitalize()} access check ({phase}-scan)", "",
        f"Expected HTTP {expected}. Authenticated writes must also report success. "
        "Active attack requests may legitimately return errors.", "",
        "| Route | Baseline response |", "|---|---:|",
    ]
    lines += [f"| `{path}` | {status} |" for path, status in results]
    text = "\n".join(lines) + "\n"
    (WORK / ("zap-post-baseline.md" if phase == "post" else "zap-baseline.md")).write_text(text)
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
        summary.write(text)
    print(text)
    return 0 if all(status == expected for _, status in results) else 1


if __name__ == "__main__":
    sys.exit(main())
