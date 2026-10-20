package net.lmor.botanicalextramachinery.blocks.pattern;

import com.google.common.collect.Range;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.base.RecipeTile;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolAdvanced;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolBase;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaPool.BlockEntityManaPoolUpgraded;
import net.lmor.botanicalextramachinery.config.LibXClientConfig;
import net.lmor.botanicalextramachinery.config.LibXServerConfig;
import net.lmor.botanicalextramachinery.util.RecipeValidityCache;
import net.lmor.botanicalextramachinery.util.SettingPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;
import org.moddingx.libx.inventory.BaseItemStackHandler;

import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.crafting.BotaniaRecipeTypes;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BlockEntityManaPoolPattern extends RecipeTile<ManaInfusionRecipe>
		implements MeOutput.Holder {

	private static List<Item> CACHED_CATALYSTS;

	private final int CATALYSTS_SLOT;
	private final int UPGRADE_SLOT;
	private final int FIRST_INPUT_SLOT;
	private final int LAST_INPUT_SLOT;
	private final int FIRST_OUTPUT_SLOT;
	private final int LAST_OUTPUT_SLOT;

	private final BaseItemStackHandler inventory;
	private final SettingPattern settingPattern;
	private final Boolean isUpgrade;

	private int cooldown = 0;
	private boolean checkWithCatalyst = false;
	private int timeCheckOutputSlot = LibXServerConfig.tickOutputSlots;

	private boolean isInfinityMana = false;

	public BlockEntityManaPoolPattern(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state,
			int manaCap, int[] slots, boolean isUpgrade, int countCraft, SettingPattern config) {
		super(blockEntityType, BotaniaRecipeTypes.MANA_INFUSION_TYPE, pos, state, manaCap, slots[1], slots[3], countCraft);

		CATALYSTS_SLOT = slots[0];
		UPGRADE_SLOT = isUpgrade ? slots[5] : -1;
		FIRST_INPUT_SLOT = slots[1];
		LAST_INPUT_SLOT = slots[2];
		FIRST_OUTPUT_SLOT = slots[3];
		LAST_OUTPUT_SLOT = slots[4];

		if (isUpgrade) {
			inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.getCatalysts().contains(stack.getItem());
					}, CATALYSTS_SLOT)
					.validator((stack) -> {
						return stack.getItem() == ModItems.catalystManaInfinity.asItem();
					}, UPGRADE_SLOT)
					.validator((stack) -> {
						return this.level != null && RecipeValidityCache.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.MANA_INFUSION_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, CATALYSTS_SLOT, UPGRADE_SLOT).output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();

		} else {
			inventory = BaseItemStackHandler.builder(LAST_OUTPUT_SLOT + 1)
					.validator((stack) -> {
						return this.getCatalysts().contains(stack.getItem());
					}, CATALYSTS_SLOT)
					.validator((stack) -> {
						return this.level != null && RecipeValidityCache.isItemValidInput(this.level.getRecipeManager(), BotaniaRecipeTypes.MANA_INFUSION_TYPE, stack);
					}, Range.closedOpen(FIRST_INPUT_SLOT, LAST_INPUT_SLOT + 1))
					.slotLimit(1, CATALYSTS_SLOT).output(Range.closedOpen(FIRST_OUTPUT_SLOT, LAST_OUTPUT_SLOT + 1)).contentsChanged(() -> {
						this.setChanged();
						this.setDispatchable();
						this.needsRecipeUpdate();
					})
					.build();
		}

		this.settingPattern = config;
		this.isUpgrade = isUpgrade;

	}

	//region BASE CODE
	@Override
	public void tick() {
		if (this.level != null && !this.level.isClientSide) {
			this.me.tick(this.level, this.getBlockPos());

			isInfinityMana = UPGRADE_SLOT != -1 && !inventory.getStackInSlot(UPGRADE_SLOT).isEmpty();

			this.updateRecipeIfNeeded();

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

			if (this.cooldown > 0) {
				--this.cooldown;
				this.setChanged();
				this.setDispatchable();
			}

			if (this.cooldown <= 0 && this.recipe != null) {
				this.craftRecipe();
			}

		} else if (this.level != null && LibXClientConfig.RenderingVisualContent.all && settingPattern.getConfigBoolean("mechanicalManaPoolRender")) {
			double particleChance = (double) this.getCurrentMana() / (double) this.getMaxMana() * 0.1;
			if (Math.random() < particleChance) {
				float red = 0.0F;
				float green = 0.7764706F;
				float blue = 1.0F;
				WispParticleData data = WispParticleData.wisp((float) Math.random() / 3.0F, red, green, blue, 2.0F);
				this.level.addParticle(data, (double) this.worldPosition.getX() + 0.3 + this.level.random.nextDouble() * 0.4, (double) this.worldPosition.getY() + 0.5 + this.level.random.nextDouble() * 0.25, (double) this.worldPosition.getZ() + 0.3 + this.level.random.nextDouble() * 0.4, 0.0, (double) (this.level.random.nextFloat() / 25.0F), 0.0);
			}
		}
	}

	@Override
	protected Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> supplier) {
		return (slot) -> {
			return slot >= FIRST_OUTPUT_SLOT && slot <= LAST_OUTPUT_SLOT;
		};
	}

	@Nonnull
	@Override
	public BaseItemStackHandler getInventory() {
		return this.inventory;
	}

	@Override
	protected java.util.function.BiPredicate<Integer, ItemStack> getInserts(Supplier<IItemHandlerModifiable> supplier) {
		// Delegate to the centralized insert guard in ExtraBotanicalTile so the "pool full" check is respected.
		return super.getInserts(supplier);
	}

	@Override
	protected void updateRecipe(BiConsumer<ItemStack, Integer> usedStacks) {
		this.recipe = null;
		this.checkWithCatalyst = true;
		super.updateRecipe(usedStacks);
		this.checkWithCatalyst = false;
		if (this.recipe == null) {
			super.updateRecipe(usedStacks);
		}
	}

	@Override
	protected boolean matchRecipe(ManaInfusionRecipe recipe, List<ItemStack> stacks) {
		if (recipe.getManaToConsume() > this.getCurrentMana() && !isInfinityMana) {
			return true;
		}

		// Botania spells "this recipe wants no catalyst" as a state ingredient
		// that tests every state true, and ManaInfusionRecipe normalises a
		// missing catalyst field to exactly that, so getRecipeCatalyst() is never
		// null. The old `== null` test therefore rejected every catalyst-less
		// recipe outright: with the catalyst slot empty the pool refused to craft
		// anything at all. Asking the ingredient whether the empty state satisfies
		// it is what tells the two cases apart.
		boolean needsCatalyst = recipe.getRecipeCatalyst() != null
				&& !recipe.getRecipeCatalyst().test(Blocks.AIR.defaultBlockState());

		Item catalystItem = this.checkWithCatalyst && !this.inventory.getStackInSlot(CATALYSTS_SLOT).isEmpty() ? this.inventory.getStackInSlot(CATALYSTS_SLOT).getItem() : null;
		Block catalyst = catalystItem == null ? null : Block.byItem(catalystItem);

		if (needsCatalyst && (catalyst == null || !recipe.getRecipeCatalyst().test(catalyst.defaultBlockState()))) {
			return true;
		}
		return super.matchRecipe(recipe, stacks);
	}

	@Override
	protected void onCrafted(ManaInfusionRecipe recipe, int countItemCraft) {
		this.cooldown = Math.max(1, settingPattern.getConfigInt("craftTime"));

		if (isUpgrade && !inventory.getStackInSlot(UPGRADE_SLOT).isEmpty()) {
			return;
		}

		if (isInfinityMana) {
			return;
		}
		for (int i = 0; i < countItemCraft; i++) {
			this.receiveMana(-recipe.getManaToConsume());
		}
	}

	@Override
	public int getComparatorOutput() {
		if (this.inventory.getStackInSlot(CATALYSTS_SLOT).isEmpty()) {
			return 0;
		} else {
			Item item = this.inventory.getStackInSlot(CATALYSTS_SLOT).getItem();
			return this.getCatalysts().contains(item) ? Mth.clamp(1 + this.getCatalysts().indexOf(item), 0, 15) : 0;
		}
	}

	@Override
	public void receiveMana(int i) {
		if (inventory.getStackInSlot(CATALYSTS_SLOT).getItem() == BotaniaBlocks.MANA_VOID.asItem()) {
			super.receiveMana(Math.min(i, this.getAvailableSpaceForMana()));
		} else {
			super.receiveMana(i);
		}

		if (this.recipe == null && checkInputSlots()) {
			this.needsRecipeUpdate();
		}
	}

	private boolean checkInputSlots() {
		for (int i = FIRST_INPUT_SLOT; i <= LAST_INPUT_SLOT; i++) {
			if (!inventory.getStackInSlot(i).isEmpty()) {
				return true;
			}
		}
		return false;
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
	public boolean isFull() {
		return this.inventory.getStackInSlot(CATALYSTS_SLOT).getItem() != BotaniaBlocks.MANA_VOID.asItem() && super.isFull();
	}

	@Override
	public int getAvailableSpaceForMana() {
		return this.inventory.getStackInSlot(CATALYSTS_SLOT).getItem() == BotaniaBlocks.MANA_VOID.asItem() ? this.getMaxMana() : super.getAvailableSpaceForMana();
	}

	@Override
	public void loadAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.loadAdditional(nbt, registries);
		this.cooldown = nbt.getInt("cooldown");
		this.me.load(nbt);

		this.setChanged();
		this.setDispatchable();

	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.putInt("cooldown", this.cooldown);
		this.me.save(nbt);
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt, HolderLookup.Provider registries) {
		super.handleUpdateTag(nbt, registries);
		if (this.level == null || this.level.isClientSide) {
			this.cooldown = nbt.getInt("cooldown");

		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);
			nbt.putInt("cooldown", this.cooldown);

			return nbt;
		}
	}

	public static void invalidateCatalysts() {
		CACHED_CATALYSTS = null;
	}

	public List<Item> getCatalysts() {
		if (CACHED_CATALYSTS == null) {
			if (this.level == null) {
				return List.of();
			}

			List<Item> catalysts = new ArrayList<>();
			this.level.getRecipeManager().getAllRecipesFor(BotaniaRecipeTypes.MANA_INFUSION_TYPE).forEach((recipeHolder) -> {
				var recipe = recipeHolder.value();
				if (recipe.getRecipeCatalyst() != null) {
					recipe.getRecipeCatalyst().getDisplayedStacks().stream().map(ItemStack::getItem).forEach((item) -> {
						if (!catalysts.contains(item)) {
							catalysts.add(item);
						}

					});
				}

			});
			CACHED_CATALYSTS = List.copyOf(catalysts);
		}

		return CACHED_CATALYSTS;
	}

	private BlockEntity getBlockEntity() {
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
		if (blockEntity instanceof BlockEntityManaPoolBase) {
			return ModBlocks.baseManaPool.asItem();
		} else if (blockEntity instanceof BlockEntityManaPoolUpgraded) {
			return ModBlocks.upgradedManaPool.asItem();
		} else if (blockEntity instanceof BlockEntityManaPoolAdvanced) {
			return ModBlocks.advancedManaPool.asItem();
		} else {
			return ModBlocks.ultimateManaPool.asItem();
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
