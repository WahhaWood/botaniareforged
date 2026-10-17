package com.jykito.voltany.client;

import com.jykito.voltany.block.entity.ManaGeneratorBlockEntity;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.api.BotaniaAPIClient;
import vazkii.botania.api.block.WandHUD;

public class ManaGeneratorWandHud implements WandHUD {
	private static final int COLOR = 0x96FF;
	private final ManaGeneratorBlockEntity be;

	public ManaGeneratorWandHud(ManaGeneratorBlockEntity be) {
		this.be = be;
	}

	@Override
	public void renderHUD(GuiGraphics gui, Window window, Font font, float partialTick) {
		String name = new ItemStack(this.be.getBlockState().getBlock()).getHoverName().getString();
		BotaniaAPIClient.INSTANCE.drawSimpleManaHUD(gui, window, font, COLOR, this.be.getMana(),
				this.be.getManaCapacity(), name);
	}
}
