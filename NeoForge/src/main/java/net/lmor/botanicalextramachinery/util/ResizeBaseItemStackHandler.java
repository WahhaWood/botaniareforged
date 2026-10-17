package net.lmor.botanicalextramachinery.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.moddingx.libx.capability.ItemCapabilities;
import org.moddingx.libx.inventory.IAdvancedItemHandlerModifiable;
import org.moddingx.libx.inventory.VanillaWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ResizeBaseItemStackHandler extends ItemStackHandler implements IAdvancedItemHandlerModifiable {
	private final int size;
	private final int defaultSlotLimit;
	private final Set<Integer> insertionOnlySlots;
	private final Set<Integer> outputSlots;
	private static Map<Integer, Integer> slotLimits;
	private final Map<Integer, Predicate<ItemStack>> slotValidators;
	private final Consumer<Integer> contentsChanged;
	private Container vanilla = null;
	private Unrestricted unrestricted = null;

	private ResizeBaseItemStackHandler(int size, int defaultSlotLimit, Set<Integer> insertionOnlySlots, Set<Integer> outputSlots, Map<Integer, Integer> slotLimits, Map<Integer, Predicate<ItemStack>> slotValidators, Consumer<Integer> contentsChanged) {
		super(size);
		this.size = size;
		this.defaultSlotLimit = defaultSlotLimit;
		this.insertionOnlySlots = ImmutableSet.copyOf(insertionOnlySlots);
		this.outputSlots = ImmutableSet.copyOf(outputSlots);
		this.slotLimits = ImmutableMap.copyOf(slotLimits);
		this.slotValidators = ImmutableMap.copyOf(slotValidators);
		this.contentsChanged = contentsChanged;
	}

	public void setSlotLimits(int limit) {
		Map<Integer, Integer> aa = new HashMap<>();

		for (Integer slot : slotLimits.keySet()) {
			aa.put(slot, limit);
		}

		slotLimits = ImmutableMap.copyOf(aa);
	}

	@Nonnull
	@Override
	public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
		return this.outputSlots.contains(slot) ? stack : super.insertItem(slot, stack, simulate);
	}

	@Nonnull
	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		return this.insertionOnlySlots.contains(slot) ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
	}

	@Override
	public int getSlotLimit(int slot) {
		return (Integer) this.slotLimits.getOrDefault(slot, this.defaultSlotLimit);
	}

	@Override
	public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
		return !this.slotValidators.containsKey(slot) || (this.slotValidators.get(slot)).test(stack);
	}

	@Override
	public void onContentsChanged(int slot) {
		if (this.contentsChanged != null) {
			this.contentsChanged.accept(slot);
		}

	}

	@Override
	protected void onLoad() {
		if (this.stacks.size() != this.size) {
			NonNullList<ItemStack> oldStacks = this.stacks;
			this.stacks = NonNullList.withSize(this.size, ItemStack.EMPTY);

			for (int slot = 0; slot < Math.min(oldStacks.size(), this.size); ++slot) {
				this.stacks.set(slot, oldStacks.get(slot));
			}
		}

	}

	public Container toVanilla() {
		if (this.vanilla == null) {
			this.vanilla = new VanillaWrapper(this, null);
		}

		return this.vanilla;
	}

	public IAdvancedItemHandlerModifiable getUnrestricted() {
		if (this.unrestricted == null) {
			this.unrestricted = new Unrestricted();
		}

		return this.unrestricted;
	}

	public IAdvancedItemHandlerModifiable createCapability() {
		return this;
	}

	public IAdvancedItemHandlerModifiable createUnrestrictedCapability() {
		return this.getUnrestricted();
	}

	public IAdvancedItemHandlerModifiable createCapability(@Nullable Predicate<Integer> extract, @Nullable BiPredicate<Integer, ItemStack> insert) {
		return ItemCapabilities.create(this, extract, insert);
	}

	public static Builder builder(int size) {
		return new Builder(size);
	}

	private class Unrestricted implements IAdvancedItemHandlerModifiable {
		private Unrestricted() {}

		@Override
		public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
			ResizeBaseItemStackHandler.this.setStackInSlot(slot, stack);
		}

		@Override
		public int getSlots() {
			return ResizeBaseItemStackHandler.this.getSlots();
		}

		@Nonnull
		@Override
		public ItemStack getStackInSlot(int slot) {
			return ResizeBaseItemStackHandler.this.getStackInSlot(slot);
		}

		@Nonnull
		@Override
		public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
			if (stack.isEmpty()) {
				return ItemStack.EMPTY;
			} else {
				ResizeBaseItemStackHandler.this.validateSlotIndex(slot);
				ItemStack current = ResizeBaseItemStackHandler.this.stacks.get(slot);
				int amount = ResizeBaseItemStackHandler.this.getStackLimit(slot, stack);
				if (!current.isEmpty()) {
					if (!ItemStack.isSameItemSameComponents(stack, current)) {
						return stack;
					}

					amount -= current.getCount();
				}

				if (amount <= 0) {
					return stack;
				} else {
					if (!simulate) {
						if (current.isEmpty()) {
							ResizeBaseItemStackHandler.this.stacks.set(slot, stack.copyWithCount(Math.min(stack.getCount(), amount)));
						} else {
							current.grow(Math.min(stack.getCount(), amount));
						}

						ResizeBaseItemStackHandler.this.onContentsChanged(slot);
					}

					return stack.copyWithCount(Math.max(0, stack.getCount() - amount));
				}
			}
		}

		@Nonnull
		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			if (amount <= 0) {
				return ItemStack.EMPTY;
			} else {
				ResizeBaseItemStackHandler.this.validateSlotIndex(slot);
				ItemStack current = ResizeBaseItemStackHandler.this.stacks.get(slot);
				if (current.isEmpty()) {
					return ItemStack.EMPTY;
				} else {
					int count = Math.min(current.getCount(), Math.min(amount, current.getMaxStackSize()));
					if (!simulate) {
						ResizeBaseItemStackHandler.this.stacks.set(slot, current.copyWithCount(Math.max(0, current.getCount() - count)));
						ResizeBaseItemStackHandler.this.onContentsChanged(slot);
					}

					return current.copyWithCount(count);
				}
			}
		}

		@Override
		public int getSlotLimit(int slot) {
			return ResizeBaseItemStackHandler.this.getSlotLimit(slot);
		}

		@Override
		public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
			return true;
		}
	}

	public static class Builder {
		private final int size;
		private int defaultSlotLimit = 64;
		private final Set<Integer> insertionOnlySlots = new HashSet<>();
		private final Set<Integer> outputSlots = new HashSet<>();
		private final Map<Integer, Integer> slotLimits = new HashMap<>();
		private final Map<Integer, Predicate<ItemStack>> slotValidators = new HashMap<>();
		private Consumer<Integer> contentsChanged = null;

		private Builder(int size) {
			this.size = size;
		}

		public Builder contentsChanged(Runnable action) {
			return this.contentsChanged((slot) -> {
				action.run();
			});
		}

		public Builder contentsChanged(Consumer<Integer> action) {
			if (this.contentsChanged == null) {
				this.contentsChanged = action;
			} else {
				Consumer<Integer> old = this.contentsChanged;
				this.contentsChanged = (slot) -> {
					old.accept(slot);
					action.accept(slot);
				};
			}

			return this;
		}

		public Builder output(int... slots) {
			int length = slots.length;

			for (int slot : slots) {
				this.outputSlots.add(slot);
			}

			return this;
		}

		public Builder output(Set<Integer> slots) {
			this.outputSlots.addAll(slots);
			return this;
		}

		public Builder output(Range<Integer> slots) {
			IntStream range = IntStream.range(0, this.size);
			Objects.requireNonNull(slots);
			range = range.filter(slots::contains);
			Set<Integer> integers = this.outputSlots;
			Objects.requireNonNull(integers);
			range.forEach(integers::add);
			return this;
		}

		public Builder insertionOnly(int... slots) {
			int var3 = slots.length;

			for (int slot : slots) {
				this.insertionOnlySlots.add(slot);
			}

			return this;
		}

		public Builder insertionOnly(Set<Integer> slots) {
			this.insertionOnlySlots.addAll(slots);
			return this;
		}

		public Builder insertionOnly(Range<Integer> slots) {
			IntStream range = IntStream.range(0, this.size);
			Objects.requireNonNull(slots);
			range = range.filter(slots::contains);
			Set<Integer> integers = this.insertionOnlySlots;
			Objects.requireNonNull(integers);
			range.forEach(integers::add);
			return this;
		}

		public Builder defaultSlotLimit(int defaultSlotLimit) {
			this.defaultSlotLimit = defaultSlotLimit;
			return this;
		}

		public Builder slotLimit(int slotLimit, int... slots) {
			int var4 = slots.length;

			for (int slot : slots) {
				this.slotLimits.put(slot, slotLimit);
			}

			return this;
		}

		public Builder slotLimit(int slotLimit, Set<Integer> slots) {

			for (int slot : slots) {
				this.slotLimits.put(slot, slotLimit);
			}

			return this;
		}

		public Builder slotLimit(int slotLimit, Range<Integer> slots) {
			IntStream range = IntStream.range(0, this.size);
			Objects.requireNonNull(slots);
			range.filter(slots::contains).forEach((slot) -> {
				this.slotLimits.put(slot, slotLimit);
			});
			return this;
		}

		public Builder validator(Predicate<ItemStack> validator, int... slots) {
			for (int slot : slots) {
				this.slotValidators.put(slot, validator);
			}

			return this;
		}

		public Builder validator(Predicate<ItemStack> validator, Set<Integer> slots) {
			for (int slot : slots) {
				this.slotValidators.put(slot, validator);
			}

			return this;
		}

		public Builder validator(Predicate<ItemStack> validator, Range<Integer> slots) {
			IntStream range = IntStream.range(0, this.size);
			Objects.requireNonNull(slots);
			range.filter(slots::contains).forEach((slot) -> {
				this.slotValidators.put(slot, validator);
			});
			return this;
		}

		public ResizeBaseItemStackHandler build() {
			Stream<Integer> stream = this.outputSlots.stream();
			Set<Integer> integers = this.insertionOnlySlots;
			Objects.requireNonNull(integers);
			if (stream.anyMatch(integers::contains)) {
				throw new IllegalStateException("Can't build ResizeBaseItemStackHandler: A slot can not be an insertion only and an output slot at the same time.");
			} else {
				return new ResizeBaseItemStackHandler(this.size, this.defaultSlotLimit, this.insertionOnlySlots, this.outputSlots, this.slotLimits, this.slotValidators, this.contentsChanged);
			}
		}
	}
}
