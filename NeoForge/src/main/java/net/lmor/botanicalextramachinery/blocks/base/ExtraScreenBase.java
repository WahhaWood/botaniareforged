package net.lmor.botanicalextramachinery.blocks.base;

import com.mojang.blaze3d.systems.RenderSystem;

import net.lmor.botanicalextramachinery.ModItems;
import net.lmor.botanicalextramachinery.blocks.screens.uitlScreen.ScreenAddInventory;
import net.lmor.botanicalextramachinery.blocks.tiles.BlockEntityGreenhouse;
import net.lmor.botanicalextramachinery.gui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.moddingx.libx.menu.BlockEntityMenu;

import javax.annotation.Nonnull;

public abstract class ExtraScreenBase<X extends BlockEntityMenu<?>> extends AbstractContainerScreen<X> {
	public ExtraScreenBase(X menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	protected void init() {
		super.init();

		this.leftPos = (width - this.imageWidth) / 2;
		// Center the entire GUI (machine + appended player inventory) once during init.
		// This avoids changing topPos every frame, which can confuse overlay mods like EMI.
		int effectiveHeight = this.imageHeight;
		ScreenAddInventory addInv = getAddInventory();
		if (addInv != null) {
			effectiveHeight = addInv.getHeightGuiMax(this.imageHeight);
		}
		this.topPos = (height - effectiveHeight) / 2;

		this.inventoryLabelY = -9999;
		this.titleLabelY = -9999;
	}

	@Override
	public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	public void drawDefaultGuiBackgroundLayer(GuiGraphics guiGraphics, ResourceLocation screenLocation,
			ScreenAddInventory addInventory, int[] storageAll, int[] maxStorageAll,
			Bars bars, SlotInfo slotInfo) {
		if (bars != null) {
			bars.setGuiCoord(this.leftPos, this.topPos);
		}
		if (slotInfo != null) {
			slotInfo.setGuiCoord(this.leftPos, this.topPos);
		}

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		guiGraphics.blit(screenLocation, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

		guiGraphics.blit(addInventory.getInventoryScreen().getResourceLocation(),
				addInventory.getXBlitScreen(this.leftPos, addInventory.getWidthGuiMax(this.imageWidth)),
				addInventory.getYBlitScreen(this.topPos, addInventory.getHeightGuiMax(this.imageHeight)),
				0, 0, addInventory.getInventoryScreen().getWidth(), addInventory.getInventoryScreen().getHeight());

		if (bars != null && storageAll.length != 0 && maxStorageAll.length != 0) {
			bars.draw(guiGraphics, storageAll, maxStorageAll, checkUpgradeInfinityMana(slotInfo));
		}
	}

	// Subclasses can override to provide their ScreenAddInventory so the
	// base class can center the full GUI correctly during init.
	protected ScreenAddInventory getAddInventory() {
		return null;
	}

	public boolean checkUpgradeInfinityMana(SlotInfo slotInfo) {
		if (slotInfo == null) {
			return false;
		}
		BlockEntity blockEntity = ((BlockEntityMenu<?>) this.menu).getBlockEntity();

		assert blockEntity != null;
		if (blockEntity instanceof ExtraBotanicalTile && !(blockEntity instanceof BlockEntityGreenhouse)) {
			for (Integer slot : slotInfo.getSlotUpgrade()) {
				if (((ExtraBotanicalTile) blockEntity).getInventory().getStackInSlot(slot).getItem() == ModItems.catalystManaInfinity ||
						((ExtraBotanicalTile) blockEntity).getInventory().getStackInSlot(slot).getItem() == ModItems.catalystWaterInfinity) {
					return true;
				}
			}
		}
		return false;
	}
}
