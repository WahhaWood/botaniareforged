package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.WorkingTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalApothecary.BlockEntityApothecaryAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalApothecary.BlockEntityApothecaryBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalApothecary.BlockEntityApothecaryUpgraded;
import net.lmor.botanicalextramachinery.config.LibXClientConfig;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.base.tile.TickingBlock;
import org.moddingx.libx.crafting.RecipeHelper;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.CustomApothecaryColor;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;
import vazkii.botania.common.handler.BotaniaSounds;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import de.melanx.botanicalmachinery.data.CommonTags;

public class BlockEntityApothecaryPattern extends WorkingTile<vazkii.botania.api.recipe.PetalApothecaryRecipe>
		implements TickingBlock, MeOutput.Holder {
	public static final int WORKING_DURATION = LibXServerConfig.ApothecarySettings.workingDuration;
	public final int FLUID_CAPACITY;

	private final int SEEDS_SLOT;
	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;

	private int UPGRADE_SLOT_1 = -1;
	private int UPGRADE_SLOT_2 = -1;

	private final BaseItemStackHandler inventory;
	private final BlockEntityApothecaryPattern.ApothecaryFluidTank fluidInventory;
	private final IFluidHandler fluidHandler;
	private ItemStack currentOutput;

	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	private final SettingPattern settingPattern;

	private boolean isInfinityWater = false;
	private int seedBuffer = 0;

	public BlockEntityApothecaryPattern(BlockEntityType<?> type, BlockPos pos, BlockState state, int[] slots, int countCraft, SettingPattern settingPattern) {
		super(type, BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE, pos, state, 0, slots[1], slots[3], countCraft);
		this.currentOutput = ItemStack.EMPTY;

		this.SEEDS_SLOT = slots[0];
		this.FIRST_INPUT_SLOT = slots[1];
		this.LAST_INPUT_SLOT = slots[2];
		this.FIRST_OUTPUT_SLOT = slots[3];
		this.LAST_OUTPUT_SLOT = slots[4];

		if (slots.length >= 6) {
			UPGRADE_SLOT_1 = slots[5];
		}

		if (slots.length >= 7) {
			UPGRADE_SLOT_2 = slots[6];
		}

		this.settingPattern = settingPattern;

		this.FLUID_CAPACITY = settingPattern.getConfigInt("fluidStorage");
		fluidInventory = new BlockEntityApothecaryPattern.ApothecaryFluidTank(FLUID_CAPACITY, (fluidStack) -> {
			return Fluids.WATER.isSame(fluidStack.getFluid());
		});
		fluidHandler = this.fluidInventory;

		if (UPGRADE_SLOT_1 != -1 && UPGRADE_SLOT_2 == -1) {
			this.inventory = BaseItemStackHandler.builder(this.LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.is(CommonTags.MECHANICAL_APOTHECARY_CATALYSTS);
					}, SEEDS_SLOT)
					.validator((stack) -> {
						return (stack.getItem() == ModItems.catalystSeedInfinity.asItem());
					}, UPGRADE_SLOT_1)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1)
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else if (UPGRADE_SLOT_1 != -1) {
			this.inventory = BaseItemStackHandler.builder(this.LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.is(CommonTags.MECHANICAL_APOTHECARY_CATALYSTS);
					}, SEEDS_SLOT)
					.validator((stack) -> {
						return (stack.getItem() == ModItems.catalystSeedInfinity.asItem() || stack.getItem() == ModItems.catalystWaterInfinity.asItem());
					}, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, UPGRADE_SLOT_1, UPGRADE_SLOT_2)
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		} else {
			this.inventory = BaseItemStackHandler.builder(this.LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return stack.is(CommonTags.MECHANICAL_APOTHECARY_CATALYSTS);
					}, SEEDS_SLOT)
					.validator((stack) -> {
						return this.level != null && RecipeHelper.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.PETAL_APOTHECARY_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		}

	}

	public void bufferCheck() {
		int old = seedBuffer;
		seedBuffer = seedBuffer > this.inventory.getStackInSlot(0).getCount() ? seedBuffer - this.inventory.getStackInSlot(0).getCount() : 0;
		this.inventory.extractItem(0, seedBuffer == 0 ? old : this.inventory.getStackInSlot(0).getCount(), false);
	}

	//region Base
	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide()) {
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

			if (!this.inventory.getStackInSlot(0).isEmpty() && seedBuffer != 0) {
				bufferCheck();
			}
			upgradedCheck();

			this.runRecipeTick();
			if (this.recipe != null) {
				this.currentOutput = this.recipe.getResultItem(this.level.registryAccess()).copy();
				this.setChanged();
				this.setDispatchable();
			} else if (!this.currentOutput.isEmpty()) {
				this.currentOutput = ItemStack.EMPTY;
				this.setChanged();
				this.setDispatchable();
			}

		} else if (this.level != null && LibXClientConfig.RenderingVisualContent.all && settingPattern.getConfigBoolean("mechanicalApothecaryRender") && this.fluidInventory.getFluidAmount() > 0) {
			int slot;
			if (this.getMaxProgress() > 0 && this.getProgress() > this.getMaxProgress() - 5) {
				for (slot = 0; slot < 5; ++slot) {
					SparkleParticleData data = SparkleParticleData.sparkle(this.level.random.nextFloat(), this.level.random.nextFloat(), this.level.random.nextFloat(), this.level.random.nextFloat(), 10);
					this.level.addParticle(data, (double) this.worldPosition.getX() + 0.3 + this.level.random.nextDouble() * 0.4, (double) this.worldPosition.getY() + 0.6, (double) this.worldPosition.getZ() + 0.3 + this.level.random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
				}

				this.level.playLocalSound((double) this.worldPosition.getX() + 0.5, (double) this.worldPosition.getY() + 0.5, (double) this.worldPosition.getZ() + 0.5, BotaniaSounds.RUNIC_ALTAR_CRAFT, SoundSource.BLOCKS, 1.0F, 1.0F, false);
			} else {
				for (slot = 0; slot < this.inventory.getSlots(); ++slot) {
					ItemStack stack = this.inventory.getStackInSlot(slot);
					if (!stack.isEmpty() && this.level.random.nextFloat() >= 0.97F) {
						Item item1 = stack.getItem();
						int i;
						if (item1 instanceof CustomApothecaryColor) {
							CustomApothecaryColor item = (CustomApothecaryColor) item1;
							i = item.getParticleColor(stack);
						} else {
							i = 8947848;
						}

						int color = i;
						float red = (float) (color >> 16 & 255) / 255.0F;
						float green = (float) (color >> 8 & 255) / 255.0F;
						float blue = (float) (color & 255) / 255.0F;
						if (Math.random() >= 0.75) {
							this.level.playSound((Player) null, this.worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.1F, 10.0F);
						}

						SparkleParticleData data = SparkleParticleData.sparkle(this.level.random.nextFloat(), red, green, blue, 10);
						this.level.addParticle(data, (double) this.worldPosition.getX() + 0.3 + this.level.random.nextDouble() * 0.4, (double) this.worldPosition.getY() + 0.6, (double) this.worldPosition.getZ() + 0.3 + this.level.random.nextDouble() * 0.4, 0.0, 0.0, 0.0);
					}
				}
			}
		}
	}

	private boolean checkOutputSlots() {
		for (int i = FIRST_OUTPUT_SLOT; i <= LAST_OUTPUT_SLOT; i++) {
			if (!inventory.getStackInSlot(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public List<ItemStack> getUpgrades() {
		List<ItemStack> res = new ArrayList<>();

		if (UPGRADE_SLOT_1 != -1) {
			res.add(new ItemStack(ModItems.catalystSeedInfinity));
		}

		if (UPGRADE_SLOT_2 != -1) {
			res.add(new ItemStack(ModItems.catalystWaterInfinity));
		}

		return res;
	}

	public void upgradedCheck() {
		if (UPGRADE_SLOT_1 != -1 && UPGRADE_SLOT_2 == -1) {
			if (!inventory.getStackInSlot(UPGRADE_SLOT_1).isEmpty() && inventory.getStackInSlot(SEEDS_SLOT).isEmpty()) {
				ItemStack seed = Items.WHEAT_SEEDS.getDefaultInstance();
				seed.setCount(64);

				inventory.setStackInSlot(SEEDS_SLOT, seed);
			}
		} else if (UPGRADE_SLOT_1 != -1) {

			if (((!inventory.getStackInSlot(UPGRADE_SLOT_1).isEmpty() && inventory.getStackInSlot(UPGRADE_SLOT_1).getItem().asItem() == ModItems.catalystSeedInfinity.asItem()) ||
					(!inventory.getStackInSlot(UPGRADE_SLOT_2).isEmpty() && inventory.getStackInSlot(UPGRADE_SLOT_2).getItem().asItem() == ModItems.catalystSeedInfinity.asItem())) &&
					inventory.getStackInSlot(SEEDS_SLOT).isEmpty()) {
				ItemStack seed = Items.WHEAT_SEEDS.getDefaultInstance();
				seed.setCount(64);

				inventory.setStackInSlot(SEEDS_SLOT, seed);
			}

			isInfinityWater =
					(!inventory.getStackInSlot(UPGRADE_SLOT_1).isEmpty() && inventory.getStackInSlot(UPGRADE_SLOT_1).getItem().asItem() == ModItems.catalystWaterInfinity.asItem()) ||
							(!inventory.getStackInSlot(UPGRADE_SLOT_2).isEmpty() && inventory.getStackInSlot(UPGRADE_SLOT_2).getItem().asItem() == ModItems.catalystWaterInfinity.asItem());
		}
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> inventory) {
		return (slot) -> {
			return slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
		};
	}

	@Nonnull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Nonnull
	public FluidTank getFluidInventory() {
		return this.fluidInventory;
	}

	@Override
	public boolean actAsMana() {
		return false;
	}

	@Override
	protected int getAndApplyProgressThisTick() {
		return 1;
	}

	@Override
	protected boolean canMatchRecipes() {
		if (this.inventory.getStackInSlot(0).isEmpty()) {
			return false;
		} else {
			if (isInfinityWater) {
				return true;
			}
			FluidStack fluid = this.getFluidInventory().getFluid();
			return !fluid.isEmpty() && fluid.getFluid() == Fluids.WATER && fluid.getAmount() >= 1000;
		}
	}

	@Override
	protected void onCrafted(PetalApothecaryRecipe recipe, int countItemCraft) {
		seedBuffer = countItemCraft > this.inventory.getStackInSlot(0).getCount() ? countItemCraft - this.inventory.getStackInSlot(0).getCount() : 0;

		this.inventory.extractItem(0, seedBuffer == 0 ? countItemCraft : this.inventory.getStackInSlot(0).getCount(), false);
		FluidStack fluid = this.getFluidInventory().getFluid().copy();
		if (!isInfinityWater && fluid.getFluid() == Fluids.WATER) {
			int newAmount = Math.max(0, fluid.getAmount() - 1000);
			fluid.setAmount(newAmount);
			this.fluidInventory.setFluid(fluid);
		}
	}

	@Override
	protected int getMaxProgress(PetalApothecaryRecipe recipe) {
		return WORKING_DURATION * settingPattern.getConfigInt("craftTime");
	}

	@Override
	public int getMaxManaPerTick() {
		return 1;
	}

	public ItemStack getCurrentOutput() {
		return this.currentOutput;
	}

	// 1.21.1: capabilities are registered via RegisterCapabilitiesEvent (see BemRegistration),
	// not through the Forge getCapability override.

	@Override
	public void loadAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.fluidInventory.setFluid((nbt.contains("fluid") ? net.neoforged.neoforge.fluids.FluidStack.parse(registries, nbt.getCompound("fluid")).orElse(FluidStack.EMPTY) : FluidStack.EMPTY));
		this.currentOutput = ItemStack.parseOptional(registries, nbt.getCompound("currentOutput"));
		this.seedBuffer = nbt.getInt("seedBuffer");
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		// FluidStack.save and ItemStack.save both throw on an empty value since 1.21,
		// and both start out empty. loadAdditional falls back to EMPTY when the tags
		// are absent, so only write them when there is something to store.
		FluidStack fluid = this.getFluidInventory().getFluid();
		if (!fluid.isEmpty()) {
			nbt.put("fluid", fluid.save(registries));
		}
		if (!this.currentOutput.isEmpty()) {
			nbt.put("currentOutput", this.currentOutput.save(registries));
		}
		nbt.putInt("seedBuffer", this.seedBuffer);
		this.me.save(nbt);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		super.handleUpdateTag(nbt, registries);
		if (this.level == null || this.level.isClientSide) {
			// registries, not this.level.registryAccess(): this branch runs
			// precisely when this.level may be null.
			this.fluidInventory.setFluid((nbt.contains("fluid") ? net.neoforged.neoforge.fluids.FluidStack.parse(registries, nbt.getCompound("fluid")).orElse(FluidStack.EMPTY) : FluidStack.EMPTY));
			this.currentOutput = ItemStack.parseOptional(registries, nbt.getCompound("currentOutput"));
			this.seedBuffer = nbt.getInt("seedBuffer");
		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);
			FluidStack fluid = this.getFluidInventory().getFluid();
			if (!fluid.isEmpty()) {
				CompoundTag tankTag = new CompoundTag();
				fluid.save(registries, tankTag);
				nbt.put("fluid", tankTag);
			}
			if (!this.currentOutput.isEmpty()) {
				nbt.put("currentOutput", this.currentOutput.save(registries));
			}
			nbt.putInt("seedBuffer", this.seedBuffer);
			return nbt;
		}
	}

	private class ApothecaryFluidTank extends FluidTank {
		public ApothecaryFluidTank(int capacity, Predicate<FluidStack> validator) {
			super(capacity, validator);
		}

		@Override
		protected void onContentsChanged() {
			BlockEntityApothecaryPattern.this.setChanged();
			BlockEntityApothecaryPattern.this.setDispatchable();
			BlockEntityApothecaryPattern.this.needsRecipeUpdate();
		}

		@Nonnull
		@Override
		public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Nonnull
		@Override
		public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	public BlockEntity getBlockEntity() {
		return this;
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
		if (blockEntity instanceof BlockEntityApothecaryBase) {
			return ModBlocks.baseApothecary.asItem();
		} else if (blockEntity instanceof BlockEntityApothecaryUpgraded) {
			return ModBlocks.upgradedApothecary.asItem();
		} else if (blockEntity instanceof BlockEntityApothecaryAdvanced) {
			return ModBlocks.advancedApothecary.asItem();
		} else {
			return ModBlocks.ultimateApothecary.asItem();
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
}
