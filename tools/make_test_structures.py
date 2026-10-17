#!/usr/bin/env python3
"""Generates the game test structure fixtures Botania's tests refer to.

Botania's port carries 25 @GameTest classes, but the structures they name
(``botania:block/apothecary_recipe`` and friends) are not published in any
upstream branch: only the five worldgen structures ship in the jar. So the
tests were dead code in this port too -- NeoForge never discovered them, and
even with @GameTestHolder they would have failed on a missing template.

TestingUtil.EMPTY_STRUCTURE points at fabric-gametest-api-v1:empty, which is
provided by the Fabric loader API and therefore never exists on NeoForge.

This script writes the fixtures as gzip NBT in the vanilla structure format
(size / palette / blocks / entities / DataVersion), from a declarative spec
kept in this file so the fixtures are reviewable and regenerable:

    python3 tools/make_test_structures.py

Output goes next to the worldgen structures in
Xplat/src/main/resources/data, which is the only resources source set that
reaches the jar on both loaders (src/generated/resources does not).
"""

import gzip
import pathlib
import re
import struct

ROOT = pathlib.Path(__file__).resolve().parent.parent
ROOT_OUT = ROOT / "Xplat/src/main/resources/data"

# DataVersion for Minecraft 1.21.1 (version.json: world_version).
DATA_VERSION = 3955

# Floor block for the test platforms. Vanilla's gametest structures use a
# polished block so failures are easy to spot, and so entities do not spawn.
FLOOR = "minecraft:smooth_stone"
BARRIER = "minecraft:barrier"


class Arena:
    """Builder for one structure: a size plus the blocks that fill it.

    Coordinates are the test's own relative BlockPos values, so a fixture can be
    read straight next to the test that asserts on it, but they are written one
    block lower than they are given: a template's bottom layer lands on relative
    y=1, so a block wanted at relative (x, y, z) goes in the file at (x, y-1, z).
    Getting this wrong puts every block one block too high, which shows up as
    the test finding whatever is at the height it did not ask about.

    Note that a structure is placed with Block.UPDATE_CLIENTS only, but
    BlockBehaviour.onPlace still runs, so a block that cannot survive where it
    lands is destroyed on the spot. A lever or button therefore needs a solid
    block on the side it attaches to, or it vanishes before the test sees it.
    """

    def __init__(self, w, h, d, floor=FLOOR):
        self.size = (w, h, d)
        self.floor = floor
        self.blocks = {}
        self.entities = []

    def set(self, x, y, z, block, nbt=None, **state):
        """Place a block, optionally with the data its block entity should hold.

        `nbt` reaches the block entity through the template's own per block `nbt`
        tag, which StructureTemplate hands to loadWithComponents. It is the only
        way a fixture can give a container its contents: the palette carries
        states and nothing else.
        """
        assert 0 <= x < self.size[0] and 1 <= y < self.size[1] and 0 <= z < self.size[2], \
            f"{(x, y, z)} outside {self.size}; relative y starts at 1, see above"
        self.blocks[(x, y - 1, z)] = (block, state, nbt)
        return self

    def items_nbt(self, *slots):
        """Container contents, in the shape ContainerHelper saves them.

        Each slot is (index, item id, count). 1.21.1 writes stacks through the
        item codec, so the keys are the lowercase `count` and `id`.
        """
        return {"Items": [{"Slot": i, "id": item, "count": count}
                          for i, item, count in slots]}

    def box(self, x0, y0, z0, x1, y1, z1, block, **state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, **state)
        return self

    def deck(self, y, x0=0, z0=0, x1=None, z1=None, block=FLOOR):
        """A solid platform at height y."""
        return self.box(x0, y, z0,
                        self.size[0] - 1 if x1 is None else x1, y,
                        self.size[2] - 1 if z1 is None else z1, block)

    def put(self, entries):
        """Apply (x, y, z, block[, props]) entries."""
        for entry in entries:
            x, y, z, block = entry[0], entry[1], entry[2], entry[3]
            props = entry[4] if len(entry) > 4 else {}
            self.set(x, y, z, block, **props)
        return self

    def lever(self, x, y, z):
        """A lever at (x, y, z) that survives being placed.

        A lever attaches to the face opposite its `facing` and is destroyed
        during onPlace when nothing is there. Structure placement still calls
        onPlace, so an unsupported lever is simply gone by the time the test
        runs and the failure reads as "Expected Lever, got Air". Support is
        therefore always placed, and `facing` is chosen to point at it.
        """
        self.set(x, y, z, "minecraft:lever", facing="west")
        self.set(x + 1, y, z, "minecraft:yellow_concrete")
        return self

    def button(self, x, y, z, facing="west"):
        """A stone button at (x, y, z), with the block it attaches to."""
        self.set(x, y, z, "minecraft:stone_button", facing=facing)
        dx = {"west": 1, "east": -1, "north": 0, "south": 0}[facing]
        dz = {"north": 1, "south": -1, "west": 0, "east": 0}[facing]
        self.set(x + dx, y, z + dz, FLOOR)
        return self

    def entity(self, x, y, z, entity_id, **nbt):
        """Add an entity at a fractional position, as a structure entity."""
        assert 0 <= int(x) < self.size[0] and 1 <= int(y) < self.size[1] and 0 <= int(z) < self.size[2], \
            f"entity {entity_id} at {(x, y, z)} outside {self.size}"
        self.entities.append({
            "pos": [float(x), float(y) - 1.0, float(z)],
            "blockPos": [int(x), int(y) - 1, int(z)],
            "id": entity_id,
            "nbt": nbt,
        })
        return self

    def mana_pool_near(self, x, y, z):
        """A creative mana pool at (x, y, z) with a floor under it.

        Functional flowers search for a pool within their binding radius rather
        than in the six neighbours, so the source can sit clear of the cells the
        test itself uses.
        """
        self.set(x, y, z, "botania:creative_mana_pool")
        self.set(x, y - 1, z, FLOOR)
        return self

    def mana_source(self, x, y, z):
        """A creative mana pool directly under (x, y, z).

        A spreader draws from a pool in one of its six neighbours and nowhere
        else (ManaSpreaderBlockEntity#commonTick walks Direction.values()), so
        a pool across the arena is invisible to it. Functional flowers search a
        radius instead, but sit next to their pool anyway in the arenas that
        work. Without a source the arena looks right and the mechanic simply
        never fires: a template also skips setPlacedBy, so a flower placed this
        way never gets the mana a hand-placed one would.
        """
        self.set(x, y - 1, z, "botania:creative_mana_pool")
        return self

    def spec(self):
        # Structure templates are placed in list order, and BlockBehaviour.onPlace
        # runs even with the UPDATE_CLIENTS flag placement uses. A block that
        # needs a neighbour therefore has to be written *after* that neighbour,
        # or it is destroyed before the support arrives. Plain filler goes first
        # so that every later block has something to attach to.
        ATTACHED = ("minecraft:lever", "minecraft:stone_button")

        def sort_key(item):
            (x, y, z), (block, _, _) = item
            if block == FLOOR:
                rank = 0
            elif block in ATTACHED:
                rank = 2
            else:
                rank = 1
            return (rank, x, y, z)

        blocks = [(x, y, z, block, dict(state), nbt)
                  for (x, y, z), (block, state, nbt) in sorted(self.blocks.items(), key=sort_key)]
        if self.floor:
            floor_blocks = [(x, 0, z, self.floor, {}, None) for x in range(self.size[0])
                            for z in range(self.size[2])]
            blocks = [b for b in floor_blocks if b[:3] not in self.blocks] + blocks
        return {"size": self.size, "blocks": blocks, "entities": self.entities}


def empty(w=3, h=3, d=3):
    """An arena with only a floor, for tests that build their own setup."""
    return Arena(w, h, d).spec()


# Botania ships the arena every one of its own game tests runs in, so this
# generator only has the two that are ours: the plain floor the registry-only
# tests use, and a bigger empty floor for the addon tests, which build their
# whole setup themselves. Everything else comes from
# import_upstream_structures.py.
#
# The DSL above is kept for those two and for anyone adding a test of their own:
# see the header for why a template's y is the test's y minus one, why supports
# have to be written before the things that attach to them, and how a block's
# own nbt carries a container's contents.
def fixtures():
    return {
        # RarityConsistencyTest and TranslationKeysTest read the registries and
        # need no blocks at all; FlowerPouchTest works on inventories and
        # LifeAggregatorTest places a spawner at BlockPos.ZERO, so relative y=0
        # has to exist. A 3x3x3 floor covers all of them.
        "botania:empty": empty(),

        # The addon tests put a machine down and then a pool two blocks away,
        # which the 3x3x3 above has no room for.
        "botania:addon_arena": empty(9, 9, 9),
    }



# ---------------------------------------------------------------------------
# NBT writing

def _tag_str(name, value):
    raw = value.encode("utf-8")
    return struct.pack(">B", 8) + struct.pack(">H", len(name)) + name.encode() + \
        struct.pack(">H", len(raw)) + raw


def _tag_compound_start(name):
    return struct.pack(">B", 10) + struct.pack(">H", len(name)) + name.encode()


def _tag_int(name, value):
    return struct.pack(">B", 3) + struct.pack(">H", len(name)) + name.encode() + \
        struct.pack(">i", value)


class IntArray:
    """An NBT int array, which SNBT writes as `[I; 1, 2, 3]`.

    Kept apart from a plain list because the game reads a UUID as an int array
    and would quietly ignore a list of ints.
    """

    def __init__(self, values):
        self.values = [int(v) for v in values]


class ByteArray:
    """An NBT byte array, which SNBT writes as `[B; 1b, 2b]`."""

    def __init__(self, values):
        self.values = [int(v) for v in values]


def _tag_byte(name, value):
    return struct.pack(">B", 1) + struct.pack(">H", len(name)) + name.encode() + \
        struct.pack(">b", value)


def _tag_long(name, value):
    return struct.pack(">B", 4) + struct.pack(">H", len(name)) + name.encode() + \
        struct.pack(">q", value)


def _tag_int_array(name, values):
    body = struct.pack(">Bi", 11, len(values)) + b"".join(struct.pack(">i", v) for v in values)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_byte_array(name, values):
    body = struct.pack(">Bi", 7, len(values)) + b"".join(struct.pack(">b", v) for v in values)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_value(name, value):
    """Write one named tag of whatever Python type the value came in as.

    A dict becomes a compound, a list a list of compounds, a bool a byte, and
    everything else the narrowest type that holds it. This only covers what a
    fixture needs, so it stays small rather than becoming a general codec.
    """
    if isinstance(value, dict):
        return _tag_compound(name, value)
    if isinstance(value, IntArray):
        return _tag_int_array(name, value.values)
    if isinstance(value, ByteArray):
        return _tag_byte_array(name, value.values)
    if isinstance(value, list):
        if value and all(isinstance(item, dict) for item in value):
            return _tag_list_of_compounds(name, [_entry_fields(item) for item in value])
        if all(isinstance(item, float) for item in value):
            return _tag_double_list(name, value)
        if all(isinstance(item, int) and not isinstance(item, bool) for item in value):
            return _tag_int_list(name, value)
        return _tag_list_of_strings(name, [str(item) for item in value])
    if isinstance(value, bool):
        return _tag_byte(name, int(value))
    if isinstance(value, int):
        if -2147483648 <= value <= 2147483647:
            return _tag_int(name, value)
        return _tag_long(name, value)
    if isinstance(value, float):
        return struct.pack(">B", 6) + struct.pack(">H", len(name)) + name.encode() + \
            struct.pack(">d", value)
    return _tag_str(name, str(value))


def _entry_fields(item):
    """The body of a list element compound: fields, with no tag byte."""
    out = b""
    for key, value in sorted(item.items()):
        out += _tag_value(key, value)
    return out + b"\x00"


def _tag_compound(name, fields):
    out = _tag_compound_start(name)
    for key, value in sorted(fields.items()):
        out += _tag_value(key, value)
    return out + b"\x00"


def _tag_double_list(name, values):
    # The element type is a byte but the list length is a 4 byte int, so the
    # pack format has to say ">Bi" rather than ">Bd".
    body = struct.pack(">Bi", 6, len(values)) + b"".join(struct.pack(">d", v) for v in values)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_float_list(name, values):
    body = struct.pack(">Bi", 5, len(values)) + b"".join(struct.pack(">f", v) for v in values)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_int_list(name, values):
    body = struct.pack(">Bi", 3, len(values)) + b"".join(struct.pack(">i", v) for v in values)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_list_of_strings(name, items):
    body = struct.pack(">Bi", 8, len(items))
    for item in items:
        raw = item.encode("utf-8")
        body += struct.pack(">H", len(raw)) + raw
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _tag_list_of_compounds(name, items):
    body = struct.pack(">Bi", 10, len(items)) + b"".join(items)
    return struct.pack(">B", 9) + struct.pack(">H", len(name)) + name.encode() + body


def _entry_name(raw):
    """Compact string, the name of a blockstate key."""
    n = len(raw)
    if n < 1 << 15:
        return struct.pack(">H", n) + raw.encode()
    raise ValueError("identifier too long")


def _list_entry():
    """Start of a compound that is a list element.

    A named field is written as tag + name + payload, but a list stores its
    element type once in the list header and then writes only the payloads, so
    an element starts at its first field with no tag byte and no name. The byte
    dump of a vanilla village structure is the reference for this.
    """
    return b""


def _palette_entry(block_name, state):
    out = _list_entry()
    out += _tag_str("Name", block_name)
    if state:
        props = _tag_compound_start("Properties")
        for key in sorted(state):
            value = str(state[key])
            props += struct.pack(">B", 8) + _entry_name(key) + \
                struct.pack(">H", len(value)) + value.encode()
        props += b"\x00"
        out += props
    out += b"\x00"
    return out


def _entity_entry(entity):
    """One structure entity: the position the template places it at, and its data.

    The position fields sit beside a nested `nbt` compound, and StructureTemplate
    only keeps an entity that has one: it reads `nbt` for the entity's own data
    and spawns nothing at all without it. The id goes inside that compound too,
    because that is where the entity type loader looks for it.
    """
    fields = dict(entity.get("nbt") or {})
    fields["id"] = entity["id"]

    out = _list_entry()
    out += _tag_double_list("pos", entity["pos"])
    out += _tag_int_list("blockPos", entity["blockPos"])
    out += _tag_double_list("Motion", [0.0, 0.0, 0.0])
    out += _tag_float_list("Rotation", [0.0, 0.0])
    out += _tag_compound("nbt", fields)
    out += b"\x00"
    return out


def build_nbt(spec_dict, data_version=None):
    size = spec_dict["size"]
    palette = []
    index = {}

    for _, _, _, block_name, state, _ in spec_dict["blocks"]:
        key = (block_name, tuple(sorted((k, str(v)) for k, v in state.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append((block_name, dict(state)))

    blocks = []
    for x, y, z, block_name, state, nbt in spec_dict["blocks"]:
        key = (block_name, tuple(sorted((k, str(v)) for k, v in state.items())))
        entry = _list_entry()
        entry += _tag_int_list("pos", [x, y, z])
        entry += _tag_int("state", index[key])
        if nbt:
            entry += _tag_compound("nbt", nbt)
        entry += b"\x00"
        blocks.append(entry)

    out = _tag_compound_start("")
    out += _tag_int_list("size", list(size))
    out += _tag_list_of_compounds(
        "entities", [_entity_entry(e) for e in spec_dict.get("entities", [])])
    out += _tag_list_of_compounds("blocks", blocks)
    out += _tag_int("DataVersion", spec_dict.get("data_version") or data_version or DATA_VERSION)
    out += _tag_list_of_compounds("palette", [_palette_entry(n, s) for n, s in palette])
    out += b"\x00"
    return out


def known_block_ids():
    """Every block id a fixture may name.

    Botania's ids come from the lang file, which is also what the game itself
    considers registered. This check exists because a structure template naming
    an unknown block is not an error the game reports: StructureTemplate skips
    it, the cell stays air, and the test fails much later with a message about
    the wrong block -- which reads like a mod bug rather than a fixture typo.
    """
    ids = set()
    for lang in ROOT.glob("*/src/main/resources/assets/botania/lang/en_us.json"):
        ids.update(re.findall(r'"block\.botania\.([a-z0-9_]+)"', lang.read_text(encoding="utf-8")))
    return ids


def validate_ids(specs):
    """Raise if a fixture names a botania block that is not registered."""
    known = known_block_ids()
    problems = []
    for name, spec_dict in specs.items():
        for _, _, _, block, _, _ in spec_dict["blocks"]:
            if not block.startswith("botania:"):
                continue
            block_id = block.split(":", 1)[1]
            if block_id not in known:
                problems.append(f"{name}: unknown block {block}")
    if problems:
        raise SystemExit("fixture block ids that do not exist:\n  " + "\n  ".join(problems))


def structure_path(name):
    """Resolve a fixture key to its file.

    Keys appear in two shapes: `botania:block/foo`, which is what the tests
    name, and `botania/empty`, which is the same thing written with a slash
    instead of a colon. Both have to land at
    data/<namespace>/structure/<path>.nbt or the game cannot load them.
    """
    namespace, colon, path = name.partition(":")
    if not colon:
        namespace, _, path = namespace.partition("/")
    return ROOT_OUT / namespace / "structure" / (path + ".nbt")


def main() -> int:
    written = 0
    specs = fixtures()
    validate_ids(specs)
    for name, spec_dict in sorted(specs.items()):
        target = structure_path(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        with gzip.open(target, "wb", compresslevel=9) as fh:
            fh.write(build_nbt(spec_dict))
        written += 1
        entities = len(spec_dict.get("entities", []))
        print(f"  {target.relative_to(ROOT_OUT)}  size={spec_dict['size']} "
              f"blocks={len(spec_dict['blocks'])} entities={entities}")
    print(f"\nwrote {written} structures to {ROOT_OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())