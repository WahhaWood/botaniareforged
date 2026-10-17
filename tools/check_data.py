#!/usr/bin/env python3
"""Checks that the merged mod's data files resolve against its own registries.

The mod merges Botania with three addons (Botanical Extra Machinery, Applied
Botanics, Voltany) under the ``botania`` namespace, and its data tree was
inherited from the upstream jars across several Minecraft versions.  Stale ids
break silently: the affected recipe or tag is just skipped by the game, so the
only visible symptom is a line in a server log.

Checks, all offline (no game launch needed):

1. every ``botania:`` item/block id referenced by the data tree is registered;
2. every tag reference points at a tag some mod actually ships;
3. no data file uses a pre-1.21 condition key or removed loot function.

Item and block ids are read from the ``Lib*Names`` constants and from the
registration tables, so this stays correct as the port grows.

Run:  python3 tools/check_data.py
Exits non-zero when something is wrong, so it can gate a commit.
"""

import json
import pathlib
import re
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
NS = "botania"

# ids owned by addons that are not merged into this jar
EXTERNAL_NAMESPACES = {"ae2", "curios", "quark", "mythicbotany", "architectury"}

# ---------------------------------------------------------------------------
# registry extraction

ITEM_NAMES = ROOT / "Xplat/src/main/java/vazkii/botania/common/lib/LibItemNames.java"
BLOCK_NAMES = ROOT / "Xplat/src/main/java/vazkii/botania/common/lib/LibBlockNames.java"


def constants_from(path):
    """All `String NAME = "value";` constants in a file."""
    if not path.exists():
        return set()
    text = path.read_text(encoding="utf-8", errors="ignore")
    return set(re.findall(r'String\s+\w+\s*=\s*"([a-z0-9_]+)"', text))


def java_source_ids():
    """Registered ids harvested from every registration helper in the tree.

    Covers the addon modules too, which register with their own helpers
    (BemRegistration, ABItems, ABBlocks, ...) and so are not in the Lib*Names
    tables.
    """
    ids = set()
    sources = list((ROOT / "Xplat/src/main/java").rglob("*.java"))
    sources += list((ROOT / "NeoForge/src/main/java").rglob("*.java"))

    patterns = [
        # make(LibItemNames.SOMETHING, ...) / make(botaniaRL("x"), ...)
        r'make\(\s*(?:LibItemNames|LibBlockNames)\.\w+\s*,',
        r'botaniaRL\(\s*"([a-z0-9_]+)"',
        # a deferred field initialised through a local helper, as in
        # `OVERCLOCK_1 = upgrade("overclock_1", ...)`
        r'=\s*\w+\(\s*"([a-z0-9_]+)"',
        r'mod\.register\(\s*Registries\.\w+\s*,\s*"([a-z0-9_]+)"',
        r'register\(\s*"([a-z0-9_]+)"',
        r'\.register\(\s*"([a-z0-9_]+)"',
        r'registerSimpleItem\(\s*"([a-z0-9_]+)"',
        # the ME machine kinds: an enum constant carries the id, ABMachines registers by it
        r'^\s*[A-Z_]+\(\s*"(me_[a-z0-9_]+)"',
    ]
    for src in sources:
        text = src.read_text(encoding="utf-8", errors="ignore")
        for pat in patterns[1:]:
            ids.update(re.findall(pat, text, re.MULTILINE))
    return ids


def all_constants():
    """Text of every Lib*Names table, so templates can be found anywhere."""
    chunks = []
    for base in ("Xplat/src/main/java", "NeoForge/src/main/java"):
        for name in ("LibItemNames", "LibBlockNames", "LibNames"):
            for path in (ROOT / base).rglob(f"{name}.java"):
                chunks.append(path.read_text(encoding="utf-8", errors="ignore"))
    return "\n".join(chunks)


def addon_ids():
    """Ids registered by the addons, from their name tables."""
    ids = set()
    for name in ("LibNames", "LibItemNames", "LibBlockNames"):
        for path in (ROOT / "NeoForge/src/main/java").rglob(f"{name}.java"):
            ids |= constants_from(path)
    return ids


# Ids that only exist at runtime, built from a template rather than written out
# in Java. Without these the checker reports hundreds of false positives, so it
# has to know which ids the mod synthesises.
DYE_COLORS = (
    "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown "
    "green red black"
).split()


def generated_ids():
    """Ids the registries build at runtime, plus the coloured variants.

    Most ids are composed from name constants at class-init time
    (LIVINGROCK + STAIRS_SUFFIX, petite(floating(BELLETHORN)), AZULEJO_PREFIX + 0),
    which a plain grep cannot see. registry_ids.py evaluates those
    expressions; on top of that we expand the template families the
    registries generate: the 16 dye colours, the optional coloured-variant
    infixes, and the block prefixes.

    Every family is expanded from one fixed base set. Deriving from the
    growing result instead compounds the families into each other and
    produces tens of millions of nonsense ids, which would make the id
    check pass for anything.
    """
    try:
        sys.path.insert(0, str(ROOT / "tools"))
        from registry_ids import harvested_ids, load_helpers

        harvested = harvested_ids()
        helper_prefixes = [p for p, _ in load_helpers().values() if p]
    except ImportError:
        harvested = set()
        helper_prefixes = ["floating_", "potted_"]

    constants = all_constants()
    infixes = re.findall(r'String\s+\w*INFIX\w*\s*=\s*"(_[a-z0-9_]+)"', constants)
    suffixes = re.findall(r'String\s+\w+\s*=\s*"(_[a-z0-9_]+)"', constants)
    prefixes = helper_prefixes + re.findall(r'String\s+\w+\s*=\s*"([a-z]+_)"', constants)

    # The one set everything else is derived from: declared names plus the ids
    # harvested from the registration calls.
    bases = {
        b
        for b in (constants_from(BLOCK_NAMES) | constants_from(ITEM_NAMES) | harvested)
        if b and not b.startswith("#") and re.fullmatch(r"[a-z0-9_]+", b)
    }

    ids = set(bases)
    colors = DYE_COLORS

    # <color>_<base>
    coloured = {f"{c}_{b}" for b in bases for c in colors}
    # <color><infix><base>, e.g. black + _covered_ + mana_spreader
    infixed = {f"{c}{i}{b}" for b in bases for i in infixes for c in colors}
    # <color><suffix> and <color><suffix without underscore>
    suffixed = {f"{c}{s}" for s in suffixes for c in colors} | {f"{c}{s[1:]}" for s in suffixes for c in colors}
    # prefix helpers over the plain, coloured and suffixed names, since
    # makePottedMundaneFlower(BLACK_MYSTICAL_FLOWER) composes the potted_
    # prefix with the <color>_<suffix> form
    seed = bases | coloured | suffixed
    prefixed = {f"{p}{s}" for p in prefixes for s in seed}

    ids |= coloured | infixed | suffixed | prefixed
    return ids


# ---------------------------------------------------------------------------
# tag handling

# Since 1.21 the registry type is not part of a tag id, so
# data/<ns>/tags/item/foo.json is the tag <ns>:foo. Some paths are two deep.
TAG_TYPE_PREFIXES = (
    "worldgen/structure_set",
    "worldgen/configured_feature",
    "worldgen/placed_feature",
    "worldgen/biome",
    "worldgen/carver",
    "worldgen/structure",
    "banner_pattern",
    "damage_type",
    "entity_type",
    "game_event",
    "block",
    "item",
    "fluid",
)


def strip_tag_type(tail):
    joined = "/".join(tail)
    for prefix in TAG_TYPE_PREFIXES:
        if joined.startswith(prefix + "/"):
            return joined[len(prefix) + 1:]
    return None


def own_tag_names():
    """Tag ids shipped by any module of this mod."""
    names = set()
    for base in DATA_ROOTS:
        tags = ROOT / base / "data"
        if not tags.exists():
            continue
        for path in tags.rglob("*.json"):
            rel = path.relative_to(tags)
            if "tags" not in rel.parts:
                continue
            tail = list(rel.parts[rel.parts.index("tags") + 1:])
            if len(tail) < 2:
                continue
            tail[-1] = path.stem
            stripped = strip_tag_type(tail)
            if stripped:
                names.add(f"{rel.parts[0]}:{stripped}")
    return names


def game_tag_names():
    """Tag ids provided by vanilla and NeoForge, read from the dev jars.

    Note: the vanilla jar is named "...-aka-minecraft-resources.jar", so the
    sources-jar test must match the suffix rather than the substring
    "sources", or every vanilla tag is skipped.
    """
    names = set()
    alt = "|".join(p.replace("/", r"\/") for p in TAG_TYPE_PREFIXES)
    entry_re = re.compile(r"data/([a-z0-9_.-]+)/tags/(" + alt + r")/([a-z0-9_./-]+)\.json")

    for jar in ROOT.rglob("build/moddev/artifacts/*.jar"):
        if jar.name.endswith("-sources.jar"):
            continue
        try:
            with zipfile.ZipFile(jar) as zf:
                for entry in zf.namelist():
                    m = entry_re.fullmatch(entry)
                    if m:
                        ns, _, path = m.groups()
                        names.add(f"{ns}:{path}")
        except (zipfile.BadZipFile, OSError):
            continue
    return names


# ---------------------------------------------------------------------------
# checks

DATA_ROOTS = (
    "Xplat/src/main/resources",
    "NeoForge/src/main/resources",
    "Xplat/src/generated/resources",
    "NeoForge/src/generated/resources",
)


def data_files():
    """Every data file the game ships, handwritten and datagen output alike.

    The bulk of the content lives in src/generated, so checking only
    src/main/resources misses most of it.
    """
    for base in DATA_ROOTS:
        root = ROOT / base / "data"
        if not root.exists():
            continue
        for path in sorted(root.rglob("*.json")):
            try:
                yield path, json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError as exc:
                yield path, exc


def check_ids(known):
    """Referenced botania: ids must be registered."""
    unresolved = {}
    parsed = 0
    for path, data in data_files():
        if isinstance(data, json.JSONDecodeError):
            print(f"  malformed JSON: {path.relative_to(ROOT)}: {data}")
            continue
        parsed += 1

        refs = []

        def walk(node):
            if isinstance(node, dict):
                for key, value in node.items():
                    if key in ("item", "id") and isinstance(value, str):
                        refs.append(value)
                    else:
                        walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)

        walk(data)
        for ref in refs:
            if ":" not in ref:
                continue
            ns, _, name = ref.partition(":")
            if ns != NS or name.startswith("#"):
                continue
            if name not in known:
                unresolved.setdefault(name, []).append(path)

    return parsed, unresolved


def check_tag_refs(provided):
    unresolved = []
    for path, data in data_files():
        if isinstance(data, json.JSONDecodeError):
            continue

        refs = []

        def walk(node):
            if isinstance(node, dict):
                for key, value in node.items():
                    if key == "values" and isinstance(value, list):
                        refs.extend(v for v in value if isinstance(v, str))
                    else:
                        walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)

        walk(data)
        for ref in refs:
            if not ref.startswith("#"):
                continue
            name = ref[1:].split("+", 1)[0]
            if name not in provided:
                unresolved.append((path, ref))

    return unresolved


def check_legacy_keys():
    """Pre-1.21 keys that the game ignores or rejects."""
    problems = []
    for path, data in data_files():
        if isinstance(data, json.JSONDecodeError):
            continue
        text = path.read_text(encoding="utf-8")
        if path.parent.name == "recipe" and re.search(r'^\s*"conditions"', text, re.M):
            problems.append((path, '"conditions" should be "neoforge:conditions"'))
        for removed in ("random_chance_with_looting", "looting_enchant"):
            if removed in text:
                problems.append((path, f"{removed} was removed in 1.21"))
        for stale in ("#forge:", '"condition": "killer_player"'):
            if stale in text:
                problems.append((path, f"stale 1.20 id {stale}"))
    return problems


def referenced_lang_keys():
    """Lang keys the code asks for as translatable messages.

    Botania uses ``item.*`` prefixed keys for plain messages too, e.g.
    LensItem names the composite lens through
    ``Component.translatable("item.botania.composite_lens", ...)``. Those are
    not registrations and must not be reported as missing.
    """
    used = set()
    for base in ("Xplat/src/main/java", "NeoForge/src/main/java"):
        root = ROOT / base
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            used.update(re.findall(r'"(?:item|block|entity)\.[a-z0-9_]+\.[a-z0-9_]+"', text))
    return used


def check_lang_keys(known):
    """Lang entries must correspond to something that exists.

    An `item.botania.foo` entry with no registered item advertises something
    that can neither be obtained nor summoned, and the game never warns. This
    is how the dead AE2 cell branches of Botanical Extra Machinery showed up:
    recipes for them survived, gated behind a mod that is now merged away.

    Keys the code references as messages are exempt.
    """
    referenced = referenced_lang_keys()
    stale = []
    for base in DATA_ROOTS:
        assets = ROOT / base / "assets"
        if not assets.exists():
            continue
        for path in assets.rglob("lang/en_us.json"):
            namespace = path.relative_to(assets).parts[0]
            text = path.read_text(encoding="utf-8", errors="ignore")
            for kind in ("item", "block", "entity"):
                pattern = rf'"{kind}\.{re.escape(namespace)}\.([a-z0-9_]+)"'
                for name in sorted(set(re.findall(pattern, text))):
                    if name in known:
                        continue
                    if f'"{kind}.{namespace}.{name}"' in referenced:
                        continue
                    stale.append((path, f"{kind}.{namespace}.{name}"))
    return stale


def main() -> int:
    known = constants_from(ITEM_NAMES) | constants_from(BLOCK_NAMES)
    known |= java_source_ids() | addon_ids() | generated_ids()
    print(f"{NS}: {len(known)} ids known from the registries")

    parsed, unresolved = check_ids(known)
    provided = own_tag_names() | game_tag_names()
    bad_tags = check_tag_refs(provided)
    legacy = check_legacy_keys()
    stale_lang = check_lang_keys(known)

    failed = False
    print(f"\nparsed {parsed} data files")
    print(f"known tags: {len(provided)}")

    if unresolved:
        failed = True
        print(f"\nFAIL unresolved {NS} ids ({len(unresolved)}):")
        for name in sorted(unresolved):
            first = unresolved[name][0].relative_to(ROOT)
            extra = len(unresolved[name]) - 1
            suffix = f" (+{extra} more)" if extra > 0 else ""
            print(f"  {name}{suffix}  <- {first}")
    else:
        print("ok    all referenced ids resolve")

    if bad_tags:
        failed = True
        print(f"\nFAIL tag references with no matching tag ({len(bad_tags)}):")
        for path, ref in bad_tags[:20]:
            print(f"  {ref}  <- {path.relative_to(ROOT)}")
    else:
        print("ok    every tag reference resolves")

    if legacy:
        failed = True
        print(f"\nFAIL pre-1.21 leftovers ({len(legacy)}):")
        for path, msg in legacy[:20]:
            print(f"  {msg}  <- {path.relative_to(ROOT)}")
    else:
        print("ok    no pre-1.21 data keys")

    if stale_lang:
        failed = True
        print(f"\nFAIL lang keys with no matching registration ({len(stale_lang)}):")
        for path, key in stale_lang[:25]:
            print(f"  {key}  <- {path.relative_to(ROOT)}")
    else:
        print("ok    every lang key resolves")

    print("\n" + ("FAILED" if failed else "PASSED"))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())