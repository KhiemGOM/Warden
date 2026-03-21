package com.warden;

import java.util.*;

/**
 * Generic tool/armor types that expand to all their tiered/variant item IDs.
 * Used for per-tool enchantment overrides in config and commands.
 * Source: https://minecraft.wiki/w/Enchanting — "Summary of enchantments by item"
 * Tier order: wooden, stone, copper, iron, golden, diamond, netherite
 */
public enum WardenToolType {

    // --- Weapons ---
    SWORD("sword",
        "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:copper_sword",
        "minecraft:iron_sword", "minecraft:golden_sword", "minecraft:diamond_sword",
        "minecraft:netherite_sword"),

    AXE("axe",
        "minecraft:wooden_axe", "minecraft:stone_axe", "minecraft:copper_axe",
        "minecraft:iron_axe", "minecraft:golden_axe", "minecraft:diamond_axe",
        "minecraft:netherite_axe"),

    MACE("mace", "minecraft:mace"),

    SPEAR("spear",
        "minecraft:wooden_spear", "minecraft:stone_spear", "minecraft:copper_spear",
        "minecraft:iron_spear", "minecraft:golden_spear", "minecraft:diamond_spear",
        "minecraft:netherite_spear"),

    // --- Ranged ---
    BOW("bow", "minecraft:bow"),
    CROSSBOW("crossbow", "minecraft:crossbow"),
    TRIDENT("trident", "minecraft:trident"),

    // --- Tools ---
    PICKAXE("pickaxe",
        "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:copper_pickaxe",
        "minecraft:iron_pickaxe", "minecraft:golden_pickaxe", "minecraft:diamond_pickaxe",
        "minecraft:netherite_pickaxe"),

    SHOVEL("shovel",
        "minecraft:wooden_shovel", "minecraft:stone_shovel", "minecraft:copper_shovel",
        "minecraft:iron_shovel", "minecraft:golden_shovel", "minecraft:diamond_shovel",
        "minecraft:netherite_shovel"),

    HOE("hoe",
        "minecraft:wooden_hoe", "minecraft:stone_hoe", "minecraft:copper_hoe",
        "minecraft:iron_hoe", "minecraft:golden_hoe", "minecraft:diamond_hoe",
        "minecraft:netherite_hoe"),

    FISHING_ROD("fishing_rod", "minecraft:fishing_rod"),
    SHEARS("shears", "minecraft:shears"),
    FLINT_AND_STEEL("flint_and_steel", "minecraft:flint_and_steel"),
    BRUSH("brush", "minecraft:brush"),
    CARROT_ON_A_STICK("carrot_on_a_stick", "minecraft:carrot_on_a_stick"),
    WARPED_FUNGUS_ON_A_STICK("warped_fungus_on_a_stick", "minecraft:warped_fungus_on_a_stick"),
    COMPASS("compass", "minecraft:compass"),
    RECOVERY_COMPASS("recovery_compass", "minecraft:recovery_compass"),

    // --- Armor ---
    HELMET("helmet",
        "minecraft:leather_helmet", "minecraft:chainmail_helmet", "minecraft:copper_helmet",
        "minecraft:iron_helmet", "minecraft:golden_helmet", "minecraft:diamond_helmet",
        "minecraft:netherite_helmet"),

    TURTLE_HELMET("turtle_helmet", "minecraft:turtle_helmet"),

    CHESTPLATE("chestplate",
        "minecraft:leather_chestplate", "minecraft:chainmail_chestplate", "minecraft:copper_chestplate",
        "minecraft:iron_chestplate", "minecraft:golden_chestplate", "minecraft:diamond_chestplate",
        "minecraft:netherite_chestplate"),

    LEGGINGS("leggings",
        "minecraft:leather_leggings", "minecraft:chainmail_leggings", "minecraft:copper_leggings",
        "minecraft:iron_leggings", "minecraft:golden_leggings", "minecraft:diamond_leggings",
        "minecraft:netherite_leggings"),

    BOOTS("boots",
        "minecraft:leather_boots", "minecraft:chainmail_boots", "minecraft:copper_boots",
        "minecraft:iron_boots", "minecraft:golden_boots", "minecraft:diamond_boots",
        "minecraft:netherite_boots"),

    // --- Other equipment ---
    SHIELD("shield", "minecraft:shield"),
    ELYTRA("elytra", "minecraft:elytra"),

    // --- Books ---
    // enchanted_book uses STORED_ENCHANTMENTS instead of ENCHANTMENTS — enforcement handles both
    ENCHANTED_BOOK("enchanted_book", "minecraft:enchanted_book");

    // -------------------------------------------------------------------------

    public final String key;
    public final List<String> items;

    private static final Map<String, WardenToolType> BY_KEY  = new LinkedHashMap<>();
    private static final Map<String, WardenToolType> BY_ITEM = new HashMap<>();

    static {
        for (WardenToolType t : values()) {
            BY_KEY.put(t.key, t);
            for (String item : t.items) BY_ITEM.put(item, t);
        }
    }

    WardenToolType(String key, String... items) {
        this.key   = key;
        this.items = List.of(items);
    }

    public static WardenToolType byKey(String key)       { return BY_KEY.get(key); }
    public static WardenToolType byItemId(String itemId) { return BY_ITEM.get(itemId); }
    public static Collection<String> keys()              { return BY_KEY.keySet(); }
}
