package com.warden;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warden.config.WardenConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class WardenMod implements ModInitializer {

    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    private static final ThreadLocal<Boolean> ENFORCING_WEAPON_COMPONENTS = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<String> XP_SOURCE = ThreadLocal.withInitial(() -> "unknown");
    public static final ThreadLocal<String> XP_CONTEXT = ThreadLocal.withInitial(() -> "");
    public static final ThreadLocal<String> XP_SOURCE_OVERRIDE = new ThreadLocal<>();
    public static final ThreadLocal<String> XP_CONTEXT_OVERRIDE = new ThreadLocal<>();

    public enum NoticeCategory {
        ITEM,
        WEAPON,
        ENCHANTMENT,
        EFFECT,
        XP
    }

    public static final Logger LOGGER = LoggerFactory.getLogger("Warden");
    public static WardenConfig CONFIG;
    private static final Map<String, WardenConfig.WeaponLimitConfig> CLIENT_SYNCED_WEAPON_LIMITS = new ConcurrentHashMap<>();
    private static volatile boolean CLIENT_SYNCED_WEAPON_LIMITS_VALID;
    private static volatile boolean CLIENT_SYNCED_WEAPON_LIMITS_ENABLED = true;

    @Override
    public void onInitialize() {
        CONFIG = WardenConfig.load();
        LOGGER.info("[Warden] Loaded. Explosion: {}, Items: {}, Weapons: {}, Enchantments: {}, Effects: {}",
                CONFIG.explosionLimitsEnabled, CONFIG.itemLimitsEnabled, CONFIG.weaponLimitsEnabled,
                CONFIG.enchantmentLimitsEnabled, CONFIG.effectLimitsEnabled);

        WardenNetworking.register();
        registerTick();
        WardenCommand.register();
    }

    private void registerTick() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (server.getTicks() % CONFIG.checkIntervalTicks != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                enforceItemLimits(player);
                enforceWeaponLimits(player);
                enforceEnchantmentLimits(player);
                enforceEffectLimits(player);
            }
        });
    }

    public static boolean isExempt(PlayerEntity player) {
        if (CONFIG.exemptCreative && player.isCreative()) {
            return true;
        }
        return CONFIG.exemptPlayers.contains(player.getName().getString());
    }

    public static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static MutableText wardenPrefix() {
        return Text.literal("[")
                .append(Text.literal("WARDEN").formatted(Formatting.DARK_PURPLE, Formatting.BOLD))
                .append(Text.literal("] "));
    }

    public static void sendNotice(ServerPlayerEntity player, NoticeCategory category, String message) {
        if (CONFIG == null || player == null) {
            return;
        }
        boolean enabledGlobally = switch (category) {
            case ITEM -> CONFIG.itemActionBarEnabled;
            case WEAPON -> CONFIG.weaponActionBarEnabled;
            case ENCHANTMENT -> CONFIG.enchantmentActionBarEnabled;
            case EFFECT -> CONFIG.effectActionBarEnabled;
            case XP -> true;
        };
        if (!enabledGlobally) {
            return;
        }
        String playerName = player.getName().getString();
        java.util.Set<String> disabled = CONFIG.playerActionBarDisabled.get(playerName);
        if (disabled == null || !disabled.contains(noticeCategoryKey(category))) {
            MutableText feedback = wardenPrefix().append(Text.literal(message).formatted(Formatting.RED));
            player.sendMessage(feedback, true);
        }
    }

    public static String noticeCategoryKey(NoticeCategory category) {
        return switch (category) {
            case ITEM -> "item";
            case WEAPON -> "weapon";
            case ENCHANTMENT -> "enchantment";
            case EFFECT -> "effect";
            case XP -> "xp";
        };
    }

    public static boolean shouldSkipDurationCap(StatusEffectInstance instance) {
        return instance.isAmbient();
    }

    public static int countItemsInInventory(PlayerInventory inv, String itemId) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            total += countItemRecursive(stack, itemId);
        }
        return total;
    }

    private static int countItemRecursive(ItemStack stack, String itemId) {
        if (stack.isEmpty()) return 0;
        int count = 0;
        if (Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
            count += stack.getCount();
        }

        // Bundle
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.iterate()) {
                count += countItemRecursive(inner, itemId);
            }
        }

        // Container
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.iterateNonEmpty()) {
                count += countItemRecursive(inner, itemId);
            }
        }
        return count;
    }

    public static void enforceItemLimits(ServerPlayerEntity player) {
        if (!CONFIG.itemLimitsEnabled || isExempt(player)) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            countItemsRecursive(stack, counts);
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String itemId = entry.getKey();
            int total = entry.getValue();
            int limit = CONFIG.itemLimits.get(itemId);
            if (total <= limit) {
                continue;
            }

            int excess = total - limit;
            // First pass: remove from main inventory
            for (int i = inv.size() - 1; i >= 0 && excess > 0; i--) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }
                final int slot = i;
                excess = removeItemsRecursive(player, stack, itemId, excess, () -> {
                    inv.setStack(slot, ItemStack.EMPTY);
                });
            }

            int removed = total - limit - excess;
            if (removed > 0) {
                sendNotice(player, NoticeCategory.ITEM,
                        "removed " + removed + "x " + shortId(itemId) + " (limit: " + limit + ")");
                LOGGER.debug("[Warden] Removed {} excess {} from {}", removed, itemId, player.getName().getString());
            }
        }
    }

    private static void countItemsRecursive(ItemStack stack, Map<String, Integer> counts) {
        if (stack.isEmpty()) return;

        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (CONFIG.itemLimits.containsKey(id)) {
            counts.merge(id, stack.getCount(), Integer::sum);
        }

        // Check Bundle
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.iterate()) {
                countItemsRecursive(inner, counts);
            }
        }

        // Check Container (Shulker Box, etc.)
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.iterateNonEmpty()) {
                countItemsRecursive(inner, counts);
            }
        }
    }

    private static int removeItemsRecursive(ServerPlayerEntity player, ItemStack stack, String itemId, int excess, Runnable onStackEmpty) {
        if (stack.isEmpty() || excess <= 0) return excess;

        // Check contents first (deepest first)
        // Bundle
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> newContents = new ArrayList<>();
            boolean bundleChanged = false;
            for (ItemStack inner : bundle.iterate()) {
                if (excess > 0) {
                    int before = excess;
                    // We don't have a good way to "setStackEmpty" inside bundle easily without rebuilding
                    // removeItemsRecursive returns remaining excess
                    // For bundle, we handle it specially
                    if (Registries.ITEM.getId(inner.getItem()).toString().equals(itemId)) {
                        int drop = Math.min(inner.getCount(), excess);
                        handleOverflow(player, inner, drop);
                        inner.decrement(drop);
                        excess -= drop;
                        bundleChanged = true;
                    }

                    // Recursively check inner containers if any
                    excess = removeItemsRecursive(player, inner, itemId, excess, () -> {
                        // inner is now empty, handled by inner.decrement above or recursive call
                    });

                    if (before != excess) bundleChanged = true;
                }
                if (!inner.isEmpty()) {
                    newContents.add(inner);
                }
            }
            if (bundleChanged) {
                stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(newContents));
            }
        }

        // Container (Shulker Box)
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
            container.copyTo(stacks);
            boolean containerChanged = false;
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack inner = stacks.get(i);
                if (inner.isEmpty()) continue;

                int before = excess;
                final int idx = i;
                excess = removeItemsRecursive(player, inner, itemId, excess, () -> {
                    stacks.set(idx, ItemStack.EMPTY);
                });

                if (Registries.ITEM.getId(inner.getItem()).toString().equals(itemId) && excess > 0) {
                    int drop = Math.min(inner.getCount(), excess);
                    handleOverflow(player, inner, drop);
                    inner.decrement(drop);
                    if (inner.isEmpty()) {
                        stacks.set(i, ItemStack.EMPTY);
                    }
                    excess -= drop;
                }

                if (before != excess) containerChanged = true;
            }
            if (containerChanged) {
                stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
            }
        }

        // Finally check the stack itself
        if (excess > 0 && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
            int drop = Math.min(stack.getCount(), excess);
            handleOverflow(player, stack, drop);
            stack.decrement(drop);
            if (stack.isEmpty()) {
                onStackEmpty.run();
            }
            excess -= drop;
        }

        return excess;
    }

    private static void handleOverflow(ServerPlayerEntity player, ItemStack stack, int amount) {
        if (CONFIG.deleteOverflowItem) {
            // Already decremented in caller
        } else {
            ItemStack dropped = stack.copyWithCount(amount);
            ItemEntity itemEntity = player.dropItem(dropped, false);
            if (itemEntity != null) {
                itemEntity.setPickupDelay(CONFIG.dropPickupDelay);
            }
        }
    }

    public static void enforceWeaponLimits(ServerPlayerEntity player) {
        if (!CONFIG.weaponLimitsEnabled || isExempt(player)) {
            return;
        }

        boolean changed = false;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                changed |= enforceWeaponComponentsRecursive(stack);
            }
        }

        if (changed) {
            sendNotice(player, NoticeCategory.WEAPON, "weapon stats updated on your items");
        }
    }

    private static boolean enforceWeaponComponentsRecursive(ItemStack stack) {
        boolean changed = enforceWeaponComponents(stack);

        // Bundle
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> newContents = new ArrayList<>();
            boolean bundleChanged = false;
            for (ItemStack inner : bundle.iterate()) {
                if (enforceWeaponComponentsRecursive(inner)) {
                    bundleChanged = true;
                }
                newContents.add(inner);
            }
            if (bundleChanged) {
                stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(newContents));
                changed = true;
            }
        }

        // Container
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
            container.copyTo(stacks);
            boolean containerChanged = false;
            for (ItemStack inner : stacks) {
                if (enforceWeaponComponentsRecursive(inner)) {
                    containerChanged = true;
                }
            }
            if (containerChanged) {
                stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
                changed = true;
            }
        }

        return changed;
    }

    public static boolean enforceWeaponComponents(ItemStack stack) {
        if (ENFORCING_WEAPON_COMPONENTS.get()) return false;
        if (CONFIG == null || !CONFIG.weaponLimitsEnabled || stack == null || stack.isEmpty()) {
            return false;
        }
        ENFORCING_WEAPON_COMPONENTS.set(true);
        try {
            WardenConfig.WeaponLimitConfig limit = CONFIG.weaponLimits.get(Registries.ITEM.getId(stack.getItem()).toString());
            ItemStack defaultStack = stack.getItem().getDefaultStack();
            boolean changed = false;
            changed |= applyWeaponAttributeLimit(
                    stack,
                    defaultStack,
                    EntityAttributes.ATTACK_DAMAGE,
                    Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                    toAttributeModifierValue("attackDamage", limit != null ? limit.attackDamage : null)
            );
            changed |= applyWeaponAttributeLimit(
                    stack,
                    defaultStack,
                    EntityAttributes.ATTACK_SPEED,
                    Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                    toAttributeModifierValue("attackSpeed", limit != null ? limit.attackSpeed : null)
            );
            changed |= applyWeaponReachLimit(stack, defaultStack, limit != null ? limit.reach : null);
            return changed;
        } finally {
            ENFORCING_WEAPON_COMPONENTS.set(false);
        }
    }

    private static Double toAttributeModifierValue(String stat, Double configuredValue) {
        if (configuredValue == null) {
            return null;
        }
        if ("attackDamage".equals(stat)) {
            return configuredValue - PLAYER_BASE_ATTACK_DAMAGE;
        }
        if ("attackSpeed".equals(stat)) {
            return configuredValue - PLAYER_BASE_ATTACK_SPEED;
        }
        return configuredValue;
    }

    private static boolean applyWeaponAttributeLimit(
            ItemStack stack,
            ItemStack defaultStack,
            RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
            net.minecraft.util.Identifier modifierId,
            Double value
    ) {
        AttributeModifiersComponent current = stack.getOrDefault(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT
        );
        AttributeModifiersComponent defaults = defaultStack.getOrDefault(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT
        );
        AttributeModifiersComponent.Entry defaultEntry = findWeaponAttributeEntry(defaults, attribute, modifierId);
        List<AttributeModifiersComponent.Entry> entries = new ArrayList<>();
        boolean found = false;
        boolean changed = false;

        for (AttributeModifiersComponent.Entry entry : current.modifiers()) {
            if (entry.slot() == AttributeModifierSlot.MAINHAND
                    && entry.attribute().equals(attribute)
                    && entry.modifier().idMatches(modifierId)) {
                found = true;
                AttributeModifiersComponent.Entry desired = defaultEntry;
                if (value != null) {
                    desired = new AttributeModifiersComponent.Entry(
                            attribute,
                            new EntityAttributeModifier(modifierId, value, EntityAttributeModifier.Operation.ADD_VALUE),
                            AttributeModifierSlot.MAINHAND,
                            entry.display()
                    );
                }

                if (desired == null) {
                    changed = true;
                    continue;
                }

                if (!entry.equals(desired)) {
                    entries.add(desired);
                    changed = true;
                } else {
                    entries.add(entry);
                }
            } else {
                entries.add(entry);
            }
        }

        if (!found) {
            if (value != null) {
                entries.add(new AttributeModifiersComponent.Entry(
                        attribute,
                        new EntityAttributeModifier(modifierId, value, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND
                ));
                changed = true;
            } else if (defaultEntry != null) {
                entries.add(defaultEntry);
                changed = true;
            }
        }

        if (changed) {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, new AttributeModifiersComponent(entries));
        }
        return changed;
    }

    private static AttributeModifiersComponent.Entry findWeaponAttributeEntry(
            AttributeModifiersComponent component,
            RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
            net.minecraft.util.Identifier modifierId
    ) {
        for (AttributeModifiersComponent.Entry entry : component.modifiers()) {
            if (entry.slot() == AttributeModifierSlot.MAINHAND
                    && entry.attribute().equals(attribute)
                    && entry.modifier().idMatches(modifierId)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean applyWeaponReachLimit(ItemStack stack, ItemStack defaultStack, Float reach) {
        AttackRangeComponent current = stack.get(DataComponentTypes.ATTACK_RANGE);
        AttackRangeComponent defaults = defaultStack.get(DataComponentTypes.ATTACK_RANGE);
        AttackRangeComponent desired;
        if (reach == null) {
            desired = defaults;
        } else if (defaults == null) {
            desired = new AttackRangeComponent(0.0f, reach, 0.0f, reach, 0.0f, 1.0f);
        } else {
            desired = new AttackRangeComponent(
                    Math.min(defaults.minRange(), reach),
                    reach,
                    Math.min(defaults.minCreativeRange(), reach),
                    reach,
                    defaults.hitboxMargin(),
                    defaults.mobFactor()
            );
        }

        if (Objects.equals(desired, current)) {
            return false;
        }

        if (desired == null) {
            stack.remove(DataComponentTypes.ATTACK_RANGE);
        } else {
            stack.set(DataComponentTypes.ATTACK_RANGE, desired);
        }
        return true;
    }

    public static Double getVanillaAttackDamage(Item item) {
        Double modifier = getVanillaAttackAttribute(item, EntityAttributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_MODIFIER_ID);
        return modifier == null ? PLAYER_BASE_ATTACK_DAMAGE : modifier + PLAYER_BASE_ATTACK_DAMAGE;
    }

    public static Double getVanillaAttackSpeed(Item item) {
        Double modifier = getVanillaAttackAttribute(item, EntityAttributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_MODIFIER_ID);
        return modifier == null ? PLAYER_BASE_ATTACK_SPEED : modifier + PLAYER_BASE_ATTACK_SPEED;
    }

    public static Float getVanillaReach(Item item) {
        AttackRangeComponent reach = item.getDefaultStack().get(DataComponentTypes.ATTACK_RANGE);
        return reach == null ? null : reach.maxRange();
    }

    public static void applyWeaponAttackCooldown(ServerPlayerEntity player) {
        if (CONFIG == null || !CONFIG.weaponLimitsEnabled || player == null || isExempt(player)) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(stack, false);
        if (limit == null || limit.disableCooldownTicks == null || limit.disableCooldownTicks <= 0) {
            return;
        }
        player.getItemCooldownManager().set(stack, limit.disableCooldownTicks);
    }

    public static boolean isWeaponAttackBlockedByCooldown(ServerPlayerEntity player) {
        if (CONFIG == null || !CONFIG.weaponLimitsEnabled || player == null || isExempt(player)) {
            return false;
        }
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return false;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(stack, false);
        if (limit == null || limit.disableCooldownTicks == null || limit.disableCooldownTicks <= 0) {
            return false;
        }
        return player.getItemCooldownManager().isCoolingDown(stack);
    }

    public static void applyWeaponUseCooldown(ServerPlayerEntity player, ItemStack stack) {
        if (CONFIG == null || !CONFIG.weaponLimitsEnabled || player == null || isExempt(player) || stack == null || stack.isEmpty()) {
            return;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(stack, false);
        if (limit == null || limit.rechargeTicks == null || limit.rechargeTicks <= 0) {
            return;
        }
        player.getItemCooldownManager().set(stack, limit.rechargeTicks);
    }

    public static boolean isWeaponUseBlockedByCooldown(PlayerEntity player, ItemStack stack) {
        if (!areWeaponLimitsEnabled(player != null && player.getEntityWorld().isClient())
                || player == null || isExempt(player) || stack == null || stack.isEmpty()) {
            return false;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(stack, player.getEntityWorld().isClient());
        if (limit == null || limit.rechargeTicks == null || limit.rechargeTicks <= 0) {
            return false;
        }
        return player.getItemCooldownManager().isCoolingDown(stack);
    }

    public static int getConfiguredRangedUseTicks(ItemStack stack, boolean clientSide, int vanillaTicks) {
        if (!areWeaponLimitsEnabled(clientSide) || stack == null || stack.isEmpty()) {
            return vanillaTicks;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(stack, clientSide);
        if (limit == null || limit.rechargeTicks == null || limit.rechargeTicks <= 0) {
            return vanillaTicks;
        }
        return limit.rechargeTicks;
    }

    public static float getConfiguredBowPullProgress(ItemStack stack, boolean clientSide, int useTicks) {
        int pullTicks = Math.max(1, getConfiguredRangedUseTicks(stack, clientSide, 20));
        float progress = (float) useTicks / (float) pullTicks;
        progress = (progress * progress + progress * 2.0f) / 3.0f;
        return Math.min(progress, 1.0f);
    }

    public static int getConfiguredTridentUseTicks(ItemStack stack, boolean clientSide, int vanillaTicks) {
        return Math.max(1, getConfiguredRangedUseTicks(stack, clientSide, vanillaTicks));
    }

    public static void applyConfiguredProjectileDamage(PersistentProjectileEntity projectile, ItemStack weaponStack) {
        if (projectile == null || weaponStack == null || weaponStack.isEmpty()
                || !areWeaponLimitsEnabled(projectile.getEntityWorld().isClient())) {
            return;
        }
        WardenConfig.WeaponLimitConfig limit = getEffectiveWeaponLimit(weaponStack, projectile.getEntityWorld().isClient());
        if (limit != null && limit.projectileDamage != null) {
            projectile.setDamage(limit.projectileDamage);
        }
    }

    public static boolean enforceProjectileDamage(PersistentProjectileEntity projectile) {
        if (CONFIG == null || !CONFIG.weaponLimitsEnabled || projectile == null || projectile.getEntityWorld().isClient()) {
            return true;
        }
        ItemStack weaponStack = resolveProjectileWeaponStack(projectile);
        if (weaponStack.isEmpty()) {
            return false;
        }
        applyConfiguredProjectileDamage(projectile, weaponStack);
        return true;
    }

    private static ItemStack resolveProjectileWeaponStack(PersistentProjectileEntity projectile) {
        ItemStack weaponStack = projectile.getWeaponStack();
        if (!weaponStack.isEmpty()) {
            return weaponStack;
        }
        if (projectile.getOwner() instanceof PlayerEntity player) {
            ItemStack mainHand = player.getMainHandStack();
            if (isRangedWeaponStack(mainHand)) {
                return mainHand;
            }
            ItemStack offHand = player.getOffHandStack();
            if (isRangedWeaponStack(offHand)) {
                return offHand;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isRangedWeaponStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        WardenToolType type = WardenToolType.byItemId(itemId);
        return type == WardenToolType.BOW || type == WardenToolType.CROSSBOW || type == WardenToolType.TRIDENT;
    }

    private static Double getVanillaAttackAttribute(
            Item item,
            RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
            net.minecraft.util.Identifier modifierId
    ) {
        AttributeModifiersComponent defaults = item.getDefaultStack().getOrDefault(
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                AttributeModifiersComponent.DEFAULT
        );
        AttributeModifiersComponent.Entry entry = findWeaponAttributeEntry(defaults, attribute, modifierId);
        return entry == null ? null : entry.modifier().value();
    }

    public static void applySyncedWeaponLimits(String json) {
        CLIENT_SYNCED_WEAPON_LIMITS.clear();
        CLIENT_SYNCED_WEAPON_LIMITS_VALID = false;
        CLIENT_SYNCED_WEAPON_LIMITS_ENABLED = true;

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        CLIENT_SYNCED_WEAPON_LIMITS_ENABLED = !root.has("enabled") || root.get("enabled").getAsBoolean();
        if (root.has("items")) {
            JsonObject items = root.getAsJsonObject("items");
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                JsonObject itemCfg = entry.getValue().getAsJsonObject();
                WardenConfig.WeaponLimitConfig cfg = new WardenConfig.WeaponLimitConfig();
                if (itemCfg.has("disable_cooldown_ticks")) {
                    cfg.disableCooldownTicks = itemCfg.get("disable_cooldown_ticks").getAsInt();
                }
                if (itemCfg.has("projectile_damage")) {
                    cfg.projectileDamage = itemCfg.get("projectile_damage").getAsDouble();
                }
                if (itemCfg.has("recharge_ticks")) {
                    cfg.rechargeTicks = itemCfg.get("recharge_ticks").getAsInt();
                }
                if (cfg.isConfigured()) {
                    CLIENT_SYNCED_WEAPON_LIMITS.put(entry.getKey(), cfg);
                }
            }
        }
        CLIENT_SYNCED_WEAPON_LIMITS_VALID = true;
    }

    public static void clearSyncedWeaponLimits() {
        CLIENT_SYNCED_WEAPON_LIMITS.clear();
        CLIENT_SYNCED_WEAPON_LIMITS_VALID = false;
        CLIENT_SYNCED_WEAPON_LIMITS_ENABLED = true;
    }

    private static boolean areWeaponLimitsEnabled(boolean clientSide) {
        if (clientSide && CLIENT_SYNCED_WEAPON_LIMITS_VALID) {
            return CLIENT_SYNCED_WEAPON_LIMITS_ENABLED;
        }
        return CONFIG != null && CONFIG.weaponLimitsEnabled;
    }

    private static WardenConfig.WeaponLimitConfig getEffectiveWeaponLimit(ItemStack stack, boolean clientSide) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (clientSide && CLIENT_SYNCED_WEAPON_LIMITS_VALID) {
            return CLIENT_SYNCED_WEAPON_LIMITS.get(itemId);
        }
        if (CONFIG == null) {
            return null;
        }
        return CONFIG.weaponLimits.get(itemId);
    }

    public static void enforceEnchantmentLimits(ServerPlayerEntity player) {
        if (!CONFIG.enchantmentLimitsEnabled || isExempt(player)) {
            return;
        }
        if (CONFIG.enchantmentLimits.isEmpty() && CONFIG.itemEnchantmentOverrides.isEmpty()) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        boolean changed = false;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) {
                continue;
            }
            changed |= enforceEnchantmentLimitsRecursive(stack);
        }

        if (changed) {
            sendNotice(player, NoticeCategory.ENCHANTMENT, "enchantment(s) capped on your items");
        }
    }

    private static boolean enforceEnchantmentLimitsRecursive(ItemStack stack) {
        boolean changed = false;
        changed |= enforceStackEnchantments(stack, DataComponentTypes.ENCHANTMENTS);
        changed |= enforceStackEnchantments(stack, DataComponentTypes.STORED_ENCHANTMENTS);

        // Bundle
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> newContents = new ArrayList<>();
            boolean bundleChanged = false;
            for (ItemStack inner : bundle.iterate()) {
                if (enforceEnchantmentLimitsRecursive(inner)) {
                    bundleChanged = true;
                }
                newContents.add(inner);
            }
            if (bundleChanged) {
                stack.set(DataComponentTypes.BUNDLE_CONTENTS, new BundleContentsComponent(newContents));
                changed = true;
            }
        }

        // Container
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
            container.copyTo(stacks);
            boolean containerChanged = false;
            for (ItemStack inner : stacks) {
                if (enforceEnchantmentLimitsRecursive(inner)) {
                    containerChanged = true;
                }
            }
            if (containerChanged) {
                stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean enforceStackEnchantments(
            ItemStack stack,
            net.minecraft.component.ComponentType<ItemEnchantmentsComponent> componentType
    ) {
        ItemEnchantmentsComponent enchants = stack.getOrDefault(componentType, ItemEnchantmentsComponent.DEFAULT);
        if (enchants.isEmpty()) {
            return false;
        }

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        Map<String, Integer> itemOverrides = CONFIG.itemEnchantmentOverrides.get(itemId);
        ItemEnchantmentsComponent.Builder builder = null;

        for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
            String enchId = entry.getKey().map(k -> k.getValue().toString()).orElse(null);
            if (enchId == null) {
                continue;
            }

            Integer limit = itemOverrides != null ? itemOverrides.get(enchId) : null;
            if (limit != null && limit == -1) {
                // Explicitly no cap for this item, overriding global cap
                continue;
            }

            if (limit == null) {
                limit = CONFIG.enchantmentLimits.get(enchId);
            }
            if (limit == null || limit == -1) {
                continue;
            }

            int current = enchants.getLevel(entry);
            if (current <= limit) {
                continue;
            }

            if (builder == null) {
                builder = new ItemEnchantmentsComponent.Builder(enchants);
            }
            builder.set(entry, limit);
            LOGGER.debug("[Warden] {} on {} capped: {} -> {}", enchId, itemId, current, limit);
        }

        if (builder != null) {
            stack.set(componentType, builder.build());
            return true;
        }
        return false;
    }

    public static void enforceEffectLimits(ServerPlayerEntity player) {
        if (!CONFIG.effectLimitsEnabled || isExempt(player) || CONFIG.effectLimits.isEmpty()) {
            return;
        }

        List<StatusEffectInstance> effects = new ArrayList<>(player.getStatusEffects());
        for (StatusEffectInstance instance : effects) {
            RegistryEntry<StatusEffect> effectType = instance.getEffectType();
            String effectId = effectType.getKey().map(k -> k.getValue().toString()).orElse(null);
            if (effectId == null) {
                continue;
            }

            WardenConfig.EffectLimitConfig limit = CONFIG.effectLimits.get(effectId);
            if (limit == null) {
                continue;
            }

            int amplifier = instance.getAmplifier();
            int duration = instance.getDuration();

            if (limit.maxDuration == 0 || limit.maxLevel == 0) {
                player.removeStatusEffect(effectType);
                sendNotice(player, NoticeCategory.EFFECT, shortId(effectId) + " blocked");
                LOGGER.debug("[Warden] Removed blocked effect {} from {}", effectId, player.getName().getString());
                continue;
            }

            int newAmplifier = (limit.maxLevel > 0 && amplifier + 1 > limit.maxLevel) ? limit.maxLevel - 1 : amplifier;
            int newDuration = duration;
            if (!shouldSkipDurationCap(instance) && limit.maxDuration > 0 && (duration == -1 || duration > limit.maxDuration)) {
                newDuration = limit.maxDuration;
            }

            if (newAmplifier != amplifier || newDuration != duration) {
                player.removeStatusEffect(effectType);
                player.addStatusEffect(new StatusEffectInstance(effectType, newDuration, newAmplifier));
                sendNotice(player, NoticeCategory.EFFECT, shortId(effectId) + " capped");
                LOGGER.debug("[Warden] Capped effect {} for {}: level {}->{}, duration {}->{}",
                        effectId, player.getName().getString(), amplifier + 1, newAmplifier + 1, duration, newDuration);
            }
        }
    }

    public static int limitExperienceGain(ServerPlayerEntity player, int experience) {
        if (CONFIG == null || !CONFIG.xpLimitsEnabled || (player != null && isExempt(player)) || experience <= 0) {
            return experience;
        }

        String source = XP_SOURCE_OVERRIDE.get();
        String context = XP_CONTEXT_OVERRIDE.get();

        if (source == null) {
            source = XP_SOURCE.get();
            context = XP_CONTEXT.get();
        }

        if (context == null) {
            context = "";
        }

        Integer limit = null;
        boolean specificOverride = false;

        Map<String, Integer> sourceOverrides = CONFIG.xpOverrides.get(source);
        if (sourceOverrides != null && !context.isEmpty()) {
            limit = sourceOverrides.get(context);
            if (limit != null) specificOverride = true;
        }

        if (limit == null) {
            limit = CONFIG.xpLimits.get(source);
            if (limit != null) specificOverride = true;
        }

        if (limit == null && !source.equals("all")) {
            limit = CONFIG.xpLimits.get("all");
        }

        // -1 means explicitly NO limit, overriding global caps
        if (specificOverride && limit != null && limit == -1) {
            return experience;
        }

        if (limit != null && limit >= 0 && experience > limit) {
            String notice = context.isEmpty() ? source : shortId(context);
            if (player != null && CONFIG.xpActionBarEnabled) {
                sendNotice(player, NoticeCategory.XP, "XP Dropped (" + notice + ") capped at " + limit);
            }
            return limit;
        }

        return experience;
    }

    public static void runWithXpSource(String source, Runnable runnable) {
        runWithXpContext(source, "", runnable);
    }

    public static void runWithXpContext(String source, String context, Runnable runnable) {
        String prevSource = XP_SOURCE.get();
        String prevContext = XP_CONTEXT.get();
        XP_SOURCE.set(source);
        XP_CONTEXT.set(context);
        try {
            runnable.run();
        } finally {
            XP_SOURCE.set(prevSource);
            XP_CONTEXT.set(prevContext);
        }
    }
}
