/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package net.lmor.botanicalextramachinery.test;

import java.util.ArrayList;
import java.util.List;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import vazkii.botania.test.TestingUtil;

/**
 * Every Botanical Extra Machinery machine exports into an ME network, so each
 * has to be reachable by AE2 cables. AE2 only looks for in-world nodes through
 * the {@code IN_WORLD_GRID_NODE_HOST} capability, and the machines hold the node
 * themselves, so the test checks that the capability is registered and that the
 * node exists once the machine has ticked.
 */
public class BemMeNodeTest {
	private static final String ARENA = "botania:addon_arena";
	private static final BlockPos MACHINE = new BlockPos(2, 2, 2);

	private static final String[] TIERS = { "base", "upgraded", "advanced", "ultimate" };
	private static final String[] TIERED_KINDS = {
			"mana_pool", "runic_altar", "daisy", "apothecary",
			"industrial_agglomeration_factory", "alfheim_market", "orechid", "mana_infuser",
	};

	private static List<String> machineIds() {
		List<String> ids = new ArrayList<>();
		for (String kind : TIERED_KINDS) {
			for (String tier : TIERS) {
				ids.add(tier + "_" + kind);
			}
		}
		ids.add("jaded_amaranthus");
		ids.add("greenhouse");
		return ids;
	}

	@GameTest(template = ARENA)
	public void everyMachineIsAGridNodeHost(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String id : machineIds()) {
			Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("botanicalextramachinery:" + id));
			helper.setBlock(MACHINE, block.defaultBlockState());
			var level = helper.getLevel();
			IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(MACHINE), null);
			if (host == null) {
				missing.add(id);
			}
		}
		TestingUtil.assertEquals(List.<String>of(), missing,
				() -> "Machines that AE2 cables cannot connect to: " + missing);
		helper.succeed();
	}

	@GameTest(template = ARENA)
	public void machineCreatesItsNodeWhenTicking(GameTestHelper helper) {
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("botanicalextramachinery:base_apothecary"));
		helper.setBlock(MACHINE, block.defaultBlockState());

		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					IInWorldGridNodeHost host = helper.getLevel().getCapability(
							AECapabilities.IN_WORLD_GRID_NODE_HOST, helper.absolutePos(MACHINE), null);
					TestingUtil.assertThat(host != null && host.getGridNode(null) != null,
							() -> "A ticking machine should have created its ME grid node");
				})
				.thenSucceed();
	}
}
