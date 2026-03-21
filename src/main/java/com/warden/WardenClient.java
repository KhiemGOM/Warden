package com.warden;

import com.warden.net.WeaponLimitsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class WardenClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WeaponLimitsSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> WardenMod.applySyncedWeaponLimits(payload.json())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                WardenMod.clearSyncedWeaponLimits());
    }
}
