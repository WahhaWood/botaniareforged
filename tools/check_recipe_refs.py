#!/usr/bin/env python3
"""Checks that every item a recipe names is an id something actually registers.

A recipe that names a missing item is dropped by the recipe manager with a
parsing error and never reaches the recipe book, so the content silently goes
missing — the addon shipped an `upgrade_slot_add` recipe built on
`botania:crafting_halo`, which 1.21 renamed to `assembly_halo`.

Item ids are read from each mod's own lang file, which lists one entry per
registered item and block, so this needs no registry dump. Namespaces owned by
mods that are not present are reported separately: those are usually a
legitimate optional dependency, but they still deserve a look.

Usage:
    python3 tools/check_recipe_refs.py
"""

import glob
import json
import os
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "NeoForge/src/main/resources/data"

# Namespaces this pack expects to be installed alongside the mod. Their ids are
# checked against a mod jar when one is found next to the project, and skipped
# with a note otherwise.
EXTERNAL = ("minecraft", "ae2", "mythicbotany", "curios", "jei", "neoforge")


def ids_from_lang(namespace):
    """Every `item.<ns>.x` / `block.<ns>.x` the mod's lang file lists."""
    found = set()
    for path in glob.glob(
            str(ROOT / f"**/src/main/resources/assets/{namespace}/lang/en_us.json"),
            recursive=True):
        try:
            data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        for key in data:
            prefix = f"item.{namespace}."
            other = f"block.{namespace}."
            if key.startswith(prefix):
                found.add(key[len(prefix):])
            elif key.startswith(other):
                found.add(key[len(other):])
    return found


def ae2_ids():
    """Item ids from an AE2 jar in the Gradle cache, when one is present."""
    found = set()
    import zipfile
    for jar in glob.glob(
            os.path.expanduser(
                "~/.gradle/caches/modules-2/files-2.1/org.appliedenergistics/"
                "appliedenergistics2/*/*/appliedenergistics2-*.jar")):
        try:
            with zipfile.ZipFile(jar) as zf:
                names = set(zf.namelist())
        except (OSError, zipfile.BadZipFile):
            continue
        for entry in names:
            match = re.fullmatch(r"assets/ae2/lang/en_us\.json", entry)
            if match:
                try:
                    with zipfile.ZipFile(jar) as zf:
                        data = json.loads(zf.read(entry).decode("utf-8"))
                except (OSError, json.JSONDecodeError, KeyError):
                    continue
                for key in data:
                    for prefix in (f"item.ae2.", f"block.ae2."):
                        if key.startswith(prefix):
                            found.add(key[len(prefix):])
        break
    return found


def references(node, found):
    if isinstance(node, str):
        if ":" in node and not node.startswith("#"):
            found.add(node)
    elif isinstance(node, list):
        for item in node:
            references(item, found)
    elif isinstance(node, dict):
        # Only the fields that take an item id; "type", "category" and friends
        # are recipe mechanics and live in their own namespaces.
            for key in ("item", "id"):
                value = node.get(key)
                if isinstance(value, str) and ":" in value:
                    found.add(value)
            for value in node.values():
                if isinstance(value, (dict, list)):
                    references(value, found)
    return found


def main():
    known = {}
    for namespace in ("botania", "botanicalextramachinery", "voltany"):
        known[namespace] = ids_from_lang(namespace)
        print(f"{namespace}: {len(known[namespace])} ids")
    known["ae2"] = ae2_ids()
    print(f"ae2: {len(known['ae2'])} ids from the Gradle cache")

    broken = {}
    unknown_namespaces = {}
    for path in sorted(glob.glob(str(DATA / "**/recipe/**/*.json"), recursive=True)):
        relative = os.path.relpath(path, DATA)
        namespace = relative.split(os.sep)[0]
        try:
            data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            broken.setdefault(relative, []).append(f"unparseable: {exc}")
            continue
        for ref in sorted(references(data, set())):
            ref_ns, _, ref_id = ref.partition(":")
            if ref_ns in known:
                if ref_id not in known[ref_ns]:
                    broken.setdefault(relative, []).append(f"{ref_ns}:{ref_id}")
            elif ref_ns not in EXTERNAL:
                unknown_namespaces.setdefault(ref_ns, set()).add(ref_id)

    if broken:
        print(f"\nrecipes naming unregistered ids: {len(broken)}")
        for name, refs in sorted(broken.items()):
            print(f"  {name}")
            for ref in refs:
                print(f"    {ref}")
    else:
        print("\nevery item id a recipe names is registered")

    if unknown_namespaces:
        print("\nids in namespaces this pack does not ship (check the dependency):")
        for namespace, ids in sorted(unknown_namespaces.items()):
            print(f"  {namespace}: {sorted(ids)}")

    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())