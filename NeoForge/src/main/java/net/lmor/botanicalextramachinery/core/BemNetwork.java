package net.lmor.botanicalextramachinery.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Re-creates the LibX block entity update network for BotaniaReforged using
 * the NeoForge payload API. {@code requestBE} asks the server for fresh BE
 * data, {@code updateBE} pushes the BE update tag to all tracking players.
 * This backs the LibX ThrottledPacket sync used by all machines.
 */
public final class BemNetwork {

    public static final BemNetwork INSTANCE = new BemNetwork();

    public record RequestBE(LevelDim dim, BlockPos pos) implements CustomPacketPayload {
        public static final Type<RequestBE> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("botanicalextramachinery", "request_be"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestBE> CODEC = StreamCodec.composite(
                LevelDim.CODEC, RequestBE::dim,
                BlockPos.STREAM_CODEC, RequestBE::pos,
                RequestBE::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UpdateBE(LevelDim dim, BlockPos pos, CompoundTag tag) implements CustomPacketPayload {
        public static final Type<UpdateBE> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("botanicalextramachinery", "update_be"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBE> CODEC = StreamCodec.composite(
                LevelDim.CODEC, UpdateBE::dim,
                BlockPos.STREAM_CODEC, UpdateBE::pos,
                net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG, UpdateBE::tag,
                UpdateBE::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LevelDim(ResourceKey<Level> key) {
        // 1.21.1: ByteBufCodecs.resourceKey(ResourceKey<Registry<T>>) was removed, and
        // ByteBufCodecs.registry(...) encodes the entry itself — a Level instance, which does
        // not exist client-side. Send the dimension id as a string and rebuild the key.
        // Written with StreamCodec.of rather than .map() because ByteBufCodecs.STRING_UTF8 is a
        // StreamCodec<ByteBuf, String> and map() would keep the wider buffer type.
        public static final StreamCodec<RegistryFriendlyByteBuf, LevelDim> CODEC = StreamCodec.of(
                (buf, dim) -> net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buf, dim.key().location().toString()),
                buf -> new LevelDim(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.decode(buf)))));
    }

    private BemNetwork() {}

    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestBE.TYPE, RequestBE.CODEC, this::handleRequest);
        registrar.playToClient(UpdateBE.TYPE, UpdateBE.CODEC, this::handleUpdate);
    }

    private void handleRequest(RequestBE payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Level level = player.serverLevel();
            if (level.dimension() == payload.dim().key()) {
                updateBE(level, payload.pos());
            }
        }
    }

    private void handleUpdate(UpdateBE payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            // Take the level from the context rather than Minecraft.getInstance().level:
            // this class is loaded on dedicated servers too, and Minecraft.level is typed
            // ClientLevel, so naming it here makes the whole mod fail to construct on a server.
            Player player = context.player();
            Level level = player != null ? player.level() : null;
            if (level != null && level.dimension() == payload.dim().key() && level.isClientSide) {
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be != null) {
                    be.handleUpdateTag(payload.tag(), level.registryAccess());
                }
            }
        });
    }

    /** Server side: called from the update queue once per tick per dirty BE. */
    public void updateBE(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be != null) {
                CompoundTag tag = be.getUpdateTag(serverLevel.registryAccess());
                PacketDistributor.sendToPlayersTrackingChunk(serverLevel, serverLevel.getChunkAt(pos).getPos(),
                        new UpdateBE(new LevelDim(serverLevel.dimension()), pos, tag));
            }
        }
    }

    /** Client side: ask the server to re-send the BE data. */
    public void requestBE(Level level, BlockPos pos) {
        if (level.isClientSide) {
            PacketDistributor.sendToServer(new RequestBE(new LevelDim(level.dimension()), pos));
        }
    }
}
