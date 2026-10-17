package net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.flowers;

import net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.GenFlowers;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import vazkii.botania.common.block.flower.BotaniaFlowerBlock;

public class Rafflowsia extends GenFlowers {
    @Override
    public boolean availableFuel(ItemStack fuel) {
        // 1.21.1: BotaniaTags.Blocks.MYSTICAL_FLOWERS was removed; check the block type instead
        return Block.byItem(fuel.getItem()) instanceof BotaniaFlowerBlock;
    }

    @Override
    public int getPerMana(ItemStack fuel) {
        return LibXServerConfig.GreenhouseSettings.Flowers.rafflowsia;
    }
}
