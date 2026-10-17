package vazkii.botania.ae2.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import vazkii.botania.ae2.AppliedBotanics;
import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.CapabilityP2PTunnelPart;
import appeng.parts.p2p.P2PModels;

public class ManaP2PTunnelPart extends CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver> {
	private static final P2PModels MODELS = new P2PModels(AppliedBotanics.id("part/mana_p2p_tunnel"));
	private final ManaSparkAttachable sparkAttachable = new ManaP2PTunnelPart.P2PSparkAttachable();

	public ManaP2PTunnelPart(IPartItem<?> partItem) {
		super(partItem, BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP));
		this.inputHandler = new ManaP2PTunnelPart.InputHandler();
		this.emptyHandler = new ManaP2PTunnelPart.EmptyHandler();
	}

	@PartModels
	public static List<IPartModel> getModels() {
		return MODELS.getModels();
	}

	@Override
	public IPartModel getStaticModels() {
		return MODELS.getModel(this.isPowered(), this.isActive());
	}

	@Nullable
	public ManaSparkAttachable getSparkAttachable() {
		return this.isOutput() ? null : this.sparkAttachable;
	}

	private class EmptyHandler implements ManaReceiver {
		@Override
		public Level getManaReceiverLevel() {
			return ManaP2PTunnelPart.this.getLevel();
		}

		@Override
		public BlockPos getManaReceiverPos() {
			return ManaP2PTunnelPart.this.getHost().getLocation().getPos();
		}

		@Override
		public int getCurrentMana() {
			return 0;
		}

		@Override
		public boolean isFull() {
			return true;
		}

		@Override
		public void receiveMana(int mana) {}

		@Override
		public boolean canReceiveManaFromBursts() {
			return false;
		}
	}

	private class InputHandler implements ManaReceiver, ManaPool, SafeMana {
		@Override
		public Level getManaReceiverLevel() {
			return ManaP2PTunnelPart.this.getLevel();
		}

		@Override
		public BlockPos getManaReceiverPos() {
			return ManaP2PTunnelPart.this.getHost().getLocation().getPos();
		}

		@Override
		public int getCurrentMana() {
			return 0;
		}

		@Override
		public boolean isFull() {
			for (ManaP2PTunnelPart output : ManaP2PTunnelPart.this.getOutputs()) {
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
					if (!((ManaReceiver) guard.get()).isFull()) {
						return false;
					}
				}
			}

			return true;
		}

		@Override
		public void receiveMana(int mana) {
			if (mana > 0) {
				List<ManaP2PTunnelPart> outputs = ManaP2PTunnelPart.this.getOutputStream().filter(part -> {
					boolean var3x;
					try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guardx = part.getAdjacentCapability()) {
						ManaReceiver receiver = (ManaReceiver) guardx.get();
						var3x = receiver.canReceiveManaFromBursts() && !receiver.isFull();
					}

					return var3x;
				}).collect(Collectors.toList());
				if (!outputs.isEmpty()) {
					Collections.shuffle(outputs);
					ManaP2PTunnelPart.this.deductTransportCost((long) (mana / 100), ManaKeyType.TYPE);
					int manaForEach = mana / outputs.size();
					int spill = mana % outputs.size();

					for (ManaP2PTunnelPart output : outputs) {
						try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
							((ManaReceiver) guard.get()).receiveMana(manaForEach + (spill-- > 0 ? 1 : 0));
						}
					}
				}
			}
		}

		@Override
		public boolean canReceiveManaFromBursts() {
			for (ManaP2PTunnelPart output : ManaP2PTunnelPart.this.getOutputs()) {
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
					ManaReceiver result = (ManaReceiver) guard.get();
					if (result.canReceiveManaFromBursts()) {
						return true;
					}
				}
			}

			return false;
		}

		@Override
		public boolean isOutputtingPower() {
			return false;
		}

		@Override
		public int getMaxMana() {
			return ManaP2PTunnelPart.this.getOutputStream().map(part -> {
				Integer var2;
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = part.getAdjacentCapability()) {
					var2 = ManaHelper.getCapacity((ManaReceiver) guard.get());
				}

				return var2;
			}).reduce(0, Integer::sum);
		}

		@Override
		public int insert(int amount, Actionable mode) {
			int inserted = 0;

			for (ManaP2PTunnelPart output : ManaP2PTunnelPart.this.getOutputs()) {
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
					inserted += SafeMana.conv((ManaReceiver) guard.get()).insert(amount - inserted, mode);
				}
			}

			return inserted;
		}

		@Override
		public int extract(int amount, Actionable mode) {
			return 0;
		}
	}

	private class P2PSparkAttachable implements ManaSparkAttachable {
		@Override
		public boolean canAttachSpark(ItemStack stack) {
			return true;
		}

		@Override
		public int getAvailableSpaceForMana() {
			int space = 0;

			for (ManaP2PTunnelPart output : ManaP2PTunnelPart.this.getOutputs()) {
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
					ManaReceiver receiver = (ManaReceiver) guard.get();
					space += ManaHelper.getCapacity(receiver);
				}
			}

			return space;
		}

		@Override
		public boolean areIncomingTransfersDone() {
			for (ManaP2PTunnelPart output : ManaP2PTunnelPart.this.getOutputs()) {
				try (CapabilityP2PTunnelPart<ManaP2PTunnelPart, ManaReceiver>.CapabilityGuard guard = output.getAdjacentCapability()) {
					ManaReceiver receiver = (ManaReceiver) guard.get();
					if (receiver.canReceiveManaFromBursts() && !receiver.isFull()) {
						return true;
					}
				}
			}

			return false;
		}
	}
}
