#!/usr/bin/env python
"""Audit policy workbook URLs without changing the workbook or database."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict
from pathlib import Path

import requests

from import_policy_excel import read_policy_rows


def normalized(value: str) -> str:
    return re.sub(r"[\s·•：:（）()《》“”\"'—–-]", "", value or "").lower()


def split_urls(value: str | None) -> list[str]:
    return [part for part in re.split(r"[,\s]+", value or "") if part.startswith(("http://", "https://"))]


def fetch(url: str, title: str, issuer: str, timeout: int) -> dict:
    result = {"url": url, "ok": False}
    try:
        response = requests.get(
            url,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
                )
            },
            timeout=timeout,
            allow_redirects=True,
        )
        body = response.content
        content_type = response.headers.get("content-type", "")
        result.update({
            "status": response.status_code,
            "final_url": response.url,
            "content_type": content_type,
            "bytes": len(body),
            "pdf_signature": body.startswith(b"%PDF-"),
        })
        if "pdf" in content_type.lower() or result["pdf_signature"]:
            result["ok"] = response.status_code == 200 and result["pdf_signature"] and len(body) > 1024
            return result

        response.encoding = response.apparent_encoding or response.encoding
        text = response.text
        title_match = re.search(r"<title[^>]*>(.*?)</title>", text, re.IGNORECASE | re.DOTALL)
        page_title = re.sub(r"\s+", " ", title_match.group(1)).strip() if title_match else ""
        compact_text = normalized(re.sub(r"<[^>]+>", " ", text))
        title_key = normalized(title)
        issuer_key = normalized(issuer)
        result.update({
            "page_title": page_title[:300],
            "title_match": bool(title_key and (title_key in compact_text or title_key[:12] in compact_text)),
            "issuer_match": bool(issuer_key and issuer_key[:8] in compact_text),
        })
        result["ok"] = (
            response.status_code == 200
            and len(body) > 1024
            and (result["title_match"] or result["issuer_match"])
        )
    except Exception as error:  # noqa: BLE001 - audit must record all URL failures
        result["error"] = f"{type(error).__name__}: {error}"
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--excel", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=20)
    args = parser.parse_args()

    rows, warnings = read_policy_rows(args.excel, None)
    audit_rows = []
    for row in rows:
        main_result = fetch(row.original_url or "", row.title, row.issuing_body, args.timeout)
        evidence_results = [
            fetch(url, row.title, row.issuing_body, args.timeout)
            for url in split_urls(row.evidence_url)
        ]
        audit_rows.append({
            "excel_row": row.excel_row,
            "title": row.title,
            "policy": asdict(row),
            "main": main_result,
            "evidence": evidence_results,
            "main_verified": main_result.get("ok") is True,
            "any_evidence_verified": any(item.get("ok") is True for item in evidence_results),
        })

    report = {"warnings": warnings, "rows": audit_rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    json.dump({
        "rows": len(audit_rows),
        "main_verified": sum(row["main_verified"] for row in audit_rows),
        "main_failed": [row["excel_row"] for row in audit_rows if not row["main_verified"]],
        "evidence_url_count": sum(len(row["evidence"]) for row in audit_rows),
        "evidence_verified": sum(
            item.get("ok") is True for row in audit_rows for item in row["evidence"]
        ),
        "output": str(args.output),
    }, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
