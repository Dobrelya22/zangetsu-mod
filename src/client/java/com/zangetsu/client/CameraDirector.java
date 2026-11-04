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
    /**
     * Ракурс: угол облёта (от взгляда игрока: 0 — из-за спины, 180 — спереди) в начале и в конце, наклон, расстояние в начале и в конце,
     * сдвиг центра вперёд (к цели) и вверх (блоки).
     */
    public record Shot(float yawFrom, float yawTo, float pitch, float distanceFrom, float distanceTo, float forward, float lift) {
        public Shot(float yawFrom, float yawTo, float pitch, float distance, float forward) {
            this(yawFrom, yawTo, pitch, distance, distance, forward, 0.0F);
        }
    }

    /** Монтажная склейка: с какого тика сцены идёт этот ракурс. */
    public record Cut(int from, Shot shot) {
    }

    private static Perspective saved;
    private static int start = -1;
    private static int until = -1;
    private static Cut[] cuts;

    private CameraDirector() {
    }

    public static void begin(int duration, Shot shot) {
        begin(duration, new Cut(0, shot));
    }

    /** Сцена из нескольких ракурсов подряд. */
    public static void begin(int duration, Cut... newCuts) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (until < 0) {
            saved = client.options.getPerspective();
        }
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        cuts = newCuts;
        start = ClientState.ticks;
        until = ClientState.ticks + duration;
    }

    /** Угол облёта и прочее для текущего кадра (null — сцены нет, камера обычная). */
    public static float[] frame(Entity focused, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (until < 0 || cuts == null || focused != client.player) {
            return null;
        }
        float now = ClientState.ticks + tickDelta - start;
        int index = 0;
        while (index + 1 < cuts.length && now >= cuts[index + 1].from()) {
            index++;
        }
        float end = index + 1 < cuts.length ? cuts[index + 1].from() : until - start;
        float t = MathHelper.clamp((now - cuts[index].from()) / Math.max(1.0F, end - cuts[index].from()), 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        Shot shot = cuts[index].shot();
        return new float[]{MathHelper.lerp(eased, shot.yawFrom(), shot.yawTo()), shot.pitch(), MathHelper.lerp(eased, shot.distanceFrom(), shot.distanceTo()), shot.forward(), shot.lift()};
    }

    static void tick(MinecraftClient client) {
        if (until >= 0 && ClientState.ticks >= until) {
            if (saved != null) {
                client.options.setPerspective(saved);
            }
            until = -1;
            cuts = null;
        }
    }

    static void reset() {
        until = -1;
        saved = null;
        cuts = null;
    }
}
