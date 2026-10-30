package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Шлейфы ударов — светящиеся дуги, которые клинок оставляет в воздухе: стальной у обычного удара, золотой,
 * когда меч рубит сам, кроваво-алый у Гецуги. Со стороны дуга лежит в плоскости удара вокруг груди бойца;
 * глазами игрока — у самого клинка, поперёк взгляда, там, где проходит меч. Голова дуги несётся вслед за
 * клинком, хвост догоняет и гаснет. Лента всегда повёрнута к камере и светится (складывается со светом
 * позади), поэтому видна и на снегу, и в темноте.
 */
public final class SlashTrails {
    public static final int STEEL = 0x8EA6D8;
    public static final int GOLD = 0xE09424;
    public static final int BLOOD = 0xC00C14;
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/misc/slash.png");
    private static final int SEGMENTS = 24;

    /**
     * Дуга в плоскости осей {@code a} (угол 0) и {@code b} (угол 90): центр, радиус, углы от/до, цвет, задержка до
     * начала, время взмаха и угасания (тики), ширина ленты (доля радиуса).
     */
    private record Trail(Vec3d center, Vector3f a, Vector3f b, float radius, float from, float to, int color, float start, float delay,
            float sweep, float life, float width) {
    }

    private static final List<Trail> TRAILS = new ArrayList<>();

    private SlashTrails() {
    }

    /** Дуга вокруг бойца: взгляд {@code yaw}, наклон плоскости {@code roll} (минус — правый край выше); угол плюс — влево. */
    private static void around(Vec3d center, float yaw, float roll, float radius, float from, float to, int color, float delay, float sweep,
            float life, float width) {
        float y = yaw * MathHelper.RADIANS_PER_DEGREE, r = roll * MathHelper.RADIANS_PER_DEGREE;
        Vector3f forward = new Vector3f(-MathHelper.sin(y), 0.0F, MathHelper.cos(y));
        Vector3f left = new Vector3f(MathHelper.cos(y), 0.0F, MathHelper.sin(y));
        Vector3f side = new Vector3f(left).mul(MathHelper.cos(r)).add(0.0F, MathHelper.sin(r), 0.0F);
        add(new Trail(center, forward, side, radius, from, to, color, now(), delay, sweep, life, width));
    }

    /**
     * Дуга глазами игрока: вокруг кулака (справа внизу экрана), поперёк взгляда; угол 0 — вверх, плюс — влево.
     * Совпадает с путём клинка в анимациях от первого лица.
     */
    private static void inView(PlayerEntity player, float right, float down, float radius, float from, float to, int color, float delay,
            float sweep, float life) {
        Vec3d look = player.getRotationVec(1.0F);
        Vector3f f = new Vector3f((float) look.x, (float) look.y, (float) look.z);
        Vector3f r = new Vector3f(f).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f up = new Vector3f(r).cross(f).normalize();
        Vector3f left = new Vector3f(r).negate();
        Vec3d center = player.getEyePos().add(look.multiply(1.05)).add(r.x * right, r.y * right, r.z * right).add(-up.x * down, -up.y * down, -up.z * down);
        add(new Trail(center, up, left, radius, from, to, color, now(), delay, sweep, life, 0.3F));
    }

    private static void add(Trail trail) {
        if (TRAILS.size() > 64) {
            TRAILS.remove(0);
        }
        TRAILS.add(trail);
    }

    /** Свой удар от первого лица — дуга у клинка, иначе — вокруг бойца. */
    private static boolean firstPerson(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        return player == client.player && client.options.getPerspective().isFirstPerson();
    }

    /** Обычный удар мечом: стальная дуга в плоскости замаха (вариант — диагональ, снизу вверх, горизонталь). */
    public static void swing(PlayerEntity player, int variant) {
        if (firstPerson(player)) {
            switch (variant) {
                case 1 -> inView(player, 0.35F, 0.45F, 1.0F, 110.0F, -55.0F, STEEL, 1.2F, 1.9F, 3.5F);
                case 2 -> inView(player, 0.05F, 0.35F, 1.0F, -100.0F, 100.0F, STEEL, 1.2F, 1.9F, 3.5F);
                default -> inView(player, 0.35F, 0.45F, 1.0F, -55.0F, 110.0F, STEEL, 1.2F, 1.9F, 3.5F);
            }
            return;
        }
        Vec3d chest = player.getPos().add(0.0, 1.15, 0.0);
        float yaw = player.getHeadYaw();
        switch (variant) {
            case 1 -> around(chest, yaw, -34.0F, 2.0F, 75.0F, -85.0F, STEEL, 1.2F, 1.9F, 4.0F, 0.26F);
            case 2 -> around(chest, yaw, -6.0F, 2.1F, -85.0F, 85.0F, STEEL, 1.2F, 1.9F, 4.0F, 0.24F);
            default -> around(chest, yaw, -38.0F, 2.0F, -80.0F, 85.0F, STEEL, 1.2F, 1.9F, 4.0F, 0.26F);
        }
    }

    /** Меч рубит сам: золотая дуга к цели ({@code toward} — угол к ней относительно взгляда). */
    public static void auto(PlayerEntity player, float toward, int variant) {
        if (firstPerson(player)) {
            inView(player, 0.35F, 0.45F, 1.0F, -55.0F, 110.0F, GOLD, 2.0F, 1.6F, 4.0F);
            return;
        }
        Vec3d chest = player.getPos().add(0.0, 1.15, 0.0);
        float yaw = player.getHeadYaw() + toward;
        boolean down = variant % 2 == 0;
        around(chest, yaw, down ? -32.0F : 28.0F, 2.5F, down ? -75.0F : 75.0F, down ? 75.0F : -75.0F, GOLD, 2.0F, 1.6F, 5.0F, 0.3F);
    }

    /** Гецуга: широкая алая дуга горизонтального взмаха — на отпускании ({@code delay} тиков после начала замаха). */
    public static void getsuga(PlayerEntity player, float delay) {
        if (firstPerson(player)) {
            inView(player, 0.05F, 0.35F, 1.05F, -100.0F, 100.0F, BLOOD, delay, 2.2F, 6.0F);
            return;
        }
        Vec3d chest = player.getPos().add(0.0, 1.25, 0.0);
        around(chest, player.getHeadYaw(), -8.0F, 3.2F, -95.0F, 95.0F, BLOOD, delay, 2.4F, 7.0F, 0.4F);
    }

    private static float now() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0.0F : client.world.getTime() + client.getRenderTickCounter().getTickDelta(false);
    }

    public static void clear() {
        TRAILS.clear();
    }

    public static void render(WorldRenderContext context) {
        if (TRAILS.isEmpty() || context.consumers() == null) {
            return;
        }
        float time = now();
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        // Свет складывается с тем, что позади: на снегу дуга не сереет, в темноте горит.
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEyes(TEXTURE));
        Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            Trail trail = it.next();
            float t = time - trail.start() - trail.delay();
            if (t > trail.sweep() + trail.life() || t < -40.0F) {
                it.remove();
                continue;
            }
            if (t < 0.0F) {
                continue;
            }
            matrices.push();
            matrices.translate(trail.center().x - camera.x, trail.center().y - camera.y, trail.center().z - camera.z);
            Vector3f eye = new Vector3f((float) (camera.x - trail.center().x), (float) (camera.y - trail.center().y), (float) (camera.z - trail.center().z));
            draw(matrices.peek(), vertices, trail, t, eye);
            matrices.pop();
        }
    }

    private static void draw(MatrixStack.Entry entry, VertexConsumer vertices, Trail trail, float t, Vector3f eye) {
        float sweep = MathHelper.clamp(t / trail.sweep(), 0.0F, 1.0F);
        float head = 1.0F - (1.0F - sweep) * (1.0F - sweep);
        float fade = t < trail.sweep() ? 0.0F : (t - trail.sweep()) / trail.life();
        float tail = MathHelper.lerp(fade, Math.max(0.0F, head - 0.62F), head);
        float alpha = 1.0F - fade * fade;
        if (head - tail < 0.01F || alpha <= 0.0F) {
            return;
        }
        int r = (trail.color() >> 16) & 0xFF, g = (trail.color() >> 8) & 0xFF, b = trail.color() & 0xFF;
        float mid = trail.radius() * (1.0F - trail.width() * 0.5F), half = trail.radius() * trail.width() * 0.5F;
        Vector3f[] centers = new Vector3f[SEGMENTS + 1], widths = new Vector3f[SEGMENTS + 1];
        Vector3f previous = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            float s = MathHelper.lerp((float) i / SEGMENTS, tail, head);
            Vector3f dir = arc(trail, s);
            Vector3f tangent = new Vector3f(arc(trail, s + 0.01F)).sub(dir);
            centers[i] = new Vector3f(dir).mul(mid);
            // Ширина — поперёк дуги и взгляда, светлой кромкой наружу; к хвосту лента сужается.
            Vector3f w = new Vector3f(tangent).cross(new Vector3f(centers[i]).sub(eye));
            if (w.lengthSquared() < 1.0E-8F) {
                w.set(dir);
            }
            w.normalize();
            // Сторона ленты не перескакивает между соседними кусками (иначе — изломы-треугольники).
            if (previous == null ? w.dot(dir) < 0.0F : w.dot(previous) < 0.0F) {
                w.negate();
            }
            previous = new Vector3f(w);
            widths[i] = w.mul(half * MathHelper.lerp((float) i / SEGMENTS, 0.35F, 1.0F));
        }
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = (float) i / SEGMENTS, a1 = (float) (i + 1) / SEGMENTS;
            Vector3f n = new Vector3f(eye).sub(centers[i]).normalize();
            Vector3f outer0 = new Vector3f(centers[i]).add(widths[i]), inner0 = new Vector3f(centers[i]).sub(widths[i]);
            Vector3f outer1 = new Vector3f(centers[i + 1]).add(widths[i + 1]), inner1 = new Vector3f(centers[i + 1]).sub(widths[i + 1]);
            // Светящийся слой отбрасывает обратные грани, а дуга может идти в любую сторону — рисуем обе.
            vertex(entry, vertices, outer0, a0, 0.0F, r, g, b, alpha * a0, n);
            vertex(entry, vertices, inner0, a0, 1.0F, r, g, b, alpha * a0, n);
            vertex(entry, vertices, inner1, a1, 1.0F, r, g, b, alpha * a1, n);
            vertex(entry, vertices, outer1, a1, 0.0F, r, g, b, alpha * a1, n);
            vertex(entry, vertices, outer1, a1, 0.0F, r, g, b, alpha * a1, n);
            vertex(entry, vertices, inner1, a1, 1.0F, r, g, b, alpha * a1, n);
            vertex(entry, vertices, inner0, a0, 1.0F, r, g, b, alpha * a0, n);
            vertex(entry, vertices, outer0, a0, 0.0F, r, g, b, alpha * a0, n);
        }
    }

    private static Vector3f arc(Trail trail, float s) {
        float angle = MathHelper.lerp(s, trail.from(), trail.to()) * MathHelper.RADIANS_PER_DEGREE;
        return new Vector3f(trail.a()).mul(MathHelper.cos(angle)).add(new Vector3f(trail.b()).mul(MathHelper.sin(angle)));
    }

    /** Вершина ленты; свет складывается, поэтому яркость (а не прозрачность) гаснет к хвосту. */
    private static void vertex(MatrixStack.Entry entry, VertexConsumer vertices, Vector3f p, float u, float v, int r, int g, int b, float a,
            Vector3f n) {
        float k = MathHelper.clamp(a, 0.0F, 1.0F);
        vertices.vertex(entry.getPositionMatrix(), p.x, p.y, p.z).color((int) (r * k), (int) (g * k), (int) (b * k), 255)
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, n.x, n.y, n.z);
    }
}
