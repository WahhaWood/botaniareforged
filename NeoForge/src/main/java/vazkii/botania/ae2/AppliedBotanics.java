package vazkii.botania.ae2;

import net.minecraft.resources.ResourceLocation;

public interface AppliedBotanics {
   String MOD_ID = "botania";

   static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("botania", path);
   }
}
