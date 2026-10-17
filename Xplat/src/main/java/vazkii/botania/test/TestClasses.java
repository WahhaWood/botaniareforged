/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.test;

import java.util.List;

import vazkii.botania.test.block.ApothecaryRecipeTest;
import vazkii.botania.test.block.BindingTest;
import vazkii.botania.test.block.DandelifeonTest;
import vazkii.botania.test.block.DrumBlockTest;
import vazkii.botania.test.block.EntropinnyumUnethicalTntDetectionTest;
import vazkii.botania.test.block.ForceRelayTest;
import vazkii.botania.test.block.HopperhockTest;
import vazkii.botania.test.block.IdleFlowerTickingTest;
import vazkii.botania.test.block.InterceptorTest;
import vazkii.botania.test.block.ManaSpreaderTest;
import vazkii.botania.test.block.OpenCrateTest;
import vazkii.botania.test.block.RannuncarpusTest;
import vazkii.botania.test.block.TargetBlockTest;
import vazkii.botania.test.item.AstrolabeTest;
import vazkii.botania.test.item.CacophoniumTest;
import vazkii.botania.test.item.FlowerPouchTest;
import vazkii.botania.test.item.GrassSeedDispensingTest;
import vazkii.botania.test.item.LifeAggregatorTest;
import vazkii.botania.test.item.RarityConsistencyTest;
import vazkii.botania.test.item.SpectatorScanTest;
import vazkii.botania.test.item.lens.BoreLensTest;
import vazkii.botania.test.item.lens.BoreWarpLensTest;
import vazkii.botania.test.item.lens.EntropicWarpLensTest;
import vazkii.botania.test.item.lens.PaintslingerLensTest;
import vazkii.botania.test.item.lens.WarpForceTest;

/**
 * Every class holding game tests, in one list.
 * <p>
 * NeoForge discovers tests by scanning for {@code @GameTestHolder}, an
 * annotation that does not exist on Fabric. Since the tests are shared between
 * loaders and so cannot be annotated with it, the loader specific entry points
 * register them from here instead. {@code tools/list_gametests.py} reads this
 * list back to report which template each test needs.
 */
public class TestClasses {
	private TestClasses() {}

	public static final List<Class<?>> ALL = List.of(
			ApothecaryRecipeTest.class,
			AstrolabeTest.class,
			BindingTest.class,
			BoreLensTest.class,
			BoreWarpLensTest.class,
			CacophoniumTest.class,
			DandelifeonTest.class,
			DrumBlockTest.class,
			EntropicWarpLensTest.class,
			EntropinnyumUnethicalTntDetectionTest.class,
			FlowerPouchTest.class,
			ForceRelayTest.class,
			GrassSeedDispensingTest.class,
			HopperhockTest.class,
			IdleFlowerTickingTest.class,
			InterceptorTest.class,
			LifeAggregatorTest.class,
			ManaSpreaderTest.class,
			OpenCrateTest.class,
			PaintslingerLensTest.class,
			RannuncarpusTest.class,
			RarityConsistencyTest.class,
			SpectatorScanTest.class,
			TargetBlockTest.class,
			TranslationKeysTest.class,
			WarpForceTest.class);
}