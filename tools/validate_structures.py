#!/usr/bin/env python3
"""Validates the game test structure fixtures.

Game test structures are gzip NBT and nothing checks them until a test tries
to load one, where a malformed file surfaces as an unrelated error deep in
the world generator. This reads each one back, checks the fields Minecraft
requires, and verifies every block lies inside the declared size.

Also used to confirm the generator in make_test_structures.py round-trips:
the reader here parsed a vanilla village structure before being trusted to
judge our own output.

Usage:
    python3 tools/validate_structures.py [dir ...]
"""

import gzip
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

DEFAULT_DIRS = (
    ROOT / "Xplat/src/main/resources/data/botania",
    ROOT / "NeoForge/src/generated/resources/data/botania",
)

EXPECTED_DATA_VERSION = 3955  # Minecraft 1.21.1


class Reader:
    def __init__(self, buf):
        self.b = buf
        self.i = 0

    def u1(self):
        self._need(1)
        v = self.b[self.i]
        self.i += 1
        return v

    def _need(self, n):
        if self.i + n > len(self.b):
            raise ValueError(f"need {n} byte(s) at {self.i}, have {len(self.b) - self.i}")

    def raw(self, n):
        self._need(n)
        v = self.b[self.i : self.i + n]
        self.i += n
        return v

    def i1(self):
        v = struct.unpack_from(">b", self.b, self.i)[0]
        self.i += 1
        return v

    def i2(self):
        v = struct.unpack_from(">h", self.b, self.i)[0]
        self.i += 2
        return v

    def u2(self):
        v = struct.unpack_from(">H", self.b, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from(">i", self.b, self.i)[0]
        self.i += 4
        return v

    def i8(self):
        v = struct.unpack_from(">q", self.b, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from(">f", self.b, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from(">d", self.b, self.i)[0]
        self.i += 8
        return v

    def string(self, n):
        return self.raw(n).decode("utf-8", "replace")


def payload(r, tag):
    """Read one tag payload.

    Every tag except a list element carries a 2-byte name field before its
    payload. Compounds that are elements of a list do not: Minecraft writes
    those straight into the payload, which the byte dump of a vanilla village
    structure confirms.
    """
    if tag == 1:
        return r.i1()
    if tag == 2:
        return r.i2()
    if tag == 3:
        return r.i4()
    if tag == 4:
        return r.i8()
    if tag == 5:
        return r.f4()
    if tag == 6:
        return r.f8()
    if tag == 7:
        return r.raw(r.i4())
    if tag == 8:
        return r.string(r.u2())
    if tag == 9:
        element = r.u1()
        count = r.i4()
        return [payload(r, element) for _ in range(count)]
    if tag == 10:
        out = {}
        while True:
            t = r.u1()
            if t == 0:
                return out
            name = r.string(r.u2())
            out[name] = payload(r, t)
    raise ValueError(f"unknown tag {tag}")


def read_structure(path):
    with gzip.open(path) as fh:
        buf = fh.read()
    r = Reader(buf)
    tag = r.u1()
    r.string(r.u2())
    root = payload(r, tag)
    if r.i != len(buf):
        raise ValueError(f"{len(buf) - r.i} trailing byte(s)")
    return root


def check(path, root):
    problems = []
    for key in ("size", "palette", "blocks", "entities", "DataVersion"):
        if key not in root:
            problems.append(f"missing {key}")

    size = root.get("size") or []
    if len(size) != 3:
        problems.append(f"size must have 3 entries, got {size}")

    version = root.get("DataVersion")
    # Older files are fine, Minecraft upconverts them: the worldgen structures
    # we ship are still 1.20.1 (3465). A newer one would be unreadable here.
    if not isinstance(version, int):
        problems.append(f"DataVersion missing or not an int: {version!r}")
    elif version > EXPECTED_DATA_VERSION:
        problems.append(f"DataVersion {version} is newer than {EXPECTED_DATA_VERSION}")

    palette = root.get("palette") or []
    # An arena with no blocks is legitimate: Botania ships one for the tests
    # that place everything themselves, and the game loads it happily.
    for entry in palette:
        if "Name" not in entry:
            problems.append(f"palette entry without Name: {entry}")
        for name, value in (entry.get("Properties") or {}).items():
            # A property is read as text and parsed against its own vocabulary.
            # A byte here reads back as "1" and is quietly turned down, so the
            # state keeps a default the arena never asked for.
            if isinstance(value, bool) or isinstance(value, (int, float)):
                problems.append(
                    f"palette entry {entry['Name']} has property {name}={value!r} "
                    "written as a number or a byte instead of text")

    width, height, depth = (list(size) + [0, 0, 0])[:3]
    for entity in root.get("entities") or []:
        pos = entity.get("blockPos")
        if pos is None or len(pos) != 3:
            problems.append(f"entity without a 3d blockPos: {entity}")
        elif not (0 <= pos[0] < width and 0 <= pos[1] < height and 0 <= pos[2] < depth):
            problems.append(f"entity at {pos} outside size {list(size)}")
        # An entity's own data, its id included, lives in a nested compound:
        # StructureTemplate spawns nothing for an entity without one.
        own = entity.get("nbt")
        if not isinstance(own, dict):
            problems.append(f"entity without an nbt compound: {entity}")
            continue
        if "id" not in own:
            problems.append(f"entity without an id: {entity}")
        for key in ("pos", "blockPos", "Motion", "Rotation"):
            if key not in entity:
                problems.append(f"entity is missing {key}")

    for block in root.get("blocks") or []:
        pos = block.get("pos")
        state = block.get("state")
        if pos is None or len(pos) != 3:
            problems.append(f"block without a 3d pos: {block}")
            continue
        if not (0 <= pos[0] < width and 0 <= pos[1] < height and 0 <= pos[2] < depth):
            problems.append(f"block {pos} outside size {list(size)}")
        if not isinstance(state, int) or not (0 <= state < len(palette)):
            problems.append(f"block {pos} has palette index {state}, palette has {len(palette)}")
        # Block entity data is how a fixture gives a container its contents, so
        # it has to be a compound the game can hand to loadWithComponents.
        nbt = block.get("nbt")
        if nbt is not None and not isinstance(nbt, dict):
            problems.append(f"block {pos} has nbt that is not a compound: {nbt!r}")
        if isinstance(nbt, dict):
            for stack in nbt.get("Items") or []:
                if not isinstance(stack, dict) or "id" not in stack or "count" not in stack:
                    problems.append(f"block {pos} has an item stack without id and count: {stack}")

    return problems


def _paths(args):
    if args:
        for a in args:
            p = pathlib.Path(a)
            yield from sorted(p.rglob("*.nbt")) if p.is_dir() else [p]
        return
    for d in DEFAULT_DIRS:
        if d.exists():
            yield from sorted(d.rglob("*.nbt"))


def main() -> int:
    total = bad = 0
    for path in _paths(sys.argv[1:]):
        total += 1
        try:
            root = read_structure(path)
            problems = check(path, root)
        except Exception as exc:  # noqa: BLE001 - report, don't crash the run
            root = None
            problems = [f"{type(exc).__name__}: {exc}"]
        try:
            shown = path.relative_to(ROOT)
        except ValueError:
            shown = path
        if problems:
            bad += 1
            print(f"FAIL {shown}")
            for p in problems[:8]:
                print(f"       {p}")
        else:
            print(
                f"ok   {shown}  size={root['size']} "
                f"blocks={len(root['blocks'])} palette={len(root['palette'])}"
            )
    print(f"\n{total - bad}/{total} structures valid")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())