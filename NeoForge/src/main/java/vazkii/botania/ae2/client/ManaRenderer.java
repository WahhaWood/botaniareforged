package vazkii.botania.ae2.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import org.joml.Matrix4f;

import vazkii.botania.api.BotaniaAPI;

import java.util.List;
import java.util.Objects;

import vazkii.botania.ae2.ae2.ManaKey;
import vazkii.botania.ae2.ae2.ManaKeyType;
import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.client.gui.style.Blitter;

public class ManaRenderer implements AEKeyRenderHandler<ManaKey> {
	private TextureAtlasSprite waterSprite;

	public static void initialize(IEventBus bus) {
		bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> AEKeyRendering.register(ManaKeyType.TYPE, ManaKey.class, new ManaRenderer())));
	}

	private void lazyInitSprite() {
		if (this.waterSprite == null) {
			this.waterSprite = Objects.requireNonNull(
					Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(BotaniaAPI.botaniaRL("block/mana_water"))
			);
		}
	}

	@Override
	public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, ManaKey stack) {
		this.lazyInitSprite();
		Blitter.sprite(this.waterSprite).blending(false).dest(x, y, 16, 16).blit(guiGraphics);
	}

	@Override
	public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, ManaKey what, float scale, int combinedLight, Level level) {
		this.lazyInitSprite();
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.0F, 0.01F);
		VertexConsumer buffer = buffers.getBuffer(RenderType.solid());
		scale -= 0.05F;
		float x0 = -scale / 2.0F;
		float y0 = scale / 2.0F;
		float x1 = scale / 2.0F;
		float y1 = -scale / 2.0F;
		Matrix4f transform = poseStack.last().pose();
		buffer.addVertex(transform, x0, y1, 0.0F)
				.setColor(-1)
				.setUv(this.waterSprite.getU0(), this.waterSprite.getV1())
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(combinedLight)
				.setNormal(0.0F, 0.0F, 1.0F);
		buffer.addVertex(transform, x1, y1, 0.0F)
				.setColor(-1)
				.setUv(this.waterSprite.getU1(), this.waterSprite.getV1())
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(combinedLight)
				.setNormal(0.0F, 0.0F, 1.0F);
		buffer.addVertex(transform, x1, y0, 0.0F)
				.setColor(-1)
				.setUv(this.waterSprite.getU1(), this.waterSprite.getV0())
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(combinedLight)
				.setNormal(0.0F, 0.0F, 1.0F);
		buffer.addVertex(transform, x0, y0, 0.0F)
				.setColor(-1)
				.setUv(this.waterSprite.getU0(), this.waterSprite.getV0())
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(combinedLight)
				.setNormal(0.0F, 0.0F, 1.0F);
		poseStack.popPose();
	}

	@Override
	public Component getDisplayName(ManaKey stack) {
		return ManaKeyType.MANA;
	}

	@Override
	public List<Component> getTooltip(ManaKey stack) {
		return List.of(this.getDisplayName(stack));
	}
}
