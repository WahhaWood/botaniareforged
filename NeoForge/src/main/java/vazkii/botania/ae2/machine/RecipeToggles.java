package vazkii.botania.ae2.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Which recipes an ME machine offers to the network: a default for all recipes plus the ids switched
 * the other way. "Enable all" / "disable all" just set the default and clear the exceptions, so the
 * stored data stays small, and recipes added later by datapacks follow the current default.
 */
public final class RecipeToggles {
	private static final String TAG_DEFAULT = "me_all_enabled";
	private static final String TAG_EXCEPTIONS = "me_exceptions";

	private boolean allEnabled = true;
	private final Set<ResourceLocation> exceptions = new HashSet<>();

	public boolean isEnabled(ResourceLocation id) {
		return allEnabled != exceptions.contains(id);
	}

	public void toggle(ResourceLocation id) {
		if (!exceptions.remove(id)) {
			exceptions.add(id);
		}
	}

	public void setAll(boolean enabled) {
		allEnabled = enabled;
		exceptions.clear();
	}

	public void save(CompoundTag tag) {
		tag.putBoolean(TAG_DEFAULT, allEnabled);
		ListTag list = new ListTag();
		for (ResourceLocation id : exceptions) {
			list.add(StringTag.valueOf(id.toString()));
		}
		tag.put(TAG_EXCEPTIONS, list);
	}

	/** Keeps the default when the tag has never been written (fresh block). */
	public void load(CompoundTag tag) {
		if (tag.contains(TAG_DEFAULT)) {
			allEnabled = tag.getBoolean(TAG_DEFAULT);
		}

		exceptions.clear();
		ListTag list = tag.getList(TAG_EXCEPTIONS, Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
			if (id != null) {
				exceptions.add(id);
			}
		}
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(allEnabled);
		buf.writeVarInt(exceptions.size());
		for (ResourceLocation id : exceptions) {
			buf.writeResourceLocation(id);
		}
	}

	public void read(FriendlyByteBuf buf) {
		allEnabled = buf.readBoolean();
		exceptions.clear();
		int size = buf.readVarInt();
		for (int i = 0; i < size; i++) {
			exceptions.add(buf.readResourceLocation());
		}
	}
}
