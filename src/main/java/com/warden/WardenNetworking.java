package com.warden;

import com.google.gson.JsonObject;
import com.warden.config.WardenConfig;
import com.warden.net.WeaponLimitsSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class WardenNetworking {

    private WardenNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(WeaponLimitsSyncPayload.ID, WeaponLimitsSyncPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                syncWeaponLimits(handler.player));
    }

    public static void syncWeaponLimits(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new WeaponLimitsSyncPayload(serializeWeaponLimits()));
    }

    public static void syncWeaponLimits(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncWeaponLimits(player);
        }
    }

    private static String serializeWeaponLimits() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", WardenMod.CONFIG.weaponLimitsEnabled);

        JsonObject items = new JsonObject();
        for (var entry : WardenMod.CONFIG.weaponLimits.entrySet()) {
            WardenConfig.WeaponLimitConfig cfg = entry.getValue();
            if (cfg == null || !cfg.isConfigured()) {
                continue;
            }
            JsonObject item = new JsonObject();
            if (cfg.disableCooldownTicks != null) {
                item.addProperty("disable_cooldown_ticks", cfg.disableCooldownTicks);
            }
            if (cfg.projectileDamage != null) {
                item.addProperty("projectile_damage", cfg.projectileDamage);
            }
            if (cfg.rechargeTicks != null) {
                item.addProperty("recharge_ticks", cfg.rechargeTicks);
            }
            items.add(entry.getKey(), item);
        }

        root.add("items", items);
        return root.toString();
    }
}
