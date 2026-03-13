package com.serverlimits;

import com.serverlimits.config.ServerLimitsConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ServerLimitsMod implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("ServerLimits");
    public static ServerLimitsConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = ServerLimitsConfig.load();
        LOGGER.info("[ServerLimits] Loaded. Explosion limits: {}, Item limits: {}",
                CONFIG.explosionLimitsEnabled, CONFIG.itemLimitsEnabled);

        registerItemLimitTick();
        ServerLimitsCommand.register();
    }

    private void registerItemLimitTick() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            // Only run every N ticks
            if (server.getTicks() % CONFIG.checkIntervalTicks != 0) return;

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                enforceItemLimits(player);
            }
        });
    }

    public static void enforceItemLimits(ServerPlayerEntity player) {
        if (!CONFIG.itemLimitsEnabled) return;

        PlayerInventory inv = player.getInventory();
        // Count totals per item type across all slots
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (CONFIG.itemLimits.containsKey(id)) {
                counts.merge(id, stack.getCount(), Integer::sum);
            }
        }

        // For items that exceed the limit, clear excess from inventory (back to front)
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String itemId = entry.getKey();
            int total = entry.getValue();
            int limit = CONFIG.itemLimits.get(itemId);
            if (total <= limit) continue;

            int excess = total - limit;
            // Walk inventory back-to-front, trimming excess
            for (int i = inv.size() - 1; i >= 0 && excess > 0; i--) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty()) continue;
                if (!Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) continue;

                int drop = Math.min(stack.getCount(), excess);
                ItemStack dropped = stack.copyWithCount(drop);
                // Drop item at player's feet
                player.dropStack(player.getEntityWorld(), dropped);
                stack.decrement(drop);
                if (stack.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
                excess -= drop;
            }

            LOGGER.debug("[ServerLimits] Dropped {} excess {} from {}",
                    total - limit, itemId, player.getName().getString());
        }
    }
}
