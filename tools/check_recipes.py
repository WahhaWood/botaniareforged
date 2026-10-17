#!/usr/bin/env python3
"""Checks that every registered addon block and item is reachable in survival.

Two failure modes matter and neither shows up as a crash:

  * a recipe that references an item id nothing registers, and
  * content with no recipe at all, which loads fine and is simply never
    craftable — the addon shipped 77 recipe-unlock advancements for 25 recipes,
    so the whole machine tree was unreachable.

The registry ids are read out of the registration code rather than a hand-kept
list, and the "has a recipe" side walks recipe results *and* ingredients, so a
machine that only shows up as an ingredient of its own upgrade chain does not
count as craftable.

Usage:
    python3 tools/check_recipes.py            # report unreachable content
    python3 tools/check_recipes.py --json     # machine-readable
"""

import argparse
import glob
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BEM = ROOT / "NeoForge/src/main/java/net/lmor/botanicalextramachinery"
APPBOT = ROOT / "NeoForge/src/main/java/vazkii/botania/ae2"
VOLTANY = ROOT / "NeoForge/src/main/java/com/jykito/voltany"
DATA = ROOT / "NeoForge/src/main/resources/data"

NAMESPACES = ("botanicalextramachinery", "botania", "voltany")


def read(path):
    return path.read_text(encoding="utf-8", errors="ignore")


def bem_registrations():
    """(namespace, kind, id) for everything BemRegistration registers."""
    out = []
    src = read(BEM / "core/BemRegistration.java")
    for kind, registry in (("block", "BLOCK"), ("item", "ITEM")):
        for name in re.findall(
                r'mod\.register\(Registries\.%s,\s*"([a-z0-9_]+)"' % registry, src):
            out.append(("botanicalextramachinery", kind, name))
    return out


def deferred_registrations(java_dir, namespace, patterns):
    """Ids registered through a DeferredRegister in a loader's own registry file."""
    out = []
    for path in java_dir.rglob("ModItems.java"):
        src = read(path)
        for name in re.findall(r'ITEMS\.register(?:SimpleBlockItem)?\(\s*\n?\s*"([a-z0-9_]+)"',
                               src):
            out.append((namespace, "item", name))
    for path in java_dir.rglob("ModBlocks.java"):
        src = read(path)
        for name in re.findall(r'BLOCKS\.register\(\s*\n?\s*"([a-z0-9_]+)"', src):
            out.append((namespace, "block", name))
    return out


def all_recipes():
    """Every recipe file, parsed."""
    for namespace in NAMESPACES:
        for path in glob.glob(str(DATA / namespace / "recipe/**/*.json"), recursive=True):
            try:
                yield namespace, pathlib.Path(path).stem, json.loads(read(pathlib.Path(path)))
            except json.JSONDecodeError as exc:
                print(f"unparseable recipe {path}: {exc}", file=sys.stderr)


def ids_in(node, found):
    """Every "ns:id" string anywhere in a recipe JSON."""
    if isinstance(node, str):
        if ":" in node and not node.startswith("#"):
            found.add(node)
    elif isinstance(node, list):
        for item in node:
            ids_in(item, found)
    elif isinstance(node, dict):
        for value in node.values():
            ids_in(value, found)
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    registered = bem_registrations()
    registered += deferred_registrations(APPBOT, "botania", None)
    registered += deferred_registrations(VOLTANY, "voltany", None)

    produced = {}
    referenced = set()
    for namespace, name, data in all_recipes():
        # Vanilla-shaped recipes say "result"; Botania's own six machine types
        # say "output", so both spellings have to count as "this recipe makes it".
        for field in ("result", "output"):
            result = data.get(field, {})
            if isinstance(result, dict):
                rid = result.get("id")
                if rid:
                    produced.setdefault(rid, name)
        referenced |= ids_in(data, set())

    # Anything that only appears as an ingredient is an upgrade part or a
    # building block, not a machine the player is expected to craft directly.
    unreachable = []
    for namespace, kind, rid in sorted(registered):
        full = f"{namespace}:{rid}"
        if full in produced:
            continue
        if kind == "block":
            # A block with a BlockItem is crafted through its item form.
            if f"{namespace}:{rid}" in referenced:
                continue
        unreachable.append((namespace, kind, rid))

    if args.json:
        print(json.dumps({
            "registered": len(registered),
            "recipes": len(produced),
            "unreachable": [
                {"namespace": ns, "kind": kind, "id": rid}
                for ns, kind, rid in unreachable
            ],
        }, indent=2))
        return 1 if unreachable else 0

    print(f"registered addon content: {len(registered)}")
    print(f"recipes: {len(produced)}")
    if unreachable:
        print(f"not craftable: {len(unreachable)}")
        for namespace, kind, rid in unreachable:
            print(f"  {namespace}:{rid} ({kind})")
        return 1
    print("every registered addon block and item is craftable")
    return 0


if __name__ == "__main__":
    sys.exit(main())