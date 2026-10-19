package vazkii.botania.gaiasim;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Gaia Simulator GUI: the take-only loot output, the player inventory and the status numbers. */
public class GaiaSimulatorMenu extends AbstractContainerMenu {
	public static final int DATA_COUNT = 6;
	public static final int OUTPUT_X = 8;
	public static final int OUTPUT_Y = 60;
	public static final int INVENTORY_Y = 114;

	private final GaiaSimulatorBlockEntity entity;
	private final ContainerLevelAccess access;
	private final ContainerData data;

	public GaiaSimulatorMenu(int containerId, Inventory inventory, GaiaSimulatorBlockEntity entity, ContainerData data) {
		super(GaiaSimulator.MENU.get(), containerId);
		this.entity = entity;
		this.access = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());
		this.data = data;
		for (int slot = 0; slot < GaiaSimulatorBlockEntity.OUTPUT_SLOTS; slot++) {
			addSlot(new SlotItemHandler(entity.getOutput(), slot, OUTPUT_X + slot % 9 * 18, OUTPUT_Y + slot / 9 * 18));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
		}
		addDataSlots(data);
	}

	/** Client side: the server wrote the block position when it opened the menu. */
	public GaiaSimulatorMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
		this(containerId, inventory, (GaiaSimulatorBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()),
				new SimpleContainerData(DATA_COUNT));
	}

	public int getMana() {
		return data.get(1) << 16 | data.get(0) & 0xFFFF;
	}

	public int getEnergy() {
		return data.get(3) << 16 | data.get(2) & 0xFFFF;
	}

	public int getProgress() {
		return data.get(4);
	}

	public GaiaSimulatorBlockEntity.Status getStatus() {
		GaiaSimulatorBlockEntity.Status[] values = GaiaSimulatorBlockEntity.Status.values();
		return values[Math.clamp(data.get(5), 0, values.length - 1)];
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack result = stack.copy();
		// only the output moves out; nothing can be put into it
		if (index >= GaiaSimulatorBlockEntity.OUTPUT_SLOTS
				|| !moveItemStackTo(stack, GaiaSimulatorBlockEntity.OUTPUT_SLOTS, slots.size(), true)) {
			return ItemStack.EMPTY;
		}
		// set() rather than setChanged(): the item handler only marks the block entity dirty on a set
		slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, GaiaSimulator.BLOCK.get());
	}
}
