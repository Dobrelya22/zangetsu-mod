package com.zangetsu.shots.fabric;

import com.zangetsu.shots.ZangetsuShots;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.MinecraftClient;

/** Проверочная съёмка на Fabric: сценарий общий, отсюда только подписка на тики и кадры. */
public class ShotsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!ZangetsuShots.enabled()) {
            return;
        }
        ZangetsuShots shots = new ZangetsuShots();
        ServerTickEvents.END_SERVER_TICK.register(server -> ZangetsuShots.serverTick());
        ClientTickEvents.END_CLIENT_TICK.register(shots::clientTick);
        WorldRenderEvents.LAST.register(context -> shots.frame(MinecraftClient.getInstance()));
    }
}
