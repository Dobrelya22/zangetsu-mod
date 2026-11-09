package com.zangetsu.shots.neoforge;

import com.zangetsu.shots.ZangetsuShots;
import net.minecraft.client.MinecraftClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Проверочная съёмка на NeoForge: тот же сценарий, что и на Fabric. ./gradlew :neoforge:runShots */
@Mod(value = "zangetsu_shots", dist = Dist.CLIENT)
public final class ShotsNeoForge {
    public ShotsNeoForge() {
        if (!ZangetsuShots.enabled()) {
            return;
        }
        ZangetsuShots shots = new ZangetsuShots();
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> ZangetsuShots.serverTick());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> shots.clientTick(MinecraftClient.getInstance()));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                shots.frame(MinecraftClient.getInstance());
            }
        });
    }
}
