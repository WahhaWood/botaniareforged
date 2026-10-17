package net.lmor.botanicalextramachinery.blocks.blockMachines.mechanicalRunicAltar;

import net.lmor.botanicalextramachinery.blocks.base.ExtraBotanicalBlock;
import net.lmor.botanicalextramachinery.blocks.containers.mechanicalRunicAltar.ContainerRunicAltarUltimate;
import net.lmor.botanicalextramachinery.blocks.tesr.mechanicalRunicAltar.RendererRunicAltarUltimate;
import net.lmor.botanicalextramachinery.blocks.tiles.mechanicalRunicAltar.BlockEntityRunicAltarUltimate;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
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

public class BlockRunicAltarUltimate extends ExtraBotanicalBlock<BlockEntityRunicAltarUltimate, ContainerRunicAltarUltimate> {
	public static final RotationShape SHAPE;

	public BlockRunicAltarUltimate(ModX mod, Class<BlockEntityRunicAltarUltimate> teClass, AdvancedMenuType<ContainerRunicAltarUltimate, ? super BlockPos> menu) {
		super(mod, teClass, menu, false, true);
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public void setupClient(SetupContext ctx) {
		super.setupClient(ctx);
		BlockEntityRenderers.register(this.getBlockEntityType(), (context) -> {
			return new RendererRunicAltarUltimate();
		});
	}

	@Nonnull
	@Override
	public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
		return SHAPE.getShape(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
	}

	static {
		SHAPE = new RotationShape(Shapes.or(ExtraBotanicalBlock.FRAME_SHAPE, box(2.0, 5.0, 2.0, 14.0, 9.0, 14.0), box(6.0, 3.0, 6.0, 10.0, 5.0, 10.0), box(4.0, 1.0, 4.0, 12.0, 3.0, 12.0)));
	}
}
