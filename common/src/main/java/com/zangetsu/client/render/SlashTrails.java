package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.zangetsu.client.WorldRenderInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Шлейфы ударов — свет, который клинок оставляет в воздухе. Обычные удары оставляют ленту по настоящему пути клинка: стальную,
 * алую в форме Пустого, золотую, когда меч рубит сам. Гецуга — широкая кроваво-алая дуга.
 * Со стороны дуга лежит в плоскости удара вокруг груди бойца;
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
    /** След обычных ударов: сколько тиков живёт и сколько точек помнит. */
    private static final float RIBBON_LIFE = 4.5F;
    private static final int RIBBON_POINTS = 90;
    /** Путь клинка по игрокам: {ближняя точка xyz, дальняя xyz, время, сила, цвет} — в мире. */
    private static final Int2ObjectOpenHashMap<ArrayDeque<float[]>> WORLD_RIBBONS = new Int2ObjectOpenHashMap<>();
    /** Свой след от первого лица — в координатах взгляда: он движется вместе с рукой. */
    private static final ArrayDeque<float[]> VIEW_RIBBON = new ArrayDeque<>();

    private SlashTrails() {
    }

    /** Запомнить, где сейчас клинок удара со стороны (координаты мира). */
    public static void sampleWorld(int id, double nx, double ny, double nz, double fx, double fy, double fz, int color, float strength) {
        ArrayDeque<float[]> points = WORLD_RIBBONS.computeIfAbsent(id, k -> new ArrayDeque<>());
        add(points, new float[]{(float) nx, (float) ny, (float) nz, (float) fx, (float) fy, (float) fz, now(), strength, color});
    }

    /** Запомнить, где сейчас клинок от первого лица (координаты взгляда). */
    public static void sampleView(Vector3f near, Vector3f far, int color, float strength) {
        add(VIEW_RIBBON, new float[]{near.x, near.y, near.z, far.x, far.y, far.z, now(), strength, color});
    }

    private static void add(ArrayDeque<float[]> points, float[] point) {
        points.addLast(point);
        if (points.size() > RIBBON_POINTS) {
            points.removeFirst();
        }
    }

    /** Нарисовать свой след прямо в проходе руки: так он точно совпадает с мечом. */
    public static void renderView(MatrixStack.Entry entry, VertexConsumerProvider consumers) {
        if (VIEW_RIBBON.isEmpty()) {
            return;
        }
        ribbon(entry, consumers.getBuffer(RenderLayer.getEyes(TEXTURE)), VIEW_RIBBON, 0.0, 0.0, 0.0);
    }

    /**
     * Лента между соседними положениями клинка: от острия к середине, свежая часть ярче, старая гаснет. Свет складывается,
     * поэтому гаснет яркость; слой отбрасывает обратные грани, а след идёт в любую сторону — рисуем обе.
     */
    private static void ribbon(MatrixStack.Entry entry, VertexConsumer vertices, ArrayDeque<float[]> points, double ox, double oy, double oz) {
        float time = now();
        while (!points.isEmpty() && (time - points.peekFirst()[6] > RIBBON_LIFE || time < points.peekFirst()[6] - 40.0F)) {
            points.removeFirst();
        }
        float[] a = null;
        for (float[] b : points) {
            if (a != null && b[6] - a[6] < 3.0F) {
                segment(entry, vertices, a, b, time, ox, oy, oz);
            }
            a = b;
        }
    }

    private static void segment(MatrixStack.Entry entry, VertexConsumer vertices, float[] a, float[] b, float time, double ox, double oy, double oz) {
        float ka = (1.0F - (time - a[6]) / RIBBON_LIFE) * a[7], kb = (1.0F - (time - b[6]) / RIBBON_LIFE) * b[7];
        int color = (int) a[8];
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        Vector3f an = new Vector3f((float) (a[0] - ox), (float) (a[1] - oy), (float) (a[2] - oz)), af = new Vector3f((float) (a[3] - ox), (float) (a[4] - oy), (float) (a[5] - oz));
        Vector3f bn = new Vector3f((float) (b[0] - ox), (float) (b[1] - oy), (float) (b[2] - oz)), bf = new Vector3f((float) (b[3] - ox), (float) (b[4] - oy), (float) (b[5] - oz));
        Vector3f n = new Vector3f(af).sub(an).cross(new Vector3f(bf).sub(af));
        if (n.lengthSquared() < 1.0E-12F) {
            n.set(0.0F, 1.0F, 0.0F);
        }
        n.normalize();
        float ua = MathHelper.clamp(ka, 0.0F, 1.0F), ub = MathHelper.clamp(kb, 0.0F, 1.0F);
        vertex(entry, vertices, af, ua, 0.0F, r, g, bl, ka, n);
        vertex(entry, vertices, an, ua, 1.0F, r, g, bl, ka, n);
        vertex(entry, vertices, bn, ub, 1.0F, r, g, bl, kb, n);
        vertex(entry, vertices, bf, ub, 0.0F, r, g, bl, kb, n);
        vertex(entry, vertices, bf, ub, 0.0F, r, g, bl, kb, n);
        vertex(entry, vertices, bn, ub, 1.0F, r, g, bl, kb, n);
        vertex(entry, vertices, an, ua, 1.0F, r, g, bl, ka, n);
        vertex(entry, vertices, af, ua, 0.0F, r, g, bl, ka, n);
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
        WORLD_RIBBONS.clear();
        VIEW_RIBBON.clear();
    }

    public static void render(WorldRenderInfo context) {
        if ((TRAILS.isEmpty() && WORLD_RIBBONS.isEmpty()) || context.consumers() == null) {
            return;
        }
        float time = now();
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        // Свет складывается с тем, что позади: на снегу дуга не сереет, в темноте горит.
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getEyes(TEXTURE));
        var ribbons = WORLD_RIBBONS.values().iterator();
        while (ribbons.hasNext()) {
            ArrayDeque<float[]> points = ribbons.next();
            ribbon(matrices.peek(), vertices, points, camera.x, camera.y, camera.z);
            if (points.isEmpty()) {
                ribbons.remove();
            }
        }
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
