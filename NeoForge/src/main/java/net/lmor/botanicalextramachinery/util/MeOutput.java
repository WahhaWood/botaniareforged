package net.lmor.botanicalextramachinery.util;

import net.lmor.botanicalextramachinery.ae2.MeOutputNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * Output of a machine into an Applied Energistics 2 network. AE2 is optional: without it every machine gets
 * {@link #NONE}, and no AE2 class is loaded. The machines only see this interface; the grid node lives in
 * {@link MeOutputNode}.
 */
public interface MeOutput {
	boolean AE2 = ModList.get().isLoaded("ae2");

	MeOutput NONE = new MeOutput() {
		@Override
		public void tick(Level level, BlockPos pos) {}

		@Override
		public boolean isOnline() {
			return false;
		}

		@Override
		public int insert(ItemStack stack) {
			return 0;
		}

		@Override
		public int insertMana(int mana) {
			return 0;
		}

		@Override
		public void load(CompoundTag tag) {}

		@Override
		public void save(CompoundTag tag) {}

		@Override
		public void destroy() {}
	};

	/** @param icon what the grid node shows for this machine */
	static MeOutput create(BlockEntity blockEntity, Item icon) {
		return AE2 ? new MeOutputNode(blockEntity, icon) : NONE;
	}

	/** Server tick: joins the network once the machine is in the world. */
	void tick(Level level, BlockPos pos);

	/** On a powered network with a channel. */
	boolean isOnline();

	/** Puts the stack into the network storage; the stack itself is not changed. @return how many went in */
	int insert(ItemStack stack);

	/** Puts mana into the network as Botania's mana key (needs a mana cell). @return how much went in */
	int insertMana(int mana);

	void load(CompoundTag tag);

	void save(CompoundTag tag);

	/** The machine is removed: leave the network. */
	void destroy();

	/** Block entity that owns a {@link MeOutput}; AE2 finds its grid node through this. */
	interface Holder {
		MeOutput meOutput();
	}
}
