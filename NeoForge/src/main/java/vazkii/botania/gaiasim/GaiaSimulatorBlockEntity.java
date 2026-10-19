package vazkii.botania.gaiasim;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.item.Relic;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.api.mana.spark.ManaSparkHelper;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.entity.BotaniaEntities;
import vazkii.botania.common.entity.GaiaGuardianEntity;
import vazkii.botania.common.item.BotaniaItems;
import vazkii.botania.common.loot.BotaniaLootTables;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gaia Simulator controller. A fight starts once the multiblock is formed, the mana buffer holds
 * {@link #MANA_PER_FIGHT} and the previous loot has left; it then runs {@link #FIGHT_TICKS} ticks, each paid with
 * {@link #ENERGY_PER_TICK} FE (no energy pauses it). At the end the Guardian's own table (head) and the hard-mode
 * reward table are rolled as if a single player had won, the way {@link GaiaGuardianEntity} rolls them.
 */
public class GaiaSimulatorBlockEntity extends BlockEntity implements MenuProvider, ManaReceiver, ManaSparkAttachable {
	public static final int MANA_PER_FIGHT = 1_000_000;
	public static final int ENERGY_PER_TICK = 10_000;
	public static final int ENERGY_CAPACITY = 1_000_000;
	public static final int FIGHT_TICKS = 5 * 60 * 20;
	public static final int OUTPUT_SLOTS = 18;
	/** Offsets of the pylons from the controller; the 3x3 base is one layer below. */
	public static final List<BlockPos> PYLONS = List.of(
			new BlockPos(-1, 0, -1), new BlockPos(1, 0, -1), new BlockPos(-1, 0, 1), new BlockPos(1, 0, 1));
	private static final int STRUCTURE_CHECK_INTERVAL = 20;
	private static final GameProfile KILLER_PROFILE = new GameProfile(
			UUID.fromString("5c8f2e74-0b7b-4c55-9a3e-6a1f3d2b9e41"), "[Gaia Simulator]");

	public enum Status {
		NO_STRUCTURE, NO_MANA, NO_ENERGY, OUTPUT_FULL, FIGHTING
	}

	private final Energy energy = new Energy();
	private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			// take-only for players and pipes; loot goes in through store()
			return false;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};
	/** Loot that did not fit into {@link #output}; no new fight starts until it is gone. */
	private final List<ItemStack> pending = new ArrayList<>();
	private int mana;
	private boolean fighting;
	private int progress;
	private boolean formed;
	private Status status = Status.NO_STRUCTURE;
	@Nullable
	private UUID owner;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			// data slots travel as shorts, so the large values go in two halves
			return switch (index) {
				case 0 -> mana & 0xFFFF;
				case 1 -> mana >>> 16;
				case 2 -> energy.getEnergyStored() & 0xFFFF;
				case 3 -> energy.getEnergyStored() >>> 16;
				case 4 -> progress;
				case 5 -> status.ordinal();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {}

		@Override
		public int getCount() {
			return GaiaSimulatorMenu.DATA_COUNT;
		}
	};

	public GaiaSimulatorBlockEntity(BlockPos pos, BlockState state) {
		super(GaiaSimulator.BLOCK_ENTITY.get(), pos, state);
	}

	public static boolean isFormed(Level level, BlockPos pos) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean corner = x != 0 && z != 0;
				Block base = x == 0 && z == 0 ? BotaniaBlocks.DRAGONSTONE_BLOCK
						: corner ? BotaniaBlocks.TERRASTEEL_BLOCK : BotaniaBlocks.ELEMENTIUM_BLOCK;
				if (!level.getBlockState(pos.offset(x, -1, z)).is(base)
						|| corner && !level.getBlockState(pos.offset(x, 0, z)).is(BotaniaBlocks.GAIA_PYLON)) {
					return false;
				}
			}
		}
		return true;
	}

	public void tickServer() {
		if (level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
			formed = isFormed(level, worldPosition);
		}
		if (!isFull()) {
			ManaSparkHelper.registerTransferFromSparksAround(ManaSparkHelper.getAttachedSpark(level, worldPosition), level, worldPosition);
		}
		if (!pending.isEmpty()) {
			flushPending();
		}
		status = step();
		boolean lit = status == Status.FIGHTING;
		if (getBlockState().getValue(GaiaSimulatorBlock.LIT) != lit) {
			level.setBlock(worldPosition, getBlockState().setValue(GaiaSimulatorBlock.LIT, lit), Block.UPDATE_ALL);
		}
	}

	private Status step() {
		if (!formed) {
			return Status.NO_STRUCTURE;
		}
		if (!fighting) {
			if (!pending.isEmpty()) {
				return Status.OUTPUT_FULL;
			}
			if (mana < MANA_PER_FIGHT) {
				return Status.NO_MANA;
			}
			mana -= MANA_PER_FIGHT;
			fighting = true;
			progress = 0;
		}
		if (!energy.consume(ENERGY_PER_TICK)) {
			return Status.NO_ENERGY;
		}
		if (++progress >= FIGHT_TICKS) {
			fighting = false;
			progress = 0;
			pending.addAll(rollLoot((ServerLevel) level));
			flushPending();
		}
		setChanged();
		return Status.FIGHTING;
	}

	private List<ItemStack> rollLoot(ServerLevel level) {
		Vec3 center = Vec3.atCenterOf(worldPosition.above());
		GaiaGuardianEntity gaia = BotaniaEntities.GAIA_GUARDIAN.create(level);
		FakePlayer killer = FakePlayerFactory.get(level, KILLER_PROFILE);
		gaia.setPos(center);
		killer.setPos(center);
		// the head pool wants an Elementium Axe in the killer's hand; a lone winner is also the "true killer"
		killer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BotaniaItems.ELEMENTIUM_AXE));
		gaia.trueKiller = killer;

		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, gaia)
				.withParameter(LootContextParams.ORIGIN, center)
				.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
				.withParameter(LootContextParams.ATTACKING_ENTITY, killer)
				.withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
				.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
				.create(LootContextParamSets.ENTITY);
		List<ItemStack> loot = new ArrayList<>();
		ReloadableServerRegistries.Holder tables = level.getServer().reloadableRegistries();
		tables.getLootTable(BotaniaEntities.GAIA_GUARDIAN.getDefaultLootTable()).getRandomItems(params, loot::add);
		tables.getLootTable(BotaniaLootTables.GAIA_GUARDIAN_REWARD_HARD).getRandomItems(params, loot::add);
		killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		// the reward binds relics to the killer; hand them to the owner instead, or leave them for the first holder
		for (ItemStack stack : loot) {
			Relic relic = Relic.LOOKUP.find(stack);
			if (relic != null) {
				if (owner != null) {
					relic.bindToUUID(owner);
				} else {
					stack.remove(BotaniaDataComponents.SOULBOUND);
				}
			}
		}
		return loot;
	}

	private void flushPending() {
		pending.removeIf(stack -> store(stack).isEmpty());
		setChanged();
	}

	/** Puts loot into the output, merging into matching stacks first; returns what did not fit. */
	private ItemStack store(ItemStack stack) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int slot = 0; slot < OUTPUT_SLOTS && !stack.isEmpty(); slot++) {
				ItemStack existing = output.getStackInSlot(slot);
				if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
					int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
					if (moved > 0) {
						output.setStackInSlot(slot, existing.copyWithCount(existing.getCount() + moved));
						stack.shrink(moved);
					}
				} else if (pass == 1 && existing.isEmpty()) {
					output.setStackInSlot(slot, stack.split(stack.getMaxStackSize()));
				}
			}
		}
		return stack;
	}

	public void dropContents() {
		for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.getStackInSlot(slot));
		}
		for (ItemStack stack : pending) {
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
		}
		pending.clear();
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		setChanged();
	}

	public Energy getEnergy() {
		return energy;
	}

	public ItemStackHandler getOutput() {
		return output;
	}

	public ContainerData getData() {
		return data;
	}

	public Status getStatus() {
		return status;
	}

	public int getProgress() {
		return progress;
	}

	public boolean isFighting() {
		return fighting;
	}

	public List<ItemStack> getPending() {
		return pending;
	}

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new GaiaSimulatorMenu(containerId, inventory, this, data);
	}

	@Override
	public Level getManaReceiverLevel() {
		return level;
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return worldPosition;
	}

	@Override
	public int getCurrentMana() {
		return mana;
	}

	@Override
	public boolean isFull() {
		return mana >= MANA_PER_FIGHT;
	}

	@Override
	public void receiveMana(int mana) {
		this.mana = Math.clamp((long) this.mana + mana, 0, MANA_PER_FIGHT);
		setChanged();
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return true;
	}

	@Override
	public boolean canAttachSpark(ItemStack stack) {
		return true;
	}

	@Override
	public boolean areIncomingTransfersDone() {
		return isFull();
	}

	@Override
	public int getAvailableSpaceForMana() {
		return Math.max(0, MANA_PER_FIGHT - mana);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Mana", mana);
		tag.put("Energy", energy.serializeNBT(registries));
		tag.putBoolean("Fighting", fighting);
		tag.putInt("Progress", progress);
		tag.put("Output", output.serializeNBT(registries));
		ListTag pendingTag = new ListTag();
		for (ItemStack stack : pending) {
			pendingTag.add(stack.save(registries));
		}
		tag.put("Pending", pendingTag);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		mana = Math.clamp(tag.getInt("Mana"), 0, MANA_PER_FIGHT);
		if (tag.contains("Energy")) {
			energy.deserializeNBT(registries, tag.get("Energy"));
		}
		fighting = tag.getBoolean("Fighting");
		progress = tag.getInt("Progress");
		output.deserializeNBT(registries, tag.getCompound("Output"));
		pending.clear();
		for (Tag stackTag : tag.getList("Pending", Tag.TAG_COMPOUND)) {
			ItemStack.parse(registries, stackTag).ifPresent(pending::add);
		}
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}

	/** FE buffer: accepts from any side, never gives energy back. */
	public class Energy extends EnergyStorage {
		Energy() {
			super(ENERGY_CAPACITY, ENERGY_CAPACITY, 0);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (received > 0 && !simulate) {
				setChanged();
			}
			return received;
		}

		boolean consume(int amount) {
			if (energy < amount) {
				return false;
			}
			energy -= amount;
			return true;
		}
	}
}
