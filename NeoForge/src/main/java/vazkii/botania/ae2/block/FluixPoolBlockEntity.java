package vazkii.botania.ae2.block;

import com.google.common.primitives.Ints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity;

import java.util.EnumSet;

import vazkii.botania.ae2.ABBlocks;
import vazkii.botania.ae2.ae2.ManaKey;
import vazkii.botania.ae2.ae2.SafeMana;
import vazkii.botania.ae2.mixins.ManaPoolBlockEntityAccessor;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.hooks.ticking.TickHandler;
import appeng.me.InWorldGridNode;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;

public class FluixPoolBlockEntity extends ManaPoolBlockEntity implements IInWorldGridNodeHost, IGridConnectedBlockEntity, SafeMana {
	private final ManaPoolBlockEntityAccessor mana = (ManaPoolBlockEntityAccessor) this;
	private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
			.setFlags(GridFlags.REQUIRE_CHANNEL)
			.setVisualRepresentation(ABBlocks.FLUIX_MANA_POOL.get())
			.setInWorldNode(true)
			.setExposedOnSides(EnumSet.complementOf(EnumSet.of(Direction.UP)))
			.setTagName("proxy");
	private final IActionSource actionSource = IActionSource.ofMachine(this.mainNode::getNode);
	private boolean saving;
	private boolean setChangedQueued = false;

	public FluixPoolBlockEntity(BlockPos pos, BlockState state) {
		super(pos, state);
	}

	@Override
	public boolean isFull() {
		IGrid grid = this.getMainNode().getGrid();
		return grid == null || !this.getMainNode().isActive()
				|| grid.getStorageService().getInventory().insert(ManaKey.KEY, 1L, Actionable.SIMULATE, this.actionSource) == 0L;
	}

	@Override
	public void receiveMana(int mana) {
		IGrid grid = this.getMainNode().getGrid();
		if (grid != null && this.getMainNode().isActive()) {
			MEStorage storage = grid.getStorageService().getInventory();
			boolean changed = false;
			if (mana > 0) {
				changed = StorageHelper.poweredInsert(grid.getEnergyService(), storage, ManaKey.KEY, (long) mana, this.actionSource) != 0L;
			} else if (mana < 0) {
				changed = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, ManaKey.KEY, (long) (-mana), this.actionSource) != 0L;
			}

			if (changed) {
				this.setChanged();
				this.markForPotentialSync();
			}
		}
	}

	@Override
	public int getCurrentMana() {
		IGrid grid = this.getMainNode().getGrid();
		if (grid == null || this.saving) {
			return this.mana.getMana();
		} else {
			return !this.getMainNode().isActive()
					? 0
					: (int) grid.getStorageService().getInventory().extract(ManaKey.KEY, 2147483647L, Actionable.SIMULATE, this.actionSource);
		}
	}

	@Override
	public int getMaxMana() {
		IGrid grid = this.getMainNode().getGrid();
		if (grid != null && !this.saving) {
			int oldMana = this.mana.getMana();
			int oldManaCap = super.getMaxMana();
			long manaCap;
			if (this.getMainNode().isActive()) {
				MEStorage storage = grid.getStorageService().getInventory();
				this.mana.setMana((int) storage.extract(ManaKey.KEY, 2147483647L, Actionable.SIMULATE, this.actionSource));
				manaCap = storage.extract(ManaKey.KEY, 2147483647L, Actionable.SIMULATE, this.actionSource)
						+ storage.insert(ManaKey.KEY, 2147483647L, Actionable.SIMULATE, this.actionSource);
			} else {
				this.mana.setMana(0);
				manaCap = 0L;
			}

			if (oldMana != this.mana.getMana() || (long) oldManaCap != manaCap) {
				this.setChanged();
				this.markForPotentialSync();
			}

			return Ints.saturatedCast(manaCap);
		} else {
			return super.getMaxMana();
		}
	}

	@Override
	public void saveAdditional(CompoundTag tag, Provider registries) {
		try {
			this.saving = true;
			super.saveAdditional(tag, registries);
		} finally {
			this.saving = false;
		}

		this.getMainNode().saveToNBT(tag);
	}

	@Override
	public void loadAdditional(CompoundTag tag, Provider registries) {
		super.loadAdditional(tag, registries);
		this.getMainNode().loadFromNBT(tag);
	}

	@Nullable
	@Override
	public IGridNode getGridNode(Direction dir) {
		IGridNode node = this.getMainNode().getNode();
		if (node instanceof InWorldGridNode inWorldNode && inWorldNode.isExposedOnSide(dir)) {
			return node;
		}

		return null;
	}

	@Override
	public AECableType getCableConnectionType(Direction dir) {
		return AECableType.SMART;
	}

	@Override
	public IManagedGridNode getMainNode() {
		return this.mainNode;
	}

	@Override
	public void saveChanges() {
		if (this.level != null) {
			if (this.level.isClientSide) {
				this.setChanged();
			} else {
				this.level.blockEntityChanged(this.worldPosition);
				if (!this.setChangedQueued) {
					TickHandler.instance().addCallable(null, this::setChangedAtEndOfTick);
					this.setChangedQueued = true;
				}
			}
		}
	}

	private void setChangedAtEndOfTick() {
		this.setChanged();
		this.setChangedQueued = false;
	}

	@Override
	public void onChunkUnloaded() {
		this.getMainNode().destroy();
	}

	public void onReady() {
		this.getMainNode().create(this.getLevel(), this.getBlockPos());
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		this.getMainNode().destroy();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		GridHelper.onFirstTick(this, FluixPoolBlockEntity::onReady);
	}

	public int calculateComparatorLevel() {
		IGrid grid = this.getMainNode().getGrid();
		long currentMana;
		long freeMana;
		if (grid == null) {
			currentMana = (long) this.mana.getMana();
			freeMana = (long) super.getMaxMana() - currentMana;
		} else if (!this.getMainNode().isActive()) {
			currentMana = 0L;
			freeMana = 0L;
		} else {
			MEStorage storage = grid.getStorageService().getInventory();
			currentMana = storage.extract(ManaKey.KEY, Long.MAX_VALUE, Actionable.SIMULATE, this.actionSource);
			freeMana = storage.insert(ManaKey.KEY, Long.MAX_VALUE, Actionable.SIMULATE, this.actionSource);
		}

		return currentMana == 0L ? 0 : (int) Math.ceil(1.0 / (1.0 + (double) freeMana / (double) currentMana) * 15.0);
	}

	@Override
	public int insert(int amount, Actionable mode) {
		IGrid grid = this.getMainNode().getGrid();
		if (grid != null && this.getMainNode().isActive()) {
			MEStorage storage = grid.getStorageService().getInventory();
			long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, ManaKey.KEY, (long) amount, this.actionSource, mode);
			if (inserted != 0L && mode == Actionable.MODULATE) {
				this.setChanged();
				this.markForPotentialSync();
			}

			return Math.toIntExact(inserted);
		} else {
			return 0;
		}
	}

	@Override
	public int extract(int amount, Actionable mode) {
		IGrid grid = this.getMainNode().getGrid();
		if (grid != null && this.getMainNode().isActive()) {
			MEStorage storage = grid.getStorageService().getInventory();
			long extracted = StorageHelper.poweredExtraction(grid.getEnergyService(), storage, ManaKey.KEY, (long) amount, this.actionSource, mode);
			if (extracted != 0L && mode == Actionable.MODULATE) {
				this.setChanged();
				this.markForPotentialSync();
			}

			return Math.toIntExact(extracted);
		} else {
			return 0;
		}
	}
}
