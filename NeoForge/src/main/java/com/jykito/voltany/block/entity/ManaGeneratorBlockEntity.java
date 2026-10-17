package com.jykito.voltany.block.entity;

import com.jykito.voltany.block.ManaGeneratorBlock;
import com.jykito.voltany.item.GeneratorUpgradeItem;
import com.jykito.voltany.menu.ManaGeneratorMenu;
import com.jykito.voltany.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSpark;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ManaGeneratorBlockEntity extends BlockEntity implements MenuProvider, ManaPool, ManaSparkAttachable {
	public static final int FE_PER_MANA = 10;
	public static final int FE_PER_MANA_EFFICIENT = 6;
	public static final int BASE_EMPTY = 80;
	public static final int BASE_UNIT = 80;
	public static final int RADIUS = 1;
	private static final int LIT_GRACE = 30;
	public static final int ENERGY_CAPACITY = 256000;
	public static final int ENERGY_MAX_RECEIVE = 8000;
	public static final int MANA_CAPACITY = 1000000;

	private final GenEnergy energy = new GenEnergy();
	private final ItemStackHandler flower = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			ManaGeneratorBlockEntity.this.recalc();
			ManaGeneratorBlockEntity.this.setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return ManaGeneratorBlockEntity.flowerTier(stack) > 0;
		}

		@Override
		public int getSlotLimit(int slot) {
			return Math.max(1, ManaGeneratorBlockEntity.this.maxFlowerStack);
		}
	};
	private final ItemStackHandler upgrades = new ItemStackHandler(4) {
		@Override
		protected void onContentsChanged(int slot) {
			ManaGeneratorBlockEntity.this.recalc();
			ManaGeneratorBlockEntity.this.setChanged();
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.getItem() instanceof GeneratorUpgradeItem;
		}
	};

	private int mana = 0;
	private int litCooldown = 0;
	private double overclockMult = 1.0;
	private int maxFlowerStack = 1;
	private boolean dispersionMode = false;
	private int dispersionRadius = 0;
	private int feRatio = 10;
	private boolean sparkMode = false;
	private int manaPerTickEff = 80;
	private final SimpleContainerData data = new SimpleContainerData(6);

	public ManaGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MANA_GENERATOR.get(), pos, state);
	}

	public static int flowerTier(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id != null && id.getNamespace().equals("botania")) {
			return switch (id.getPath()) {
				case "hydroangeas", "endoflame" -> 4;
				case "thermalily", "munchdew", "gourmaryllis", "kekimurus", "entropinnyum", "narslimmus",
						"spectrolus" ->
					8;
				case "rafflowsia", "shulk_me_not", "dandelifeon" -> 16;
				default -> id.getPath().endsWith("_mystical_flower") ? 2 : 0;
			};
		}
		return 0;
	}

	private void recalc() {
		int oc = 0;
		int disp = 0;
		boolean eff = false;
		boolean spark = false;

		for (int i = 0; i < this.upgrades.getSlots(); i++) {
			Item gen = this.upgrades.getStackInSlot(i).getItem();
			if (gen instanceof GeneratorUpgradeItem up) {
				switch (up.getType()) {
					case OVERCLOCK_1 -> oc = Math.max(oc, 1);
					case OVERCLOCK_2 -> oc = Math.max(oc, 2);
					case OVERCLOCK_3 -> oc = Math.max(oc, 3);
					case DISPERSION_1 -> disp = Math.max(disp, 1);
					case DISPERSION_2 -> disp = Math.max(disp, 2);
					case DISPERSION_3 -> disp = Math.max(disp, 3);
					case EFFICIENCY -> eff = true;
					case SPARK -> spark = true;
				}
			}
		}

		this.overclockMult = switch (oc) {
			case 1 -> 1.5;
			case 2, 3 -> 2.0;
			default -> 1.0;
		};
		this.maxFlowerStack = oc == 3 ? 8 : 1;
		this.dispersionMode = disp > 0;
		this.dispersionRadius = switch (disp) {
			case 1 -> 5;
			case 2 -> 15;
			case 3 -> 30;
			default -> 0;
		};
		this.feRatio = eff ? FE_PER_MANA_EFFICIENT : FE_PER_MANA;
		this.sparkMode = spark;

		ItemStack f = this.flower.getStackInSlot(0);
		int tier = flowerTier(f);
		int gen;
		if (tier <= 0) {
			gen = BASE_UNIT;
		} else {
			int count = Math.min(f.getCount(), this.maxFlowerStack);
			gen = BASE_UNIT * tier * count;
		}

		this.manaPerTickEff = (int) Math.round(gen * this.overclockMult);
		int feNeeded = Math.max(ENERGY_MAX_RECEIVE, this.manaPerTickEff * this.feRatio);
		this.energy.configure(Math.max(ENERGY_CAPACITY, feNeeded * 4), feNeeded);
		syncContainerData();
	}

	/**
	 * Mirrors energy and mana into the data slots the menu reads.
	 * <p>
	 * The menu is bound to this block entity's own {@code SimpleContainerData},
	 * and the update tag carries energy and mana rather than those slots, so
	 * without this the client's copy stayed zeroed: tickServer is the only other
	 * writer and it returns immediately on a client. Called from recalc, which
	 * onLoad and loadAdditional both run, so the client's copy catches up with
	 * every tag the server sends.
	 */
	private void syncContainerData() {
		int e = this.energy.getEnergyStored();
		this.data.set(0, e & 0xFFFF);
		this.data.set(1, (e >> 16) & 0xFFFF);
		this.data.set(2, this.mana & 0xFFFF);
		this.data.set(3, (this.mana >> 16) & 0xFFFF);
		// The capacity moves with the overclock upgrades, so the screen's fill
		// bar has to follow it rather than assume the base value.
		int cap = this.energy.getMaxEnergyStored();
		this.data.set(4, cap & 0xFFFF);
		this.data.set(5, (cap >> 16) & 0xFFFF);
	}

	public void tickServer() {
		Level level = this.getLevel();
		if (level == null || level.isClientSide) {
			return;
		}

		boolean working = false;
		int room = MANA_CAPACITY - this.mana;
		int generated = Math.min(this.manaPerTickEff,
				Math.min(room, this.energy.getEnergyStored() / this.feRatio));
		if (generated > 0) {
			this.mana += generated;
			this.energy.consume(generated * this.feRatio);
			working = true;
		}

		boolean sparkActive = this.sparkMode && this.findAttachedSpark() != null;
		int sent = 0;
		if (this.mana > 0 && !sparkActive) {
			int budget = this.mana;
			if (this.dispersionMode) {
				sent = distributeToPools(level, this.getBlockPos(), this.dispersionRadius, budget);
			} else {
				ManaReceiver target = findClosestReceiver(level, this.getBlockPos(), RADIUS);
				if (target != null && !target.isFull()) {
					sent = sendTo(target, targetMaxMana(target), budget);
				}
			}
			this.mana -= sent;
		}

		if (sent > 0 || sparkActive) {
			working = true;
		}

		if (working) {
			this.litCooldown = LIT_GRACE;
		} else if (this.litCooldown > 0) {
			this.litCooldown--;
		}

		this.updateLit(this.litCooldown > 0);
		if (generated > 0 || sent > 0) {
			this.setChanged();
		}

		syncContainerData();
		if (level.getGameTime() % 10 == 0) {
			level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
		}
	}

	private static int targetMaxMana(ManaReceiver receiver) {
		return receiver instanceof ManaPool pool ? pool.getMaxMana() : MANA_CAPACITY;
	}

	/**
	 * Replaces the 1.20.1 ManaNetwork lookups (server-side pool tracking was
	 * removed in Botania 1.21.1): scans loaded chunks around the origin.
	 */
	@Nullable
	private static ManaReceiver findClosestReceiver(Level level, BlockPos origin, int radius) {
		ManaReceiver best = null;
		double bestDist = Double.MAX_VALUE;
		for (var candidate : scanReceivers(level, origin, radius)) {
			BlockPos pos = candidate.getManaReceiverPos();
			// The generator is a pool too, and it sits at the centre of its own
			// search, so without this it would always pick itself and hand its
			// mana straight back.
			if (pos.equals(origin)) {
				continue;
			}
			double dist = pos.distSqr(origin);
			if (dist < bestDist) {
				bestDist = dist;
				best = candidate;
			}
		}
		return best;
	}

	private static int sendTo(ManaReceiver target, int targetMax, int budget) {
		int headroom = targetMax - target.getCurrentMana();
		int send = Math.min(budget, Math.max(0, headroom));
		if (send <= 0) {
			return 0;
		}
		target.receiveMana(send);
		return send;
	}

	private static int distributeToPools(Level level, BlockPos origin, int radius, int budget) {
		List<ManaPool> targets = new ArrayList<>();
		for (var receiver : scanReceivers(level, origin, radius)) {
			if (receiver instanceof ManaPool pool && !pool.isFull()) {
				targets.add(pool);
			}
		}

		if (targets.isEmpty()) {
			return 0;
		}
		int per = budget / targets.size();
		int remainder = budget % targets.size();
		int sent = 0;
		for (ManaPool pool : targets) {
			int give = per + (remainder-- > 0 ? 1 : 0);
			int headroom = pool.getMaxMana() - pool.getCurrentMana();
			give = Math.min(give, Math.max(0, headroom));
			if (give > 0) {
				pool.receiveMana(give);
				sent += give;
			}
		}
		return sent;
	}

	private static List<ManaReceiver> scanReceivers(Level level, BlockPos origin, int radius) {
		List<ManaReceiver> out = new ArrayList<>();
		double radiusSq = (double) radius * radius;
		int minX = (origin.getX() - radius) >> 4;
		int maxX = (origin.getX() + radius) >> 4;
		int minZ = (origin.getZ() - radius) >> 4;
		int maxZ = (origin.getZ() + radius) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (var entry : chunk.getBlockEntities().entrySet()) {
					BlockPos pos = entry.getKey();
					if (pos.distSqr(origin) > radiusSq) {
						continue;
					}
					BlockEntity be = entry.getValue();
					if (be instanceof ManaReceiver receiver) {
						out.add(receiver);
					} else {
						ManaReceiver found = ManaReceiver.LOOKUP.find(level, pos, be.getBlockState(), be, null);
						if (found != null) {
							out.add(found);
						}
					}
				}
			}
		}
		return out;
	}

	private void updateLit(boolean lit) {
		Level level = this.getLevel();
		if (level != null) {
			BlockState state = this.getBlockState();
			if (state.hasProperty(ManaGeneratorBlock.LIT) && state.getValue(ManaGeneratorBlock.LIT) != lit) {
				level.setBlock(this.getBlockPos(), state.setValue(ManaGeneratorBlock.LIT, lit), 3);
			}
		}
	}

	public void dropContents(Level level, BlockPos pos) {
		SimpleContainer container = new SimpleContainer(this.flower.getSlots() + this.upgrades.getSlots());
		int idx = 0;
		for (int i = 0; i < this.flower.getSlots(); i++) {
			container.setItem(idx++, this.flower.getStackInSlot(i));
		}
		for (int i = 0; i < this.upgrades.getSlots(); i++) {
			container.setItem(idx++, this.upgrades.getStackInSlot(i));
		}
		Containers.dropContents(level, pos, container);
	}

	public GenEnergy getEnergy() {
		return this.energy;
	}

	public ItemStackHandler getFlowerHandler() {
		return this.flower;
	}

	public ItemStackHandler getUpgradeHandler() {
		return this.upgrades;
	}

	/**
	 * What the current flower and upgrades make of each other: how much mana the
	 * generator aims to make per tick, what it costs in energy per mana, how far
	 * it will spread that mana, and whether it will attach a spark instead of
	 * handing mana to a pool itself. All of it is derived by {@code recalc()} when
	 * either inventory changes, so these read the state a player would see.
	 */
	public int manaPerTick() {
		return this.manaPerTickEff;
	}

	public int energyPerMana() {
		return this.feRatio;
	}

	public int dispersionRadius() {
		return this.dispersionRadius;
	}

	public boolean usesSpark() {
		return this.sparkMode;
	}

	public double overclockMultiplier() {
		return this.overclockMult;
	}

	public ContainerData getContainerData() {
		return this.data;
	}

	public int getMana() {
		return this.mana;
	}

	public int getManaCapacity() {
		return MANA_CAPACITY;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.voltany.mana_generator");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return new ManaGeneratorMenu(id, inv, this);
	}

	@Override
	public Level getManaReceiverLevel() {
		return this.getLevel();
	}

	@Override
	public BlockPos getManaReceiverPos() {
		return this.getBlockPos();
	}

	@Override
	public int getCurrentMana() {
		return this.mana;
	}

	@Override
	public boolean isFull() {
		return true;
	}

	@Override
	public boolean canReceiveManaFromBursts() {
		return false;
	}

	@Override
	public void receiveMana(int delta) {
		if (delta < 0) {
			this.mana = Math.max(0, this.mana + delta);
			this.setChanged();
		}
	}

	@Override
	public boolean isOutputtingPower() {
		return this.sparkMode;
	}

	@Override
	public int getMaxMana() {
		return MANA_CAPACITY;
	}

	@Override
	public boolean canAttachSpark(ItemStack stack) {
		return this.sparkMode;
	}

	@Nullable
	public ManaSpark findAttachedSpark() {
		Level level = this.getLevel();
		if (level == null) {
			return null;
		}
		BlockPos above = this.getBlockPos().above();
		List<Entity> sparks = level.getEntitiesOfClass(Entity.class,
				new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(above),
						net.minecraft.world.phys.Vec3.atLowerCornerOf(above.offset(1, 1, 1))),
				e -> e instanceof ManaSpark);
		return sparks.size() == 1 ? (ManaSpark) sparks.get(0) : null;
	}

	@Override
	public boolean areIncomingTransfersDone() {
		return true;
	}

	@Override
	public int getAvailableSpaceForMana() {
		return 0;
	}

	@Override
	public void onLoad() {
		super.onLoad();
		this.recalc();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Energy", this.energy.getEnergyStored());
		tag.putInt("Mana", this.mana);
		tag.put("Flower", this.flower.serializeNBT(registries));
		tag.put("Upgrades", this.upgrades.serializeNBT(registries));
	}

	@Override
	public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		this.energy.load(tag.getInt("Energy"));
		this.mana = Mth.clamp(tag.getInt("Mana"), 0, MANA_CAPACITY);
		this.flower.deserializeNBT(registries, tag.getCompound("Flower"));
		this.upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
		this.recalc();
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveWithoutMetadata(registries);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
		CompoundTag tag = pkt.getTag();
		if (tag != null) {
			this.loadAdditional(tag, lookupProvider);
		}
	}

	public Optional<DyeColor> getColor() {
		return Optional.empty();
	}

	public void setColor(Optional<DyeColor> color) {}

	public class GenEnergy extends EnergyStorage {
		public GenEnergy() {
			super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
		}

		@Override
		public boolean canExtract() {
			return false;
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			int received = super.receiveEnergy(toReceive, simulate);
			if (received > 0 && !simulate) {
				ManaGeneratorBlockEntity.this.setChanged();
			}
			return received;
		}

		public void consume(int amount) {
			this.energy = Math.max(0, this.energy - amount);
		}

		public void load(int amount) {
			this.energy = Mth.clamp(amount, 0, this.capacity);
		}

		public void configure(int cap, int maxRec) {
			this.capacity = cap;
			this.maxReceive = maxRec;
			if (this.energy > cap) {
				this.energy = cap;
			}
		}
	}
}
