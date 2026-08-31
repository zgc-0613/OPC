import concurrent.futures
import html
import json
import re
import subprocess
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RECORDS = json.loads((ROOT / "outputs/policy-new-gd-zj-xj-20260826/classification-review.json").read_text(encoding="utf-8"))
OUT = ROOT / "outputs/policy-new-gd-zj-xj-20260826/source-texts"
OUT.mkdir(parents=True, exist_ok=True)


def clean_html(body):
    body = re.sub(r"<(script|style|noscript|svg|nav|footer)[^>]*>.*?</\1>", " ", body, flags=re.I | re.S)
    body = re.sub(r"<br\s*/?>", "\n", body, flags=re.I)
    body = re.sub(r"</(p|div|li|tr|h[1-6])>", "\n", body, flags=re.I)
    text = html.unescape(re.sub(r"<[^>]+>", " ", body))
    lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
    return "\n".join(line for line in lines if line)


def decode(raw, content_type=""):
    candidates = []
    match = re.search(r"charset=([\w-]+)", content_type, re.I)
    for encoding in [match.group(1) if match else None, "utf-8", "gb18030", "gbk"]:
        if not encoding:
            continue
        try:
            text = raw.decode(encoding, errors="replace")
            score = len(re.findall(r"[\u4e00-\u9fff]", text)) - text.count("\ufffd") * 50
            candidates.append((score, text))
        except LookupError:
            pass
    return max(candidates, key=lambda item: item[0])[1]


def url_list(row):
    result = []
    for kind, value in (("original", row.get("originalUrl")), ("evidence", row.get("evidenceUrl"))):
        for url in re.split(r"[|；;\n]+", value or ""):
            url = url.strip()
            if url and url not in [item[1] for item in result]:
                result.append((kind, url))
    return result


def fetch(row):
    attempts = []
    selected = None
    for kind, url in url_list(row):
        attempt = {"kind": kind, "url": url, "status": None, "contentType": None, "textLength": 0, "error": None}
        raw = b""
        try:
            request = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FindOPC-policy-review/1.0",
                "Accept-Language": "zh-CN,zh;q=0.9",
            })
            with urllib.request.urlopen(request, timeout=22) as response:
                attempt["status"] = response.status
                attempt["contentType"] = response.headers.get("Content-Type", "")
                raw = response.read(4_000_000)
        except urllib.error.HTTPError as exc:
            attempt["status"] = exc.code
            attempt["error"] = str(exc)
        except Exception as exc:
            attempt["error"] = f"{type(exc).__name__}: {exc}"

        if not raw:
            try:
                curl = subprocess.run(
                    ["curl.exe", "-L", "--max-time", "35", "-A", "Mozilla/5.0", "-sS", url],
                    capture_output=True, timeout=40, check=False,
                )
                if curl.returncode == 0:
                    raw = curl.stdout
            except Exception:
                pass

        if raw:
            if raw[:4] == b"%PDF" or "pdf" in (attempt["contentType"] or "").lower():
                pdf_path = OUT / f"{row['id']}-{kind}.pdf"
                pdf_path.write_bytes(raw)
                attempt["error"] = "pdf_saved"
            else:
                text = clean_html(decode(raw, attempt["contentType"] or ""))
                attempt["textLength"] = len(text)
                if selected is None and len(text) >= 200:
                    selected = {"kind": kind, "url": url, "text": text}
        attempts.append(attempt)

    if selected:
        text_path = OUT / f"{row['id']}.txt"
        text_path.write_text(selected["text"], encoding="utf-8")
    else:
        text_path = None
    return {
        "id": row["id"],
        "title": row["title"],
        "selectedUrl": selected["url"] if selected else None,
        "selectedKind": selected["kind"] if selected else None,
        "textFile": str(text_path) if text_path else None,
        "textLength": len(selected["text"]) if selected else 0,
        "attempts": attempts,
    }


def main():
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        manifest = list(pool.map(fetch, RECORDS))
    target = ROOT / "outputs/policy-new-gd-zj-xj-20260826/source-manifest.json"
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "records": len(manifest),
        "textReady": sum(bool(row["textFile"]) for row in manifest),
        "pdfSaved": sum(any(a["error"] == "pdf_saved" for a in row["attempts"]) for row in manifest),
        "unavailable": [row["id"] for row in manifest if not row["textFile"] and not any(a["error"] == "pdf_saved" for a in row["attempts"])],
        "output": str(target),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
