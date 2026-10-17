package net.lmor.botanicalextramachinery.blocks.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.moddingx.libx.base.tile.MenuBlockBE;
import org.moddingx.libx.menu.BlockEntityMenu;
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
public class ExtraBotanicalBlock<T extends ExtraBotanicalTile, C extends BlockEntityMenu<T>> extends MenuBlockBE<T, C> {
	public static final VoxelShape FRAME_SHAPE = Shapes.or(box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0), box(0.0, 0.0, 0.0, 1.0, 16.0, 1.0), box(15.0, 0.0, 0.0, 16.0, 16.0, 1.0), box(0.0, 0.0, 15.0, 1.0, 16.0, 16.0), box(15.0, 0.0, 15.0, 16.0, 16.0, 16.0), box(0.0, 15.0, 0.0, 1.0, 16.0, 16.0), box(0.0, 15.0, 0.0, 16.0, 16.0, 1.0), box(15.0, 15.0, 0.0, 16.0, 16.0, 16.0), box(0.0, 15.0, 15.0, 16.0, 16.0, 16.0));
	public final boolean fullCube;
	public final boolean specialRender;

	public ExtraBotanicalBlock(ModX mod, Class<T> teClass, AdvancedMenuType<C, ? super BlockPos> menu, boolean fullCube, boolean specialRender) {
		super(mod, teClass, menu, fullCube ? Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 10.0F) : Properties.ofFullCopy(Blocks.STONE).strength(2.0F, 10.0F).dynamicShape().noOcclusion().forceSolidOn(), new Item.Properties());
		this.fullCube = fullCube;
		this.specialRender = specialRender;
	}

	@Override
	public void setupClient(SetupContext ctx) {
		if (this.specialRender) {
			ItemStackRenderer.addRenderBlock(this.getBlockEntityType(), true);
		}
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void registerClientAdditional(RegistrationContext ctx, EntryCollector builder) {
		super.registerClientAdditional(ctx, builder);
		builder.register(null, new ClientExtensionInfo.Item(ItemStackRenderer.createProperties()));
	}

	public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
		ExtraBotanicalTile blockEntity = (ExtraBotanicalTile) level.getBlockEntity(pos);
		if (blockEntity == null) {
			return null;
		} else {
			// 1.21.1: block entity data is stored in the BLOCK_ENTITY_DATA component
			ItemStack stack = new ItemStack(this);
			if (level instanceof Level) {
				CompoundTag tag = blockEntity.saveCustomOnly(((Level) level).registryAccess());
				stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
			}
			return stack;
		}
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
	public boolean hasAnalogOutputSignal(@Nonnull BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos) {
		return this.getBlockEntity(level, pos).getComparatorOutput();
	}

	@Override
	public int getLightBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
		return !this.fullCube ? 0 : super.getLightBlock(state, level, pos);
	}

	@Override
	public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter reader, @Nonnull BlockPos pos) {
		return !this.fullCube;
	}

	@Override
	public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
		return !this.fullCube;
	}

	@Override
	@Nonnull
	public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return !this.fullCube ? FRAME_SHAPE : super.getShape(state, level, pos, context);
	}

	@Override
	protected boolean shouldDropInventory(Level level, BlockPos pos, BlockState state) {
		return true;
	}
}
