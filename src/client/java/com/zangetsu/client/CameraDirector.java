package com.zangetsu.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Камера в сценах (касание рукояти, казнь, превращение, сон): отходит от первого лица и медленно
 * облетает игрока сбоку, чтобы были видны и он, и меч, и тот, кого рубят. Положение камеры ставит
 * {@link com.zangetsu.client.mixin.CameraMixin}.
 */
public final class CameraDirector {
    /** Ракурс: угол облёта (от взгляда игрока) в начале и в конце, наклон и расстояние. */
    public record Shot(float yawFrom, float yawTo, float pitch, float distance) {
    }

    private static Perspective saved;
    private static int start = -1;
    private static int until = -1;
    private static Shot shot;

    private CameraDirector() {
    }

    public static void begin(int duration, Shot newShot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (until < 0) {
            saved = client.options.getPerspective();
        }
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        shot = newShot;
        start = ClientState.ticks;
        until = ClientState.ticks + duration;
    }

    /** Угол облёта и прочее для текущего кадра (null — сцены нет, камера обычная). */
    public static float[] frame(Entity focused, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (until < 0 || shot == null || focused != client.player) {
            return null;
        }
        float t = MathHelper.clamp((ClientState.ticks + tickDelta - start) / Math.max(1, until - start), 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        return new float[]{MathHelper.lerp(eased, shot.yawFrom(), shot.yawTo()), shot.pitch(), shot.distance()};
    }

    static void tick(MinecraftClient client) {
        if (until >= 0 && ClientState.ticks >= until) {
            if (saved != null) {
                client.options.setPerspective(saved);
            }
            until = -1;
            shot = null;
        }
    }

    static void reset() {
        until = -1;
        saved = null;
        shot = null;
    }
}
