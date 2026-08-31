import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

records = json.loads(Path("outputs/policies-public-20260826.json").read_text(encoding="utf-8"))
start = int(sys.argv[1]) if len(sys.argv) > 1 else 0
count = int(sys.argv[2]) if len(sys.argv) > 2 else 20
rows = []
for record in records[start:start + count]:
    url = record.get("evidenceUrl") or record.get("originalUrl")
    status = None
    title = ""
    content_len = 0
    error = ""
    if url:
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "FindOPC policy audit/1.0"})
            with urllib.request.urlopen(request, timeout=15) as response:
                body = response.read(500_000).decode("utf-8", errors="replace")
                status = response.status
                content_len = len(body)
                match = re.search(r"<title[^>]*>(.*?)</title>", body, re.I | re.S)
                if match:
                    title = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", match.group(1))).strip()
        except urllib.error.HTTPError as exc:
            status = exc.code
            error = str(exc)
        except Exception as exc:
            error = type(exc).__name__ + ": " + str(exc)
    rows.append({"id": record.get("id"), "title": record.get("title"), "url": url, "http_status": status, "page_title": title, "content_length": content_len, "error": error})
Path(f"outputs/policy-source-batch-{start+1}-{start+len(rows)}.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(rows, ensure_ascii=False, indent=2))
