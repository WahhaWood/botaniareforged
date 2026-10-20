package net.lmor.botanicalextramachinery.ae2;

import com.google.common.primitives.Ints;

import net.lmor.botanicalextramachinery.util.MeOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageHelper;
import appeng.api.util.AECableType;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import vazkii.botania.ae2.ae2.ManaKey;

/**
 * The AE2 side of {@link MeOutput}: an in-world grid node (smart cable connection, one channel) that stores a
 * machine's results and excess mana in the network. Loaded only when AE2 is installed.
 */
public final class MeOutputNode implements MeOutput, IGridConnectedBlockEntity {
	private final BlockEntity blockEntity;
	private final IManagedGridNode mainNode;
	private boolean setChangedQueued;

	public MeOutputNode(BlockEntity blockEntity, Item icon) {
		this.blockEntity = blockEntity;
		this.mainNode = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
				.setVisualRepresentation(icon)
				.setInWorldNode(true)
				.setTagName("proxy");
	}

	/** AE2 finds the grid node of a {@link MeOutput.Holder} block entity through this capability. */
	public static void registerCapability(RegisterCapabilitiesEvent event, BlockEntityType<?> type) {
		event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type,
				(be, side) -> be instanceof MeOutput.Holder holder && holder.meOutput() instanceof MeOutputNode node ? node : null);
	}

	@Override
	public void tick(Level level, BlockPos pos) {
		if (!mainNode.isReady()) {
			mainNode.create(level, pos);
		}
	}

	@Override
	public boolean isOnline() {
		return mainNode.getNode() != null && mainNode.isOnline();
	}

	@Override
	public int insert(ItemStack stack) {
		AEItemKey key = AEItemKey.of(stack);
		if (key == null || mainNode.getGrid() == null) {
			return 0;
		}
		return Ints.saturatedCast(mainNode.getGrid().getStorageService().getInventory()
				.insert(key, stack.getCount(), Actionable.MODULATE, IActionSource.empty()));
	}

	@Override
	public int insertMana(int mana) {
		var grid = mainNode.getGrid();
		if (grid == null || mana <= 0) {
			return 0;
		}
		// ManaKeyType quantises by bytes, so a small amount often moves nothing: callers retry later
		long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), grid.getStorageService().getInventory(),
				ManaKey.KEY, mana, IActionSource.ofMachine(mainNode::getNode), Actionable.MODULATE);
		return Ints.saturatedCast(Math.max(0, inserted));
	}

	@Override
	public void load(CompoundTag tag) {
		mainNode.loadFromNBT(tag);
	}

	@Override
	public void save(CompoundTag tag) {
		mainNode.saveToNBT(tag);
	}

	@Override
	public void destroy() {
		mainNode.destroy();
	}

	// ---- IGridConnectedBlockEntity ----

	@Override
	public IManagedGridNode getMainNode() {
		return mainNode;
	}

	@Nullable
	@Override
	public IGridNode getGridNode(Direction direction) {
		return mainNode.getNode();
	}

	@Override
	public AECableType getCableConnectionType(Direction dir) {
		return AECableType.SMART;
	}

	/** Grid changes mark the machine dirty once, at the end of the tick. */
	@Override
	public void saveChanges() {
		Level level = blockEntity.getLevel();
		if (level == null) {
			return;
		}
		if (level.isClientSide) {
			blockEntity.setChanged();
		} else {
			level.blockEntityChanged(blockEntity.getBlockPos());
			if (!setChangedQueued) {
				TickHandler.instance().addCallable(null, () -> {
					blockEntity.setChanged();
					setChangedQueued = false;
				});
				setChangedQueued = true;
			}
		}
	}
}
