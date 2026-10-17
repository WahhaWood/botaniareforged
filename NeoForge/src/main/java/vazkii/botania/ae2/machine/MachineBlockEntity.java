package vazkii.botania.ae2.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;

/**
 * ME machine: offers every enabled recipe of its kind to the ME network as a pattern, takes the inputs
 * a crafting CPU pushes (the recipe's items, mana, water: all of it is paid by the pattern), and does up
 * to {@value #OPERATIONS_PER_TICK} crafts per tick on average, in passes every {@value #WORK_INTERVAL} ticks.
 * Results, returned catalysts and container leftovers go straight back into the network.
 */
public class MachineBlockEntity extends AENetworkedBlockEntity implements ICraftingProvider, IGridTickable, MenuProvider {
	/** Upper bound of crafts one machine does per tick, on average. */
	public static final int OPERATIONS_PER_TICK = 1024;
	/**
	 * Ticks between two work passes of a busy machine; a pass does the work of the ticks since the last one.
	 * Every pass talks to the network (inserting results), so batching cuts that cost by this factor.
	 */
	public static final int WORK_INTERVAL = 5;
	/** Pushed crafts the machine accepts before it reports busy: two work passes, so it never runs dry between CPU pushes. */
	private static final long MAX_QUEUED = 2L * OPERATIONS_PER_TICK * WORK_INTERVAL;
	/** AE per tick the machine draws just for being on the network. */
	private static final double IDLE_POWER = 1.0;
	/** Machines loaded on the server, refreshed when datapacks reload. */
	private static final Set<MachineBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	public final MachineKind kind;
	private final RecipeToggles toggles = new RecipeToggles();
	private final IActionSource actionSource = IActionSource.ofMachine(this);
	private final Deque<Job> queue = new ArrayDeque<>();
	private final KeyCounter outputBuffer = new KeyCounter();
	private long queued;
	private long lastSaveTick = -1;
	/** Crafts per tick during the last work pass, for the GUI. */
	public int lastOperations;
	@Nullable
	private Map<ResourceLocation, MachineRecipe> recipes;
	private int recipesGeneration;
	@Nullable
	private List<IPatternDetails> patterns;
	@Nullable
	private BlockState catalystSeen;

	public MachineBlockEntity(MachineKind kind, BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.kind = kind;
		getMainNode()
				.setFlags(GridFlags.REQUIRE_CHANNEL)
				.setIdlePowerUsage(IDLE_POWER)
				.addService(ICraftingProvider.class, this)
				.addService(IGridTickable.class, this);
	}

	/** Called after a datapack reload: every loaded machine re-reads its recipes and re-offers patterns. */
	public static void refreshAll() {
		MachineRecipe.invalidate();
		for (MachineBlockEntity machine : List.copyOf(LOADED)) {
			machine.patterns = null;
			ICraftingProvider.requestUpdate(machine.getMainNode());
		}
	}

	@Override
	public void onReady() {
		super.onReady();
		if (!isClientSide()) {
			LOADED.add(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		LOADED.remove(this);
	}

	@Override
	public void onChunkUnloaded() {
		super.onChunkUnloaded();
		LOADED.remove(this);
	}

	// ---- recipes and toggles -------------------------------------------------------------------

	/** All usable recipes of this machine's kind, by id, in GUI order. */
	public Map<ResourceLocation, MachineRecipe> recipes() {
		if (level == null) {
			return Map.of();
		}

		int generation = MachineRecipe.generation();
		if (recipes == null || recipesGeneration != generation) {
			recipes = MachineRecipe.collect(kind, level);
			recipesGeneration = generation;
			patterns = null;
		}
		return recipes;
	}

	public boolean isEnabled(ResourceLocation id) {
		return toggles.isEnabled(id);
	}

	/** Server side: flips one recipe, or all of them, and re-offers the patterns. */
	public void applyToggle(ToggleRecipePayload.Action action, @Nullable ResourceLocation id) {
		switch (action) {
			case TOGGLE -> {
				if (id == null || !recipes().containsKey(id)) {
					return;
				}
				toggles.toggle(id);
			}
			case ENABLE_ALL -> toggles.setAll(true);
			case DISABLE_ALL -> toggles.setAll(false);
		}

		patterns = null;
		ICraftingProvider.requestUpdate(getMainNode());
		saveChanges();
		markForClientUpdate();
	}

	/** The block under the machine changed: mana infusion recipes may need it as their catalyst. */
	public void onBelowChanged() {
		if (kind == MachineKind.MANA_INFUSER && level != null && !level.isClientSide() && !catalystState().equals(catalystSeen)) {
			patterns = null;
			ICraftingProvider.requestUpdate(getMainNode());
		}
	}

	private BlockState catalystState() {
		return level.getBlockState(worldPosition.below());
	}

	@Override
	protected void writeToStream(RegistryFriendlyByteBuf data) {
		super.writeToStream(data);
		toggles.write(data);
	}

	@Override
	protected boolean readFromStream(RegistryFriendlyByteBuf data) {
		boolean changed = super.readFromStream(data);
		toggles.read(data);
		return changed;
	}

	// ---- ICraftingProvider ---------------------------------------------------------------------

	@Override
	public List<IPatternDetails> getAvailablePatterns() {
		Map<ResourceLocation, MachineRecipe> all = recipes();
		if (patterns == null) {
			BlockState below = level == null ? null : catalystState();
			catalystSeen = below;
			List<IPatternDetails> list = new ArrayList<>();
			for (MachineRecipe recipe : all.values()) {
				if (toggles.isEnabled(recipe.id()) && (recipe.catalyst() == null || below != null && recipe.catalyst().test(below))) {
					list.add(new MachinePattern(recipe));
				}
			}
			patterns = List.copyOf(list);
		}
		return patterns;
	}

	@Override
	public boolean pushPattern(IPatternDetails details, KeyCounter[] inputHolder) {
		if (!(details instanceof MachinePattern pattern) || isBusy() || !getMainNode().isActive()) {
			return false;
		}

		MachineRecipe recipe = recipes().get(pattern.recipeId());
		if (recipe == null || !toggles.isEnabled(recipe.id())) {
			return false;
		}

		List<GenericStack> inputs = new ArrayList<>();
		List<GenericStack> leftovers = new ArrayList<>();
		for (int i = 0; i < inputHolder.length; i++) {
			MachineRecipe.Part part = i < recipe.parts().size() ? recipe.parts().get(i) : null;
			for (var entry : inputHolder[i]) {
				inputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
				AEKey left = part == null ? null : MachinePattern.leftover(part, entry.getKey());
				if (left != null) {
					leftovers.add(new GenericStack(left, entry.getLongValue()));
				}
			}
		}

		Job last = queue.peekLast();
		if (last != null && last.recipe.equals(recipe.id()) && last.inputs.equals(inputs)) {
			last.crafts++;
		} else {
			queue.addLast(new Job(recipe.id(), inputs, leftovers, 1));
		}

		// several CPUs and direct pushes may come in one tick: wake and save once per tick, not per push
		boolean idle = queued == 0;
		queued++;
		long now = level != null ? level.getGameTime() : 0;
		if (now != lastSaveTick) {
			lastSaveTick = now;
			saveChanges();
		}
		if (idle) {
			wake();
		}
		return true;
	}

	long queuedOperations() {
		return queued;
	}

	/** Crafts the queue still takes. */
	long freeCapacity() {
		return Math.max(0, MAX_QUEUED - queued);
	}

	/** More copies of the push just accepted, inputs already taken from the CPU ({@link MachineCpuHooks#pushMore}). */
	void acceptMore(long crafts) {
		Job last = queue.peekLast();
		if (last != null && crafts > 0) {
			last.crafts += crafts;
			queued += crafts;
		}
	}

	public int pendingOutputs() {
		return outputBuffer.size();
	}

	@Override
	public boolean isBusy() {
		return queued >= MAX_QUEUED || !outputBuffer.isEmpty();
	}

	// ---- work ----------------------------------------------------------------------------------

	private boolean hasWork() {
		return !queue.isEmpty() || !outputBuffer.isEmpty();
	}

	private void wake() {
		getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
	}

	@Override
	public TickingRequest getTickingRequest(IGridNode node) {
		return new TickingRequest(WORK_INTERVAL, 20, !hasWork(), WORK_INTERVAL);
	}

	@Override
	public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
		IGrid grid = node.getGrid();
		if (!getMainNode().isActive() || grid == null) {
			lastOperations = 0;
			return TickRateModulation.SLEEP;
		}

		// a wake-up after a long sleep must not turn into a huge pass: the work of one interval at most
		TickRateModulation next = doWork(grid, Math.max(1, Math.min(ticksSinceLastCall, WORK_INTERVAL)));
		if (next == TickRateModulation.SLEEP) {
			lastOperations = 0;
		}
		return next;
	}

	@Override
	public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
		super.onMainNodeStateChanged(reason);
		// channel or power came back: resume a queue that went to sleep while offline
		getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
	}

	/** Does the work of {@code ticks} game ticks (1..{@value #WORK_INTERVAL}) and tells AE2 how soon to come back. */
	private TickRateModulation doWork(IGrid grid, int ticks) {
		MEStorage storage = grid.getStorageService().getInventory();
		flushOutputs(storage, grid);
		if (!outputBuffer.isEmpty()) {
			return TickRateModulation.SLOWER; // network full: retry before taking more work
		}

		Map<ResourceLocation, MachineRecipe> all = recipes();
		long budget = (long) OPERATIONS_PER_TICK * ticks;
		int done = 0;
		while (budget > 0 && !queue.isEmpty()) {
			Job job = queue.peekFirst();
			MachineRecipe recipe = all.get(job.recipe);
			if (recipe == null) {
				// recipe gone after a datapack change: hand everything back to the network
				for (GenericStack input : job.inputs) {
					outputBuffer.add(input.what(), input.amount() * job.crafts);
				}
				queued -= job.crafts;
				queue.pollFirst();
				continue;
			}

			long crafts = Math.min(budget, job.crafts);
			for (ItemStack output : recipe.outputs()) {
				AEItemKey key = AEItemKey.of(output);
				if (key != null) {
					outputBuffer.add(key, output.getCount() * crafts);
				}
			}
			for (GenericStack leftover : job.leftovers) {
				outputBuffer.add(leftover.what(), leftover.amount() * crafts);
			}

			job.crafts -= crafts;
			queued -= crafts;
			budget -= crafts;
			done += (int) crafts;
			if (job.crafts <= 0) {
				queue.pollFirst();
			}
		}

		lastOperations = done / ticks;
		flushOutputs(storage, grid);
		if (done > 0) {
			saveChanges();
		}

		if (!hasWork()) {
			return TickRateModulation.SLEEP;
		}
		// still queued: either the budget ran out (go on next tick) or the network is full (back off)
		return outputBuffer.isEmpty() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
	}

	private void flushOutputs(MEStorage storage, IGrid grid) {
		if (outputBuffer.isEmpty()) {
			return;
		}

		List<GenericStack> pending = new ArrayList<>(outputBuffer.size());
		for (var entry : outputBuffer) {
			pending.add(new GenericStack(entry.getKey(), entry.getLongValue()));
		}
		for (GenericStack stack : pending) {
			long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), storage, stack.what(), stack.amount(), actionSource);
			if (inserted > 0) {
				outputBuffer.remove(stack.what(), inserted);
			}
		}

		outputBuffer.removeZeros();
		saveChanges();
	}

	// ---- persistence and drops -----------------------------------------------------------------

	@Override
	public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		toggles.save(tag);
		ListTag jobs = new ListTag();
		for (Job job : queue) {
			CompoundTag jobTag = new CompoundTag();
			jobTag.putString("recipe", job.recipe.toString());
			jobTag.putLong("crafts", job.crafts);
			jobTag.put("inputs", writeStacks(job.inputs, registries));
			jobTag.put("leftovers", writeStacks(job.leftovers, registries));
			jobs.add(jobTag);
		}
		tag.put("me_queue", jobs);

		List<GenericStack> outputs = new ArrayList<>();
		for (var entry : outputBuffer) {
			outputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
		}
		tag.put("me_outputs", writeStacks(outputs, registries));
	}

	@Override
	public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadTag(tag, registries);
		toggles.load(tag);
		queue.clear();
		queued = 0;
		ListTag jobs = tag.getList("me_queue", Tag.TAG_COMPOUND);
		for (int i = 0; i < jobs.size(); i++) {
			CompoundTag jobTag = jobs.getCompound(i);
			ResourceLocation id = ResourceLocation.tryParse(jobTag.getString("recipe"));
			long crafts = jobTag.getLong("crafts");
			if (id != null && crafts > 0) {
				queue.addLast(new Job(id, readStacks(jobTag.getList("inputs", Tag.TAG_COMPOUND), registries),
						readStacks(jobTag.getList("leftovers", Tag.TAG_COMPOUND), registries), crafts));
				queued += crafts;
			}
		}

		outputBuffer.clear();
		for (GenericStack stack : readStacks(tag.getList("me_outputs", Tag.TAG_COMPOUND), registries)) {
			outputBuffer.add(stack.what(), stack.amount());
		}
	}

	private static ListTag writeStacks(List<GenericStack> stacks, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (GenericStack stack : stacks) {
			list.add(GenericStack.writeTag(registries, stack));
		}
		return list;
	}

	private static List<GenericStack> readStacks(ListTag list, HolderLookup.Provider registries) {
		List<GenericStack> stacks = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			GenericStack stack = GenericStack.readTag(registries, list.getCompound(i));
			if (stack != null && stack.amount() > 0) {
				stacks.add(stack);
			}
		}
		return stacks;
	}

	/** Items held by the machine (queued inputs, undelivered outputs) that drop when it is broken. */
	public void addContentDrops(List<ItemStack> drops) {
		if (level == null) {
			return;
		}

		for (Job job : queue) {
			for (GenericStack input : job.inputs) {
				input.what().addDrops(input.amount() * job.crafts, drops, level, worldPosition);
			}
		}
		for (var entry : outputBuffer) {
			entry.getKey().addDrops(entry.getLongValue(), drops, level, worldPosition);
		}
	}

	// ---- menu ----------------------------------------------------------------------------------

	@Override
	public Component getDisplayName() {
		return getBlockState().getBlock().getName();
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new MachineMenu(containerId, inventory, this);
	}

	/** A run of identical pushes: same recipe, same actual input items (tag substitutes included). */
	private static final class Job {
		final ResourceLocation recipe;
		/** Inputs of one craft, as pushed. */
		final List<GenericStack> inputs;
		/** What one craft gives back besides its result: returned catalysts, container items. */
		final List<GenericStack> leftovers;
		long crafts;

		Job(ResourceLocation recipe, List<GenericStack> inputs, List<GenericStack> leftovers, long crafts) {
			this.recipe = recipe;
			this.inputs = List.copyOf(inputs);
			this.leftovers = List.copyOf(leftovers);
			this.crafts = crafts;
		}
	}
}
