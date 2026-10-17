package net.lmor.botanicalextramachinery.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.lmor.botanicalextramachinery.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

@EmiEntrypoint
public class emi implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        // Botania categories — only add if present
        // Use Botania's category constants directly
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.MANA_INFUSION, EmiStack.of(new ItemStack(ModBlocks.baseManaPool)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.MANA_INFUSION, EmiStack.of(new ItemStack(ModBlocks.upgradedManaPool)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.MANA_INFUSION, EmiStack.of(new ItemStack(ModBlocks.advancedManaPool)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.MANA_INFUSION, EmiStack.of(new ItemStack(ModBlocks.ultimateManaPool)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.RUNIC_ALTAR, EmiStack.of(new ItemStack(ModBlocks.baseRunicAltar)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.RUNIC_ALTAR, EmiStack.of(new ItemStack(ModBlocks.upgradedRunicAltar)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.RUNIC_ALTAR, EmiStack.of(new ItemStack(ModBlocks.advancedRunicAltar)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.RUNIC_ALTAR, EmiStack.of(new ItemStack(ModBlocks.ultimateRunicAltar)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PETAL_APOTHECARY, EmiStack.of(new ItemStack(ModBlocks.baseApothecary)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PETAL_APOTHECARY, EmiStack.of(new ItemStack(ModBlocks.upgradedApothecary)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PETAL_APOTHECARY, EmiStack.of(new ItemStack(ModBlocks.advancedApothecary)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PETAL_APOTHECARY, EmiStack.of(new ItemStack(ModBlocks.ultimateApothecary)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.TERRESTRIAL_AGGLOMERATION, EmiStack.of(new ItemStack(ModBlocks.baseIndustrialAgglomerationFactory)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.TERRESTRIAL_AGGLOMERATION, EmiStack.of(new ItemStack(ModBlocks.upgradedIndustrialAgglomerationFactory)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.TERRESTRIAL_AGGLOMERATION, EmiStack.of(new ItemStack(ModBlocks.advancedIndustrialAgglomerationFactory)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.TERRESTRIAL_AGGLOMERATION, EmiStack.of(new ItemStack(ModBlocks.ultimateIndustrialAgglomerationFactory)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ELVEN_TRADE, EmiStack.of(new ItemStack(ModBlocks.baseAlfheimMarket)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ELVEN_TRADE, EmiStack.of(new ItemStack(ModBlocks.upgradedAlfheimMarket)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ELVEN_TRADE, EmiStack.of(new ItemStack(ModBlocks.advancedAlfheimMarket)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ELVEN_TRADE, EmiStack.of(new ItemStack(ModBlocks.ultimateAlfheimMarket)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PURE_DAISY, EmiStack.of(new ItemStack(ModBlocks.baseDaisy)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PURE_DAISY, EmiStack.of(new ItemStack(ModBlocks.upgradedDaisy)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PURE_DAISY, EmiStack.of(new ItemStack(ModBlocks.advancedDaisy)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.PURE_DAISY, EmiStack.of(new ItemStack(ModBlocks.ultimateDaisy)));

        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ORECHID, EmiStack.of(new ItemStack(ModBlocks.baseOrechid)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ORECHID, EmiStack.of(new ItemStack(ModBlocks.upgradedOrechid)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ORECHID, EmiStack.of(new ItemStack(ModBlocks.advancedOrechid)));
        registry.addWorkstation(vazkii.botania.client.integration.emi.BotaniaEmiPlugin.ORECHID, EmiStack.of(new ItemStack(ModBlocks.ultimateOrechid)));

        // MythicBotany infusion (optional dependency)
        if (ModList.get().isLoaded("mythicbotany") &&
                ModBlocks.baseManaInfuser != null &&
                ModBlocks.upgradedManaInfuser != null &&
                ModBlocks.advancedManaInfuser != null &&
                ModBlocks.ultimateManaInfuser != null) {
            // MythicBotany EMI may not exist; if present, its category id is likely mythicbotany:infuser
            // Try to attach by scanning for a matching category id at runtime.
            tryAttachById(registry, ResourceLocation.fromNamespaceAndPath("mythicbotany", "infuser"),
                    new ItemStack(ModBlocks.baseManaInfuser),
                    new ItemStack(ModBlocks.upgradedManaInfuser),
                    new ItemStack(ModBlocks.advancedManaInfuser),
                    new ItemStack(ModBlocks.ultimateManaInfuser));
        }
    }

    private static void tryAttachById(EmiRegistry registry, ResourceLocation id, ItemStack... stacks) {
        // Best-effort: resolve an existing category by ID via the live EMI manager
        dev.emi.emi.api.recipe.EmiRecipeManager mgr = dev.emi.emi.api.EmiApi.getRecipeManager();
        for (dev.emi.emi.api.recipe.EmiRecipeCategory cat : mgr.getCategories()) {
            if (cat.getId().getNamespace().equals(id.getNamespace()) && cat.getId().getPath().equals(id.getPath())) {
                for (ItemStack s : stacks) {
                    registry.addWorkstation(cat, EmiStack.of(s));
                }
                break;
            }
        }
    }
}
