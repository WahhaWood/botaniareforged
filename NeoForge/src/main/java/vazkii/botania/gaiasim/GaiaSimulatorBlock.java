package vazkii.botania.gaiasim;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.client.fx.WispParticleData;

import java.util.List;

/**
 * Controller of the Gaia Simulator. {@link #LIT} is on while a fight is running; the client then draws wisps
 * flowing from the pylons into the controller.
 */
public class GaiaSimulatorBlock extends Block implements EntityBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public GaiaSimulatorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GaiaSimulatorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != GaiaSimulator.BLOCK_ENTITY.get()) {
			return null;
		}
		return (lvl, pos, st, be) -> ((GaiaSimulatorBlockEntity) be).tickServer();
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		// relics from the loot are bound to whoever placed the simulator
		if (placer instanceof Player player && level.getBlockEntity(pos) instanceof GaiaSimulatorBlockEntity simulator) {
			simulator.setOwner(player.getUUID());
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GaiaSimulatorBlockEntity simulator) {
			player.openMenu(simulator, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GaiaSimulatorBlockEntity simulator) {
			simulator.dropContents();
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		for (BlockPos offset : GaiaSimulatorBlockEntity.PYLONS) {
			Vec3 from = Vec3.atCenterOf(pos.offset(offset)).add(0, 0.3, 0);
			Vec3 motion = center.subtract(from).scale(0.05);
			level.addParticle(WispParticleData.wisp(0.15F + random.nextFloat() * 0.1F, 0.9F, 0.4F, 0.9F, 1.0F),
					from.x, from.y, from.z, motion.x, motion.y, motion.z);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("botania.gaia_simulator.tooltip.bottom").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("botania.gaia_simulator.tooltip.top").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("botania.gaia_simulator.tooltip.cost",
				GaiaSimulatorBlockEntity.MANA_PER_FIGHT, GaiaSimulatorBlockEntity.ENERGY_PER_TICK,
				GaiaSimulatorBlockEntity.FIGHT_TICKS / 20 / 60).withStyle(ChatFormatting.GRAY));
	}
}
