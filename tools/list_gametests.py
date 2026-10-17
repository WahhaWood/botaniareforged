#!/usr/bin/env python3
"""Lists every @GameTest in the port together with the structure it needs.

Vanilla derives a structure name for tests that do not name one: lowercased
class name, a dot, lowercased method name (GameTestRegistry#turnMethodIntoTestFunction).
So a test class without explicit templates still needs one fixture per method,
and missing fixtures only surface as a runtime "structure not found" error.

Usage:
    python3 tools/list_gametests.py            # summary
    python3 tools/list_gametests.py --missing  # only what has no fixture yet
"""

import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEST_DIR = ROOT / "Xplat/src/main/java/vazkii/botania/test"
STRUCTURE_DIRS = (
    ROOT / "Xplat/src/main/resources/data",
    ROOT / "NeoForge/src/generated/resources/data",
)

# @GameTest(...) up to the method declaration that follows it.
GAMETEST = re.compile(
    r"@GameTest\s*(?:\((?P<args>[^)]*)\))?(?P<between>(?:@[\w.]+(?:\([^)]*\))?\s*)*)"
    r"\s*(?:public\s+|private\s+|protected\s+)?(?:static\s+)?void\s+"
    r"(?P<method>\w+)\s*\(\s*GameTestHelper",
    re.S,
)
TEMPLATE = re.compile(r'template\s*=\s*(?:"([^"]+)"|([\w.]+))')
CONSTANT = re.compile(
    r'static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"', re.MULTILINE)
# Qualified references like TestingUtil.EMPTY_STRUCTURE, whose literal lives in
# another file of the same test package.
QUALIFIED = re.compile(r'(\w+)\.(\w+)')


def shared_constants():
    """Every `static final String X = "..."` under the test tree, by name."""
    out = {}
    for path in TEST_DIR.rglob("*.java"):
        for name, value in CONSTANT.findall(path.read_text(encoding="utf-8", errors="ignore")):
            out.setdefault(name, value)
    return out


def constants(text, shared):
    """Map constant name -> literal, so `template = ARENA` resolves.

    A reference may be qualified (`TestingUtil.EMPTY_STRUCTURE`), so both the
    bare and the qualified form have to be looked up.
    """
    out = {}
    for name, value in CONSTANT.findall(text):
        out[name] = out.get(value, value)
    # A qualified name may point at a constant from another file, so fall back
    # to the shared table for both the bare and the qualified spelling.
    for name, value in list(out.items()):
        out[name] = shared.get(value, value)
    for owner, name in QUALIFIED.findall(text):
        out[f"{owner}.{name}"] = out.get(name, shared.get(name))
    return out


def fixture_exists(template):
    if ":" in template:
        ns, _, path = template.partition(":")
    else:
        ns, path = "minecraft", template
    for base in STRUCTURE_DIRS:
        if (base / ns / "structure" / f"{path}.nbt").exists():
            return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--missing", action="store_true", help="only tests with no fixture")
    ap.add_argument("--class", dest="cls", help="filter by class simple name")
    args = ap.parse_args()

    total = 0
    missing = 0
    classes = 0
    shared = shared_constants()
    for path in sorted(TEST_DIR.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "@GameTest" not in text:
            continue
        if args.cls and path.stem != args.cls:
            continue
        cls = path.stem
        classes += 1
        names = constants(text, shared)
        rows = []
        for match in GAMETEST.finditer(text):
            args_text = match.group("args") or ""
            method = match.group("method")
            found = TEMPLATE.search(args_text)
            if found:
                literal, ref = found.group(1), found.group(2)
                template = literal if literal is not None else names.get(ref, f"?{ref}?")
            else:
                template = f"{cls.lower()}.{method.lower()}"
            # Tests without an explicit template get the test class's namespace,
            # which is what @GameTestHolder supplies.
            full = template if ":" in template else f"botania:{template}"
            rows.append((method, full, fixture_exists(full)))
        total += len(rows)
        gap = [r for r in rows if not r[2]]
        missing += len(gap)
        if args.missing and not gap:
            continue
        print(f"\n{path.relative_to(ROOT)}  ({len(rows)} tests, {len(gap)} without fixture)")
        if "@GameTestHolder" not in text:
            print("  ! no @GameTestHolder, NeoForge will not discover this class")
        for method, full, ok in rows:
            mark = "ok  " if ok else "MISS"
            print(f"  {mark} {method:<45} {full}")

    print(f"\n{classes} classes, {total} tests, {missing} without a fixture")
    return 1 if (missing and args.missing) else 0


if __name__ == "__main__":
    sys.exit(main())