#!/usr/bin/env python3
"""Draws the placeholder GUI of the ME recipe machines (botania:me_*).

Run it again after changing the GUI; the output is deterministic:
    python3 tools/make_machine_assets.py
"""

import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "NeoForge/src/main/resources/assets/botania/textures"


def gui():
    """176x145 window, 9x5 recipe grid at (7,31), tab icons at u200 (check) and u216 (cross)."""
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # vanilla-like panel
    d.rectangle([0, 0, 175, 144], fill=(198, 198, 198, 255))
    d.rectangle([0, 0, 175, 0], fill=(255, 255, 255, 255))
    d.rectangle([0, 0, 0, 144], fill=(255, 255, 255, 255))
    d.rectangle([175, 1, 175, 144], fill=(85, 85, 85, 255))
    d.rectangle([1, 144, 175, 144], fill=(85, 85, 85, 255))
    d.rectangle([0, 0, 175, 144], outline=None)
    d.point((0, 0), fill=(0, 0, 0, 0))
    d.point((175, 144), fill=(0, 0, 0, 0))
    for row in range(5):
        for col in range(9):
            x, y = 7 + col * 18, 31 + row * 18
            d.rectangle([x, y, x + 17, y + 17], fill=(139, 139, 139, 255))
            d.line([x, y, x + 16, y], fill=(55, 55, 55, 255))
            d.line([x, y, x, y + 16], fill=(55, 55, 55, 255))
            d.line([x + 1, y + 17, x + 17, y + 17], fill=(255, 255, 255, 255))
            d.line([x + 17, y + 1, x + 17, y + 17], fill=(255, 255, 255, 255))
    # icons 16x16: green check, red cross
    check = [(3, 8), (4, 8), (5, 9), (6, 10), (7, 11), (8, 10), (9, 9), (10, 8), (11, 7), (12, 6), (13, 5)]
    for x, y in check:
        for dx, dy in ((0, 0), (0, 1)):
            d.point((200 + x, y + dy), fill=(30, 160, 40, 255))
    for i in range(3, 13):
        for dx, dy in ((0, 0), (1, 0)):
            d.point((216 + i + dx, i), fill=(200, 40, 40, 255))
            d.point((216 + 15 - i + dx, i), fill=(200, 40, 40, 255))
    return img


def main():
    gui_dir = ASSETS / "gui"
    gui_dir.mkdir(parents=True, exist_ok=True)
    gui().save(gui_dir / "machine.png")
    print("ok: gui")


if __name__ == "__main__":
    main()
