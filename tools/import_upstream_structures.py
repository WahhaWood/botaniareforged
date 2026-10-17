#!/usr/bin/env python3
"""Convert Botania's SNBT arenas into the structures the game can load.

Upstream stores its game test arenas as text NBT with a friendlier shape than
StructureTemplate.load reads: the block list is called `data`, each entry names
its state as a string, and the palette is a list of those same strings. The game
wants `blocks` with numeric palette indices and a palette of state compounds, so
the conversion is a reshape rather than an invention.

The state strings are handed to the game verbatim as {Name, Properties}, which
is the same thing vanilla writes, so an unknown property is simply skipped by
NbtUtils.readBlockState and shows up as a wrong block in a test failure rather
than as a file that will not load.

Arenas upstream does not ship are left to make_test_structures.py: this script
only ever writes the ones it has files for, and it refuses to write a template
the generator owns.
"""

from __future__ import annotations

import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from make_test_structures import (  # noqa: E402  (path set up above)
    DATA_VERSION, ROOT_OUT, build_nbt, structure_path,
)
from make_test_structures import ByteArray, IntArray

RAW = HERE / "upstream_structures"

# ---------------------------------------------------------------------------
# SNBT reading

_INT_SUFFIXES = {"b": 1, "s": 2, "L": 4, "f": 4, "d": 8}


class SnbtError(Exception):
    pass


class Reader:
    """Just enough SNBT to read a structure file.

    SNBT has no schema, so values come back as Python ints, floats, strings,
    dicts and lists with the type tags resolved and the number suffixes
    applied. Block entity NBT needs nothing more: the byte, short and int
    suffixes all round-trip through the tag the game reads.
    """

    def __init__(self, text: str):
        self.text = text
        self.pos = 0
        self.start = 0
        self.typed = None

    def error(self, what: str) -> SnbtError:
        line = self.text.count("\n", 0, self.pos) + 1
        return SnbtError(f"{what} at line {line}")

    def skip_space(self) -> None:
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char in " \t\r\n":
                self.pos += 1
            elif char == "/" and self.text.startswith("//", self.pos):
                end = self.text.find("\n", self.pos)
                self.pos = len(self.text) if end < 0 else end + 1
            else:
                return

    def read(self) -> object:
        self.skip_space()
        if self.pos >= len(self.text):
            raise self.error("unexpected end of file")
        char = self.text[self.pos]
        if char == "{":
            return self.read_compound()
        if char == "[":
            return self.read_list()
        if char in "\"'":
            return self.read_quoted()
        return parse_value(self.read_bare())

    def read_compound(self) -> dict:
        assert self.text[self.pos] == "{"
        self.pos += 1
        out: dict = {}
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unclosed compound")
            if self.text[self.pos] == "}":
                self.pos += 1
                return out
            key = self.read_bare(quoted_ok=True)
            self.skip_space()
            if self.text[self.pos] != ":":
                raise self.error(f"expected ':' after {key!r}")
            self.pos += 1
            out[key] = self.read()
            self.skip_space()
            if self.pos < len(self.text) and self.text[self.pos] == ",":
                self.pos += 1

    def read_list(self) -> list:
        assert self.text[self.pos] == "["
        self.pos += 1
        self.start = self.pos
        # `[I; ...]` names the element type once, up front. Consume it before
        # parsing the payload, or the type letter reads as the first value.
        self.typed = None
        typed = re.match(r"([BILF]);", self.text[self.pos:self.pos + 2])
        if typed:
            self.typed = typed.group(1)
            self.pos += 2
        out: list = []
        while True:
            self.skip_space()
            if self.pos >= len(self.text):
                raise self.error("unclosed list")
            if self.text[self.pos] == "]":
                self.pos += 1
                return out
            out.append(self.read())
            self.skip_space()
            if self.pos < len(self.text) and self.text[self.pos] == ",":
                self.pos += 1
        if self.typed == "I":
            return IntArray(out)
        if self.typed == "B":
            return ByteArray(out)
        return out

    def read_quoted(self) -> str:
        quote = self.text[self.pos]
        self.pos += 1
        out = []
        while True:
            if self.pos >= len(self.text):
                raise self.error("unclosed string")
            char = self.text[self.pos]
            if char == "\\":
                out.append(self.text[self.pos + 1])
                self.pos += 2
                continue
            if char == quote:
                self.pos += 1
                return "".join(out)
            out.append(char)
            self.pos += 1

    def read_bare(self, quoted_ok: bool = False) -> str:
        if quoted_ok and self.pos < len(self.text) and self.text[self.pos] in "\"'":
            return self.read_quoted()
        start = self.pos
        while self.pos < len(self.text) and (
                self.text[self.pos].isalnum() or self.text[self.pos] in "_-.+"):
            self.pos += 1
        if start == self.pos:
            raise self.error(f"expected a value, found {self.text[self.pos]!r}")
        return self.text[start:self.pos]


def parse_value(token: str) -> object:
    """A bare SNBT token as the value it stands for."""
    if token in ("true", "false"):
        return token == "true"
    if re.fullmatch(r"-?\d+[bslfdBSLFD]?", token):
        digits, suffix = re.match(r"(-?\d+)([a-zA-Z]?)", token).groups()
        # b and s name byte and short tags; the game coerces an int tag into
        # either on read, and L/f/d name a long or a float, which become
        # doubles because a structure has no use for the exact width.
        if suffix.lower() in ("f", "d"):
            return float(digits)
        if suffix.lower() == "l":
            return float(digits)
        return int(digits)
    if re.fullmatch(r"-?\d*\.\d+([dDfF])?", token):
        return float(token.rstrip("dDfF"))
    return token


def parse_snbt(text: str) -> dict:
    reader = Reader(text)
    value = reader.read()
    if not isinstance(value, dict):
        raise SnbtError("a structure file has to be one compound")
    return value


# ---------------------------------------------------------------------------
# Reshaping

def split_state(state: str) -> tuple[str, dict]:
    """`mod:block{prop:value}` into the block id and its properties.

    Values keep their SNBT spelling: the game parses them itself, and a
    property it cannot read is one it silently skips, which is a clearer
    failure than a conversion guess.
    """
    brace = state.find("{")
    if brace < 0:
        return state, {}
    name = state[:brace]
    body = state[brace + 1:state.rfind("}")]
    props: dict = {}
    if not body:
        return name, props
    for part in re.split(r",(?![^{]*\})", body):
        if not part.strip():
            continue
        key, _, value = part.partition(":")
        props[key.strip()] = _normalise_value(value.strip())
    return name, props


def _normalise_value(text: str) -> object:
    """One block state property value, in the form the game reads it.

    A property is read with getString and then parsed against the property's own
    vocabulary, so a boolean has to be the text "true" or "false". Writing it as
    a byte instead leaves getString reading "1", BooleanProperty turns that down
    and the state silently keeps its default -- which for a lever or a lamp is
    the opposite of what the arena said.
    """
    if text.startswith("[") and text.endswith("]"):
        return [parse_value(part.strip()) for part in text[1:-1].split(",") if part.strip()]
    if text[:1] in "\"'":
        return text[1:-1]
    if text in ("true", "false"):
        return text
    return parse_value(text)


# The arenas were saved out of a world, so an entity's own NBT carries the
# absolute position it had there and the tile it stood on. Neither survives a
# template: the game rewrites Pos when it places an entity, and an item frame
# reads TileX/Y/Z back out to work out where it hangs, so a stale value puts it
# at the world origin. Both are rewritten below in template coordinates. What is
# left alone is what says what the entity is -- a spark's network flags, an item
# frame's item.
_ENTITY_OWN_DATA = frozenset({"pos", "blockPos", "id", "Pos",
                              "TileX", "TileY", "TileZ"})


# DyeColor's wire value is its name, not its index, and the arenas were saved
# when a lens stored the index.
_DYE_COLOURS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")

# What an item's old `tag` keys are called now. 1.21 moved item data onto
# components, so a tag the game no longer reads is simply data the lens loses:
# a paintslinger lens without its colour paints nothing at all.
_COMPONENT_NAMES = {
    "Damage": "minecraft:damage",
    "Unbreakable": "minecraft:unbreakable",
    "CustomModelData": "minecraft:custom_model_data",
    "color": "botania:lens_tint",
    "compositeLens": "botania:attached_lens",
}


def _component_value(name: str, value: object) -> object:
    """One legacy tag value in the shape its component codec expects."""
    if name == "botania:lens_tint" and isinstance(value, int):
        if not 0 <= value < len(_DYE_COLOURS):
            raise SnbtError(f"lens tint {value} is not a dye colour")
        return _DYE_COLOURS[value]
    if name == "botania:attached_lens" and isinstance(value, dict):
        return _item_stack(value)
    return value


def _item_stack(old: dict) -> dict:
    """A 1.20 item stack as the 1.21 codec reads one."""
    out: dict = {"id": old.get("id", "minecraft:air")}
    if "Count" in old:
        out["count"] = int(old["Count"])
    elif "count" in old:
        out["count"] = int(old["count"])
    components = {}
    for key, value in (old.get("tag") or {}).items():
        name = _COMPONENT_NAMES.get(key)
        if name is not None:
            components[name] = _component_value(name, value)
    if components:
        out["components"] = components
    return out


def _fix_values(value):
    """Keep an int array an int array; everything else comes through as read."""
    if isinstance(value, list) and value and all(isinstance(v, int) for v in value):
        return list(value)
    return value


def _modernise_items(nbt: dict) -> dict:
    """Rewrite every container's Items list into the current stack shape."""
    out = dict(nbt)
    items = out.get("Items")
    if isinstance(items, list):
        out["Items"] = [_item_stack(entry) if isinstance(entry, dict) else entry
                        for entry in items]
    return out


def to_spec(root: dict) -> dict:
    """Upstream's shape into the one build_nbt writes."""
    size = [int(axis) for axis in root["size"]]
    blocks = []
    entities = []

    for entry in root.get("data") or root.get("blocks") or []:
        pos = [int(axis) for axis in entry["pos"]]
        state = entry["state"]
        if isinstance(state, int):
            # Already the indexed form, with the palette holding state strings.
            name, props = split_state(str(root["palette"][state]))
        else:
            name, props = split_state(str(state))
        nbt = entry.get("nbt")
        if isinstance(nbt, dict):
            nbt = _modernise_items(nbt)
        blocks.append((pos[0], pos[1], pos[2], name, props, nbt))

    for entry in root.get("entities") or []:
        pos = [float(axis) for axis in entry["pos"]]
        block_pos = [int(axis) for axis in entry.get("blockPos", pos)]
        raw = dict(entry.get("nbt") or {})
        # An entity's type is written inside its own NBT, not beside it, which
        # is why a placeholder here turns every spark and item frame into a pig.
        entity_id = str(entry.get("id") or raw.get("id") or "minecraft:pig")
        extra = {k: _fix_values(v) for k, v in raw.items()
                 if k not in _ENTITY_OWN_DATA}
        extra["Pos"] = pos
        extra["TileX"] = block_pos[0]
        extra["TileY"] = block_pos[1]
        extra["TileZ"] = block_pos[2]
        if isinstance(extra.get("Item"), dict):
            # An item frame carries its filter item, and it has to be in the
            # current stack shape or the filter is an unlabelled boot.
            extra["Item"] = _item_stack(extra["Item"])
        entities.append({
            "pos": pos,
            "blockPos": block_pos,
            "id": entity_id,
            "nbt": _modernise_items(extra),
        })

    return {"size": (size[0], size[1], size[2]), "blocks": blocks, "entities": entities,
            "data_version": int(root.get("DataVersion", DATA_VERSION))}


def main() -> int:
    if not RAW.exists():
        raise SystemExit("no upstream_structures/; run fetch_upstream_structures.py first")

    written = 0
    for source in sorted(RAW.rglob("*.snbt")):
        rel = source.relative_to(RAW).with_suffix("")
        name = "botania/" + str(rel)
        spec = to_spec(parse_snbt(source.read_text(encoding="utf-8")))

        target = structure_path(name)
        raw = build_nbt(spec)
        target.parent.mkdir(parents=True, exist_ok=True)
        import gzip
        target.write_bytes(gzip.compress(raw))
        print(f"  {name}: {len(spec['blocks'])} blocks, {len(spec['entities'])} entities")
        written += 1
    print(f"{written} arenas imported")
    return 0


if __name__ == "__main__":
    sys.exit(main())