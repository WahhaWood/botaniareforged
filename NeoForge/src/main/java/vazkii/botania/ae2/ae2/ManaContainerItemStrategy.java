package vazkii.botania.ae2.ae2;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.common.handler.BotaniaSounds;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

public class ManaContainerItemStrategy implements ContainerItemStrategy<ManaKey, ManaItem> {
	@Nullable
	@Override
	public GenericStack getContainedStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		} else {
			ManaItem item = (ManaItem) ManaItem.LOOKUP.find(stack);
			return item != null ? new GenericStack(ManaKey.KEY, (long) item.getMana()) : null;
		}
	}

	@Nullable
	@Override
	public ManaItem findCarriedContext(Player player, AbstractContainerMenu menu) {
		return (ManaItem) ManaItem.LOOKUP.find(menu.getCarried());
	}

	@Nullable
	@Override
	public ManaItem findPlayerSlotContext(Player player, int slot) {
		return (ManaItem) ManaItem.LOOKUP.find(player.getInventory().getItem(slot));
	}

	@Override
	public long extract(ManaItem item, ManaKey what, long amount, Actionable mode) {
		int extracted = (int) Math.min(amount, (long) item.getMana());
		if (extracted > 0 && mode == Actionable.MODULATE) {
			item.addMana(-extracted);
		}

		return (long) extracted;
	}

	@Override
	public long insert(ManaItem item, ManaKey what, long amount, Actionable mode) {
		int inserted = (int) Math.min(amount, (long) (item.getMaxMana() - item.getMana()));
		if (inserted > 0 && mode == Actionable.MODULATE) {
			item.addMana(inserted);
		}

		return (long) inserted;
	}

	@Override
	public void playFillSound(Player player, ManaKey what) {
		player.playNotifySound(BotaniaSounds.MANA_POOL_CRAFT, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	@Override
	public void playEmptySound(Player player, ManaKey what) {
		player.playNotifySound(BotaniaSounds.BLACK_LOTUS, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	@Nullable
	@Override
	public GenericStack getExtractableContent(ManaItem item) {
		return new GenericStack(ManaKey.KEY, (long) item.getMana());
	}
}
