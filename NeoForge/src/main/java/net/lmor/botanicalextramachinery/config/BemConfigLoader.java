package net.lmor.botanicalextramachinery.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import org.moddingx.libx.config.Config;
import org.moddingx.libx.config.Group;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replaces the LibX config system for BotaniaReforged. The original config
 * classes (LibXServerConfig / LibXClientConfig) declare plain static fields
 * with default values that LibX rewrote at config load. Here the same
 * classes are mirrored into a NeoForge {@link ModConfigSpec} via reflection
 * and the loaded values are written back into the static fields, so no
 * gameplay code had to change. Nested {@code @Group} classes become dotted
 * config paths.
 */
public final class BemConfigLoader {

	private BemConfigLoader() {}

	public record ConfigClass(String fileName, Class<?> clazz) {
		public static ConfigClass of(String fileName, Class<?> clazz) {
			return new ConfigClass(fileName, clazz);
		}
	}

	public static void register(ModContainer container, ConfigClass... configs) {
		for (ConfigClass config : configs) {
			List<Binding> bindings = new ArrayList<>();
			ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
			collect(builder, config.clazz(), "", bindings);
			ModConfigSpec spec = builder.build();
			container.registerConfig(ModConfig.Type.CLIENT == guessType(config) && false ? ModConfig.Type.CLIENT : typeOf(config),
					spec, config.fileName());
			container.getEventBus().addListener((net.neoforged.fml.event.config.ModConfigEvent event) -> {
				// ModConfigSpec.ConfigValue.get() throws once the config is being unloaded,
				// and the server unloads all configs while stopping, so skip that event.
				if (event instanceof net.neoforged.fml.event.config.ModConfigEvent.Unloading) {
					return;
				}
				if (event.getConfig().getSpec() == spec) {
					for (Binding binding : bindings) {
						binding.push();
					}
				}
			});
		}
	}

	private static ModConfig.Type typeOf(ConfigClass config) {
		return config.fileName().endsWith("server.toml") ? ModConfig.Type.SERVER : ModConfig.Type.CLIENT;
	}

	private static ModConfig.Type guessType(ConfigClass config) {
		return typeOf(config);
	}

	private static void collect(ModConfigSpec.Builder builder, Class<?> clazz, String prefix, List<Binding> bindings) {
		for (Field field : clazz.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
				continue;
			}
			Class<?> type = field.getType();
			String comment = null;
			Config cfg = field.getAnnotation(Config.class);
			if (cfg != null && cfg.value().length > 0) {
				comment = String.join("\n", cfg.value());
			}
			String name = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();
			if (comment != null) {
				builder.comment(comment);
			}
			if (type == int.class) {
				bindings.add(new IntBinding(field, builder.defineInRange(name, getInt(field), Integer.MIN_VALUE, Integer.MAX_VALUE)));
			} else if (type == boolean.class) {
				bindings.add(new BoolBinding(field, builder.define(name, getBool(field))));
			} else if (type == double.class) {
				bindings.add(new DoubleBinding(field, builder.defineInRange(name, getDouble(field), Double.MIN_VALUE, Double.MAX_VALUE)));
			} else if (type == long.class) {
				bindings.add(new LongBinding(field, builder.defineInRange(name, getLong(field), Long.MIN_VALUE, Long.MAX_VALUE)));
			}
		}
		for (Class<?> inner : clazz.getDeclaredClasses()) {
			if (Modifier.isStatic(inner.getModifiers()) && inner.getAnnotation(Group.class) != null) {
				collect(builder, inner, prefix.isEmpty() ? camelToSnake(inner.getSimpleName()) : prefix + "." + camelToSnake(inner.getSimpleName()), bindings);
			}
		}
	}

	private static String camelToSnake(String name) {
		return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase(Locale.ROOT);
	}

	private sealed interface Binding permits IntBinding, BoolBinding, DoubleBinding, LongBinding {
		void push();
	}

	private static final Object LOCK = new Object();

	private record IntBinding(Field field, ModConfigSpec.ConfigValue<Integer> value) implements Binding {
		@Override
		public void push() {
			synchronized (LOCK) {
				try {
					field.setInt(null, value.get());
				} catch (IllegalAccessException ignored) {}
			}
		}
	}

	private record BoolBinding(Field field, ModConfigSpec.ConfigValue<Boolean> value) implements Binding {
		@Override
		public void push() {
			synchronized (LOCK) {
				try {
					field.setBoolean(null, value.get());
				} catch (IllegalAccessException ignored) {}
			}
		}
	}

	private record DoubleBinding(Field field, ModConfigSpec.ConfigValue<Double> value) implements Binding {
		@Override
		public void push() {
			synchronized (LOCK) {
				try {
					field.setDouble(null, value.get());
				} catch (IllegalAccessException ignored) {}
			}
		}
	}

	private record LongBinding(Field field, ModConfigSpec.ConfigValue<Long> value) implements Binding {
		@Override
		public void push() {
			synchronized (LOCK) {
				try {
					field.setLong(null, value.get());
				} catch (IllegalAccessException ignored) {}
			}
		}
	}

	private static int getInt(Field f) {
		try {
			return f.getInt(null);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}

	private static boolean getBool(Field f) {
		try {
			return f.getBoolean(null);
		} catch (IllegalAccessException e) {
			return false;
		}
	}

	private static double getDouble(Field f) {
		try {
			return f.getDouble(null);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}

	private static long getLong(Field f) {
		try {
			return f.getLong(null);
		} catch (IllegalAccessException e) {
			return 0;
		}
	}
}
