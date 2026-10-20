package vazkii.botania.ae2;

import vazkii.botania.ae2.ae2.MEStorageManaItem;
import vazkii.botania.ae2.ae2.ManaContainerItemStrategy;
import vazkii.botania.ae2.ae2.ManaExternalStorageStrategy;
import vazkii.botania.ae2.ae2.ManaGenericStackInvStorage;
import vazkii.botania.ae2.ae2.ManaKey;
import vazkii.botania.ae2.ae2.ManaKeyType;
import vazkii.botania.ae2.ae2.ManaP2PTunnelPart;
import vazkii.botania.ae2.ae2.ManaStorageExportStrategy;
import vazkii.botania.ae2.ae2.ManaStorageImportStrategy;
import vazkii.botania.ae2.botania.MECorporeaNode;
import vazkii.botania.ae2.item.cell.ManaCellHandler;
import appeng.api.AECapabilities;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.client.StorageCellModels;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.items.tools.powered.AbstractPortableCell;
import appeng.parts.automation.StackWorldBehaviors;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.DataComponents;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.api.mana.spark.ManaSparkAttachable;
import vazkii.botania.common.integration.corporea.CorporeaNodeDetectors;
import vazkii.botania.api.neoforge.BotaniaNeoForgeCapabilities;

/**
 * Applied Botanics (by ramidzkh), merged into BotaniaReforged.
 *
 * <p>Upstream this is its own mod container. Here it is constructed from Botania's mod
 * constructor, like Botanical Extra Machinery, and everything is registered under the
 * {@code botania} namespace. That is why this class has no {@code @Mod} annotation.
 */
public class AppliedBotanicsForge {
   private static final DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "botania");
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> AE_MANA = DATA_COMPONENTS.registerComponentType(
      "ae_mana", builder -> builder.persistent(Codec.LONG).cacheEncoding()
   );

   public AppliedBotanicsForge(IEventBus bus) {
      ABBlocks.initialize(bus);
      ABItems.initialize(bus);
      ABMenus.initialize(bus);
      DATA_COMPONENTS.register(bus);
      bus.addListener((RegisterEvent event) -> {
         if (event.getRegistryKey().equals(Registries.BLOCK)) {
            AEKeyTypes.register(ManaKeyType.TYPE);
         }
      });
      bus.addListener(
         (RegisterCapabilitiesEvent event) -> event.registerBlock(
               AECapabilities.IN_WORLD_GRID_NODE_HOST,
               (level, pos, state, be, context) -> be instanceof IInWorldGridNodeHost ? (IInWorldGridNodeHost)be : null,
               ABBlocks.FLUIX_MANA_POOL.get()
            )
      );
      bus.addListener(EventPriority.LOWEST, this::registerGenericAdapters);
      bus.addListener(
         (RegisterPartCapabilitiesEvent event) -> {
            event.register(
               BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), (object, context) -> object.getExposedApi(), ManaP2PTunnelPart.class
            );
            event.register(
               BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP),
               (object, context) -> object.getSparkAttachable(),
               ManaP2PTunnelPart.class
            );
         }
      );
      StackWorldBehaviors.registerImportStrategy(ManaKeyType.TYPE, ManaStorageImportStrategy::new);
      StackWorldBehaviors.registerExportStrategy(ManaKeyType.TYPE, ManaStorageExportStrategy::new);
      StackWorldBehaviors.registerExternalStorageStrategy(ManaKeyType.TYPE, ManaExternalStorageStrategy::new);
      ContainerItemStrategy.register(ManaKeyType.TYPE, ManaKey.class, new ManaContainerItemStrategy());
      GenericSlotCapacities.register(ManaKeyType.TYPE, 10000L);
      bus.addListener((FMLCommonSetupEvent event) -> {
         CorporeaNodeDetectors.register(MECorporeaNode::getNode);
         event.enqueueWork(() -> {
            for (ABItems.Tier tier : ABItems.Tier.values()) {
               Upgrades.add(AEItems.VOID_CARD, ABItems.get(tier), 1, GuiText.StorageCells.getTranslationKey());
               Upgrades.add(AEItems.VOID_CARD, ABItems.getPortableCell(tier), 1, GuiText.StorageCells.getTranslationKey());
            }

            P2PTunnelAttunement.registerAttunementTag(ABItems.MANA_P2P_TUNNEL.get());
            StorageCells.addCellHandler(ManaCellHandler.INSTANCE);
            for (ABItems.Tier tier : ABItems.Tier.values()) {
               ResourceLocation model = AppliedBotanics.id("block/drive/cells/" + tier.size() + "_mana_cell");
               StorageCellModels.registerModel(ABItems.get(tier).get(), model);
               StorageCellModels.registerModel(ABItems.getPortableCell(tier).get(), model);
            }
         });
      });
   }

   private void registerGenericAdapters(RegisterCapabilitiesEvent event) {
      for (Block block : BuiltInRegistries.BLOCK) {
         if (event.isBlockRegistered(AECapabilities.GENERIC_INTERNAL_INV, block)) {
            event.registerBlock(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaReceiver.LOOKUP), (level, pos, state, blockEntity, context) -> {
               GenericInternalInventory genericInv = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, state, blockEntity, context);
               return genericInv != null ? new ManaGenericStackInvStorage(genericInv, level, pos) : null;
            }, block);
            event.registerBlock(BotaniaNeoForgeCapabilities.getBlockApiLookupById(ManaSparkAttachable.LOOKUP), (level, pos, state, blockEntity, context) -> {
               GenericInternalInventory genericInv = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, pos, state, blockEntity, Direction.UP);
               return genericInv != null ? new ManaGenericStackInvStorage(genericInv, level, pos) : null;
            }, block);
         }
      }

      for (Item item : BuiltInRegistries.ITEM) {
         if (item instanceof AbstractPortableCell) {
            event.registerItem(BotaniaNeoForgeCapabilities.getItemApiLookupById(ManaItem.LOOKUP), (object, context) -> MEStorageManaItem.forItem(object), item);
         }
      }
   }
}
