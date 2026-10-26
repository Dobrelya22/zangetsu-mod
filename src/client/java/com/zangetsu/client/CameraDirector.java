package com.zangetsu.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/** На время сцены (касание рукояти, казнь, превращение, сон) камера уходит от первого лица. */
public final class CameraDirector {
    private static Perspective saved;
    private static int until = -1;

    private CameraDirector() {
    }

    public static void begin(int duration, Perspective perspective) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (until < 0) {
            saved = client.options.getPerspective();
        }
        client.options.setPerspective(perspective);
        until = Math.max(until, ClientState.ticks + duration);
    }

    static void tick(MinecraftClient client) {
        if (until >= 0 && ClientState.ticks >= until) {
            if (saved != null) {
                client.options.setPerspective(saved);
            }
            until = -1;
        }
    }

    static void reset() {
        until = -1;
        saved = null;
    }
}
