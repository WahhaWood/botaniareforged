/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.neoforge;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.test.TestClasses;
import vazkii.botania.test.item.lens.BoreLensTest;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Registers Botania's game tests on NeoForge.
 * <p>
 * The tests live in the common source set so Fabric can use them too, and that
 * rules out the two NeoForge annotations that would make registration work
 * normally:
 * <ul>
 * <li>{@code @GameTestHolder} is NeoForge's own, so it does not compile there.</li>
 * <li>Neohorge rewrites an explicit template into
 * {@code <namespace>:<classname>.<template>}, and every template in these tests
 * already carries its namespace. The result is a name with a colon in the path,
 * which no ResourceLocation can parse, so the tests would never find their
 * structures.</li>
 * </ul>
 * A {@link GameTestGenerator} builds the {@link TestFunction} records directly,
 * taking each template exactly as the test wrote it, which is what Fabric's game
 * test api does and what the fixtures in
 * {@code tools/make_test_structures.py} are named for.
 * <p>
 * Batch hooks cannot be built this way -- they are looked up by batch name in
 * {@code GameTestRegistry} -- so they are re-declared below and delegated to.
 */
public class BotaniaGameTests {
	/** Tests for the merged addons, which are loader specific code. */
	private static final List<Class<?>> ADDON_TESTS = List.of(
			net.lmor.botanicalextramachinery.test.BemDaisyTest.class,
			net.lmor.botanicalextramachinery.test.BemMeNodeTest.class,
			net.lmor.botanicalextramachinery.test.BemManaInfuserTest.class,
			net.lmor.botanicalextramachinery.test.BemManaPoolTest.class,
			net.lmor.botanicalextramachinery.test.BemJadedAmaranthusTest.class,
			net.lmor.botanicalextramachinery.test.BemOrechidTest.class,
			net.lmor.botanicalextramachinery.test.BemRunicAltarTest.class,
			net.lmor.botanicalextramachinery.test.BemAlfheimMarketTest.class,
			net.lmor.botanicalextramachinery.test.BemApothecaryTest.class,
			net.lmor.botanicalextramachinery.test.BemGreenhouseTest.class,
			net.lmor.botanicalextramachinery.test.BemIndustrialAgglomerationFactoryTest.class,
			vazkii.botania.ae2.ae2.ManaCellTest.class,
			vazkii.botania.ae2.machine.MachineTest.class,
			com.jykito.voltany.test.VoltanyFlowerTierTest.class,
			com.jykito.voltany.test.VoltanyManaGeneratorTest.class);

	private BotaniaGameTests() {}

	@GameTestGenerator
	public static Collection<TestFunction> allTests() {
		List<TestFunction> out = new ArrayList<>();
		for (Class<?> testClass : TestClasses.ALL) {
			collect(testClass, out);
		}
		// Addon tests live on the loader side, since the addons themselves do.
		for (Class<?> addonTest : ADDON_TESTS) {
			collect(addonTest, out);
		}
		BotaniaAPI.LOGGER.info("Collected {} game test functions", out.size());
		return out;
	}

	private static void collect(Class<?> testClass, List<TestFunction> out) {
		List<Method> methods = new ArrayList<>(List.of(testClass.getDeclaredMethods()));
		methods.sort(Comparator.comparing(Method::getName));

		for (Method method : methods) {
			GameTest gameTest = method.getAnnotation(GameTest.class);
			if (gameTest != null) {
				out.add(toTestFunction(testClass, method, gameTest));
				continue;
			}
			if (method.getAnnotation(GameTestGenerator.class) != null) {
				// A generator method returns its own TestFunctions, which already
				// carry the structure name the author intended.
				out.addAll(invokeGenerator(method));
			}
		}
	}

	private static TestFunction toTestFunction(Class<?> testClass, Method method, GameTest gameTest) {
		// The tests name templates as "botania:block/thing", namespace included.
		// One without a template falls back to the vanilla convention of the
		// lowercased class and method names, which is also a valid name; an
		// empty path would not be.
		String template = gameTest.template();
		String structureName;
		if (template.isEmpty()) {
			structureName = BotaniaAPI.MODID + ":" + testFunctionName(testClass, method);
		} else {
			structureName = template.contains(":") ? template : BotaniaAPI.MODID + ":" + template;
		}

		return new TestFunction(
				gameTest.batch(),
				testFunctionName(testClass, method),
				structureName,
				StructureUtils.getRotationForRotationSteps(gameTest.rotationSteps()),
				gameTest.timeoutTicks(),
				gameTest.setupTicks(),
				gameTest.required(),
				gameTest.manualOnly(),
				gameTest.attempts(),
				gameTest.requiredSuccesses(),
				gameTest.skyAccess(),
				helper -> invoke(testClass, method, helper));
	}

	private static String testFunctionName(Class<?> testClass, Method method) {
		return testClass.getSimpleName().toLowerCase(java.util.Locale.ROOT)
				+ "." + method.getName().toLowerCase(java.util.Locale.ROOT);
	}

	private static void invoke(Class<?> testClass, Method method, Object helper) {
		try {
			// GameTestHelper is the only parameter these tests take; the mock
			// player they get from it is not a ServerPlayer, which is why the
			// tests drive interactions through TestingUtil.
			Object instance = Modifier.isStatic(method.getModifiers())
					? null
					: testClass.getDeclaredConstructor().newInstance();
			method.setAccessible(true);
			method.invoke(instance, helper);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to run " + method, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<TestFunction> invokeGenerator(Method method) {
		try {
			Object instance = Modifier.isStatic(method.getModifiers())
					? null
					: method.getDeclaringClass().getDeclaredConstructor().newInstance();
			method.setAccessible(true);
			return (Collection<TestFunction>) method.invoke(instance);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to run test generator " + method, e);
		}
	}

	// --- batch hooks ---------------------------------------------------------
	//
	// BoreLensTest sets the mining level config around each batch so the bore
	// lens sees a controlled world. GameTestBatchFactory looks these up by batch
	// name in GameTestRegistry, which only fills from annotated methods, so they
	// are declared here and delegate to the test that owns the behaviour.

	@BeforeBatch(batch = BoreLensTest.WOOD_LEVEL_BATCH)
	public static void beforeWoodLevelBatch(ServerLevel level) {
		new BoreLensTest().beforeWoodBatch(level);
	}

	@BeforeBatch(batch = BoreLensTest.STONE_LEVEL_BATCH)
	public static void beforeStoneLevelBatch(ServerLevel level) {
		new BoreLensTest().beforeStoneBatch(level);
	}

	@BeforeBatch(batch = BoreLensTest.IRON_LEVEL_BATCH)
	public static void beforeIronLevelBatch(ServerLevel level) {
		new BoreLensTest().beforeIronBatch(level);
	}

	@BeforeBatch(batch = BoreLensTest.DIAMOND_LEVEL_BATCH)
	public static void beforeDiamondLevelBatch(ServerLevel level) {
		new BoreLensTest().beforeDiamondBatch(level);
	}

	@AfterBatch(batch = BoreLensTest.WOOD_LEVEL_BATCH)
	public static void afterWoodLevelBatch(ServerLevel level) {
		new BoreLensTest().afterWoodBatch(level);
	}

	@AfterBatch(batch = BoreLensTest.STONE_LEVEL_BATCH)
	public static void afterStoneLevelBatch(ServerLevel level) {
		new BoreLensTest().afterStoneBatch(level);
	}

	@AfterBatch(batch = BoreLensTest.IRON_LEVEL_BATCH)
	public static void afterIronLevelBatch(ServerLevel level) {
		new BoreLensTest().afterIronBatch(level);
	}

	@AfterBatch(batch = BoreLensTest.DIAMOND_LEVEL_BATCH)
	public static void afterDiamondLevelBatch(ServerLevel level) {
		new BoreLensTest().afterDiamondBatch(level);
	}

	public static void register(IEventBus modBus) {
		modBus.addListener(BotaniaGameTests::onRegisterGameTests);
	}

	private static void onRegisterGameTests(RegisterGameTestsEvent event) {
		BotaniaAPI.LOGGER.info("Registering Botania game tests");
		// register(Class) walks declared methods, so this picks up the generator
		// above plus the @BeforeBatch/@AfterBatch hooks below.
		event.register(BotaniaGameTests.class);
	}
}
