package com.warden.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class WardenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Warden");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("warden.json");

    // --- Explosion config ---
    public boolean explosionLimitsEnabled = true;
    public Map<String, ExplosionSourceConfig> explosionSources = new LinkedHashMap<>();

    // --- Item limit config ---
    public boolean itemLimitsEnabled = true;
    public int checkIntervalTicks = 20;
    public Map<String, ItemConfig> itemLimits = new LinkedHashMap<>();

    public static class ExplosionSourceConfig {
        public boolean enabled;
        public float maxPower;

        public ExplosionSourceConfig(boolean enabled, float maxPower) {
            this.enabled = enabled;
            this.maxPower = maxPower;
        }
    }

    public static class ItemConfig {
        public boolean enabled;
        public int limit;

        public ItemConfig(boolean enabled, int limit) {
            this.enabled = enabled;
            this.limit = limit;
        }
    }

    // -------------------------------------------------------------------------

    public static WardenConfig load() {
        WardenConfig config = new WardenConfig();
        if (!Files.exists(CONFIG_PATH)) {
            config.populateDefaults();
            config.save();
            LOGGER.info("[Warden] Created default config at {}", CONFIG_PATH);
            return config;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            config.readFrom(root);
            LOGGER.info("[Warden] Loaded config from {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.error("[Warden] Failed to read config, using defaults: {}", e.getMessage());
            config.populateDefaults();
        }
        return config;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(toJson(), writer);
        } catch (IOException e) {
            LOGGER.error("[Warden] Failed to save config: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private void populateDefaults() {
        explosionLimitsEnabled = true;
        explosionSources.put("tnt",          new ExplosionSourceConfig(true,  4.0f));
        explosionSources.put("tnt_minecart", new ExplosionSourceConfig(true,  4.0f));
        explosionSources.put("bed",          new ExplosionSourceConfig(true,  5.0f));
        explosionSources.put("end_crystal",  new ExplosionSourceConfig(true,  4.0f));
        explosionSources.put("creeper",      new ExplosionSourceConfig(false, 3.0f));
        explosionSources.put("ghast",        new ExplosionSourceConfig(false, 1.0f));

        itemLimitsEnabled = true;
        checkIntervalTicks = 20;
        itemLimits.put("minecraft:totem_of_undying",       new ItemConfig(true, 1));
        itemLimits.put("minecraft:cobweb",                 new ItemConfig(true, 16));
        itemLimits.put("minecraft:obsidian",               new ItemConfig(true, 64));
        itemLimits.put("minecraft:golden_apple",           new ItemConfig(true, 8));
        itemLimits.put("minecraft:enchanted_golden_apple", new ItemConfig(true, 1));
    }

    private void readFrom(JsonObject root) {
        if (root.has("explosion_limits")) {
            JsonObject expl = root.getAsJsonObject("explosion_limits");
            explosionLimitsEnabled = getBool(expl, "enabled", true);
            if (expl.has("sources")) {
                JsonObject sources = expl.getAsJsonObject("sources");
                for (Map.Entry<String, JsonElement> entry : sources.entrySet()) {
                    JsonObject src = entry.getValue().getAsJsonObject();
                    boolean en = getBool(src, "enabled", true);
                    float power = src.has("max_power") ? src.get("max_power").getAsFloat() : 4.0f;
                    explosionSources.put(entry.getKey(), new ExplosionSourceConfig(en, power));
                }
            }
        }

        if (root.has("item_limits")) {
            JsonObject items = root.getAsJsonObject("item_limits");
            itemLimitsEnabled = getBool(items, "enabled", true);
            checkIntervalTicks = items.has("check_interval_ticks")
                    ? items.get("check_interval_ticks").getAsInt() : 20;
            if (items.has("items")) {
                JsonObject itemMap = items.getAsJsonObject("items");
                for (Map.Entry<String, JsonElement> entry : itemMap.entrySet()) {
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    boolean en = getBool(obj, "enabled", true);
                    int lim = obj.has("limit") ? obj.get("limit").getAsInt() : 1;
                    itemLimits.put(entry.getKey(), new ItemConfig(en, lim));
                }
            }
        }
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();

        JsonObject expl = new JsonObject();
        expl.addProperty("enabled", explosionLimitsEnabled);
        JsonObject sources = new JsonObject();
        for (Map.Entry<String, ExplosionSourceConfig> e : explosionSources.entrySet()) {
            JsonObject src = new JsonObject();
            src.addProperty("enabled", e.getValue().enabled);
            src.addProperty("max_power", e.getValue().maxPower);
            sources.add(e.getKey(), src);
        }
        expl.add("sources", sources);
        root.add("explosion_limits", expl);

        JsonObject itemSection = new JsonObject();
        itemSection.addProperty("enabled", itemLimitsEnabled);
        itemSection.addProperty("check_interval_ticks", checkIntervalTicks);
        JsonObject itemMap = new JsonObject();
        for (Map.Entry<String, ItemConfig> e : itemLimits.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", e.getValue().enabled);
            obj.addProperty("limit", e.getValue().limit);
            itemMap.add(e.getKey(), obj);
        }
        itemSection.add("items", itemMap);
        root.add("item_limits", itemSection);

        return root;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }
}
