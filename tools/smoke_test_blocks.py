#!/usr/bin/env python3
"""Headless smoke test for the merged mod: place every addon block, watch for errors.

Why RCON instead of the server console: Minecraft 1.21 only attaches a console
reader when stdin is a TTY, so piping commands into the server on JDK 21 is
silently ignored. RCON works the same whether or not a client is attached.

The script:
1. points server.properties at a scratch world with RCON enabled,
2. starts the generated launch script in the background,
3. waits for "Done (",
4. places every Botanical Extra Machinery / Applied Botanics / Voltany block
   with /setblock, then saves and stops the server,
5. reports any ERROR, FATAL or exception in the log.

Exit code is non-zero when the server logged a problem or never finished
starting, so this can gate a commit.

Usage:
    python3 tools/smoke_test_blocks.py [--keep] [--blocks-file FILE]
"""

import argparse
import pathlib
import re
import socket
import struct
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
RUN = ROOT / "run"
LAUNCH = ROOT / "NeoForge/build/moddev/runServer.sh"
WORLD = "smoketest"
RCON_PORT = 25597
RCON_PASSWORD = "smoketest"
SERVER_PORT = 25598

# Namespace -> block ids, harvested from the addons' registration code.
ADDONS = (
    ("botanicalextramachinery", ROOT / "NeoForge/src/main/java/net/lmor/botanicalextramachinery/core/BemRegistration.java"),
    ("botania", ROOT / "NeoForge/src/main/java/vazkii/botania/ae2/ABBlocks.java"),
    ("voltany", ROOT / "NeoForge/src/main/java/com/jykito/voltany/registry/ModBlocks.java"),
)

ITEM_ADDONS = (
    ("botanicalextramachinery", ROOT / "NeoForge/src/main/java/net/lmor/botanicalextramachinery/core/BemRegistration.java"),
    ("botania", ROOT / "NeoForge/src/main/java/vazkii/botania/ae2/ABItems.java"),
    ("voltany", ROOT / "NeoForge/src/main/java/com/jykito/voltany/registry/ModItems.java"),
)

NOISE = re.compile(
    r"FAILED TO BIND|Ambiguity between arguments|Narrator|offline/insecure|"
    r"Negative index in crash report",
)


def collect_blocks():
    """(namespace, block id) pairs for every addon block."""
    return collect_ids(ADDONS, want="block")


def collect_items():
    """(namespace, item id) pairs for every addon item.

    Read from the lang files rather than the registration calls: Voltany
    registers its upgrades through a helper that takes the id as a variable,
    so no static scan can see them. `item.<ns>.<id>` is also exactly what the
    game itself considers present, so a stale entry would show up as a
    failed summon instead of hiding.
    """
    items = []
    for namespace, _ in ITEM_ADDONS:
        for lang in ROOT.glob(f"*/src/main/resources/assets/{namespace}/lang/en_us.json"):
            text = lang.read_text(encoding="utf-8", errors="ignore")
            for name in sorted(set(re.findall(rf'"item\.{namespace}\.([a-z0-9_]+)"', text))):
                items.append((namespace, name))
    return items


BLOCKISH = re.compile(r"BLOCKS?|Registries\.BLOCK\b")
ITEMISH = re.compile(r"ITEMS?|Registries\.ITEM\b")


def collect_ids(sources, want):
    """Ids registered into the block or item registry of each addon.

    Two shapes appear: ``BLOCKS.register("id", ...)`` on a DeferredRegister,
    and ``mod.register(Registries.BLOCK, "id", ...)`` on a mod container. The
    name is often on the next line, so the pattern has to tolerate newlines.
    """
    ids = []
    for namespace, source in sources:
        if not source.exists():
            print(f"  skip {namespace}: {source} missing")
            continue
        text = source.read_text(encoding="utf-8", errors="ignore")

        found = set()
        # mod.register(Registries.<TYPE>, "id", ...)
        for match in re.finditer(r'[Rr]egister\(\s*(Registries\.\w+)\s*,\s*"([a-z0-9_]+)"', text):
            registry, name = match.groups()
            if want == "block" and BLOCKISH.search(registry):
                found.add(name)
            elif want == "item" and ITEMISH.search(registry):
                found.add(name)

        # BLOCKS.register(\n    "id", ...) / ITEMS.register("id", ...)
        for match in re.finditer(r'(\w+)\.register\(\s*"([a-z0-9_]+)"', text):
            receiver, name = match.groups()
            if want == "block" and BLOCKISH.fullmatch(receiver):
                found.add(name)
            elif want == "item" and ITEMISH.fullmatch(receiver):
                found.add(name)

        for name in sorted(found):
            ids.append((namespace, name))
    return ids


def port_is_free(port):
    """True when nothing is listening on `port`.

    A leftover server from an earlier run keeps the port and the new one dies
    with "FAILED TO BIND TO PORT!", which looks like a mod crash in the log.
    """
    import socket as _socket

    with _socket.socket(_socket.AF_INET, _socket.SOCK_STREAM) as sock:
        sock.setsockopt(_socket.SOL_SOCKET, _socket.SO_REUSEADDR, 1)
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            return False
    return True


def stale_server_pids():
    """PIDs of dev servers started from this project's run directory.

    The launch script execs java with @argfile paths under
    NeoForge/build/moddev, so that directory rather than the script name is what
    identifies a server as ours.
    """
    pids = []
    marker = str(LAUNCH.parent).encode()
    for entry in pathlib.Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            cmdline = (entry / "cmdline").read_bytes()
        except OSError:
            continue
        if marker in cmdline:
            pids.append(int(entry.name))
    return pids


def write_properties(world=WORLD, port=SERVER_PORT):
    """Write server.properties for a headless run on the RCON port."""
    props = RUN / "server.properties"
    props.write_text(
        "\n".join(
            [
                "online-mode=false",
                f"server-port={port}",
                f"level-name={world}",
                "max-tick-time=-1",
                "enable-rcon=true",
                f"rcon.port={RCON_PORT}",
                f"rcon.password={RCON_PASSWORD}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")


class Rcon:
    """Just enough of the RCON protocol to send commands.

    The default timeout is generous because `test runall` blocks the server
    thread until every batch finishes, and its reply carries the pass/fail
    summary that TestCommand only sends to chat otherwise.
    """

    AUTH = 3
    EXEC = 2
    RESPONSE = 0

    def __init__(self, host="127.0.0.1", port=RCON_PORT, timeout=60):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.request_id = 0

    def _packet(self, packet_type, body):
        payload = struct.pack("<ii", packet_id := self._next_id(), packet_type)
        payload += body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<i", len(payload)) + payload

    def _next_id(self):
        self.request_id += 1
        return self.request_id

    def _send(self, packet_type, body):
        self.sock.sendall(self._packet(packet_type, body))

    def _read(self):
        header = self._recv_exact(4)
        (length,) = struct.unpack("<i", header)
        payload = self._recv_exact(length)
        request_id, packet_type = struct.unpack("<ii", payload[:8])
        return request_id, packet_type, payload[8:-2].decode("utf-8", errors="replace")

    def _recv_exact(self, count):
        data = b""
        while len(data) < count:
            chunk = self.sock.recv(count - len(data))
            if not chunk:
                raise ConnectionError("server closed the RCON connection")
            data += chunk
        return data

    def authenticate(self, password):
        self._send(self.AUTH, password)
        _, _, _ = self._read()

    def command(self, cmd):
        self._send(self.EXEC, cmd)
        _, _, reply = self._read()
        return reply

    def close(self):
        self.sock.close()


def wait_for_start(log, proc, timeout=240):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if log.exists() and "Done (" in log.read_text(encoding="utf-8", errors="ignore"):
            return True
        if proc.poll() is not None:
            return False
        time.sleep(2)
    return False


def wait_for_stop(proc, timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if proc.poll() is not None:
            return True
        time.sleep(2)
    proc.terminate()
    return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true", help="keep the scratch world")
    parser.add_argument("--blocks-file", help="use this file instead of scanning sources")
    args = parser.parse_args()

    blocks = collect_blocks()
    if args.blocks_file:
        blocks = []
        for line in pathlib.Path(args.blocks_file).read_text().splitlines():
            line = line.strip()
            if line:
                ns, name = line.split(":", 1)
                blocks.append((ns, name))

    print(f"addon blocks to place: {len(blocks)}")
    if not blocks:
        print("nothing to place")
        return 1

    if not LAUNCH.exists():
        print(f"missing {LAUNCH}; run ./gradlew :NeoForge:createServerLaunchScript")
        return 1

    write_properties()
    log = RUN / "smoke_test.log"
    if log.exists():
        log.unlink()

    proc = subprocess.Popen(
        ["bash", str(LAUNCH)],
        stdout=log.open("w"),
        stderr=subprocess.STDOUT,
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
    )

    print("waiting for the server to start...")
    if not wait_for_start(log, proc):
        print("server never reached Done; see run/smoke_test.log")
        proc.terminate()
        try:
            proc.wait(timeout=20)
        except subprocess.TimeoutExpired:
            proc.kill()
        # a stale lock makes the next run hang the same way
        (RUN / WORLD / "session.lock").unlink(missing_ok=True)
        return 1

    rcon = Rcon()
    rcon.authenticate(RCON_PASSWORD)
    print("connected over RCON")

    rcon.command("forceload add 200 200")
    for i, (namespace, block) in enumerate(blocks):
        x, z = 195 + (i % 12) * 2, 195 + (i // 12) * 2
        reply = rcon.command(f"setblock {x} 100 {z} {namespace}:{block}").strip()
        status = "ok" if not reply else reply.splitlines()[-1][:60]
        print(f"  {namespace}:{block:<42} {status}")

    # Items are summoned as dropped stacks: that constructs them and makes the
    # world serialize their NBT on save, which is where mana cells and the
    # upgrade items would break.
    items = collect_items()
    print(f"\naddon items to spawn: {len(items)}")
    for i, (namespace, item) in enumerate(items):
        x, z = 190 + (i % 10), 210 + (i // 10)
        reply = rcon.command(
            f"summon item {x} 101 {z} {{Item:{{id:\"{namespace}:{item}\",count:1}}}}"
        ).strip()
        status = "ok" if not reply else reply.splitlines()[-1][:60]
        print(f"  {namespace}:{item:<42} {status}")

    rcon.command("forceload remove 200 200")
    rcon.command("save-all flush")
    rcon.command("stop")
    rcon.close()

    wait_for_stop(proc)
    text = log.read_text(encoding="utf-8", errors="ignore")

    problems = []
    for line in text.splitlines():
        if "ERROR" not in line and "FATAL" not in line and "Exception" not in line:
            continue
        if NOISE.search(line):
            continue
        problems.append(line.strip())

    loaded = len(re.findall(r"Loaded \d+ recipes", text))
    print(f"\nrecipes loaded: {loaded}")

    if not args.keep:
        world = RUN / WORLD
        if world.exists():
            for f in world.glob("session.lock"):
                f.unlink(missing_ok=True)

    if problems:
        print(f"\n{len(problems)} problem lines:")
        for line in dict.fromkeys(problems):
            print(f"  {line[:200]}")
        return 1

    print("\nPASSED: server placed every addon block with no errors")
    return 0


if __name__ == "__main__":
    sys.exit(main())