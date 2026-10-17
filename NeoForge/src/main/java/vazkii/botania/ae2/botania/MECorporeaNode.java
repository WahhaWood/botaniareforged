package vazkii.botania.ae2.botania;

import com.google.common.primitives.Ints;

import it.unimi.dsi.fastutil.objects.Object2LongMap.Entry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.corporea.CorporeaNode;
import vazkii.botania.api.corporea.CorporeaRequest;
import vazkii.botania.api.corporea.CorporeaSpark;
import vazkii.botania.common.impl.corporea.AbstractCorporeaNode;

import java.util.ArrayList;
import java.util.List;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

public class MECorporeaNode extends AbstractCorporeaNode {
	private final MEStorage storage;

	public MECorporeaNode(Level level, BlockPos pos, CorporeaSpark spark, MEStorage storage) {
		super(level, pos, spark);
		this.storage = storage;
	}

	@Nullable
	public static CorporeaNode getNode(Level level, CorporeaSpark spark) {
		MEStorage accessor = level.getCapability(AECapabilities.ME_STORAGE, spark.getAttachPos(), Direction.UP);
		return accessor != null ? new MECorporeaNode(level, spark.getAttachPos(), spark, accessor) : null;
	}

	@Override
	public List<ItemStack> countItems(CorporeaRequest request) {
		return this.work(request, false);
	}

	@Override
	public List<ItemStack> extractItems(CorporeaRequest request) {
		return this.work(request, true);
	}

	protected List<ItemStack> work(CorporeaRequest request, boolean execute) {
		ArrayList<ItemStack> list = new ArrayList<>();
		IActionSource source;
		if (request.getEntity() instanceof Player player) {
			source = IActionSource.ofPlayer(player);
		} else {
			source = IActionSource.empty();
		}

		if (this.storage == null) {
			return list;
		} else {
			for (Entry<AEKey> entry : this.storage.getAvailableStacks()) {
				int amount = Ints.saturatedCast(entry.getLongValue());
				if (entry.getKey() instanceof AEItemKey itemKey) {
					ItemStack stack = itemKey.toStack();
					if (request.getMatcher().test(stack)) {
						request.trackFound(amount);
						int remainder = Math.min(amount, request.getStillNeeded() == -1 ? amount : request.getStillNeeded());
						if (remainder > 0) {
							request.trackSatisfied(remainder);
							if (execute) {
								if (!this.getSpark().isCreative()) {
									remainder = (int) this.storage.extract((AEKey) entry.getKey(), (long) remainder, Actionable.MODULATE, source);
								}

								this.getSpark().onItemExtracted(stack);
								request.trackExtracted(remainder);
							}

							while (remainder > 0) {
								int taken = Math.min(remainder, stack.getMaxStackSize());
								remainder -= taken;
								list.add(itemKey.toStack(taken));
							}
						}
					}
				}
			}

			return list;
		}
	}
}
