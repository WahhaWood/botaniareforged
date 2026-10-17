#!/usr/bin/env python3
"""Runs Botania's game tests on the dev server and reports which pass.

Why RCON instead of the server console: Minecraft 1.21 only attaches a console
reader when stdin is a TTY, so piping `test runall` into the server on JDK 21 is
silently ignored. RCON works whether or not a client is attached.

The script:
1. points server.properties at a scratch world with RCON enabled,
2. starts the generated launch script,
3. runs `/test runall`,
4. waits for the run to finish, parses the per-test log lines,
5. stops the server and prints a pass/fail table.

Exit code is non-zero when any required test failed, so this can gate a commit.

Usage:
    python3 tools/run_gametests.py                 # all tests
    python3 tools/run_gametests.py --filter relay  # only matching tests
"""

import argparse
import os
import pathlib
import re
import signal
import subprocess
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from smoke_test_blocks import ROOT, stale_server_pids, wait_for_stop

WORLD = "gametest"
# The system default JDK is 8 here and the NeoForge plugin refuses it.
JAVA_HOME = "/usr/lib/jvm/java-21-openjdk"
# The GameTestServer run configuration, not the plain server: it builds its own
# flat world, runs every registered test and exits when done, which is why
# there is no RCON here.
LAUNCH = ROOT / "NeoForge/build/moddev/runGameTestServer.sh"
GAME_DIR = ROOT / "run-gametest"

# `test runall` replies with these lines when every batch has finished
# (TestCommand#showTestSummaryIfAllDone).
# GameTestServer prints a progress bar every second and finishes with this block.
BARS = re.compile(r"\]:\s*(\[[+_ X]+\])")
COMPLETE = re.compile(r"=+\s*(\d+) GAME TESTS COMPLETE IN .*? =+")
FAILED_REQUIRED = re.compile(r"(\d+) required tests failed")
FAILED_OPTIONAL = re.compile(r"(\d+) optional tests failed")
# LogTestReporter logs "<test> failed at <pos>! <error>" for a required test and
# "(optional) <test> failed at <pos>. <error>" for an optional one.
# The position is BlockPos.toShortString(), which looks like "[12, 5, 3]" and so
# contains spaces; a required failure is separated from the error by "!" and an
# optional one by ".". Neither appears inside the bracketed position.
FAILURE = re.compile(
    r"\]:\s*\(optional\)\s+([\w.]+) failed at (.+?)\.\s+(.*)$"
    r"|\]:\s*([\w.]+) failed at (.+?)!\s*(.*)$")

# Exceptions the mod throws on purpose, or that say nothing about our code.
NOISE = re.compile(
    r"FAILED TO BIND|Ambiguity between arguments|Narrator|offline/insecure|"
    r"Negative index in crash report|UnsupportedClassVersionError|"
    # An item frame keeps the tile it was saved at and refuses one that is more
    # than sixteen blocks from where it is being placed, which is every arena:
    # the game writes the position itself and the frame lands correctly anyway.
    r"Block-attached entity at invalid position|"
    # The AE2 CPU mixin also targets AdvancedAE's quantum computer under @Pseudo; the
    # dev run has no AdvancedAE, so Mixin warns once per target class and skips it.
    r"Error loading class: net/pedroksl/advanced_ae/",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout", type=int, default=1800, help="seconds to wait for the run")
    parser.add_argument("--no-build", action="store_true", help="skip refreshing the resources")
    parser.add_argument("--verbose", action="store_true", help="print the tail of the log")
    args = parser.parse_args()

    if not LAUNCH.exists():
        print(f"missing {LAUNCH}; run ./gradlew :NeoForge:createGameTestServerLaunchScript")
        return 1

    # The fixtures live in Xplat/src/main/resources and reach the game through
    # NeoForge/build/resources/main, which Gradle only refreshes when asked.
    # Without this the run silently tests the previous fixtures.
    if not args.no_build:
        print("refreshing resources...")
        result = subprocess.run(
            [str(ROOT / "gradlew"), ":NeoForge:createGameTestServerLaunchScript",
             "--console=plain", "-q"],
            cwd=ROOT, env={**os.environ, "JAVA_HOME": JAVA_HOME},
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(result.stdout[-2000:])
            print(result.stderr[-2000:])
            return 1

    # A leftover run from a previous invocation holds the game directory's lock
    # and makes this one hang with no output at all.
    stale = stale_server_pids()
    if stale:
        print(f"stopping {len(stale)} leftover server(s): {stale}")
        for pid in stale:
            try:
                os.kill(pid, signal.SIGTERM)
            except OSError:
                pass
        for _ in range(15):
            if not stale_server_pids():
                break
            time.sleep(1)

    GAME_DIR.mkdir(parents=True, exist_ok=True)
    (GAME_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    log = GAME_DIR / "gametest.log"
    if log.exists():
        log.unlink()

    proc = subprocess.Popen(
        ["bash", str(LAUNCH)],
        # Line buffered: the default block buffer would hold the whole log back
        # until the process exits, so progress would be invisible.
        stdout=log.open("w", buffering=1),
        stderr=subprocess.STDOUT,
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
    )

    try:
        print("running the game test server, this takes a few minutes...")
        # The launch script already passes --nogui, so the log is the only
        # output. Progress is a run of [+++xx] bars, and the run ends with the
        # "GAME TESTS COMPLETE" block.
        deadline = time.time() + args.timeout
        finished = False
        last_bars = -1
        while time.time() < deadline:
            text = log.read_text(encoding="utf-8", errors="ignore")
            if COMPLETE.search(text):
                finished = True
                break
            if proc.poll() is not None:
                text = log.read_text(encoding="utf-8", errors="ignore")
                finished = bool(COMPLETE.search(text))
                break
            bars = [m.group(1) for m in BARS.finditer(text)]
            if bars and len(bars[-1]) != last_bars:
                last_bars = len(bars[-1])
                print(f"\r{bars[-1][:80]}", end="", flush=True)
            time.sleep(3)
        print()

        if not finished:
            print("the run did not finish before the timeout; see run/gametest.log")
            if args.verbose:
                print("\n".join(text.splitlines()[-40:]))
            return 1

        text = log.read_text(encoding="utf-8", errors="ignore")
        return report(text, args)
    finally:
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()


def report(text, args) -> int:
    # Vanilla's LogTestReporter logs failures and says nothing about passes, so
    # the totals come from GameTestServer's closing block and the log supplies
    # which tests failed and why.
    ran = COMPLETE.search(text)
    failed_required = FAILED_REQUIRED.search(text)
    failed_optional = FAILED_OPTIONAL.search(text)
    # The game words this "All 266 required tests passed", with the count in
    # the middle.
    all_passed = re.search(r"All \d+ required tests passed", text) is not None

    if ran:
        print(f"\n{ran.group(1)} tests were run")
    else:
        print("\nno completion block in the log; run may not have finished")
        if args.verbose:
            print("\n".join(text.splitlines()[-60:]))
        return 1

    failures = {}
    for line in text.splitlines():
        match = FAILURE.search(line)
        if not match:
            continue
        # The two alternatives in the pattern capture into the same slots: the
        # optional branch leaves the required ones empty and vice versa.
        optional_name, optional_pos, optional_err, name, pos, err = match.groups()
        name = optional_name or name
        position = optional_pos or pos
        reason = optional_err or err
        # A retry that later passes leaves both lines; the last one wins.
        failures[name] = (position, reason.strip())

    # A clean run only prints that everything passed, so there is no count to
    # read; report zero rather than a question mark.
    required = failed_required.group(1) if failed_required else ("0" if all_passed else "?")
    optional = failed_optional.group(1) if failed_optional else ("0" if all_passed else "?")
    print(f"required failures: {required}   optional failures: {optional}")

    if failures:
        print(f"\n{len(failures)} failing tests:")
        for name, (position, reason) in failures.items():
            first = reason.strip().splitlines()[0] if reason.strip() else ""
            print(f"  {name:<58} {first[:100]}")

        # Grouping by class is what makes a failing run actionable: the arena a
        # class needs is one structure, so a class failing several tests at once
        # almost always means the fixture is wrong rather than the mod.
        by_class = {}
        for name, (_, reason) in failures.items():
            by_class.setdefault(name.split(".")[0], []).append((name, reason))

        print("\nby test class:")
        for cls in sorted(by_class, key=lambda c: -len(by_class[c])):
            print(f"\n  {cls} ({len(by_class[cls])})")
            seen = set()
            for name, reason in by_class[cls]:
                first = reason.strip().splitlines()[0] if reason.strip() else ""
                if first in seen:
                    continue
                seen.add(first)
                print(f"    {name:<52} {first[:90]}")
    elif all_passed:
        print("\nevery required test passed")

    problems = [
        line for line in text.splitlines()
        if ("ERROR" in line or "FATAL" in line or "Exception" in line)
        and not NOISE.search(line)
        and not FAILURE.search(line)
    ]
    other = problems
    if other:
        print(f"\n{len(other)} other error lines:")
        for line in other[:20]:
            print(f"  {line[:170]}")

    if args.verbose:
        print("\n--- log tail ---")
        print("\n".join(text.splitlines()[-60:]))

    # Optional failures are annotated as such upstream and do not gate the run.
    bad = (failed_required and failed_required.group(1) != "0") or other
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())