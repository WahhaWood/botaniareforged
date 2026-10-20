package vazkii.botania.ae2;

import net.neoforged.fml.loading.LoadingModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the Applied Botanics mixins ({@code botania.ae2.mixins.json}) only when AE2 is installed: they add
 * methods with AE2 types to Botania's own block entities, which would not load without it.
 */
public final class Ae2MixinPlugin implements IMixinConfigPlugin {
	private boolean ae2;

	@Override
	public void onLoad(String mixinPackage) {
		ae2 = LoadingModList.get().getModFileById("ae2") != null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return ae2;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
