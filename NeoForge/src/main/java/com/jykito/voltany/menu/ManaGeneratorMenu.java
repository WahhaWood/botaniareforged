package com.jykito.voltany.menu;

import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import com.jykito.voltany.registry.ModBlocks;
import com.jykito.voltany.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class ManaGeneratorMenu extends AbstractContainerMenu {
	public static final int CONTAINER_SLOTS = 5;
	private static final int[][] UPGRADES = new int[][] { { 94, 43 }, { 146, 43 }, { 94, 85 }, { 146, 85 } };
	private static final int INV_X = 44;
	private static final int INV_Y = 133;
	private static final int HOTBAR_Y = 196;
	private static final int SLOT_STEP = 19;

	private final ManaGeneratorBlockEntity be;
	private final ContainerLevelAccess access;
	private final ContainerData data;

	public ManaGeneratorMenu(int id, Inventory inv, BlockPos pos) {
		this(id, inv, resolve(inv, pos));
	}

	public ManaGeneratorMenu(int id, Inventory inv, ManaGeneratorBlockEntity be) {
		super(ModMenus.MANA_GENERATOR.get(), id);
		this.be = be;
		this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
		this.data = be.getContainerData();
		this.addSlot(new SlotItemHandler(be.getFlowerHandler(), 0, 120, 64));
		for (int i = 0; i < UPGRADES.length; i++) {
			this.addSlot(new SlotItemHandler(be.getUpgradeHandler(), i, UPGRADES[i][0], UPGRADES[i][1]));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * SLOT_STEP, INV_Y + row * SLOT_STEP));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inv, col, INV_X + col * SLOT_STEP, HOTBAR_Y));
		}
		this.addDataSlots(this.data);
	}

	private static ManaGeneratorBlockEntity resolve(Inventory inv, BlockPos pos) {
		return (ManaGeneratorBlockEntity) inv.player.level().getBlockEntity(pos);
	}

	public int getEnergy() {
		return this.data.get(1) << 16 | this.data.get(0) & 0xFFFF;
	}

	public int getEnergyCapacity() {
		// Carried in the data slots alongside energy: overclock upgrades raise
		// the capacity, and a hardcoded base made the fill bar read as full when
		// it was only part full (or the reverse).
		return this.data.get(5) << 16 | this.data.get(4) & 0xFFFF;
	}

	public int getMana() {
		return this.data.get(3) << 16 | this.data.get(2) & 0xFFFF;
	}

	public int getManaCapacity() {
		return 1000000;
	}

	@Override
	@Nullable
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			result = stack.copy();
			if (index < CONTAINER_SLOTS) {
				if (!this.moveItemStackTo(stack, CONTAINER_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, CONTAINER_SLOTS, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, (Block) ModBlocks.MANA_GENERATOR.get());
	}
}
