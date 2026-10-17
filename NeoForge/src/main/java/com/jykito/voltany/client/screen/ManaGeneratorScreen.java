package com.jykito.voltany.client.screen;

import com.jykito.voltany.menu.ManaGeneratorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ManaGeneratorScreen extends AbstractContainerScreen<ManaGeneratorMenu> {
	private static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("voltany",
			"textures/gui/mana_generator.png");
	private static final ResourceLocation ENERGY_FILL = ResourceLocation.fromNamespaceAndPath("voltany",
			"textures/gui/energybot_fill_6x_anim.png");
	private static final ResourceLocation MANA_FILL = ResourceLocation.fromNamespaceAndPath("voltany",
			"textures/gui/mana_fill_anim_6x.png");
	private static final int TEX_W = 256;
	private static final int TEX_H = 315;
	private static final int FILL_TEX_W = 55;
	private static final int FILL_TEX_H = 36;
	private static final int BAR_W = 55;
	private static final int BAR_H = 6;
	private static final int ENERGY_X = 101;
	private static final int ENERGY_Y = 34;
	private static final int MANA_X = 101;
	private static final int MANA_Y = 104;
	private static final int FRAMES = 6;
	private static final int FRAME_TICKS = 12;

	private int fillTick = 0;
	private int fillFrame = 0;

	public ManaGeneratorScreen(ManaGeneratorMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = TEX_W;
		this.imageHeight = 224;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		guiGraphics.blit(BG, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, TEX_W, TEX_H);

		if (++this.fillTick >= FRAME_TICKS) {
			this.fillTick = 0;
			this.fillFrame = (this.fillFrame + 1) % FRAMES;
		}
		int vOffset = this.fillFrame * BAR_H;

		int energyFill = (int) (BAR_W * frac(this.menu.getEnergy(), this.menu.getEnergyCapacity()));
		if (energyFill > 0) {
			guiGraphics.blit(ENERGY_FILL, this.leftPos + ENERGY_X, this.topPos + ENERGY_Y, 0.0F, vOffset,
					energyFill, BAR_H, FILL_TEX_W, FILL_TEX_H);
		}

		int manaFill = (int) (BAR_W * frac(this.menu.getMana(), this.menu.getManaCapacity()));
		if (manaFill > 0) {
			guiGraphics.blit(MANA_FILL, this.leftPos + MANA_X, this.topPos + MANA_Y, 0.0F, vOffset,
					manaFill, BAR_H, FILL_TEX_W, FILL_TEX_H);
		}
	}

	private static float frac(int value, int max) {
		return max <= 0 ? 0.0F : Math.min(1.0F, (float) value / (float) max);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		// no labels, the background art carries everything
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
}
