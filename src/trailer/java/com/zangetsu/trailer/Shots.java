package com.zangetsu.trailer;

import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.trailer.TrailerCamera.Pose;
import com.zangetsu.trailer.TrailerCamera.Shot;
import java.util.Comparator;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Словарь камеры: точки (неподвижные, едущие, на орбите, привязанные к мобу) и планы из них. Все точки —
 * относительно центра площадки ({@link Stage#center}), он известен только после подготовки мира.
 */
public final class Shots {
    /** Точка как функция от доли плана (0..1) и доли тика. */
    @FunctionalInterface
    public interface P {
        Vec3d at(float t, float tickDelta);

        default P plus(double x, double y, double z) {
            return (t, d) -> this.at(t, d).add(x, y, z);
        }
    }

    private Shots() {
    }

    // ---------------------------------------------------------------- точки

    public static P p(double x, double y, double z) {
        return (t, d) -> Stage.at(x, y, z);
    }

    /** Едет по прямой от (x0, y0, z0) к (x1, y1, z1). */
    public static P move(double x0, double y0, double z0, double x1, double y1, double z1) {
        return (t, d) -> Stage.at(MathHelper.lerp(t, x0, x1), MathHelper.lerp(t, y0, y1), MathHelper.lerp(t, z0, z1));
    }

    /** По дуге вокруг центра: радиус, угол (0 — юг, за спиной героя; 180 — север, перед ним), высота. */
    public static P arc(double r0, double a0, double h0, double r1, double a1, double h1) {
        return (t, d) -> TrailerCamera.around(Stage.center, MathHelper.lerp(t, r0, r1), MathHelper.lerp(t, a0, a1), MathHelper.lerp(t, h0, h1));
    }

    public static P arc(double r, double a, double h) {
        return arc(r, a, h, r, a, h);
    }

    /** По дуге вокруг другой точки. */
    public static P arcAround(P center, double r0, double a0, double h0, double r1, double a1, double h1) {
        return (t, d) -> TrailerCamera.around(center.at(t, d), MathHelper.lerp(t, r0, r1), MathHelper.lerp(t, a0, a1),
                MathHelper.lerp(t, h0, h1));
    }

    /** Ноги моба по имени (плавно, по доле тика); пока его нет — запасная точка. */
    public static P mob(String name, P fallback) {
        return (t, d) -> {
            Vec3d pos = TrailerCamera.entityPos(Stage.id(name), d);
            return pos != null ? pos : fallback.at(t, d);
        };
    }

    public static P hero() {
        return (t, d) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            return client.player != null ? client.player.getLerpedPos(d) : Stage.center;
        };
    }

    /** Ближайший к центру летящий или воткнутый Зангецу. */
    public static P sword(P fallback) {
        return (t, d) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                return fallback.at(t, d);
            }
            Vec3d c = Stage.center;
            return client.world.getEntitiesByClass(ThrownZangetsuEntity.class, Box.of(c, 120, 120, 120), e -> true).stream()
                    .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(c)))
                    .map(e -> e.getLerpedPos(d)).orElseGet(() -> fallback.at(t, d));
        };
    }

    /**
     * Точка у фигуры в небе: смещение в её ростах (фигура стоит лицом на юг, к герою; ноги — в 0, голова — около 1.9).
     */
    public static P spirit(double x, double y, double z) {
        return (t, d) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ZangetsuSpiritEntity spirit = client.world == null ? null
                    : client.world.getEntitiesByClass(ZangetsuSpiritEntity.class, Box.of(Stage.center, 120, 120, 120), e -> true)
                            .stream().findFirst().orElse(null);
            Vec3d base = spirit != null ? spirit.getLerpedPos(d) : Stage.at(0, 13, -18);
            float scale = spirit != null ? spirit.getSpiritScale() : 4.0F;
            return base.add(x * scale, y * scale, z * scale);
        };
    }

    /** Глаз меча на крупном плане. */
    public static P eye(double x, double y, double z) {
        return (t, d) -> {
            Insert.Spec spec = Insert.current();
            return spec == null ? Stage.at(0, 60, 0) : spec.eyeWorld().add(x, y, z);
        };
    }

    /** Смягчённая цель: камера-оператор ведёт объект с небольшим опозданием. */
    public static P follow(P target, float lag) {
        Vec3d[] last = new Vec3d[1];
        float[] lastT = {-1.0F};
        return (t, d) -> {
            Vec3d now = target.at(t, d);
            if (last[0] == null || t < lastT[0]) {
                last[0] = now;
            } else {
                last[0] = last[0].lerp(now, lag);
            }
            lastT[0] = t;
            return last[0];
        };
    }

    // ---------------------------------------------------------------- планы

    public static Shot shot(P from, P to, float fov) {
        return shot(from, to, fov, fov, 0.0F, 0.0F, false);
    }

    public static Shot shot(P from, P to, float fov0, float fov1) {
        return shot(from, to, fov0, fov1, 0.0F, 0.0F, false);
    }

    /** Камера в {@code from}, смотрит на {@code to}; угол обзора и крен меняются за план; {@code ease} — плавный разгон. */
    public static Shot shot(P from, P to, float fov0, float fov1, float roll0, float roll1, boolean ease) {
        return (t, v, d) -> {
            float k = ease ? TrailerCamera.ease(t) : t;
            return Pose.look(from.at(k, d), to.at(k, d), MathHelper.lerp(k, fov0, fov1), MathHelper.lerp(k, roll0, roll1));
        };
    }

    /** Облёт перед съёмкой: широкий угол, камера крутится над центром — так строятся все куски мира вокруг. */
    public static Shot sweep(Supplier<Vec3d> center) {
        long start = System.nanoTime();
        return (t, v, d) -> {
            double s = (System.nanoTime() - start) / 1.0E9;
            Vec3d c = center.get();
            if (c == null) {
                return null;
            }
            float pitch = (float) (25.0 + 20.0 * Math.sin(s * 0.7));
            return new Pose(c.add(0.0, 10.0, 0.0), (float) (s * 140.0 % 360.0), pitch, 110.0F, 0.0F);
        };
    }
}
