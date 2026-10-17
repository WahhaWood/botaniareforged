package net.lmor.botanicalextramachinery.blocks.blockMachines.mechanicalManaInfuser;

import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalBlock;
import net.lmor.botanicalextramachinery.blocks.containers.mechanicalManaInfuser.ContainerManaInfuserUpgraded;
import net.lmor.botanicalextramachinery.blocks.tesr.mechanicalManaInfuser.RenderManaInfuserUpgraded;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalManaInfuser.BlockEntityManaInfuserUpgraded;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.moddingx.libx.block.RotationShape;
import org.moddingx.libx.menu.type.AdvancedMenuType;
import org.moddingx.libx.mod.ModX;
import org.moddingx.libx.registration.SetupContext;

import javax.annotation.Nonnull;

public class BlockManaInfuserUpgraded extends ExtraBotanicalBlock<BlockEntityManaInfuserUpgraded, ContainerManaInfuserUpgraded> {
	public static final RotationShape SHAPE;

	public BlockManaInfuserUpgraded(ModX mod, Class<BlockEntityManaInfuserUpgraded> teClass, AdvancedMenuType<ContainerManaInfuserUpgraded, ? super BlockPos> menu) {
		super(mod, teClass, menu, false, true);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void setupClient(SetupContext ctx) {
		super.setupClient(ctx);
		BlockEntityRenderers.register(this.getBlockEntityType(), (context) -> {
			return new RenderManaInfuserUpgraded();
		});
	}

	@Nonnull
	@Override
	public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return SHAPE.getShape((Direction) state.getValue(BlockStateProperties.HORIZONTAL_FACING));
	}

	static {
		SHAPE = new RotationShape(Shapes.or(ExtraBotanicalBlock.FRAME_SHAPE, box(2.6, 0.0, 2.6, 13.4, 4.6, 13.4), box(6.2, 0.0, 6.2, 9.8, 5.3, 9.8)));
	}
}
