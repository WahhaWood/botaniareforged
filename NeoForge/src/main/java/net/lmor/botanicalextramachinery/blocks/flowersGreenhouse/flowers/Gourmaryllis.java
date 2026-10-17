package net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.flowers;

import net.lmor.botanicalextramachinery.blocks.flowersGreenhouse.GenFlowers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;

public class Gourmaryllis extends GenFlowers {

    @Override
    public boolean availableFuel(ItemStack fuel) {
        // 1.21.1: isEdible() was replaced by the FOOD data component
        return fuel.has(DataComponents.FOOD);
    }

    @Override
    public int getPerMana(ItemStack fuel) {
        FoodProperties food = fuel.get(DataComponents.FOOD);
        int nutrition = food != null ? food.nutrition() : 0;
        int foodValue = Math.min(12, nutrition);

        return (int)((double)(foodValue * foodValue * 70) * 1.8);
    }
}

