package vazkii.botania.ae2.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import java.text.NumberFormat;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import vazkii.botania.client.fx.WispParticleData;

public class ManaKey extends AEKey {
   public static final AEKey KEY = new ManaKey();
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("botania", "mana");

   private ManaKey() {
   }

   @Override
   public AEKeyType getType() {
      return ManaKeyType.TYPE;
   }

   @Override
   public AEKey dropSecondary() {
      return this;
   }

   @Override
   public CompoundTag toTag(Provider registries) {
      return new CompoundTag();
   }

   @Override
   public Object getPrimaryKey() {
      return this;
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   @Override
   public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
      if (level instanceof ServerLevel serverLevel) {
         long times = Math.min((amount + 999L) / 1000L, 10L);

         for (int i = 0; (long)i < times; i++) {
            float red = (float)i / (float)times;
            float green = 0.5F;
            float blue = 0.2F;
            WispParticleData data = WispParticleData.wisp((float)Math.random() / 3.0F, red, green, blue, 2.0F);
            serverLevel.sendParticles(
               data,
               (double)pos.getX() + 0.3 + Math.random() * 0.5,
               (double)pos.getY() + 0.6 + Math.random() * 0.25,
               (double)pos.getZ() + Math.random(),
               8,
               0.1,
               0.1,
               0.1,
               0.04
            );
         }
      }
   }

   @Override
   protected Component computeDisplayName() {
      return ManaKeyType.MANA;
   }

   @Override
   public void writeToPacket(RegistryFriendlyByteBuf data) {
   }

   @Override
   public boolean hasComponents() {
      return false;
   }

   @Override
   public String formatAmount(long amount, AmountFormat format) {
      if (format == AmountFormat.FULL) {
         double units = (double)amount / (double)this.getType().getAmountPerUnit();
         return NumberFormat.getNumberInstance().format(units) + (units == 1.0 ? " pool" : " pools");
      } else {
         return super.formatAmount(amount, format);
      }
   }
}
