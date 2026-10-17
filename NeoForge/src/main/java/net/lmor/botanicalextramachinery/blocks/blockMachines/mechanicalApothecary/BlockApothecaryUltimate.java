package net.lmor.botanicalextramachinery.blocks.blockMachines.mechanicalApothecary;

import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalBlock;
import net.lmor.botanicalextramachinery.blocks.containers.mechanicalApothecary.ContainerApothecaryUltimate;
import net.lmor.botanicalextramachinery.blocks.tesr.mechanicalApothecary.RenderApothecaryUltimate;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalApothecary.BlockEntityApothecaryUltimate;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.moddingx.libx.base.tile.BlockEntityBase;
import org.moddingx.libx.base.tile.MenuBlockBE;
import org.moddingx.libx.block.RotationShape;
import org.moddingx.libx.menu.type.AdvancedMenuType;
import org.moddingx.libx.mod.ModX;
import org.moddingx.libx.registration.Registerable.EntryCollector;
import org.moddingx.libx.registration.RegistrationContext;
import org.moddingx.libx.registration.SetupContext;
import org.moddingx.libx.registration.util.ClientExtensionInfo;
import org.moddingx.libx.render.ItemStackRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class BlockApothecaryUltimate extends MenuBlockBE<BlockEntityApothecaryUltimate, ContainerApothecaryUltimate> {
	public static final RotationShape SHAPE;

	public BlockApothecaryUltimate(ModX mod, Class<BlockEntityApothecaryUltimate> teClass, AdvancedMenuType<ContainerApothecaryUltimate, ? super BlockPos> menu) {
		super(mod, teClass, menu, Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 10.0F).dynamicShape().forceSolidOn().noOcclusion(), new Item.Properties());
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void setupClient(SetupContext ctx) {
		ItemStackRenderer.addRenderBlock(this.getBlockEntityType(), true);
		BlockEntityRenderers.register(this.getBlockEntityType(), (context) -> new RenderApothecaryUltimate());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void registerClientAdditional(RegistrationContext ctx, EntryCollector builder) {
		super.registerClientAdditional(ctx, builder);
		builder.register(null, new ClientExtensionInfo.Item(ItemStackRenderer.createProperties()));
	}

	@Nonnull
	@Override
	protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
			@Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
			@Nonnull BlockHitResult hit) {
		if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlockEntityBase tile) {
			// 1.21.1: block capabilities are looked up through the Capabilities API instead of a
			// getCapability override. useItemOn(ItemStack, BlockState, ...) replaced Forge's use(...).
			IFluidHandler tank = tile.getCapability(Capabilities.FluidHandler.BLOCK, null);
			if (tank != null) {
				FluidActionResult drained = FluidUtil.tryEmptyContainer(stack, tank, 1000, player, true);
				if (drained.isSuccess()) {
					tile.setDispatchable();
					if (!player.isCreative()) {
						player.setItemInHand(hand, drained.getResult());
					}
					return ItemInteractionResult.SUCCESS;
				}
			}
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	public int getLightBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
		return 0;
	}

	@Override
	public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter reader, @Nonnull BlockPos pos) {
		return true;
	}

	@Override
	public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
		return true;
	}

	@Nonnull
	@Override
	public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return SHAPE.getShape(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
	}

	static {
		SHAPE = new RotationShape(Shapes.or(ExtraBotanicalBlock.FRAME_SHAPE, box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0), box(0.0, 1.0, 0.0, 1.0, 15.0, 1.0), box(15.0, 1.0, 0.0, 16.0, 15.0, 1.0), box(0.0, 1.0, 15.0, 1.0, 15.0, 16.0), box(15.0, 1.0, 15.0, 16.0, 15.0, 16.0), box(0.0, 15.0, 1.0, 1.0, 16.0, 15.0), box(15.0, 15.0, 1.0, 16.0, 16.0, 15.0), box(0.0, 15.0, 0.0, 16.0, 16.0, 1.0), box(0.0, 15.0, 15.0, 16.0, 16.0, 16.0), box(12.0, 10.0, 4.0, 13.0, 13.0, 12.0), box(3.0, 1.0, 3.0, 13.0, 2.0, 13.0),
				box(5.0, 2.0, 5.0, 11.0, 9.0, 11.0), box(3.0, 9.0, 3.0, 13.0, 10.0, 13.0), box(3.0, 10.0, 12.0, 13.0, 13.0, 13.0), box(3.0, 10.0, 3.0, 13.0, 13.0, 4.0), box(3.0, 10.0, 4.0, 4.0, 13.0, 12.0)));
	}
}
