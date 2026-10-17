package vazkii.botania.ae2.integration.igtooltip;

import vazkii.botania.ae2.block.FluixPool;
import vazkii.botania.ae2.block.FluixPoolBlockEntity;
import appeng.api.integrations.igtooltip.BaseClassRegistration;
import appeng.api.integrations.igtooltip.TooltipProvider;

public class ABTooltipProvider implements TooltipProvider {
   @Override
   public void registerBlockEntityBaseClasses(BaseClassRegistration registration) {
      registration.addBaseBlockEntity(FluixPoolBlockEntity.class, FluixPool.class);
   }
}
