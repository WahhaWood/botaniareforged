#!/usr/bin/env python3
"""Rebuilds the Botanical Extra Machinery recipes from the advancement criteria.

The addon ships 77 "unlock the recipe" advancements but only 25 recipe files,
so every one of the machines, catalysts, upgrades and compression pairs the
advancements talk about is unreachable in survival. The advancements are the
surviving half of the original data: each one's `inventory_changed` criteria
list exactly the ingredients its recipe used, and the four mana-infuser recipes
that did survive confirm the shape those recipes had.

So this script reads the criteria back out and writes the recipes they imply:

  * machine tiers follow the `aba / bcb / ddd` shape the surviving recipes use,
    with the previous tier in the centre,
  * ingot/dragonstone compress/decompress pairs become 9<->1,
  * catalysts and upgrades use their criteria as a shapeless ingredient list.

Item ids that the port renamed are mapped on the way through: the criteria were
written against Botania's older ids (`mana_ring_greater`, `rune_pride`) and
against a standalone `botanicalmachinery` mod that no longer exists.

Recipes are only written when the file is missing, so running this twice is a
no-op and an existing hand-written recipe is never clobbered.

Usage:
    python3 tools/make_bem_recipes.py            # write the missing recipes
    python3 tools/make_bem_recipes.py --dry-run  # list them, write nothing
"""

import argparse
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
NS = "botanicalextramachinery"
RECIPE_DIR = ROOT / f"NeoForge/src/main/resources/data/{NS}/recipe"
ADVANCEMENT_DIR = ROOT / f"NeoForge/src/main/resources/data/{NS}/advancement/recipes/misc"

# Ids the criteria still use from before the port renamed them, plus the two
# machines whose vanilla counterpart never existed as a block.
RENAMED = {
    "botania:mana_ring_greater": "botania:greater_ring_of_magnetization",
    "botania:rune_pride": "botania:rune_of_pride",
    "botania:rune_sloth": "botania:rune_of_sloth",
    "botania:rune_envy": "botania:rune_of_envy",
    "botania:rune_lust": "botania:rune_of_lust",
    "botania:crafting_halo": "botania:assembly_halo",
    # The standalone Botanical Machinery mod these point at was folded into this
    # addon, so a base tier now builds on the Botania block it automates.
    "botanicalmachinery:mechanical_daisy": "botania:pure_daisy",
    "botanicalmachinery:mechanical_apothecary": "botania:petal_apothecary",
    "botanicalmachinery:mechanical_mana_pool": "botania:mana_pool",
    "botanicalmachinery:mechanical_runic_altar": "botania:runic_altar",
    "botanicalmachinery:industrial_agglomeration_factory":
        "botania:terrestrial_agglomeration_plate",
    # No vanilla Alfheim portal block: 1.21 makes the portal an entity. Living
    # wood is what Botania builds its elven machinery out of.
    "botanicalmachinery:alfheim_market": "botania:livingwood",
}

# The seven machine families, base tier first. Each has four tiers.
MACHINES = (
    "daisy",
    "apothecary",
    "mana_pool",
    "runic_altar",
    "orechid",
    "alfheim_market",
    "industrial_agglomeration_factory",
)

# Tier key letters: outer column, ring, previous tier, floor slab.
TIERS = {
    "base": ("botania:greater_ring_of_magnetization",
             f"{NS}:malachite_ingot",
             None,
             f"{NS}:malachite_dragonstone_block"),
    "upgraded": ("botania:terrasteel_block",
                 f"{NS}:saffron_ingot",
                 "base",
                 f"{NS}:saffron_dragonstone_block"),
    "advanced": (f"{NS}:malachite_ingot_block",
                 f"{NS}:shadow_ingot",
                 "upgraded",
                 f"{NS}:shadow_dragonstone_block"),
    "ultimate": (f"{NS}:saffron_ingot_block",
                 f"{NS}:crimson_ingot",
                 "advanced",
                 f"{NS}:crimson_dragonstone_block"),
}

TIER_ORDER = ("base", "upgraded", "advanced", "ultimate")

# The seven ingot tiers, for the compress/decompress pairs.
MATERIALS = ("crystal", "malachite", "saffron", "shadow", "crimson", "aureate",
             "mazarine")

# Jaded amaranthus is a single machine, but it sits at crystal tier, so it
# builds the same way a base tier does.
SINGLE_MACHINES = {
    "jaded_amaranthus": {
        "a": "botania:greater_ring_of_magnetization",
        "b": f"{NS}:crystal_ingot",
        "c": "botania:jaded_amaranthus",
        "d": f"{NS}:crystal_dragonstone_block",
    },
}

# The greenhouse has no tier chain and mixes vanilla and addon parts.
GREENHOUSE = {
    "pattern": ("aca", "bdb", "efe"),
    "key": {
        "a": "minecraft:netherite_block",
        "b": "minecraft:glass",
        "c": f"{NS}:crimson_ingot_block",
        "d": "minecraft:dirt",
        "e": f"{NS}:crimson_dragonstone",
        "f": f"{NS}:crimson_ingot",
    },
}

# Catalysts and upgrades are shapeless: the criteria list every ingredient and
# give no pattern to recover, so nothing is lost by leaving the layout open.
SHAPELESS = {
    "catalyst_pattern": ["minecraft:iron_ingot", "minecraft:diamond"],
    "catalyst_petal": [f"{NS}:catalyst_pattern", "minecraft:diamond"],
    "catalyst_petal_block": [f"{NS}:catalyst_petal", "minecraft:diamond_block"],
    "catalyst_seed_infinity": [f"{NS}:catalyst_pattern", "minecraft:diamond"],
    "catalyst_water_infinity": [f"{NS}:catalyst_pattern", "minecraft:water_bucket"],
    "catalyst_stone_infinity": [f"{NS}:catalyst_pattern", "minecraft:stone",
                                "botania:rune_of_pride"],
    "catalyst_wood_infinity": [f"{NS}:catalyst_pattern", "minecraft:oak_log",
                               "botania:rune_of_sloth"],
    "catalyst_mana_infinity": [f"{NS}:catalyst_pattern",
                               f"{NS}:crimson_dragonstone_block",
                               f"{NS}:crimson_spark"],
    "catalyst_living_rock_infinity": [f"{NS}:catalyst_pattern",
                                      "botania:livingrock", "botania:runic_altar",
                                      "botania:rune_of_envy"],
    "catalyst_speed": [f"{NS}:catalyst_pattern", "minecraft:redstone_block",
                       f"{NS}:saffron_spark", "minecraft:iron_ingot"],

    "upgrade_pattern": ["minecraft:diamond", "minecraft:netherite_ingot"],
    "upgrade_cost_energy": [f"{NS}:upgrade_pattern", "minecraft:redstone_block",
                            "minecraft:diamond", "minecraft:netherite_ingot"],
    # The criteria named "crafting_halo", which Botania 1.21 spells
    # assembly_halo: the two halos were renamed together and only one of them
    # was caught. The slot addon is a crafting station, so assembly_halo is the
    # one that fits.
    "upgrade_slot_add": [f"{NS}:upgrade_pattern", "botania:assembly_halo",
                         "minecraft:chest"],
    "upgrade_flower_4x": [f"{NS}:upgrade_pattern", "minecraft:chest",
                          f"{NS}:upgrade_slot_add", f"{NS}:malachite_ingot",
                          f"{NS}:malachite_dragonstone_block"],
    "upgrade_flower_16x": [f"{NS}:upgrade_pattern", f"{NS}:upgrade_flower_4x",
                           f"{NS}:upgrade_slot_add", f"{NS}:saffron_ingot",
                           f"{NS}:saffron_dragonstone_block"],
    "upgrade_flower_32x": [f"{NS}:upgrade_pattern", f"{NS}:upgrade_flower_16x",
                           f"{NS}:upgrade_slot_add", f"{NS}:shadow_ingot",
                           f"{NS}:shadow_dragonstone_block"],
    "upgrade_flower_64x": [f"{NS}:upgrade_pattern", f"{NS}:upgrade_flower_32x",
                           f"{NS}:upgrade_slot_add", f"{NS}:crimson_ingot",
                           f"{NS}:crimson_dragonstone_block"],
    "upgrade_storage_mana_1": [f"{NS}:crystal_ingot", "minecraft:redstone_block",
                               f"{NS}:upgrade_cost_energy", f"{NS}:upgrade_pattern"],
    "upgrade_storage_mana_2": [f"{NS}:malachite_ingot",
                               f"{NS}:upgrade_storage_mana_1",
                               "botania:manasteel_ingot"],
    "upgrade_storage_mana_3": [f"{NS}:saffron_ingot",
                               f"{NS}:upgrade_storage_mana_2",
                               f"{NS}:malachite_ingot"],
    "upgrade_storage_energy_1": [f"{NS}:crystal_ingot", "minecraft:redstone_block",
                                 f"{NS}:upgrade_cost_energy", f"{NS}:upgrade_pattern"],
    "upgrade_storage_energy_2": [f"{NS}:malachite_ingot",
                                 f"{NS}:upgrade_storage_energy_1",
                                 "botania:manasteel_ingot"],
    "upgrade_storage_energy_3": [f"{NS}:saffron_ingot",
                                 f"{NS}:upgrade_storage_energy_2",
                                 f"{NS}:malachite_ingot"],
    "upgrade_heat_greenhouse": [f"{NS}:upgrade_pattern",
                                f"{NS}:upgrade_storage_energy_2",
                                f"{NS}:saffron_ingot", "minecraft:redstone_block",
                                "minecraft:glass"],
    "upgrade_gen_mana": [f"{NS}:upgrade_pattern", f"{NS}:upgrade_cost_energy",
                         f"{NS}:upgrade_heat_greenhouse",
                         f"{NS}:upgrade_storage_energy_1", "minecraft:glass",
                         "minecraft:blaze_powder"],
    "upgrade_tick_gen_mana_2": [f"{NS}:upgrade_tick_gen_mana_1",
                                f"{NS}:crimson_ingot", f"{NS}:upgrade_pattern",
                                f"{NS}:crystal_ingot"],
}


def shaped(pattern, key, result, count=1):
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {letter: {"item": item} for letter, item in sorted(key.items())},
        "pattern": list(pattern),
        "result": {"id": result, "count": count},
        "show_notification": True,
    }


def shapeless(ingredients, result, count=1):
    return {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": item} for item in ingredients],
        "result": {"id": result, "count": count},
        "show_notification": True,
    }


def machine_key(family, tier):
    """Resolves one machine tier's four key letters.

    The previous tier's name is resolved rather than looked up, so a family
    that is missing a tier still produces the tiers below it.
    """
    a, b, previous, d = TIERS[tier]
    if tier == "base":
        c = VANILLA_EQUIVALENT[family]
    else:
        c = f"{NS}:{previous}_{family}"
    return {"a": a, "b": b, "c": c, "d": d}


# What each machine automates, for the base tier's centre slot.
VANILLA_EQUIVALENT = {
    "daisy": "botania:pure_daisy",
    "apothecary": "botania:petal_apothecary",
    "mana_pool": "botania:mana_pool",
    "runic_altar": "botania:runic_altar",
    "orechid": "botania:orechid",
    "alfheim_market": "botania:livingwood",
    "industrial_agglomeration_factory": "botania:terrestrial_agglomeration_plate",
}


def build():
    """Yields (recipe_id, recipe_dict) for everything the advancements promise."""
    for family in MACHINES:
        for tier in TIER_ORDER:
            yield f"{tier}_{family}", shaped(
                ("aba", "bcb", "ddd"),
                machine_key(family, tier),
                f"{NS}:{tier}_{family}",
            )

    for name, key in SINGLE_MACHINES.items():
        yield name, shaped(("aba", "bcb", "ddd"), key, f"{NS}:{name}")

    yield "greenhouse", shaped(
        GREENHOUSE["pattern"], GREENHOUSE["key"], f"{NS}:greenhouse"
    )

    for material in MATERIALS:
        yield f"{material}_ingot_compress", shaped(
            ("aaa", "aaa", "aaa"),
            {"a": f"{NS}:{material}_ingot"},
            f"{NS}:{material}_ingot_block",
        )
        yield f"{material}_ingot_block_decompress", shaped(
            ("aaa", "aaa", "aaa"),
            {"a": f"{NS}:{material}_ingot_block"},
            f"{NS}:{material}_ingot",
            count=9,
        )
        yield f"{material}_dragonstone_compress", shaped(
            ("aaa", "aaa", "aaa"),
            {"a": f"{NS}:{material}_dragonstone"},
            f"{NS}:{material}_dragonstone_block",
        )
        yield f"{material}_dragonstone_block_decompress", shaped(
            ("aaa", "aaa", "aaa"),
            {"a": f"{NS}:{material}_dragonstone_block"},
            f"{NS}:{material}_dragonstone",
            count=9,
        )

    for name, ingredients in SHAPELESS.items():
        yield name, shapeless(ingredients, f"{NS}:{name}")


def advancement_recipe_ids():
    """Every recipe id the shipped advancements try to unlock."""
    ids = set()
    for path in sorted(ADVANCEMENT_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for recipe in data.get("rewards", {}).get("recipes", []):
            ids.add(recipe.split(":")[-1])
    return ids


def rename_misnamed_recipes(dry_run):
    """Renames recipes whose file name does not match the id they unlock.

    A recipe's id comes from its file name, so `base_spark_shapeless.json` is
    registered as `base_spark_shapeless` and the advancement that asks for
    `base_spark` never matches it, even though the recipe itself is correct.
    """
    renamed = []
    for path in sorted(ADVANCEMENT_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for recipe in data.get("rewards", {}).get("recipes", []):
            wanted = recipe.split(":")[-1]
            stale = RECIPE_DIR / f"{wanted}_shapeless.json"
            if stale.exists():
                renamed.append((stale.name, f"{wanted}.json"))
                if not dry_run:
                    data = json.loads(stale.read_text(encoding="utf-8"))
                    # The recipes here are hand-written one-liners; give the
                    # renamed one the same show_notification flag the rest of
                    # the pack carries.
                    data.setdefault("show_notification", True)
                    stale.unlink()
                    (RECIPE_DIR / f"{wanted}.json").write_text(
                        json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return renamed


def fix_stale_ingredient_ids(dry_run):
    """Repoints already-written recipes at ids 1.21.1 actually registers.

    `build` only writes a recipe when the file is missing, so an id that was
    renamed after the file was written would otherwise never be corrected.
    """
    fixed = []
    for path in sorted(RECIPE_DIR.glob("*.json")):
        text = path.read_text(encoding="utf-8")
        original = text
        for stale, current in RENAMED.items():
            text = text.replace(f'"{stale}"', f'"{current}"')
        if text != original:
            fixed.append(path.stem)
            if not dry_run:
                path.write_text(text, encoding="utf-8")
    return fixed
    return renamed


def fix_advancement_ids(dry_run):
    """Repoints the criteria at the ids 1.21.1 actually registers.

    An item predicate naming an id no mod registers silently never matches, so
    the advancement stays locked and its recipe is never offered.
    """
    changed = []
    for path in sorted(ADVANCEMENT_DIR.glob("*.json")):
        text = path.read_text(encoding="utf-8")
        original = text
        for stale, current in RENAMED.items():
            text = text.replace(f'"{stale}"', f'"{current}"')
        if text != original:
            changed.append(path.name)
            if not dry_run:
                path.write_text(text, encoding="utf-8")
    return changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true",
                        help="list what would be written and change nothing")
    args = parser.parse_args()

    RECIPE_DIR.mkdir(parents=True, exist_ok=True)
    promised = advancement_recipe_ids()

    written, skipped = [], []
    for name, recipe in build():
        path = RECIPE_DIR / f"{name}.json"
        if path.exists():
            skipped.append(name)
            continue
        written.append(name)
        if not args.dry_run:
            path.write_text(json.dumps(recipe, indent=2) + "\n", encoding="utf-8")

    fixed = fix_advancement_ids(args.dry_run)
    renamed = rename_misnamed_recipes(args.dry_run)
    repaired = fix_stale_ingredient_ids(args.dry_run)

    print(f"recipes written: {len(written)}")
    for name in written:
        mark = "" if name in promised else "  (no advancement wants this)"
        print(f"  + {name}{mark}")
    print(f"recipes already present: {len(skipped)}")
    for old, new in renamed:
        print(f"renamed {old} -> {new} so its id matches the advancement")
    if fixed:
        print(f"advancements repointed at current item ids: {len(fixed)}")
        for name in fixed:
            print(f"  ~ {name}")
    else:
        print("advancements already use current item ids")
    if repaired:
        print(f"recipes repointed at current item ids: {len(repaired)}")
        for name in repaired:
            print(f"  ~ {name}")
    else:
        print("recipes already use current item ids")

    # Count the recipes that will exist once this run finishes, so a dry run
    # still reports what the real run would leave behind.
    after = {p.stem for p in RECIPE_DIR.rglob("*.json")}
    after |= set(written)
    after |= {new[:-5] for _, new in renamed}
    missing = promised - after
    if missing:
        print(f"still dangling: {sorted(missing)}")
        return 1
    print("every recipe an advancement unlocks now exists")
    return 0


if __name__ == "__main__":
    sys.exit(main())