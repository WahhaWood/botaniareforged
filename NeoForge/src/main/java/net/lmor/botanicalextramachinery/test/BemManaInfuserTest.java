/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.lmor.botanicalextramachinery.blocks.pattern.BlockEntityManaInfuserPattern;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaInfuser.BlockEntityManaInfuserBase;
import net.lmor.botanicalextramachinery.config.LibXServerConfig.ManaInfuserSettings;

import vazkii.botania.test.TestingUtil;

/**
 * Covers Botanical Extra Machinery's mana infuser, which pays mana to run
 * Botania's mana infusion recipes.
 * <p>
 * The recipe is {@code botania:recipe/mana_infusion/mana_in_a_bottle}: one glass
 * bottle for 5000 mana. The infuser is worth its own test because it is the one
 * machine whose price comes from a reflection path — it asks the recipe what it
 * costs, and used to fall back to a cost of zero when that lookup failed, which
 * let it craft on the first tick for no mana at all.
 * <p>
 * The block's own crafting recipe needs MythicBotany, but the machine itself
 * runs Botania's infusion recipes either way, so it is testable here.
 */
public class BemManaInfuserTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos INFUSER = new BlockPos(2, 2, 2);

	/** The base infuser's own slot layout. */
	private static final int FIRST_INPUT_SLOT = 0;
	private static final int FIRST_OUTPUT_SLOT = 3;

	/** botania:recipe/mana_infusion/mana_in_a_bottle. */
	private static final String RECIPE_INPUT = "minecraft:glass_bottle";
	private static final String RECIPE_OUTPUT = "botania:mana_in_a_bottle";
	private static final int RECIPE_MANA = 5000;

	/**
	 * 5000 mana at the infuser's 20 mana a tick is 250 ticks of paying out;
	 * the slack on top covers the tick the recipe is picked up in.
	 */
	private static final int CRAFT_TICKS = RECIPE_MANA / ManaInfuserSettings.workingDuration + 50;

	private static BlockEntityManaInfuserPattern place(GameTestHelper helper) {
		Block block = block("botanicalextramachinery:base_mana_infuser");
		helper.setBlock(INFUSER, block.defaultBlockState());
		helper.assertBlockPresent(block, INFUSER);
		BlockEntityManaInfuserPattern infuser = (BlockEntityManaInfuserPattern) helper.getBlockEntity(INFUSER);
		infuser.setCurrentMana(infuser.getMaxMana());
		return infuser;
	}

	private static ItemStack out(BlockEntityManaInfuserPattern infuser) {
		return infuser.getInventory().getStackInSlot(FIRST_OUTPUT_SLOT);
	}

	@GameTest(template = ARENA, timeoutTicks = 600)
	public void paysForItsRecipe(GameTestHelper helper) {
		BlockEntityManaInfuserPattern infuser = place(helper);
		infuser.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(RECIPE_INPUT), 1));
		int manaBefore = infuser.getCurrentMana();

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(manaBefore - RECIPE_MANA, infuser.getCurrentMana(),
							() -> "A recipe costing " + RECIPE_MANA + " should leave " + (manaBefore - RECIPE_MANA)
									+ " mana, but the infuser holds " + infuser.getCurrentMana());
					TestingUtil.assertEquals(item(RECIPE_OUTPUT), out(infuser).getItem(),
							() -> "The infuser should have made " + RECIPE_OUTPUT + ", but the output slot holds " + out(infuser));
				})
				.thenSucceed();
	}

	@GameTest(template = ARENA, timeoutTicks = 400)
	public void refusesToCraftWithoutMana(GameTestHelper helper) {
		BlockEntityManaInfuserPattern infuser = place(helper);
		infuser.setCurrentMana(0);
		infuser.getInventory().setStackInSlot(FIRST_INPUT_SLOT, new ItemStack(item(RECIPE_INPUT), 1));

		helper.startSequence()
				.thenIdle(CRAFT_TICKS)
				.thenExecute(() -> {
					TestingUtil.assertEquals(ItemStack.EMPTY, out(infuser),
							() -> "A powerless infuser should make nothing, but the output slot holds " + out(infuser));
					TestingUtil.assertEquals(1, infuser.getInventory().getStackInSlot(FIRST_INPUT_SLOT).getCount(),
							() -> "A powerless infuser should not have eaten the input either");
				})
				.thenSucceed();
	}

	private static Block block(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		Block block = BuiltInRegistries.BLOCK.get(key);
		if (block == Blocks.AIR) {
			throw new IllegalStateException("No such block: " + id);
		}
		return block;
	}

	private static Item item(String id) {
		ResourceLocation key = ResourceLocation.parse(id);
		Item item = BuiltInRegistries.ITEM.get(key);
		if (item == Items.AIR) {
			throw new IllegalStateException("No such item: " + id);
		}
		return item;
	}
}
