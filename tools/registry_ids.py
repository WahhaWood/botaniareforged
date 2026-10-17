#!/usr/bin/env python3
"""Resolves Botania's runtime-generated registry ids for tools/check_data.py.

Botania builds most of its ids by composing name constants at class-init time,
for example:

    make(LibBlockNames.LIVINGROCK + LibBlockNames.STAIRS_SUFFIX, ...)
    make(petite(floating(LibBlockNames.SUBTILE_BELLETHORN)), ...)
    make(LibBlockNames.AZULEJO_PREFIX + 0, ...)

A plain grep of the Lib*Names tables therefore misses hundreds of real ids
(livingrock_stairs, floating_bellethorne_petite, azulejo_0, black_pavement)
and produces false positives in the checker.

This module does a small symbolic evaluation over the registration calls:
constants become strings, ``+`` concatenates, the helper functions apply their
infix/prefix, and a numeric operand appends an index. Anything it cannot
evaluate is skipped rather than guessed.
"""

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
BLOCKS = ROOT / "Xplat/src/main/java/vazkii/botania/common/block/BotaniaBlocks.java"
ITEMS = ROOT / "Xplat/src/main/java/vazkii/botania/common/item/BotaniaItems.java"
LIB_FILES = (
    ROOT / "Xplat/src/main/java/vazkii/botania/common/lib/LibBlockNames.java",
    ROOT / "Xplat/src/main/java/vazkii/botania/common/lib/LibItemNames.java",
)

# helper functions defined in BotaniaBlocks, extracted from their bodies
HELPER_PATTERNS = (
    (re.compile(r"static String (\w+)\(String orig\)\s*\{\s*return (?:orig \+ )?\"([a-z0-9_]*)\"(?: \+ orig)?;"), True),
)


def load_constants():
    """Map constant name -> value for every `String X = ...;` in the tables.

    Values are not always literals: ``LUMINIZER_SUFFIX = "_" + LUMINIZER``
    refers to another constant, so resolve references between constants too.
    """
    raw = {}
    for path in LIB_FILES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        for name, value in re.findall(r'String\s+(\w+)\s*=\s*([^;]+);', text):
            raw[name] = value.strip()

    # iterate until fixed point; unreferenced or self-referential stay unresolved
    consts = {}
    for name, expr in raw.items():
        resolved = resolve_constant(expr, raw, depth=0)
        if resolved is not None:
            consts[name] = resolved
    return consts


def resolve_constant(expr, raw, depth):
    """Value of one constant expression, or None if it cannot be evaluated."""
    if depth > 8:
        return None
    parts = [p.strip() for p in split_top_level_plus(expr)]
    out = []
    for part in parts:
        if re.fullmatch(r'"[a-z0-9_]*"', part):
            out.append(part.strip('"'))
        elif re.fullmatch(r"[A-Z][A-Z0-9_]*", part):
            if part not in raw:
                return None
            inner = resolve_constant(raw[part], raw, depth + 1)
            if inner is None:
                return None
            out.append(inner)
        elif re.fullmatch(r"\d+", part):
            out.append(part)
        else:
            return None
    return "".join(out)


def split_top_level_plus(expr):
    """Split on '+' that is not inside parentheses."""
    parts = []
    depth = 0
    current = []
    for ch in expr:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "+" and depth == 0:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    parts.append("".join(current))
    return parts


def load_helpers():
    """Map helper name -> (prefix, suffix) from its single-return body."""
    helpers = {}
    if not BLOCKS.exists():
        return helpers
    text = BLOCKS.read_text(encoding="utf-8", errors="ignore")
    body = re.compile(
        r"static String (\w+)\(String orig\)\s*\{\s*return\s+(?:orig \+\s*)?\"([a-z0-9_]*)\"(?:\s*\+\s*orig)?;\s*\}",
        re.S,
    )
    for name, literal in body.findall(text):
        # "floating_" + orig  ->  prefix form;  orig + "_petite"  ->  suffix form
        start = text.index(f"static String {name}(String orig)")
        segment = text[start : start + 220]
        returns = segment[segment.index("return") :]
        if returns.replace(" ", "").startswith("returnorig+"):
            helpers[name] = ("", literal)
        else:
            helpers[name] = (literal, "")
    return helpers


TOKEN = re.compile(
    r"""
    (?P<num>\d+)                        # numeric index
  | (?P<str>"[a-z0-9_]*")                # inline literal
  | (?P<lib>Lib(?:Item|Block)Names\.\w+) # table constant
  | (?P<fn>\w+)\s*\((?P<fnarg>.*)\)$     # helper call, possibly nested
  | (?P<sym>[A-Z][A-Z0-9_]*)              # bare constant
    """,
    re.X,
)


def evaluate(expr, consts, helpers, depth=0):
    """Best-effort string value of a registration expression, or None."""
    if depth > 6 or len(expr) > 400:
        return None

    out = []
    for part in split_top_level_plus(expr):
        part = part.strip()
        if not part:
            continue

        m = TOKEN.fullmatch(part)
        if not m:
            # a trailing field access such as LibBlockNames.FOO.bar
            sub = part.rsplit(".", 1)[0].strip()
            if sub != part:
                value = evaluate(sub, consts, helpers, depth + 1)
                if value is not None:
                    out.append(value)
                    continue
            return None

        if m.group("num"):
            out.append(m.group("num"))
        elif m.group("str"):
            out.append(m.group("str").strip('"'))
        elif m.group("fn"):
            fn = m.group("fn")
            if fn in helpers:
                inner = evaluate(m.group("fnarg"), consts, helpers, depth + 1)
                if inner is None:
                    return None
                prefix, suffix = helpers[fn]
                out.append(prefix + inner + suffix)
            else:
                return None
        elif m.group("lib"):
            name = m.group("lib").split(".", 1)[1]
            if name not in consts:
                return None
            out.append(consts[name])
        elif m.group("sym"):
            name = m.group("sym")
            if name not in consts:
                return None
            out.append(consts[name])

    return "".join(out)


def harvested_ids():
    """Every id the registration calls evaluate to."""
    consts = load_constants()
    helpers = load_helpers()

    # Registration helpers whose first argument is the registry id. Most are thin
# wrappers around make(...); makeBlockWithColoredVariants and
# makePetalApothecary both register <arg> directly.
MAKE_HELPERS = (
    "make",
    "makeBlockWithColoredVariants",
    "makePetalApothecary",
    "makeMysticalFlower",
    "makeMushroom",
    "makeBlockSet",
    "makeQuartz",
    "makeWall",
    "makeSlab",
    "makeStairs",
    "makeFence",
    "makeFenceGate",
    "makePane",
    "makeButton",
    "makePressurePlate",
    "makeSign",
    "makeHangingSign",
    "makeLeaves",
    "makeSapling",
    "makeShimmerrock",
    "makePortal",
    "makePool",
    "makeLotus",
    "makeMushroom",
)


def harvested_ids():
    """Every id the registration calls evaluate to."""
    consts = load_constants()
    helpers = load_helpers()

    ids = set()
    for path in (BLOCKS, ITEMS):
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")

        # The first argument may span lines and itself contain commas, so read
        # up to the matching close paren and cut at the last top-level comma.
        for match in re.finditer(r"\b(" + "|".join(MAKE_HELPERS) + r")\(\s*", text):
            start = match.end()
            arg = first_argument(text, start)
            if arg is None:
                continue
            value = evaluate(arg, consts, helpers)
            if value and re.fullmatch(r"[a-z0-9_]+", value):
                ids.add(value)

    return ids


def first_argument(text, start):
    """Text of the first argument of a call beginning at `start`."""
    depth = 0
    for i in range(start, min(len(text), start + 600)):
        ch = text[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
            if depth < 0:
                return None
        elif ch == "," and depth == 0:
            return text[start:i].strip()
    return None


if __name__ == "__main__":
    found = harvested_ids()
    print(f"resolved {len(found)} ids")
    for probe in ("livingrock_stairs", "floating_bellethorne_petite", "azulejo_0", "black_pavement"):
        print(f"  {probe}: {probe in found}")