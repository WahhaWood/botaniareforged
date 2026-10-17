package org.moddingx.libx.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.moddingx.libx.menu.type.AdvancedMenuFactory;
import org.moddingx.libx.menu.type.AdvancedMenuType;

import javax.annotation.Nullable;

/**
 * A {@link BlockMenu} for blocks with block entities.
 */
public class BlockEntityMenu<T extends BlockEntity> extends BlockMenu {

    /**
     * Re-created LibX 5 style factory for BotaniaReforged: menu types whose
     * payload is the position of the block entity. The factory matches the
     * conventional LibX container constructor order
     * {@code (type, windowId, level, pos, inventory, player)}.
     */
    public static <T extends BlockEntity, C extends BlockEntityMenu<T>> AdvancedMenuType<C, BlockPos> createMenuType(MenuFactory<T, C> factory) {
        return AdvancedMenuType.create(
                (menuType, windowId, level, pos, player, inventory) -> factory.create(menuType, windowId, level, pos, inventory, player),
                (StreamCodec<? super RegistryFriendlyByteBuf, BlockPos>) BlockPos.STREAM_CODEC);
    }

    @FunctionalInterface
    public interface MenuFactory<T extends BlockEntity, C extends BlockEntityMenu<T>> {
        C create(MenuType<C> menuType, int windowId, Level level, BlockPos pos, Inventory inventory, Player player);
    }

    protected final T blockEntity;

    public BlockEntityMenu(@Nullable MenuType<? extends BlockEntityMenu<?>> type, int windowId, Level level, BlockPos pos, Inventory inventory, Player player, int firstOutputSlot, int firstInventorySlot) {
        super(type, windowId, level, pos, inventory, player, firstOutputSlot, firstInventorySlot);
        //noinspection unchecked
        this.blockEntity = (T) level.getBlockEntity(pos);
    }

    public T getBlockEntity() {
        return this.blockEntity;
    }
}
