package vazkii.botania.ae2.machine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.ae2.AppliedBotanics;

/** Client to server: a click in the ME machine GUI (one recipe on or off, or all of them). */
public record ToggleRecipePayload(int containerId, Action action, @Nullable ResourceLocation recipe) implements CustomPacketPayload {
	public static final Type<ToggleRecipePayload> TYPE = new Type<>(AppliedBotanics.id("toggle_recipe"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleRecipePayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.containerId);
				buf.writeEnum(payload.action);
				buf.writeBoolean(payload.recipe != null);
				if (payload.recipe != null) {
					buf.writeResourceLocation(payload.recipe);
				}
			},
			buf -> new ToggleRecipePayload(buf.readVarInt(), buf.readEnum(Action.class), buf.readBoolean() ? buf.readResourceLocation() : null));

	public enum Action {
		TOGGLE,
		ENABLE_ALL,
		DISABLE_ALL
	}

	public static void handle(ToggleRecipePayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			// only the menu the player really has open, and only while it is still valid
			if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MachineMenu menu
					&& menu.containerId == payload.containerId && menu.stillValid(player)) {
				menu.entity.applyToggle(payload.action, payload.recipe);
			}
		});
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
