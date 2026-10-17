package vazkii.botania.ae2.integration.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

import vazkii.botania.client.integration.emi.BotaniaEmiPlugin;

import vazkii.botania.ae2.ABBlocks;

public class ABEMIPlugin implements EmiPlugin {
	@Override
	public void register(EmiRegistry registry) {
		registry.addWorkstation(BotaniaEmiPlugin.MANA_INFUSION, EmiStack.of(ABBlocks.FLUIX_MANA_POOL.get()));
	}
}
