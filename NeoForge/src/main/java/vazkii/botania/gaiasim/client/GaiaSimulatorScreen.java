package vazkii.botania.gaiasim.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.gaiasim.GaiaSimulatorBlockEntity;
import vazkii.botania.gaiasim.GaiaSimulatorMenu;

import java.util.Locale;

/**
 * Gaia Simulator GUI, drawn without a texture: three bars (mana, energy, fight progress) with their numbers in
 * tooltips, a status line, the loot output and the player inventory.
 */
public class GaiaSimulatorScreen extends AbstractContainerScreen<GaiaSimulatorMenu> {
	private static final int BAR_X = 8;
	private static final int BAR_WIDTH = 160;
	private static final int BAR_HEIGHT = 6;
	private static final int MANA_Y = 18;
	private static final int ENERGY_Y = 28;
	private static final int PROGRESS_Y = 38;
	private static final int STATUS_Y = 48;

	public GaiaSimulatorScreen(GaiaSimulatorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		imageWidth = 176;
		imageHeight = 196;
		inventoryLabelY = GaiaSimulatorMenu.INVENTORY_Y - 11;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
		Component bar = barTooltip(mouseX - leftPos, mouseY - topPos);
		if (bar != null) {
			graphics.renderTooltip(font, bar, mouseX, mouseY);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFC6C6C6);
		graphics.fill(x + 1, y + 1, x + imageWidth - 2, y + 2, 0xFFFFFFFF);
		graphics.fill(x + 1, y + 1, x + 2, y + imageHeight - 2, 0xFFFFFFFF);
		graphics.fill(x + 2, y + imageHeight - 2, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
		graphics.fill(x + imageWidth - 2, y + 2, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);

		bar(graphics, MANA_Y, menu.getMana(), GaiaSimulatorBlockEntity.MANA_PER_FIGHT, 0xFF1E9BE6);
		bar(graphics, ENERGY_Y, menu.getEnergy(), GaiaSimulatorBlockEntity.ENERGY_CAPACITY, 0xFFD23C32);
		bar(graphics, PROGRESS_Y, menu.getProgress(), GaiaSimulatorBlockEntity.FIGHT_TICKS, 0xFFD96CD0);

		for (int slot = 0; slot < GaiaSimulatorBlockEntity.OUTPUT_SLOTS; slot++) {
			slotFrame(graphics, GaiaSimulatorMenu.OUTPUT_X + slot % 9 * 18, GaiaSimulatorMenu.OUTPUT_Y + slot / 9 * 18);
		}
		for (int slot = 0; slot < 36; slot++) {
			int row = slot / 9;
			slotFrame(graphics, 8 + slot % 9 * 18, GaiaSimulatorMenu.INVENTORY_Y + (row == 3 ? 58 : row * 18));
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		GaiaSimulatorBlockEntity.Status status = menu.getStatus();
		Component text = Component.translatable("botania.gaia_simulator.status." + status.name().toLowerCase(Locale.ROOT));
		int color = switch (status) {
			case FIGHTING -> 0x7A1F7A;
			case NO_STRUCTURE, OUTPUT_FULL -> 0xA02020;
			default -> 0x404040;
		};
		graphics.drawString(font, text, (imageWidth - font.width(text)) / 2, STATUS_Y, color, false);
	}

	private void bar(GuiGraphics graphics, int barY, int value, int max, int color) {
		int x = leftPos + BAR_X;
		int y = topPos + barY;
		graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xFF373737);
		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF1C1C1C);
		int filled = (int) ((long) BAR_WIDTH * Math.clamp(value, 0, max) / max);
		if (filled == 0 && value > 0) {
			filled = 1;
		}
		graphics.fill(x, y, x + filled, y + BAR_HEIGHT, color);
	}

	private void slotFrame(GuiGraphics graphics, int slotX, int slotY) {
		int x = leftPos + slotX - 1;
		int y = topPos + slotY - 1;
		graphics.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
		graphics.fill(x, y, x + 17, y + 17, 0xFF373737);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
	}

	@Nullable
	private Component barTooltip(int x, int y) {
		if (x < BAR_X - 1 || x > BAR_X + BAR_WIDTH) {
			return null;
		}
		if (y >= MANA_Y - 1 && y <= MANA_Y + BAR_HEIGHT) {
			return Component.translatable("botania.gaia_simulator.mana",
					format(menu.getMana()), format(GaiaSimulatorBlockEntity.MANA_PER_FIGHT));
		}
		if (y >= ENERGY_Y - 1 && y <= ENERGY_Y + BAR_HEIGHT) {
			return Component.translatable("botania.gaia_simulator.energy",
					format(menu.getEnergy()), format(GaiaSimulatorBlockEntity.ENERGY_CAPACITY),
					format(GaiaSimulatorBlockEntity.ENERGY_PER_TICK));
		}
		if (y >= PROGRESS_Y - 1 && y <= PROGRESS_Y + BAR_HEIGHT) {
			return Component.translatable("botania.gaia_simulator.progress",
					time(menu.getProgress()), time(GaiaSimulatorBlockEntity.FIGHT_TICKS));
		}
		return null;
	}

	private static String format(int value) {
		return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
	}

	private static String time(int ticks) {
		int seconds = ticks / 20;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}
}
