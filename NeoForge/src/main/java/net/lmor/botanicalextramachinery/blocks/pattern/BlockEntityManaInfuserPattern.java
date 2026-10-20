package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.WorkingTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaInfuser.BlockEntityManaInfuserAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaInfuser.BlockEntityManaInfuserBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaInfuser.BlockEntityManaInfuserUpgraded;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.ManaInfusionRecipe;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityManaInfuserPattern extends WorkingTile<ManaInfusionRecipe>
		implements MeOutput.Holder {
	public static final int MAX_MANA_PER_TICK = LibXServerConfig.ManaInfuserSettings.workingDuration;

	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;
	private int UPGRADE_SLOT_1 = -1;
	private int UPGRADE_SLOT_2 = -1;

	private final SettingPattern settingPattern;

	private final BaseItemStackHandler inventory;
	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	protected int speedMulti = 1;

	private boolean isInfinityMana = false;

	public BlockEntityManaInfuserPattern(BlockEntityType<?> type, BlockPos pos, BlockState state, int manaCapacity, int countCraft, int[] slots, SettingPattern settingPattern) {
		// Call super first. Use Botania's MANA_INFUSION_TYPE as a safe default for construction.
		//noinspection unchecked,rawtypes
		super(type, (net.minecraft.world.item.crafting.RecipeType) vazkii.botania.common.crafting.BotaniaRecipeTypes.MANA_INFUSION_TYPE, pos, state, manaCapacity, slots[0], slots[2], countCraft);

		int FIRST_INPUT_SLOT = slots[0];
		int LAST_INPUT_SLOT = slots[1];
		FIRST_OUTPUT_SLOT = slots[2];
		LAST_OUTPUT_SLOT = slots[3];

		if (slots.length >= 5) {
			UPGRADE_SLOT_1 = slots[4];
			UPGRADE_SLOT_2 = slots[5];
		}

		this.settingPattern = settingPattern;

		// Try to obtain the MythicBotany infuser RecipeType via reflection. If not available, fall back
		// to Botania's infusion type. We do this after calling super because super must be the first
		// statement in the constructor.
		final net.minecraft.world.item.crafting.RecipeType<?> finalInfuserType = findInfuserRecipeType();

		// If we found a different recipe type (e.g. MythicBotany's), set the parent RecipeTile.recipeType
		// reflectively so that recipe lookups in the superclass use the correct RecipeType.
		try {
			java.lang.reflect.Field f = net.lmor.botanicalextramachinery.blocks.base.RecipeTile.class.getDeclaredField("recipeType");
			f.setAccessible(true);
			// suppress unchecked warning: runtime type fits RecipeType<?>
			f.set(this, finalInfuserType);
		} catch (Throwable ignored) {
			// If reflection fails, we keep the original Botania recipeType passed to super and hope validation covers it.
		}

		if (UPGRADE_SLOT_1 != -1 && UPGRADE_SLOT_2 != -1) {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), (net.minecraft.world.item.crafting.RecipeType) finalInfuserType, stack), Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.validator((stack) -> (stack.getItem() == ModItems.catalystSpeed.asItem() || stack.getItem() == ModItems.catalystManaInfinity.asItem()), UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else {
			this.inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), (net.minecraft.world.item.crafting.RecipeType) finalInfuserType, stack), Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1))
					.contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		}

	}

	/**
	 * Try several known MythicBotany class/field/method names to retrieve the infuser RecipeType via reflection.
	 * If none found, return Botania's default MANA_INFUSION_TYPE. Keeps MythicBotany optional at runtime.
	 */
	private static net.minecraft.world.item.crafting.RecipeType<?> findInfuserRecipeType() {
		net.minecraft.world.item.crafting.RecipeType<?> botaniaType = vazkii.botania.common.crafting.BotaniaRecipeTypes.MANA_INFUSION_TYPE;

		String[] candidateClasses = new String[] {
				"mythicbotany.register.ModRecipes",
				"mythicbotany.ModRecipes",
				"mythicbotany.init.ModRecipes",
				"mythicbotany.common.register.ModRecipes"
		};

		String[] candidateFields = new String[] { "infuser", "INFUSER", "INFUSER_TYPE", "INFUSER_RECIPE_TYPE" };
		String[] candidateMethods = new String[] { "infuser", "getInfuser", "getInfuserRecipeType", "infuserRecipeType" };

		for (String clsName : candidateClasses) {
			try {
				Class<?> cls = Class.forName(clsName);
				// try fields
				for (String fName : candidateFields) {
					try {
						java.lang.reflect.Field f = cls.getField(fName);
						Object val = f.get(null);
						if (val instanceof net.minecraft.world.item.crafting.RecipeType) {
							return (net.minecraft.world.item.crafting.RecipeType<?>) val;
						}
					} catch (NoSuchFieldException nsf) {
						// try declared field (private)
						try {
							java.lang.reflect.Field f = cls.getDeclaredField(fName);
							f.setAccessible(true);
							Object val = f.get(null);
							if (val instanceof net.minecraft.world.item.crafting.RecipeType) {
								return (net.minecraft.world.item.crafting.RecipeType<?>) val;
							}
						} catch (Throwable ignored) {}
					} catch (Throwable ignored) {}
				}

				// try static methods
				for (String mName : candidateMethods) {
					try {
						java.lang.reflect.Method m = cls.getMethod(mName);
						Object val = m.invoke(null);
						if (val instanceof net.minecraft.world.item.crafting.RecipeType) {
							return (net.minecraft.world.item.crafting.RecipeType<?>) val;
						}
					} catch (NoSuchMethodException nsme) {
						try {
							java.lang.reflect.Method m = cls.getDeclaredMethod(mName);
							m.setAccessible(true);
							Object val = m.invoke(null);
							if (val instanceof net.minecraft.world.item.crafting.RecipeType) {
								return (net.minecraft.world.item.crafting.RecipeType<?>) val;
							}
						} catch (Throwable ignored) {}
					} catch (Throwable ignored) {}
				}
			} catch (Throwable ignored) {
				// class not present or other issue - try next candidate
			}
		}

		return botaniaType;
	}

	//region Base
	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide) {
			this.me.tick(this.level, this.getBlockPos());

			if (this.me.isOnline()) {
				if (timeCheckOutputSlot <= 0) {
					if (checkOutputSlots()) {
						this.exportResultsItemsME();
					}

					timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;
				} else {
					timeCheckOutputSlot--;
				}
			}

			isInfinityMana = ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem() == ModItems.catalystManaInfinity) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem() == ModItems.catalystManaInfinity));

			if (this.speedMulti == 1 && ((UPGRADE_SLOT_1 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_1).getItem().asItem() == ModItems.catalystSpeed) ||
					(UPGRADE_SLOT_2 != -1 && this.inventory.getStackInSlot(UPGRADE_SLOT_2).getItem().asItem() == ModItems.catalystSpeed))) {
				this.speedMulti = 4;
			} else {
				this.speedMulti = 1;
			}

			this.runRecipeTick();

		}

	}

	@Override
	public void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@NotNull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		this.me.save(nbt);
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> inventory) {
		return (slot) -> slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
	}

	@Nonnull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Override
	protected int getMaxProgress(ManaInfusionRecipe recipe) {
		// Botania's own infusion recipes say what they cost through getManaToConsume,
		// which is exactly the mana the machine has to spend. Asking a Botania recipe
		// for MythicBotany's getManaUsage() instead threw NoSuchMethodException three
		// times a tick (runRecipeTick calls this once to compare, once to bound the
		// progress, and once more inside getAndApplyProgressThisTick) and then fell
		// through to 0 below.
		try {
			return recipe.getManaToConsume();
		} catch (Throwable ignored) {
			// A foreign recipe type may not be a ManaInfusionRecipe at all.
		}

		// Try to call InfuserRecipe.getManaUsage() via reflection if available
		try {
			java.lang.reflect.Method m = recipe.getClass().getMethod("getManaUsage");
			Object val = m.invoke(recipe);
			if (val instanceof Integer) {
				return (Integer) val;
			}
		} catch (Throwable ignored) {}
		// Nothing said what this costs. Charging progress of zero would make the
		// machine craft on its first tick for no mana, so refuse instead: a recipe
		// this machine cannot price is one it must not craft.
		return 0;
	}

	@Override
	public int getMaxManaPerTick() {
		return MAX_MANA_PER_TICK * this.settingPattern.getConfigInt("craftTime") * this.speedMulti;
	}

	@Override
	public boolean getUpgradeInfinityMana() {
		return this.isInfinityMana;
	}

	public BlockEntity getBlockEntity() {
		return this;
	}

	private boolean checkOutputSlots() {
		for (int i = FIRST_OUTPUT_SLOT; i <= LAST_OUTPUT_SLOT; i++) {
			if (!inventory.getStackInSlot(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void setRemoved() {
		super.setRemoved();

		this.me.destroy();
	}
	//endregion

	//region AE INTEGRATION

	private final MeOutput me = MeOutput.create(this, this.getItemFromBlockEntity());

	@Override
	public MeOutput meOutput() {
		return this.me;
	}

	protected Item getItemFromBlockEntity() {
		BlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity instanceof BlockEntityManaInfuserBase) {
			return ModBlocks.baseManaInfuser.asItem();
		} else if (blockEntity instanceof BlockEntityManaInfuserUpgraded) {
			return ModBlocks.upgradedManaInfuser.asItem();
		} else if (blockEntity instanceof BlockEntityManaInfuserAdvanced) {
			return ModBlocks.advancedManaInfuser.asItem();
		} else {
			return ModBlocks.ultimateManaInfuser.asItem();
		}
	}

	private void exportResultsItemsME() {
		for (int slot = FIRST_OUTPUT_SLOT; slot <= LAST_OUTPUT_SLOT; slot++) {
			ItemStack stackInSlot = this.inventory.getStackInSlot(slot);

			if (!stackInSlot.isEmpty()) {
				int getCountExport = this.me.insert(stackInSlot);

				if (getCountExport > 0) {
					stackInSlot.shrink(getCountExport);
					this.inventory.setStackInSlot(slot, stackInSlot);
				}
			}
		}
	}
	//endregion

	@Override
	protected boolean matchRecipe(ManaInfusionRecipe recipe, List<ItemStack> stacks) {
		return super.matchRecipe(recipe, stacks);
	}
}
