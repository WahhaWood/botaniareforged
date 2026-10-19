# Botania Reforged

A fork of **Botania** for **Minecraft 1.21.1 / NeoForge** with several addons merged in.
Mod id `botania`. Applied Energistics 2 is required.

| Merged addon | Package |
|---|---|
| Applied Botanics | `vazkii/botania/ae2/` |
| Botanical Machinery Extra | `net/lmor/botanicalextramachinery/` |
| Voltany | `com/jykito/voltany/` |
| Garden of Glass | `garden_of_glass/` (skyblock datapack) |

On top of that the fork adds ME recipe machines: Botania crafting stations that work as AE2 grid nodes.

ME mana cells hold 1000 mana per byte (a 1k cell is one full mana pool). With MEGA Cells installed there are also
1M-256M mana cells and portable cells, crafted from the mana cell housing and a MEGA cell component. MEGA Cells'
own mana cells stay off, since they look for the standalone Applied Botanics mod.

The Gaia Simulator (`vazkii/botania/gaiasim/`) replays a hard-mode Gaia Guardian fight. The multiblock is a 3x3 slab
of Blocks of Elementium with a 3x3 base on it (Block of Dragonstone in the centre, Malachite Blocks on the sides,
Blocks of Terrasteel in the corners), with the simulator on the centre and Gaia Pylons on the corners. It has a
Lexicon entry (Alfhomancy) with a visualizable multiblock page. A fight costs 1,000,000 mana (spreader or spark on
top) and 10,000 FE/t for 5 minutes. It rolls the Guardian's head table and the hard-mode reward of a lone winner.
Relics are bound to whoever placed the simulator.

## Building

Java 21 is required.

```sh
./gradlew build
```

## Credits

- **Botania** and **Garden of Glass** were created by **Vazkii** and the Botania contributors.
- **Applied Botanics** was created by **ramidzkh**.
- **Botanical Machinery Extra** was created by **LMor**.
- **Voltany** was created by **jykito**.

This is an unofficial fork, not affiliated with or endorsed by the original authors.

## License

Botania is distributed under the [Botania License](LICENSE.txt), and so is this fork.
Parts imported from other projects keep their own licenses, see [ALTERNATE_LICENSES.txt](ALTERNATE_LICENSES.txt).
