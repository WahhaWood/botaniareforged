package vazkii.botania.ae2.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.block.mana.ManaPoolBlock;

public class FluixPool extends ManaPoolBlock {
	public FluixPool() {
		super(1000000, true, false, ManaPoolBlock.NORMAL_SHAPE_VARIANT, null, Properties.ofFullCopy(BotaniaBlocks.LIVINGROCK));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FluixPoolBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos blockPos, BlockState blockState, @Nullable LivingEntity livingEntity, ItemStack itemStack) {
		if (livingEntity instanceof Player player && level.getBlockEntity(blockPos) instanceof FluixPoolBlockEntity blockEntity) {
			blockEntity.getMainNode().setOwningPlayer(player);
		}
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
		FluixPoolBlockEntity pool = (FluixPoolBlockEntity) world.getBlockEntity(pos);
		return pool != null ? pool.calculateComparatorLevel() : 0;
	}

	@Override
	public VoxelShape getInnerShape(BlockState state) {
		return box(1.0, 1.0, 1.0, 15.0, 6.0, 15.0);
	}
}
