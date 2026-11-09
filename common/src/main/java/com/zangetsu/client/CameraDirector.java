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
    private static Key[] path;
    /** Удары по камере: момент и сила. */
    private static final java.util.List<float[]> KICKS = new java.util.ArrayList<>();
    /** Камера идёт не от игрока: у неё свой поворот, а центр догоняет игрока с запаздыванием и стоит на месте в окне {@code freeFrom}..{@code freeTo}. */
    private static boolean anchored;
    private static float baseYaw, freeFrom, freeTo;
    private static double centerX, centerY, centerZ, chaseX, chaseY, chaseZ, prevChaseX, prevChaseY, prevChaseZ;

    private CameraDirector() {
    }

    public static boolean isActive() {
        return until >= 0;
    }

    /** Точка плавного пролёта: тик сцены и весь ракурс в этот момент. */
    public record Key(float at, float yaw, float pitch, float distance, float forward, float lift) {
        float value(int field) {
            return switch (field) {
                case 0 -> this.yaw;
                case 1 -> this.pitch;
                case 2 -> this.distance;
                case 3 -> this.forward;
                default -> this.lift;
            };
        }
    }

    /** Сцена одним дублем: камера без склеек и остановок летит через все точки. */
    public static void fly(int duration, Key... keys) {
        begin(duration, new Cut(0, new Shot(0.0F, 0.0F, 0.0F, 3.0F, 0.0F)));
        path = keys;
    }

    /** Камеру в эти моменты встряхивает и отбрасывает назад. */
    public static void impacts(float... momentsAndStrengths) {
        KICKS.clear();
        for (int i = 0; i + 1 < momentsAndStrengths.length; i += 2) {
            KICKS.add(new float[]{momentsAndStrengths[i], momentsAndStrengths[i + 1]});
        }
    }

    /** Удар прямо сейчас. */
    public static void kick(float strength) {
        if (until >= 0) {
            KICKS.add(new float[]{ClientState.ticks - start, strength});
        }
    }

    /** Камера сцены с множеством рывков: её поворот считается от {@code yaw}, а центр следует за игроком. */
    public static void anchor(Entity player, float yaw, float from, float to) {
        anchored = true;
        baseYaw = yaw;
        freeFrom = from;
        freeTo = to;
        centerX = chaseX = prevChaseX = player.getX();
        centerY = chaseY = prevChaseY = player.getY();
        centerZ = chaseZ = prevChaseZ = player.getZ();
    }

    public static boolean isAnchored() {
        return anchored;
    }

    public static float baseYaw() {
        return baseYaw;
    }

    /** Точка, вокруг которой строится кадр. */
    public static double[] anchorAt(float tickDelta) {
        float now = ClientState.ticks + tickDelta - start;
        if (now >= freeFrom && now < freeTo) {
            return new double[]{centerX, centerY, centerZ};
        }
        return new double[]{MathHelper.lerp(tickDelta, prevChaseX, chaseX), MathHelper.lerp(tickDelta, prevChaseY, chaseY), MathHelper.lerp(tickDelta, prevChaseZ, chaseZ)};
    }

    private static float[] along(float now) {
        int i = 0;
        while (i + 2 < path.length && now >= path[i + 1].at()) {
            i++;
        }
        Key a = path[i], b = path[i + 1];
        float dt = Math.max(0.001F, b.at() - a.at());
        float s = MathHelper.clamp((now - a.at()) / dt, 0.0F, 1.0F), s2 = s * s, s3 = s2 * s;
        float[] out = new float[5];
        for (int f = 0; f < out.length; f++) {
            out[f] = (2.0F * s3 - 3.0F * s2 + 1.0F) * a.value(f) + (s3 - 2.0F * s2 + s) * dt * slope(i, f) + (3.0F * s2 - 2.0F * s3) * b.value(f) + (s3 - s2) * dt * slope(i + 1, f);
        }
        for (float[] kick : KICKS) {
            float x = now - kick[0];
            if (x >= 0.0F) {
                float power = kick[1] * (float) Math.exp(-x / 7.0F);
                out[0] += power * 2.4F * MathHelper.sin(x * 1.9F);
                out[1] += power * 2.0F * MathHelper.sin(x * 2.6F + 1.0F);
                out[2] *= 1.0F + 0.16F * kick[1] * (float) Math.exp(-x / 5.0F) * (1.0F - (float) Math.exp(-x / 0.8F));
            }
        }
        return out;
    }

    /** Скорость в точке пролёта: на первой и последней нулевая, между ними камера проходит насквозь. */
    private static float slope(int i, int field) {
        if (i == 0 || i == path.length - 1) {
            return 0.0F;
        }
        // Две точки в один тик это монтажная склейка: кадр сменился, скорость через неё не проходит.
        if (path[i + 1].at() - path[i].at() < 0.01F || path[i].at() - path[i - 1].at() < 0.01F) {
            return 0.0F;
        }
        return (path[i + 1].value(field) - path[i - 1].value(field)) / Math.max(0.001F, path[i + 1].at() - path[i - 1].at());
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
        path = null;
        KICKS.clear();
        anchored = false;
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
        if (path != null) {
            return along(now);
        }
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
        if (anchored && client.player != null) {
            prevChaseX = chaseX;
            prevChaseY = chaseY;
            prevChaseZ = chaseZ;
            chaseX += (client.player.getX() - chaseX) * 0.42;
            chaseY += (client.player.getY() - chaseY) * 0.42;
            chaseZ += (client.player.getZ() - chaseZ) * 0.42;
        }
        if (until >= 0 && ClientState.ticks >= until) {
            if (saved != null) {
                client.options.setPerspective(saved);
            }
            until = -1;
            cuts = null;
            path = null;
            anchored = false;
        }
    }

    static void reset() {
        until = -1;
        saved = null;
        cuts = null;
        path = null;
        anchored = false;
    }
}
