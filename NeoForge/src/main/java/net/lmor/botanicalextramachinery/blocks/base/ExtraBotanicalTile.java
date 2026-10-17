package net.lmor.botanicalextramachinery.blocks.base;

import com.google.common.base.Predicates;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.moddingx.libx.base.tile.BlockEntityBase;
import org.moddingx.libx.base.tile.TickingBlock;
import org.moddingx.libx.capability.ItemCapabilities;
import org.moddingx.libx.inventory.BaseItemStackHandler;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;

import vazkii.botania.api.block.WandHUD;
import vazkii.botania.api.item.SparkEntity;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.client.core.helper.RenderHelper;
import vazkii.botania.client.gui.HUDHandler;
import vazkii.botania.common.block.block_entity.mana.ThrottledPacket;

import javax.annotation.Nonnull;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class ExtraBotanicalTile extends BlockEntityBase implements ManaPool, ManaSparkAttachable, ThrottledPacket<ExtraBotanicalTile>, WandHUD, TickingBlock {
	private int mana;
	private int manaCap;

	private final IAdvancedItemHandlerModifiable capability = this.createCap(this::getInventory);

	public ExtraBotanicalTile(BlockEntityType<?> blockEntityTypeIn, BlockPos pos, BlockState state, int cap) {
		super(blockEntityTypeIn, pos, state);
		manaCap = cap;
	}

	protected IAdvancedItemHandlerModifiable createCap(Supplier<IItemHandlerModifiable> inventory) {
		return ItemCapabilities.create(inventory, this.getExtracts(inventory), this.getInserts(inventory));
	}

	/**
	 * Handler exposed through the NeoForge capability registration
	 * (see BemRegistration), restricted by the extract/insert predicates.
	 */
	public IAdvancedItemHandlerModifiable getItemHandlerCap() {
		return this.isRemoved() ? null : this.capability;
	}

	protected abstract Predicate<Integer> getExtracts(Supplier<IItemHandlerModifiable> var1);

	protected BiPredicate<Integer, ItemStack> getInserts(Supplier<IItemHandlerModifiable> inventory) {
		return null;
	}

	@Nonnull
	public abstract BaseItemStackHandler getInventory();

	public abstract int getComparatorOutput();

	@Override
	public void setChanged() {
		super.setChanged();
		if (this.level != null) {
			this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
		}

	}

	@Override
	public void loadAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.getInventory().deserializeNBT(registries, tag.getCompound("inv"));
		this.mana = tag.getInt("mana");
		manaCap = tag.getInt("manaCap");
	}

	@Override
	public void saveAdditional(@Nonnull CompoundTag nbt, HolderLookup.Provider registries) {
		super.saveAdditional(nbt, registries);
		nbt.put("inv", this.getInventory().serializeNBT(registries));
		nbt.putInt("mana", this.getCurrentMana());
		nbt.putInt("manaCap", this.getMaxMana());
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		if (this.level == null || this.level.isClientSide) {
			this.getInventory().deserializeNBT(registries, tag.getCompound("inv"));
			this.mana = tag.getInt("mana");
			manaCap = tag.getInt("manaCap");
		}
	}

	@Nonnull
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		if (this.level != null && this.level.isClientSide) {
			return super.getUpdateTag(registries);
		} else {
			CompoundTag nbt = super.getUpdateTag(registries);
			nbt.put("inv", this.getInventory().serializeNBT(registries));
			nbt.putInt("mana", this.getCurrentMana());
			nbt.putInt("manaCap", this.getMaxMana());
			return nbt;
		}
	}

	@Override
	public void renderHUD(GuiGraphics guiGraphics, Window window, Font font, float partialTick) {
		ItemStack block = new ItemStack(this.getBlockState().getBlock());
		String name = block.getHoverName().getString();
		int centerX = window.getGuiScaledWidth() / 2;
		int centerY = window.getGuiScaledHeight() / 2;
		int width = Math.max(102, font.width(name)) + 4;
		RenderHelper.renderHUDBox(guiGraphics, centerX - width / 2, centerY + 8, centerX + width / 2, centerY + 28);
		HUDHandler.drawSimpleManaHUD(guiGraphics, window, font, 4474111, this.getCurrentMana(), this.getMaxMana(), name);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(770, 771);
		RenderSystem.disableBlend();
	}

	@Override
	public boolean canAttachSpark(ItemStack itemStack) {
		return this.actAsMana();
	}

	@Override
	public int getAvailableSpaceForMana() {
		return !this.actAsMana() ? 0 : Math.max(Math.max(0, this.getMaxMana() - this.getCurrentMana()), 0);
	}

	public ManaSpark getAttachedSpark() {
		if (!this.actAsMana()) {
			return null;
		} else {
			assert this.level != null;
			BlockPos sparkMin = this.worldPosition.above();
			BlockPos sparkMax = sparkMin.offset(1, 1, 1);
			AABB sparkBox = new AABB(sparkMin.getX(), sparkMin.getY(), sparkMin.getZ(), sparkMax.getX(), sparkMax.getY(), sparkMax.getZ());
			List<Entity> sparks = this.level.getEntitiesOfClass(Entity.class, sparkBox, Predicates.instanceOf(SparkEntity.class));
			if (sparks.size() == 1) {
				Entity entity = sparks.get(0);
				return (ManaSpark) entity;
			}
		}

		return null;
	}

	@Override
	public boolean areIncomingTransfersDone() {
		return !this.actAsMana();
	}

	@Override
	public boolean isFull() {
		return this.getCurrentMana() >= this.getMaxMana();
	}

	@Override
	public void receiveMana(int i) {
		int old = this.getCurrentMana();
		this.mana = Math.max(0, Math.min(this.getCurrentMana() + i, this.getMaxMana()));
		if (old != this.getCurrentMana()) {
			this.setChanged();
			this.setDispatchable();
		}
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return true;
	}

	@Override
	public int getCurrentMana() {
		return this.mana;
	}

	public void setCurrentMana(int val) {
		this.mana = val;
	}

	@Override
	public int getMaxMana() {
		return manaCap;
	}

	public void setMaxMana(int val) {
		manaCap = val;
	}

	@Override
	public boolean isOutputtingPower() {
		return false;
	}

	public Optional<DyeColor> getColor() {
		return Optional.empty();
	}

	public void setColor(Optional<DyeColor> color) {}

	@Override
	public Level getManaReceiverLevel() {
		return this.getLevel();
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return this.getBlockPos();
	}

	public boolean actAsMana() {
		return true;
	}

	// ---- ThrottledPacket (1.21.1) adapter ----
	private boolean markedForSync;

	@Override
	public ExtraBotanicalTile getSelf() {
		return this;
	}

	@Override
	public void setMarkedForSync(boolean markedForSync) {
		this.markedForSync = markedForSync;
	}

	@Override
	public int getSyncInterval() {
		return 10;
	}

	@Override
	public boolean isMarkedForSync() {
		return this.markedForSync;
	}

	/** 1.20.1 API name kept: schedules a throttled client sync. */
	@Override
	public void setDispatchable() {
		// this overrides BlockEntityBase.setDispatchable(), so it has to repeat
		// the base class' null guard: loadAdditional() calls this while the BE
		// is still detached, and getLevel() is null until the BE is placed.
		if (this.level != null && !this.level.isClientSide) {
			org.moddingx.libx.impl.BlockEntityUpdateQueue.scheduleUpdate(this.level, this.worldPosition);
		}
		this.markedForSync = true;
	}

	public void markDispatchable() {
		this.setDispatchable();
	}
}
