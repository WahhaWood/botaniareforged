package vazkii.botania.ae2.ae2;

import com.google.common.primitives.Ints;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.UnknownNullability;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

public class ManaGenericStackInvStorage implements ManaReceiver, ManaPool, ManaSparkAttachable, SafeMana {
	private final Level level;
	private final BlockPos pos;
	private final GenericInternalInventory inv;

	public ManaGenericStackInvStorage(GenericInternalInventory inv, Level level, BlockPos pos) {
		this.inv = inv;
		this.level = level;
		this.pos = pos;
	}

	@UnknownNullability
	@Override
	public Level getManaReceiverLevel() {
		return this.level;
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return this.pos;
	}

	@Override
	public int getCurrentMana() {
		return this.extract(Integer.MAX_VALUE, Actionable.SIMULATE);
	}

	@Override
	public boolean isFull() {
		return this.insert(1, Actionable.SIMULATE) == 0;
	}

	@Override
	public void receiveMana(int mana) {
		if (mana > 0) {
			this.insert(mana, Actionable.MODULATE);
		} else if (mana < 0) {
			this.extract(-mana, Actionable.MODULATE);
		}
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return !this.isFull();
	}

	@Override
	public boolean isOutputtingPower() {
		return false;
	}

	@Override
	public int getMaxMana() {
		int slots = 0;

		for (int i = 0; i < this.inv.size(); i++) {
			AEKey key = this.inv.getKey(i);
			if (key == null || key == ManaKey.KEY) {
				slots++;
			}
		}

		return Ints.saturatedCast((long) slots * this.inv.getMaxAmount(ManaKey.KEY));
	}

	@Override
	public boolean canAttachSpark(ItemStack stack) {
		return true;
	}

	@Override
	public int getAvailableSpaceForMana() {
		return this.insert(Integer.MAX_VALUE, Actionable.SIMULATE);
	}

	@Override
	public boolean areIncomingTransfersDone() {
		return !this.isFull();
	}

	@Override
	public int insert(int amount, Actionable actionable) {
		long inserted = 0L;

		for (int i = 0; i < this.inv.size() && inserted < (long) amount; i++) {
			inserted += this.inv.insert(i, ManaKey.KEY, (long) amount - inserted, actionable);
		}

		return (int) inserted;
	}

	@Override
	public int extract(int amount, Actionable actionable) {
		long extracted = 0L;

		for (int i = 0; i < this.inv.size() && extracted < (long) amount; i++) {
			extracted += this.inv.extract(i, ManaKey.KEY, (long) amount - extracted, actionable);
		}

		return (int) extracted;
	}
}
