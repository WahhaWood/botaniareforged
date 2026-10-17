package vazkii.botania.ae2.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import vazkii.botania.ae2.AppliedBotanics;
import appeng.api.stacks.GenericStack;

/**
 * ME machine GUI: every recipe of the machine as an icon (pages of 9x5, search by name), a click
 * switches the recipe on or off for the ME network; switched-off recipes are dimmed.
 *
 * <p>Inside the window there is no text that grows with the language: the title is cut to fit, the
 * search box has its own row, "all on/off" are icon tabs on the right and the status lives in the
 * black panel on the left, as wide as its text, label and value on separate lines.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
	private static final ResourceLocation TEXTURE = AppliedBotanics.id("textures/gui/machine.png");
	private static final int COLS = 9;
	private static final int ROWS = 5;
	private static final int PAGE_SIZE = COLS * ROWS;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 32;
	private static final int BOTTOM_Y = 124;
	/** Check and cross icons of the tabs, 16x16 each, in the spare part of the texture. */
	private static final int ICON_U = 200;
	private static final int PANEL_LABEL = 0xA0A0A0;
	private static final int PANEL_VALUE = 0x00B61E;
	private static final int PANEL_BAD = 0xE04040;

	private EditBox search;
	private int page;
	private List<MachineRecipe> filtered = List.of();
	@Nullable
	private Map<ResourceLocation, MachineRecipe> filteredFrom;
	private String filteredQuery = "";

	public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 145;
	}

	@Override
	protected void setInitialFocus() {
		this.setFocused(null);
	}

	@Override
	protected void init() {
		super.init();
		Component hint = Component.translatable("gui.botania.machine.search");
		search = new EditBox(font, leftPos + 8, topPos + 17, 160, 12, hint);
		search.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
		search.setMaxLength(64);
		search.setValue(filteredQuery);
		search.setResponder(value -> page = 0);
		addRenderableWidget(search);
		addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1)).bounds(leftPos + 7, topPos + BOTTOM_Y, 20, 14).build());
		addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1)).bounds(leftPos + 149, topPos + BOTTOM_Y, 20, 14).build());
		addTab(0, "gui.botania.machine.enable_all", ToggleRecipePayload.Action.ENABLE_ALL);
		addTab(1, "gui.botania.machine.disable_all", ToggleRecipePayload.Action.DISABLE_ALL);
	}

	/** Icon tab stuck to the right edge of the window; the icon is drawn in render. */
	private void addTab(int index, String tooltip, ToggleRecipePayload.Action action) {
		addRenderableWidget(Button.builder(Component.empty(), button -> send(action, null))
				.bounds(leftPos + imageWidth - 1, topPos + 17 + index * 22, 20, 20)
				.tooltip(Tooltip.create(Component.translatable(tooltip)))
				.build());
	}

	private List<MachineRecipe> visibleRecipes() {
		Map<ResourceLocation, MachineRecipe> all = menu.entity.recipes();
		String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		if (all != filteredFrom || !query.equals(filteredQuery)) {
			List<MachineRecipe> list = new ArrayList<>();
			for (MachineRecipe recipe : all.values()) {
				if (matches(recipe, query)) {
					list.add(recipe);
				}
			}
			filtered = list;
			filteredFrom = all;
			filteredQuery = query;
		}

		int pages = pageCount();
		if (page >= pages) {
			page = pages - 1;
		}
		return filtered;
	}

	/** Search over output names, input names and the recipe id. */
	private static boolean matches(MachineRecipe recipe, String query) {
		if (query.isEmpty() || recipe.id().toString().contains(query)) {
			return true;
		}
		for (ItemStack output : recipe.outputs()) {
			if (output.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		for (MachineRecipe.Part part : recipe.parts()) {
			for (ItemStack input : part.ingredient().getItems()) {
				if (input.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
					return true;
				}
			}
		}
		return false;
	}

	private int pageCount() {
		return Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void turnPage(int delta) {
		visibleRecipes();
		page = Math.floorMod(page + delta, pageCount());
	}

	private void send(ToggleRecipePayload.Action action, @Nullable ResourceLocation recipe) {
		PacketDistributor.sendToServer(new ToggleRecipePayload(menu.containerId, action, recipe));
	}

	private int cellAt(double mouseX, double mouseY) {
		int col = (int) Math.floor((mouseX - leftPos - GRID_X + 1) / 18.0);
		int row = (int) Math.floor((mouseY - topPos - GRID_Y + 1) / 18.0);
		return col < 0 || col >= COLS || row < 0 || row >= ROWS ? -1 : row * COLS + col;
	}

	@Nullable
	private MachineRecipe recipeAt(double mouseX, double mouseY) {
		int cell = cellAt(mouseX, mouseY);
		if (cell < 0) {
			return null;
		}
		List<MachineRecipe> recipes = visibleRecipes();
		int index = page * PAGE_SIZE + cell;
		return index < recipes.size() ? recipes.get(index) : null;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
		List<MachineRecipe> recipes = visibleRecipes();
		int start = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && start + i < recipes.size(); i++) {
			MachineRecipe recipe = recipes.get(start + i);
			int x = leftPos + GRID_X + (i % COLS) * 18;
			int y = topPos + GRID_Y + (i / COLS) * 18;
			graphics.renderItem(recipe.icon(), x, y);
			graphics.renderItemDecorations(font, recipe.icon(), x, y);
			if (!menu.entity.isEnabled(recipe.id())) {
				// above the stack size (drawn at z 200), so the digits are dimmed too
				graphics.fill(x, y, x + 16, y + 16, 250, 0xB0303030);
			}
		}

		int enabled = 0;
		Map<ResourceLocation, MachineRecipe> all = menu.entity.recipes();
		for (ResourceLocation id : all.keySet()) {
			if (menu.entity.isEnabled(id)) {
				enabled++;
			}
		}

		renderStatusPanel(graphics, font, leftPos, topPos, List.of(
				Component.translatable("gui.botania.machine.network"),
				Component.translatable(menu.isOnline() ? "gui.botania.machine.online" : "gui.botania.machine.offline"),
				Component.translatable("gui.botania.machine.operations"),
				Component.literal(menu.lastOperations() + " / " + MachineBlockEntity.OPERATIONS_PER_TICK),
				Component.translatable("gui.botania.machine.queue"),
				Component.literal(Integer.toString(menu.queuedOperations())),
				Component.translatable("gui.botania.machine.recipes"),
				Component.literal(enabled + " / " + all.size())), menu.isOnline());
	}

	/**
	 * Black panel left of the window: {@code lines} go label, value, label, value... A label and its
	 * number on separate lines keep the panel narrow enough to stay on a small screen in any language.
	 */
	static void renderStatusPanel(GuiGraphics graphics, Font font, int leftPos, int topPos, List<Component> lines, boolean online) {
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}

		width += 9;
		int pairs = (lines.size() + 1) / 2;
		int x = leftPos - width - 3;
		graphics.fill(x, topPos, x + width, topPos + lines.size() * 9 + (pairs - 1) * 3 + 8, 0xFF000000);
		int y = topPos + 4;
		for (int i = 0; i < lines.size(); i++) {
			int color = i % 2 == 0 ? PANEL_LABEL : (i == 1 && !online ? PANEL_BAD : PANEL_VALUE);
			graphics.drawString(font, lines.get(i), x + 4, y, color);
			y += i % 2 == 0 ? 9 : 12;
		}
	}

	/** The title, cut with an ellipsis when a long translation would not fit the window. */
	static String fitTitle(Font font, Component title, int width) {
		String text = title.getString();
		return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, fitTitle(font, title, imageWidth - 16), 8, 6, 0x404040, false);
		String pages = (page + 1) + " / " + pageCount();
		graphics.drawString(font, pages, (imageWidth - font.width(pages)) / 2, BOTTOM_Y + 3, 0x404040, false);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		for (int i = 0; i < 2; i++) {
			graphics.blit(TEXTURE, leftPos + imageWidth + 1, topPos + 19 + i * 22, ICON_U + i * 16, 0, 16, 16);
		}

		MachineRecipe hovered = recipeAt(mouseX, mouseY);
		if (hovered != null) {
			int cell = cellAt(mouseX, mouseY);
			int x = leftPos + GRID_X + (cell % COLS) * 18;
			int y = topPos + GRID_Y + (cell / COLS) * 18;
			graphics.fill(x, y, x + 16, y + 16, 300, 0x80FFFFFF);
			graphics.renderComponentTooltip(font, tooltip(hovered), mouseX, mouseY);
		} else {
			renderTooltip(graphics, mouseX, mouseY);
		}
	}

	private List<Component> tooltip(MachineRecipe recipe) {
		List<Component> lines = new ArrayList<>();
		for (ItemStack output : recipe.outputs()) {
			lines.add(Component.literal(output.getCount() + "× ").append(output.getHoverName()));
		}

		for (MachineRecipe.Part part : recipe.parts()) {
			ItemStack[] items = part.ingredient().getItems();
			Component input = Component.literal(part.count() + "× ").append(items[0].getHoverName());
			if (items.length > 1) {
				input = Component.translatable("gui.botania.machine.input_variants", input, items.length - 1);
			}
			if (part.returned()) {
				input = Component.translatable("gui.botania.machine.input_returned", input);
			}
			lines.add(Component.translatable("gui.botania.machine.input", input).withStyle(ChatFormatting.GRAY));
		}

		for (GenericStack cost : recipe.costs()) {
			lines.add(Component.empty().append(cost.what().getDisplayName()).append(": " + cost.amount()).withStyle(ChatFormatting.AQUA));
		}

		if (recipe.catalyst() != null) {
			lines.add(Component.translatable("gui.botania.machine.catalyst").withStyle(ChatFormatting.GOLD));
		}

		lines.add(menu.entity.isEnabled(recipe.id())
				? Component.translatable("gui.botania.machine.enabled").withStyle(ChatFormatting.GREEN)
				: Component.translatable("gui.botania.machine.disabled").withStyle(ChatFormatting.RED));
		return lines;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		MachineRecipe recipe = recipeAt(mouseX, mouseY);
		if (recipe != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			send(ToggleRecipePayload.Action.TOGGLE, recipe.id());
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			turnPage(scrollY > 0 ? -1 : 1);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// typing in the search box must not close the screen on the inventory key
		if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput();
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
