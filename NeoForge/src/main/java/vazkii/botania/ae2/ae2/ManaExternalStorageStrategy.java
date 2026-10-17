package vazkii.botania.ae2.ae2;

import com.google.common.primitives.Ints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.localization.GuiText;

public class ManaExternalStorageStrategy implements ExternalStorageStrategy {
	private final BlockCapabilityCache<ManaReceiver, Direction> apiCache;

	public ManaExternalStorageStrategy(ServerLevel level, BlockPos fromPos, Direction fromSide) {
		this.apiCache = BlockCapabilityCache.create(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), level, fromPos, fromSide);
	}

	@Nullable
	@Override
	public MEStorage createWrapper(boolean extractableOnly, Runnable injectOrExtractCallback) {
		ManaReceiver receiver = this.apiCache.getCapability();
		return receiver == null ? null : new ManaExternalStorageStrategy.ManaStorageAdapter(receiver, injectOrExtractCallback);
	}

	private record ManaStorageAdapter(ManaReceiver receiver, Runnable changeListener) implements MEStorage {
		@Override
		public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
			if (!(what instanceof ManaKey)) {
				return 0L;
			} else {
				int inserted = SafeMana.conv(this.receiver).insert(Ints.saturatedCast(amount), mode);
				if (inserted > 0 && mode == Actionable.MODULATE) {
					this.changeListener.run();
				}

				return (long) inserted;
			}
		}

		@Override
		public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
			if (!(what instanceof ManaKey)) {
				return 0L;
			} else {
				int extracted = SafeMana.conv(this.receiver).extract(Ints.saturatedCast(amount), mode);
				if (extracted > 0 && mode == Actionable.MODULATE) {
					this.changeListener.run();
				}

				return (long) extracted;
			}
		}

		@Override
		public void getAvailableStacks(KeyCounter out) {
			int currentMana = this.receiver.getCurrentMana();
			if (currentMana != 0) {
				out.add(ManaKey.KEY, (long) currentMana);
			}
		}

		@Override
		public Component getDescription() {
			return GuiText.ExternalStorage.text(new Object[] { ManaKeyType.TYPE.getDescription() });
		}
	}
}
