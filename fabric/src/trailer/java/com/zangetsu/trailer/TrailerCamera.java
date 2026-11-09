package com.zangetsu.trailer;

import com.zangetsu.trailer.mixin.CameraInvoker;
import java.util.function.DoubleSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Кино-камера трейлера: никакого вида от первого или третьего лица. Каждый кадр камера берёт ракурс у
 * текущего плана (положение, взгляд, угол обзора, крен), добавляет лёгкое «дыхание» ручной камеры и
 * тряску на ударах.
 */
public final class TrailerCamera {
    /** Ракурс: где камера, куда смотрит, угол обзора и крен (градусы). */
    public record Pose(Vec3d pos, float yaw, float pitch, float fov, float roll) {
        public static Pose look(Vec3d from, Vec3d to, float fov) {
            return look(from, to, fov, 0.0F);
        }

        public static Pose look(Vec3d from, Vec3d to, float fov, float roll) {
            double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            return new Pose(from, yaw, pitch, fov, roll);
        }
    }

    /** Ракурс плана как функция от доли плана (0..1) и времени ролика (секунды). */
    @FunctionalInterface
    public interface Shot {
        Pose at(float t, double video, float tickDelta);
    }

    private static volatile boolean active;
    private static volatile Shot shot;
    private static volatile double shotStart;
    private static volatile double shotEnd = 1.0;
    private static DoubleSupplier video = () -> 0.0;
    private static double shakeAt = -100.0;
    private static float shakeStrength;
    private static float fov = 70.0F;
    private static float roll;

    private TrailerCamera() {
    }

    public static boolean isActive() {
        return active && shot != null;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static void setClock(DoubleSupplier clock) {
        video = clock;
    }

    public static void setShot(Shot value, double start, double end) {
        shot = value;
        shotStart = start;
        shotEnd = end;
    }

    /** Тряска на ударе: резкий толчок, быстро затухающий. */
    public static void shake(float strength) {
        shakeAt = video.getAsDouble();
        shakeStrength = strength;
    }

    public static float fov() {
        return fov;
    }

    public static float roll() {
        return roll;
    }

    public static void apply(Camera camera, float tickDelta) {
        Shot current = shot;
        if (!active || current == null) {
            return;
        }
        double v = video.getAsDouble();
        float t = (float) MathHelper.clamp((v - shotStart) / Math.max(1.0E-3, shotEnd - shotStart), 0.0, 1.0);
        Pose p = current.at(t, v, tickDelta);
        if (p == null) {
            return;
        }
        // «Дыхание» ручной камеры и тряска на ударе.
        double since = v - shakeAt;
        double hit = since >= 0.0 ? shakeStrength * Math.exp(-since * 6.0) : 0.0;
        double bx = 0.010 * Math.sin(v * 1.3) + 0.006 * Math.sin(v * 2.9 + 1.1) + hit * 0.10 * Math.sin(v * 71.0);
        double by = 0.008 * Math.sin(v * 1.7 + 0.5) + hit * 0.10 * Math.sin(v * 83.0 + 2.0);
        float yaw = (float) (p.yaw() + 0.22 * Math.sin(v * 0.9) + hit * 1.6 * Math.sin(v * 57.0));
        float pitch = (float) (p.pitch() + 0.18 * Math.sin(v * 1.1 + 0.7) + hit * 1.6 * Math.sin(v * 63.0 + 1.0));
        fov = p.fov();
        roll = (float) (p.roll() + 0.15 * Math.sin(v * 0.7) + hit * 2.5 * Math.sin(v * 49.0));
        ((CameraInvoker) camera).trailer$setPos(p.pos().x + bx, p.pos().y + by, p.pos().z);
        ((CameraInvoker) camera).trailer$setRotation(yaw, pitch);
    }

    // ---------------------------------------------------------------- помощники

    public static float ease(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static Vec3d lerp(float t, Vec3d a, Vec3d b) {
        return a.lerp(b, t);
    }

    /** Положение сущности на клиенте с учётом доли тика (плавно), или null. */
    public static Vec3d entityPos(int id, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || id < 0) {
            return null;
        }
        Entity e = client.world.getEntityById(id);
        return e == null ? null : e.getLerpedPos(tickDelta);
    }

    /** Точка на окружности вокруг центра (угол в градусах, 0 — на юг, +90 — на запад, как yaw игры). */
    public static Vec3d around(Vec3d center, double radius, double angle, double height) {
        double a = Math.toRadians(angle);
        return center.add(-Math.sin(a) * radius, height, Math.cos(a) * radius);
    }
}
