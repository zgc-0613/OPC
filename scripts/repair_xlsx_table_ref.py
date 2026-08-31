#!/usr/bin/env python
"""Create a readable copy of an XLSX whose first table spans an invalid whole-row range."""

from __future__ import annotations

import argparse
import re
import zipfile
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--table", default="xl/tables/table1.xml")
    parser.add_argument("--ref", default="A1:Y11")
    parser.add_argument("--columns", type=int, default=25)
    args = parser.parse_args()

    args.target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.source, "r") as source, zipfile.ZipFile(
        args.target, "w", zipfile.ZIP_DEFLATED
    ) as target:
        for info in source.infolist():
            payload = source.read(info.filename)
            if info.filename == args.table:
                text = payload.decode("utf-8")
                text = re.sub(r' ref="[^"]+"', f' ref="{args.ref}"', text, count=1)
                columns = re.search(r"<tableColumns[^>]*>(.*?)</tableColumns>", text, re.DOTALL)
                if columns is None:
                    raise RuntimeError(f"No tableColumns element in {args.table}")
                selected = re.findall(r"<tableColumn\b[^>]*/>", columns.group(1))[: args.columns]
                if len(selected) != args.columns:
                    raise RuntimeError(
                        f"Expected at least {args.columns} columns, found {len(selected)}"
                    )
                replacement = (
                    f'<tableColumns count="{args.columns}">'
                    + "".join(selected)
                    + "</tableColumns>"
                )
                text = text[: columns.start()] + replacement + text[columns.end() :]
                payload = text.encode("utf-8")
            target.writestr(info, payload)


if __name__ == "__main__":
    main()
