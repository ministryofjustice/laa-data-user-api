"""Verify the fixture-backed local GET routes before ZAP mutates requests."""

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit


WORK = Path("build/zap")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check_routes(requests, token):
    if not requests:
        raise ValueError("No baseline requests configured")
    for request in requests:
        url = urlsplit(request["url"])
        if (request["method"] != "GET" or url.scheme != "http"
                or url.hostname not in ("localhost", "127.0.0.1")
                or url.username is not None or url.password is not None
                or not url.path.startswith("/api/v1/")):
            raise ValueError(f"Baseline request must be a local API GET: {request['url']}")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    results = []
    for request in requests:
        path = urlsplit(request["url"]).path
        req = urllib.request.Request(request["url"], headers={"Authorization": f"Bearer {token}"})
        try:
            with opener.open(req, timeout=30) as response:
                status = response.status
        except urllib.error.HTTPError as error:
            status = error.code
        except (urllib.error.URLError, TimeoutError) as error:
            print(f"::error::Baseline GET {path} failed: {error}")
            status = "connection error"
        results.append((path, status))
        if status != 200:
            print(f"::error::Baseline GET {path}: {status}; expected HTTP 200")
    return results


def main():
    results = check_routes(
        json.loads((WORK / "read-baseline-requests.json").read_text()),
        os.environ["ZAP_AUTH_TOKEN"],
    )
    lines = [
        "### Read-only baseline before active scanning", "",
        "These fixture-backed GETs must return HTTP 200 before scanning. "
        "Active attack requests may legitimately return errors.", "",
        "| Route | Baseline response |", "|---|---:|",
    ]
    lines += [f"| `{path}` | {status} |" for path, status in results]
    text = "\n".join(lines) + "\n"
    (WORK / "zap-read-baseline.md").write_text(text)
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
        summary.write(text)
    print(text)
    return 0 if all(status == 200 for _, status in results) else 1


if __name__ == "__main__":
    sys.exit(main())
