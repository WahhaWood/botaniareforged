#!/usr/bin/env python3
"""Fetch Botania's own game test arenas and turn them into loadable structures.

Botania ships the arenas its game tests run in, as SNBT text, under
`data/botania/gametest/structure/` on the 1.21.1-porting branch. The game
itself only reads structures from `data/<ns>/structure/`, as gzipped binary
NBT, so the files have to be converted on the way in.

The raw files are kept in `tools/upstream_structures/` so the conversion runs
offline afterwards, and `import_upstream_structures.py` is what actually
writes them into the resources. `make_test_structures.py` fills in whatever
upstream does not ship, so the two never fight over a template.
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent

# Pinned so a rerun cannot silently pick up an unrelated arena set.
BRANCH = "1.21.1-porting"
COMMIT = "fd9167ee59315e4d90ab866b88ded59970e14920"
RAW = "https://raw.githubusercontent.com/Vazkii/Botania"
API = "https://api.github.com/repos/Vazkii/Botania"
PREFIX = "Xplat/src/main/resources/data/botania/gametest/structure/"

STORE = HERE / "upstream_structures"


def list_arena_paths() -> list[str]:
    """Every arena path upstream ships, relative to the structure root."""
    url = f"{API}/git/trees/{BRANCH}?recursive=1"
    with urllib.request.urlopen(url, timeout=60) as response:
        import json
        tree = json.load(response)
    return sorted(
        entry["path"][len(PREFIX):]
        for entry in tree.get("tree", [])
        if entry["path"].startswith(PREFIX) and entry["path"].endswith(".snbt")
    )


def fetch_all(force: bool) -> int:
    STORE.mkdir(parents=True, exist_ok=True)
    paths = list_arena_paths()
    if not paths:
        raise SystemExit("upstream listed no arenas; is the branch or the path wrong?")
    for rel in paths:
        target = STORE / rel
        if target.exists() and not force:
            continue
        url = f"{RAW}/{BRANCH}/{PREFIX}{rel}"
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                body = response.read()
        except urllib.error.URLError as error:
            raise SystemExit(f"could not fetch {rel}: {error}") from error
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(body)
        print(f"  fetched {rel} ({len(body)} bytes)")
    (STORE / "SOURCE.txt").write_text(
        f"Botania {BRANCH} @ {COMMIT}\n{PREFIX}\n", encoding="utf-8")
    return len(paths)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true",
                        help="download again even if a file is already stored")
    args = parser.parse_args()
    count = fetch_all(args.force)
    print(f"{count} arenas in {STORE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())