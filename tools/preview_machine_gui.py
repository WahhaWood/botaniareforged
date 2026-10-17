#!/usr/bin/env python3
"""Draws the ME machine GUI with Minecraft's own bitmap font, per language, on a small screen.

Checks the rule of the GUI: nothing that grows with the translation may leave the window or the
screen. Mirrors NeoForge/src/main/java/vazkii/botania/ae2/machine/MachineScreen.java (layout constants and the
status panel width rule), so change both together.

    python3 tools/preview_machine_gui.py [--jar client-extra.jar] [--out DIR] [--gui-scale 3]

Window size 1280x720 at GUI scale 3 gives a 427x240 screen. Exit code is non-zero when something
does not fit. Glyphs missing from the bitmap fonts fall back to Unifont in the game; they are
reported and measured as 8 px wide here.
"""

import argparse
import glob
import json
import pathlib
import sys
import zipfile
from io import BytesIO

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "NeoForge/src/main/resources/assets/botania"
LANGS = ["en_us", "ru_ru", "uk_ua"]
KINDS = ["me_mana_infuser", "me_apothecary", "me_runic_altar", "me_agglomeration_factory", "me_alfheim_market"]

# MachineScreen constants
WIN_W, WIN_H = 176, 145
TITLE_MAX = WIN_W - 16
PANEL_LABEL = (0xA0, 0xA0, 0xA0)
PANEL_VALUE = (0x00, 0xB6, 0x1E)
TITLE_COLOR = (0x40, 0x40, 0x40)


class Font:
    def __init__(self, jar):
        self.glyphs = {}  # char -> (image cell RGBA, advance)
        self.missing = set()
        with zipfile.ZipFile(jar) as z:
            for name in ("default", "space"):
                for provider in json.loads(z.read(f"assets/minecraft/font/include/{name}.json"))["providers"]:
                    self._add(z, provider)

    def _add(self, z, provider):
        if provider["type"] == "space":
            for ch, adv in provider["advances"].items():
                self.glyphs[ch] = (None, adv)
            return
        if provider["type"] != "bitmap":
            return
        path = provider["file"].replace("minecraft:", "assets/minecraft/textures/")
        image = Image.open(BytesIO(z.read(path))).convert("RGBA")
        rows = provider["chars"]
        height = provider.get("height", 8)
        cell_w = image.width // len(rows[0])
        cell_h = image.height // len(rows)
        scale = height / cell_h
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch in ("\u0000", " ") or ch in self.glyphs:
                    continue
                cell = image.crop((c * cell_w, r * cell_h, (c + 1) * cell_w, (r + 1) * cell_h))
                actual = 0
                for x in range(cell_w):
                    if any(cell.getpixel((x, y))[3] > 0 for y in range(cell_h)):
                        actual = x + 1
                advance = int(0.5 + actual * scale) + 1
                self.glyphs[ch] = (cell, advance)

    def width(self, text):
        total = 0
        for ch in text:
            glyph = self.glyphs.get(ch)
            if glyph is None:
                self.missing.add(ch)
                total += 8
            else:
                total += glyph[1]
        return total

    def draw(self, canvas, x, y, text, color, scale):
        for ch in text:
            glyph = self.glyphs.get(ch)
            if glyph is None:
                x += 8
                continue
            cell, adv = glyph
            if cell is not None:
                tinted = Image.new("RGBA", cell.size, color + (255,))
                tinted.putalpha(cell.getchannel("A"))
                big = tinted.resize((cell.width * scale, cell.height * scale), Image.NEAREST)
                canvas.alpha_composite(big, (x * scale, y * scale))
            x += adv

    def fit(self, text, width):
        if self.width(text) <= width:
            return text
        cut = text
        while cut and self.width(cut) > width - self.width("…"):
            cut = cut[:-1]
        return cut + "…"


def load_lang(lang):
    base = json.loads((ASSETS / "lang/en_us.json").read_text())
    base.update(json.loads((ASSETS / f"lang/{lang}.json").read_text()))
    return base


def panel_lines(t, online=True):
    return [
        t["gui.botania.machine.network"], t["gui.botania.machine.online" if online else "gui.botania.machine.offline"],
        t["gui.botania.machine.operations"], "1024 / 1024",
        t["gui.botania.machine.queue"], "4096",
        t["gui.botania.machine.recipes"], "100 / 100",
    ]


def render(font, t, kind, screen_w, screen_h, scale, out):
    problems = []
    img = Image.new("RGBA", (screen_w * scale, screen_h * scale), (24, 24, 28, 255))
    left = (screen_w - WIN_W) // 2
    top = (screen_h - WIN_H) // 2
    gui = Image.open(ASSETS / "textures/gui/machine.png").convert("RGBA").crop((0, 0, WIN_W, WIN_H))
    img.alpha_composite(gui.resize((WIN_W * scale, WIN_H * scale), Image.NEAREST), (left * scale, top * scale))
    d = ImageDraw.Draw(img)

    title = font.fit(t[f"block.botania.{kind}"], TITLE_MAX)
    font.draw(img, left + 8, top + 6, title, TITLE_COLOR, scale)
    if font.width(title) > TITLE_MAX:
        problems.append(f"title too wide: {title}")

    # search hint, page counter
    font.draw(img, left + 10, top + 19, t["gui.botania.machine.search"], (0x55, 0x55, 0x55), scale)
    if font.width(t["gui.botania.machine.search"]) > 160 - 4:
        problems.append("search hint too wide")
    pages = "1 / 3"
    font.draw(img, left + (WIN_W - font.width(pages)) // 2, top + 127, pages, TITLE_COLOR, scale)

    # tabs
    for i in range(2):
        x, y = left + WIN_W - 1, top + 17 + i * 22
        d.rectangle([x * scale, y * scale, (x + 20) * scale - 1, (y + 20) * scale - 1], fill=(120, 120, 120, 255), outline=(20, 20, 20, 255))
        if x + 20 > screen_w:
            problems.append("tab leaves the screen")

    # status panel, as MachineScreen.renderStatusPanel
    lines = panel_lines(t)
    width = max(font.width(line) for line in lines) + 9
    pairs = (len(lines) + 1) // 2
    px = left - width - 3
    height = len(lines) * 9 + (pairs - 1) * 3 + 8
    d.rectangle([px * scale, top * scale, (px + width) * scale - 1, (top + height) * scale - 1], fill=(0, 0, 0, 255))
    y = top + 4
    for i, line in enumerate(lines):
        font.draw(img, px + 4, y, line, PANEL_LABEL if i % 2 == 0 else PANEL_VALUE, scale)
        y += 9 if i % 2 == 0 else 12
    if px < 0:
        problems.append(f"status panel leaves the screen (x={px})")
    if width > 110:
        problems.append(f"status panel {width}px wide, limit 110")

    # tab tooltips (free floating, must fit the screen)
    for key in ("enable_all", "disable_all"):
        w = font.width(t[f"gui.botania.machine.{key}"]) + 8
        if w > screen_w:
            problems.append(f"tooltip {key} wider than the screen")

    img.convert("RGB").save(out)
    return problems, width, px


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", help="Minecraft client jar (or -extra jar) with the font textures")
    parser.add_argument("--out", default="me_gui_preview")
    parser.add_argument("--gui-scale", type=int, default=3)
    parser.add_argument("--window", default="1280x720")
    args = parser.parse_args()

    jar = args.jar
    if not jar:
        candidates = glob.glob(str(pathlib.Path.home() / ".local/share/*/libraries/net/minecraft/client/1.21.1-*/client-*-extra.jar"))
        if not candidates:
            sys.exit("no Minecraft client jar found, pass --jar")
        jar = candidates[0]

    w, h = (int(v) for v in args.window.split("x"))
    screen_w, screen_h = w // args.gui_scale, h // args.gui_scale
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    font = Font(jar)
    failed = False
    for lang in LANGS:
        t = load_lang(lang)
        widest = 0
        for kind in KINDS:
            problems, width, px = render(font, t, kind, screen_w, screen_h, args.gui_scale, out / f"{lang}_{kind}.png")
            widest = max(widest, width)
            for problem in problems:
                failed = True
                print(f"FAIL {lang} {kind}: {problem}")
        print(f"{lang}: screen {screen_w}x{screen_h}, status panel {widest}px, window x {(screen_w - WIN_W) // 2}")
    if font.missing:
        print("not in bitmap fonts (Unifont in game, 8px assumed):", "".join(sorted(font.missing)))
    print("previews in", out)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
