package vazkii.botania.ae2.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import vazkii.botania.ae2.ABMachines;

/** ME machine GUI: no slots, only the recipe switches (sent as {@link ToggleRecipePayload}) and status. */
public class MachineMenu extends AbstractContainerMenu {
	public final MachineBlockEntity entity;
	private final DataSlot operations = DataSlot.standalone();
	private final DataSlot queued = DataSlot.standalone();
	private final DataSlot online = DataSlot.standalone();

	public MachineMenu(int containerId, Inventory inventory, MachineBlockEntity entity) {
		super(ABMachines.MACHINE_MENU.get(), containerId);
		this.entity = entity;
		addDataSlot(operations);
		addDataSlot(queued);
		addDataSlot(online);
	}

	/** Client side: the server wrote the block position when it opened the menu. */
	public MachineMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
		this(containerId, inventory, (MachineBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
	}

	@Override
	public void broadcastChanges() {
		operations.set(entity.lastOperations);
		queued.set((int) Math.min(Integer.MAX_VALUE, entity.queuedOperations()));
		online.set(entity.getMainNode().isActive() ? 1 : 0);
		super.broadcastChanges();
	}

	public int lastOperations() {
		return operations.get();
	}

	public int queuedOperations() {
		return queued.get();
	}

	public boolean isOnline() {
		return online.get() != 0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return !entity.isRemoved() && player.distanceToSqr(entity.getBlockPos().getX() + 0.5, entity.getBlockPos().getY() + 0.5,
				entity.getBlockPos().getZ() + 0.5) <= 64.0;
	}
}
