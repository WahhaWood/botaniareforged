package net.lmor.botanicalextramachinery.blocks.pattern;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalDaisy.BlockEntityDaisyAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalDaisy.BlockEntityDaisyBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalDaisy.BlockEntityDaisyUpgraded;
import net.lmor.botanicalextramachinery.config.LibXClientConfig;
import net.lmor.botanicalextramachinery.config.LibXServerConfig.DaisySettings;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.PureDaisyRecipe;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityDaisyPattern extends ExtraBotanicalTile implements
		MeOutput.Holder {
	/**
	 * Every tier shares one mana pool size; the daisies differ in slot count and
	 * craft speed, not in how much mana they hold.
	 */
	private static final int MANA_CAPACITY = 10000;

	private int ticksToNextUpdate = DaisySettings.ticksToNextUpdate;
	private int[] workingTicks;
	private BaseItemStackHandler inventory;

	private final int SLOT_UPGRADE;
	private BaseItemStackHandler inventoryUpgrade;

	private final int sizeItemSlots;
	private final int countSlotInventory;
	private final SettingPattern setting;
	private boolean recipeOutputItem = false;

	private final FluidTank tank;
	private int waterProgress = 0;

	public BlockEntityDaisyPattern(BlockEntityType<?> type, BlockPos pos, BlockState state, int countSlotInventory,
			SettingPattern settingPattern, int... slotUpgrade) {
		super(type, pos, state, MANA_CAPACITY);

		this.SLOT_UPGRADE = slotUpgrade.length == 0 ? -1 : slotUpgrade[0];
		if (this.SLOT_UPGRADE != -1) {
			inventoryUpgrade = BaseItemStackHandler.builder(this.SLOT_UPGRADE + 1)
					.validator(stack -> stack.getItem() == ModItems.catalystStoneInfinity.asItem() || stack.getItem() == ModItems.catalystWoodInfinity.asItem(), this.SLOT_UPGRADE)
					.slotLimit(1, this.SLOT_UPGRADE).output().contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.updateUpgradeSlot();
					})
					.build();
		}

		this.countSlotInventory = countSlotInventory;
		this.setting = settingPattern;
		this.sizeItemSlots = settingPattern.getConfigInt("sizeSlots");

		int buckets = 16; // base default (number of buckets used for tank capacity calculation)
		if (this instanceof BlockEntityDaisyUpgraded) {
			buckets = 32;
		} else if (this instanceof BlockEntityDaisyAdvanced) {
			buckets = 64;
		} else if (!(this instanceof BlockEntityDaisyBase)) {
			buckets = 128;
		}
		this.tank = new WaterOnlyTank(buckets * 1000);

		this.workingTicks = new int[this.countSlotInventory];

		// The handler carries the daisy's own rules through the builder rather
		// than through an overridden ItemStackHandler: the capability exposed by
		// ExtraBotanicalTile hands this handler to hoppers and pipes directly, and
		// BaseItemStackHandler is what that plumbing expects.
		this.inventory = BaseItemStackHandler.builder(this.countSlotInventory)
				.defaultSlotLimit(this.sizeItemSlots)
				.validator(this::isCraftable)
				.contentsChanged(slot -> {
					if (slot >= 0 && slot < this.countSlotInventory && this.inventory.getStackInSlot(slot).isEmpty()) {
						this.workingTicks[slot] = 0;
					}
					this.setChanged();
					this.setDispatchable();
				})
				.build();

	}

	// region: ExtraBotanicalTile plumbing
	@Nonnull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	public BaseItemStackHandler getInventoryUpgrade() {
		return this.inventoryUpgrade;
	}

	/**
	 * The tank backs the water-to-snow conversion. Without a capability nothing
	 * can fill it, so the branch in tick() that makes snow is unreachable.
	 */
	public IFluidHandler getTankCap() {
		return this.tank;
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> inventory) {
		// Pulling from a slot mid-craft would hand out the input before it is done.
		return slot -> slot == null || slot < 0 || slot >= this.workingTicks.length || this.workingTicks[slot] < 0;
	}

	@Override
	protected BiPredicate<Integer, ItemStack> getInserts(Supplier<IItemHandlerModifiable> inventory) {
		return (slot, stack) -> this.inventory.getStackInSlot(slot).isEmpty() || this.workingTicks[slot] < 0;
	}

	@Override
	public int getComparatorOutput() {
		return 0;
	}

	/**
	 * The vanilla handler lets a slot take any block; the daisy only converts
	 * blocks a pure daisy recipe exists for.
	 */
	private boolean isCraftable(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem
				&& this.getRecipe(blockItem.getBlock().defaultBlockState()) != null;
	}
	// endregion

	// region: Base behavior and ticking logic
	@Override
	public void tick() {
		boolean hasSpawnedParticles = false;

		for (int i = 0; i < this.countSlotInventory; ++i) {
			if (!this.inventory.getStackInSlot(i).isEmpty() &&
					(this.inventory.getStackInSlot(i).getItem() == BotaniaBlocks.LIVINGROCK.asItem() ||
							this.inventory.getStackInSlot(i).getItem() == BotaniaBlocks.LIVINGWOOD_LOG.asItem())) {
					continue;
				}

			PureDaisyRecipe recipe = this.getRecipe(i);

			if (recipe != null && this.workingTicks[i] >= 0) {
				assert this.level != null;
				if (!this.level.isClientSide) {

					if (this.workingTicks[i] >= recipe.getTime() * this.setting.getConfigInt("durationTime")) {
						// 1.21.1: PureDaisyRecipe#getOutput returns a StateIngredient; pick() resolves
						// one concrete BlockState (a daisy output may have several variants).
						BlockState state = recipe.getOutput().pick(this.level.getRandom());

						this.recipeOutputItem = true;
						//noinspection deprecation
						this.setCraftResult(i, state.getBlock().getCloneItemStack(this.level, this.worldPosition, state));

						this.workingTicks[i] = -1;
						this.setDispatchable();
					} else {
						this.workingTicks[i]++;
					}
				} else if (!hasSpawnedParticles && LibXClientConfig.RenderingVisualContent.all && this.setting.getConfigBoolean("renderingDaisy")) {
					hasSpawnedParticles = true;
					double x = (double) this.worldPosition.getX() + Math.random();
					double y = (double) this.worldPosition.getY() + Math.random() + 0.25;
					double z = (double) this.worldPosition.getZ() + Math.random();
					WispParticleData data = WispParticleData.wisp((float) Math.random() / 2.0F, 1.0F, 1.0F, 1.0F);
					this.level.addParticle(data, x, y, z, 0.0, 0.0, 0.0);
				}
			} else if (this.workingTicks[i] < 0 && !this.inventory.getStackInSlot(i).isEmpty()) {
				this.workingTicks[i] = -1;
			} else {
				this.workingTicks[i] = 0;
			}
		}

		if (this.level != null && !this.level.isClientSide) {

			this.me.tick(this.level, this.getBlockPos());

			if (this.me.isOnline() && this.recipeOutputItem) {
				exportResultsItemsME();
				this.recipeOutputItem = false;
			}

			FluidStack fluidInTank = this.tank.getFluid();
			boolean createdSnow = false;

			if (!fluidInTank.isEmpty() && Fluids.WATER.isSame(fluidInTank.getFluid())) {
				int multiplier = 2;
				if (this instanceof BlockEntityDaisyUpgraded) {
					multiplier = 4;
				} else if (this instanceof BlockEntityDaisyAdvanced) {
					multiplier = 8;
				} else if (!(this instanceof BlockEntityDaisyBase)) {
					multiplier = 16;
				}

				int availableCapacity = 0;
				for (int slot = 0; slot < this.countSlotInventory; slot++) {
					ItemStack s = this.inventory.getStackInSlot(slot);
					int limit = this.inventory.getSlotLimit(slot);
					if (s.isEmpty()) {
						availableCapacity += limit;
					}
					else if (s.getItem() instanceof BlockItem && ((BlockItem) s.getItem()).getBlock() == Blocks.SNOW_BLOCK) {
						availableCapacity += Math.max(0, limit - s.getCount());
					}
				}

				int possibleFromWater = fluidInTank.getAmount() / 1000;
				if (availableCapacity > 0 && possibleFromWater > 0) {
					this.waterProgress++;
					if (this.waterProgress >= 200) {
						int waterAvailable = fluidInTank.getAmount();
						int totalCreated = 0;

						for (int slot = 0; slot < this.countSlotInventory && waterAvailable >= 1000; slot++) {
							ItemStack s = this.inventory.getStackInSlot(slot);
							int limit = this.inventory.getSlotLimit(slot);

							if (s.isEmpty()) {
								int put = Math.min(multiplier, limit);
								int maxByWater = waterAvailable / 1000;
								put = Math.min(put, maxByWater);
								if (put > 0) {
									this.inventory.setStackInSlot(slot, new ItemStack(Blocks.SNOW_BLOCK, put));
									waterAvailable -= 1000 * put;
									totalCreated += put;
								}
							} else if (s.getItem() instanceof BlockItem && ((BlockItem) s.getItem()).getBlock() == Blocks.SNOW_BLOCK) {
								int canAdd = limit - s.getCount();
								if (canAdd > 0) {
									int put = Math.min(multiplier, canAdd);
									int maxByWater = waterAvailable / 1000;
									put = Math.min(put, maxByWater);
									s.grow(put);
									this.inventory.setStackInSlot(slot, s);
									waterAvailable -= 1000 * put;
									totalCreated += put;
								}
							}
						}

						if (totalCreated > 0) {
							int consumed = fluidInTank.getAmount() - waterAvailable;
							if (consumed > 0) {
								this.tank.drain(consumed, IFluidHandler.FluidAction.EXECUTE);
							}
							createdSnow = true;
						}

						this.waterProgress = 0;
					}
				}
			} else {
				this.waterProgress = 0;
			}

			if (createdSnow) {
				this.setChanged();
				this.setDispatchable();
			}

			if (this.ticksToNextUpdate <= 0) {
				this.ticksToNextUpdate = DaisySettings.ticksToNextUpdate;
				org.moddingx.libx.impl.BlockEntityUpdateQueue.scheduleUpdate(this.getLevel(), this.getBlockPos());
			} else {
				--this.ticksToNextUpdate;
			}
		}
		updateUpgradeSlot();
	}

	/**
	 * Writes the craft result without losing what the slot already held: the
	 * clone stack from getCloneItemStack always has a count of one, while the
	 * daisy converts a whole stack at a time.
	 */
	private void setCraftResult(int slot, ItemStack result) {
		ItemStack existing = this.inventory.getStackInSlot(slot);
		if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, result)) {
			result.setCount(existing.getCount());
		}
		this.inventory.setStackInSlot(slot, result);
	}

	private void updateUpgradeSlot() {
		if (this.inventoryUpgrade == null || this.inventoryUpgrade.getStackInSlot(0).isEmpty()) {
			return;
		}

		for (int i = 0; i < this.countSlotInventory; i++) {
			activeUpgradeSlot(i);
		}
	}

	private void activeUpgradeSlot(int slot) {
		if (!this.inventory.getStackInSlot(slot).isEmpty()) {
			return;
		}

		if (this.inventoryUpgrade != null && !this.inventoryUpgrade.getStackInSlot(0).isEmpty()) {
			ItemStack upgradeItem = this.inventoryUpgrade.getStackInSlot(this.SLOT_UPGRADE);
			ItemStack setSlotItem = ItemStack.EMPTY;

			if (upgradeItem.getItem() == ModItems.catalystStoneInfinity.asItem()) {
				setSlotItem = new ItemStack(Blocks.STONE).copy();
			} else if (upgradeItem.getItem() == ModItems.catalystWoodInfinity.asItem()) {
				setSlotItem = new ItemStack(Blocks.OAK_LOG).copy();
			}

			if (!setSlotItem.isEmpty()) {
				setSlotItem.setCount(this.setting.getConfigInt("sizeSlots"));
				this.inventory.setStackInSlot(slot, setSlotItem.copy());
			}
		}
	}

	@Nullable
	private PureDaisyRecipe getRecipe(int slot) {
		BlockState state = this.getState(slot);
		return state == null ? null : this.getRecipe(state);
	}

	@Nullable
	public BlockState getState(int slot) {
		BlockState state = null;
		ItemStack stack = this.inventory.getStackInSlot(slot);

		if (stack.getItem() instanceof BlockItem) {
			state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
		}
		return state;
	}

	@Nullable
	public PureDaisyRecipe getRecipe(BlockState state) {
		if (this.level != null) {
			for (RecipeHolder<PureDaisyRecipe> pureDaisyRecipeHolder : this.level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.PURE_DAISY_TYPE)) {
				if (pureDaisyRecipeHolder.value().matches(this.level, this.worldPosition, state)) {
					return pureDaisyRecipeHolder.value();
				}
			}
		}
		return null;
	}

	public List<ItemStack> getUpgrades() {
		List<ItemStack> upgrade = new ArrayList<>();
		upgrade.add(new ItemStack(ModItems.catalystWoodInfinity));
		upgrade.add(new ItemStack(ModItems.catalystStoneInfinity));
		return upgrade;
	}

	// 1.21.1: capabilities are registered via RegisterCapabilitiesEvent (see BemRegistration),
	// not through the Forge getCapability override.

	@Override
	public void loadAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		// ExtraBotanicalTile reads "inv" into the main inventory; the upgrade
		// inventory uses its own key so the two cannot overwrite each other.
		super.loadAdditional(nbt, registries);
		if (nbt.contains("workingTicks")) {
			int[] loaded = nbt.getIntArray("workingTicks");
			this.workingTicks = new int[this.countSlotInventory];
			System.arraycopy(loaded, 0, this.workingTicks, 0, Math.min(loaded.length, this.countSlotInventory));
		}
		if (this.inventoryUpgrade != null && nbt.contains("invUpgrade")) {
			this.inventoryUpgrade.deserializeNBT(registries, nbt.getCompound("invUpgrade"));
		}
		if (nbt.contains("tank")) {
			// an empty tank is saved as an empty tag, which parse() would log as an error
			FluidStack loaded = nbt.getCompound("tank").isEmpty() ? FluidStack.EMPTY : FluidStack.parse(registries, nbt.getCompound("tank")).orElse(FluidStack.EMPTY);
			if (loaded != null && !loaded.isEmpty() && Fluids.WATER.isSame(loaded.getFluid())) {
				this.tank.setFluid(loaded);
			} else {
				this.tank.setFluid(FluidStack.EMPTY);
			}
		}
		if (nbt.contains("waterProgress")) {
			this.waterProgress = nbt.getInt("waterProgress");
		}
		this.me.load(nbt);
		this.setChanged();
		this.setDispatchable();
	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putIntArray("workingTicks", this.workingTicks);
		this.me.save(nbt);
		if (this.inventoryUpgrade != null) {
			nbt.put("invUpgrade", this.inventoryUpgrade.serializeNBT(registries));
		}
		CompoundTag tankNbt = new CompoundTag();
		if (!this.tank.getFluid().isEmpty()) {
			tankNbt = (CompoundTag) this.tank.getFluid().save(registries);
		}
		nbt.put("tank", tankNbt);
		nbt.putInt("waterProgress", this.waterProgress);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		super.handleUpdateTag(nbt, registries);
		// registries is the provider to read ItemStacks with: this.level can
		// still be null here, so this.level.registryAccess() would throw.
		if (this.level == null || this.level.isClientSide) {
			if (nbt.contains("workingTicks")) {
				int[] loaded = nbt.getIntArray("workingTicks");
				this.workingTicks = new int[this.countSlotInventory];
				System.arraycopy(loaded, 0, this.workingTicks, 0, Math.min(loaded.length, this.countSlotInventory));
			}
			if (this.inventoryUpgrade != null && nbt.contains("invUpgrade")) {
				this.inventoryUpgrade.deserializeNBT(registries, nbt.getCompound("invUpgrade"));
			}
			if (nbt.contains("tank")) {
				// an empty tank is saved as an empty tag, which parse() would log as an error
			FluidStack loaded = nbt.getCompound("tank").isEmpty() ? FluidStack.EMPTY : FluidStack.parse(registries, nbt.getCompound("tank")).orElse(FluidStack.EMPTY);
				if (loaded != null && !loaded.isEmpty() && Fluids.WATER.isSame(loaded.getFluid())) {
					this.tank.setFluid(loaded);
				} else {
					this.tank.setFluid(FluidStack.EMPTY);
				}
			}
			if (nbt.contains("waterProgress")) {
				this.waterProgress = nbt.getInt("waterProgress");
			}
		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag nbt = super.getUpdateTag(registries);
		if (this.level != null && this.level.isClientSide) {
			return nbt;
		}
		nbt.putIntArray("workingTicks", this.workingTicks);
		if (this.inventoryUpgrade != null) {
			nbt.put("invUpgrade", this.inventoryUpgrade.serializeNBT(registries));
		}
		CompoundTag tankNbt = new CompoundTag();
		if (!this.tank.getFluid().isEmpty()) {
			tankNbt = (CompoundTag) this.tank.getFluid().save(registries);
		}
		nbt.put("tank", tankNbt);
		nbt.putInt("waterProgress", this.waterProgress);
		return nbt;
	}

	public BlockEntity getBlockEntity() {
		return this;
	}

	public void drops() {
		if (this.inventoryUpgrade != null && !this.inventoryUpgrade.getStackInSlot(0).isEmpty()) {
			assert this.level != null;
			ItemEntity ie = new ItemEntity(this.level, (double) this.worldPosition.getX() + 0.5, (double) this.worldPosition.getY() + 0.7, (double) this.worldPosition.getZ() + 0.5, this.inventoryUpgrade.getStackInSlot(0).copy());
			this.level.addFreshEntity(ie);
		}
	}

	//region AE INTEGRATION

	private final MeOutput me = MeOutput.create(this, this.getItemFromBlockEntity());

	@Override
	public MeOutput meOutput() {
		return this.me;
	}

	protected Item getItemFromBlockEntity() {
		BlockEntity blockEntity = this.getBlockEntity();
		if (blockEntity instanceof BlockEntityDaisyBase) {
			return ModBlocks.baseDaisy.asItem();
		} else if (blockEntity instanceof BlockEntityDaisyUpgraded) {
			return ModBlocks.upgradedDaisy.asItem();
		} else if (blockEntity instanceof BlockEntityDaisyAdvanced) {
			return ModBlocks.advancedDaisy.asItem();
		} else {
			return ModBlocks.ultimateDaisy.asItem();
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		this.me.destroy();
		// 1.21.1: no LazyOptional to invalidate
	}

	private void exportResultsItemsME() {
		for (int slot = 0; slot < this.countSlotInventory; slot++) {
			ItemStack stackInSlot = this.inventory.getStackInSlot(slot);
			if (!stackInSlot.isEmpty()) {
				int getCountExport = this.me.insert(stackInSlot);
				if (getCountExport > 0) {
					stackInSlot.shrink(getCountExport);
					this.inventory.setStackInSlot(slot, stackInSlot);
					this.inventory.onContentsChanged(slot);
				}
			}
			this.activeUpgradeSlot(slot);
		}
	}

	private static class WaterOnlyTank extends FluidTank {
		public WaterOnlyTank(int capacity) {
			super(capacity);
		}

		@Override
		public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
			if (resource == null || resource.isEmpty()) {
				return 0;
			}
			if (!Fluids.WATER.isSame(resource.getFluid())) {
				return 0;
			}
			return super.fill(resource, action);
		}

		@Override
		public boolean isFluidValid(FluidStack stack) {
			return stack != null && !stack.isEmpty() && Fluids.WATER.isSame(stack.getFluid());
		}
	}

}
