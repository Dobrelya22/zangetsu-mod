package com.zangetsu.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Куда смотрит глаз на мече. Если рядом злой моб — на него; если нет — на того, кто смотрит на меч
 * (на тебя), иногда коротко поглядывая по сторонам. В инвентаре глаз следит за курсором.
 * Взгляд сглаживается и иногда моргает — у каждого держателя свой.
 */
public final class EyeTracker {
    /** Кто сейчас держит отрисовываемый меч (ставят миксин рук и наши отрисовщики). */
    private static Entity holder;
    private static final Map<Integer, float[]> GAZE = new HashMap<>();
    private static long lastNanos;

    private EyeTracker() {
    }

    public static Entity holder() {
        return holder;
    }

    /** Поставить держателя на время отрисовки; вернуть прежнего (для вложенных вызовов). */
    public static Entity push(Entity entity) {
        Entity previous = holder;
        holder = entity;
        return previous;
    }

    public static void pop(Entity previous) {
        holder = previous;
    }

    private static int key(ModelTransformationMode mode) {
        if (mode == ModelTransformationMode.GUI) {
            return Integer.MIN_VALUE;
        }
        return holder != null ? holder.getId() : Integer.MIN_VALUE + 1;
    }

    /**
     * Смещение радужки {x, y} (в пикселях модели) для глаза в точке {@code eyeLocal} модели,
     * которая рисуется с матрицей {@code model} в режиме {@code mode}.
     */
    public static float[] gaze(ModelTransformationMode mode, Matrix4f model, Vector3f eyeLocal) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Vector3f eye = model.transformPosition(new Vector3f(eyeLocal));
        Vector3f target;
        boolean idle = false;
        if (mode == ModelTransformationMode.GUI) {
            // В инвентаре — на курсор (экранные координаты GUI, ось Z — к зрителю).
            double scale = (double) client.getWindow().getScaledWidth() / Math.max(1, client.getWindow().getWidth());
            target = new Vector3f((float) (client.mouse.getX() * scale), (float) (client.mouse.getY() * scale), eye.z + 120.0F);
        } else {
            Vec3d cameraPos = camera.getPos();
            Vec3d world = worldTarget(client, cameraPos, eye, mode);
            idle = world == null;
            Vector3f relative = world == null ? new Vector3f() : new Vector3f((float) (world.x - cameraPos.x),
                    (float) (world.y - cameraPos.y), (float) (world.z - cameraPos.z));
            if (mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) {
                // Руки рисуются в пространстве камеры.
                relative.rotate(new Quaternionf(camera.getRotation()).conjugate());
            }
            target = relative;
        }
        Vector3f direction = target.sub(eye);
        new Matrix4f(model).invert().transformDirection(direction);
        if (direction.lengthSquared() < 1.0E-8F) {
            direction.set(0.0F, 0.0F, 1.0F);
        }
        direction.normalize();
        float x = MathHelper.clamp(direction.x * 1.4F, -0.85F, 0.85F);
        float y = MathHelper.clamp(direction.y * 1.0F, -0.36F, 0.36F);
        int key = key(mode);
        long ticks = client.world != null ? client.world.getTime() : 0L;
        if (idle) {
            // Никого рядом — иногда коротко глядит в сторону.
            long phase = Math.floorMod(ticks + key * 37L, 70L);
            if (phase < 16) {
                long glance = Math.floorDiv(ticks + key * 37L, 70L);
                x = ((glance * 7919L) % 3 - 1) * 0.75F;
                y = ((glance * 104729L) % 3 - 1) * 0.25F;
            }
        }
        // Плавно, независимо от частоты кадров.
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        float[] current = GAZE.computeIfAbsent(key, k -> new float[]{0.0F, 0.0F});
        float k = 1.0F - (float) Math.exp(-dt * 14.0F);
        current[0] += (x - current[0]) * k;
        current[1] += (y - current[1]) * k;
        if (GAZE.size() > 256) {
            GAZE.clear();
        }
        return new float[]{current[0], current[1]};
    }

    /** Злой моб поблизости (null — никого, тогда глаз смотрит на камеру). */
    private static Vec3d worldTarget(MinecraftClient client, Vec3d cameraPos, Vector3f eye, ModelTransformationMode mode) {
        if (client.world == null) {
            return null;
        }
        Vec3d center;
        if (holder != null) {
            center = holder.getEyePos();
        } else if (mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND || mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND) {
            center = cameraPos;
        } else {
            center = cameraPos.add(eye.x, eye.y, eye.z);
        }
        List<MobEntity> mobs = client.world.getEntitiesByClass(MobEntity.class, Box.of(center, 20.0, 12.0, 20.0),
                m -> m instanceof Monster && m.isAlive() && m != holder);
        MobEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MobEntity mob : mobs) {
            double d = mob.squaredDistanceTo(center);
            if (d < bestDistance) {
                bestDistance = d;
                best = mob;
            }
        }
        return best != null ? best.getEyePos() : null;
    }

    /** Насколько открыт глаз с учётом моргания (0 — закрыт). */
    public static float blink(ModelTransformationMode mode, float openness) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || openness <= 0.0F) {
            return openness;
        }
        float t = Math.floorMod(client.world.getTime() + key(mode) * 29L, 97L) + client.getRenderTickCounter().getTickDelta(false);
        if (t < 5.0F) {
            float closed = 1.0F - Math.abs(t - 2.5F) / 2.5F;
            return openness * (1.0F - closed);
        }
        return openness;
    }
}
