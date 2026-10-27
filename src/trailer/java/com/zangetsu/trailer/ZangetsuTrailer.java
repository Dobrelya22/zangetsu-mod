package com.zangetsu.trailer;

import com.zangetsu.client.render.EyeTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Трейлер мода: игра сама создаёт мир, разыгрывает сцены и снимает их кино-камерой кадр за кадром
 * (60 кадров в секунду ролика). Включается флагом -Dzangetsu.trailer=true (./gradlew runTrailer), в jar
 * мода не попадает. Монтаж, цвет и музыку сводит tools/trailer/edit.sh.
 */
public class ZangetsuTrailer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("zangetsu.trailer")) {
            return;
        }
        Director.init(Script.build());
        ClientTickEvents.END_CLIENT_TICK.register(Director::onClientTick);
        HudRenderCallback.EVENT.register(Overlay::render);
        WorldRenderEvents.AFTER_ENTITIES.register(Insert::render);
        EyeTracker.nanos = Director::eyeNanos;
    }
}
