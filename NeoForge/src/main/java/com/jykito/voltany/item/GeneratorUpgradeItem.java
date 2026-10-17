package com.jykito.voltany.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

public class GeneratorUpgradeItem extends Item {
	private final Type type;

	public GeneratorUpgradeItem(Type type, Properties props) {
		super(props);
		this.type = type;
	}

	public Type getType() {
		return this.type;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.voltany.upgrade." + this.type.name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		super.appendHoverText(stack, context, tooltip, flag);
	}

	public enum Type {
		OVERCLOCK_1,
		OVERCLOCK_2,
		OVERCLOCK_3,
		DISPERSION_1,
		DISPERSION_2,
		DISPERSION_3,
		EFFICIENCY,
		SPARK;
	}
}
