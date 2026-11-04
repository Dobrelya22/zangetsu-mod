package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Настоящий огонь: зацикленная покадровая анимация пламени (см. {@code tools/textures.cs}), которую показывают полотна, всегда развёрнутые к камере. Пламя растёт вверх по миру,
 * как бы ни была повёрнута рука, и изгибается встречным ветром. Сначала рисуется полупрозрачное тело с тёмно-алым краем, оно не выцветает на светлом снегу, поверх него светом —
 * раскалённая серединка. Несколько полотен с разных точек сливаются в один сгусток огня.
 */
final class Flames {
    static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    /** Тело пламени (с прозрачностью) и его раскалённая серединка на чёрном фоне. Кадры в обеих лежат одинаково. */
    static final Identifier BODY = ZangetsuMod.id("textures/misc/fire_body.png");
    static final Identifier CORE = ZangetsuMod.id("textures/misc/fire_core.png");
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int COLUMNS = 8, ROWS = 8, FRAMES = COLUMNS * ROWS;
    /** Скорость анимации: кадров на тик времени огня. От жара время бежит быстрее. */
    private static final float FRAMES_PER_TICK = 1.5F;
    /** На сколько частей делится полотно по высоте, чтобы ветер его изгибал. */
    private static final int STRIPS = 3;

    private static final RenderLayer BODY_LAYER = new SmoothLayer("zangetsu_fire_body", RenderLayer.getEntityTranslucentEmissive(BODY), BODY, true);
    private static final RenderLayer CORE_LAYER = new SmoothLayer("zangetsu_fire_core", RenderLayer.getEyes(CORE), CORE, false);

    /** В инвентаре модель нарисована на экране: там «вверх» направлен против оси y, а смотрят на неё вдоль оси z. */
    static final Vector3f SCREEN_UP = new Vector3f(0.0F, -1.0F, 0.0F), SCREEN_VIEW = new Vector3f(0.0F, 0.0F, 1.0F);

    /** Где сейчас рисуем: матрица, куда в этих координатах «вверх» и куда сносит ветром, и время. */
    static final class Frame {
        final Matrix4f matrix;
        final Vector3f up, wind, a, b;
        final Vector3f spaceUp, spaceWind;
        /** Куда смотреть на огонь, если камера не в начале координат (null — камера в начале, как и в мире, и от первого лица). */
        final Vector3f view;
        /** Сколько единиц пространства в единице матрицы. */
        final float unit;
        final float time;
        /** От жара пламя выше и бьётся сильнее. */
        final float scale, sway;

        /** {@code spaceUp} и {@code spaceWind} — вверх по миру и ветер в пространстве, где стоит матрица (мир без поворота камеры либо вид камеры). */
        Frame(MatrixStack.Entry entry, Vector3f spaceUp, Vector3f spaceWind, float time, float heat) {
            this(entry, spaceUp, spaceWind, time, heat, null);
        }

        Frame(MatrixStack.Entry entry, Vector3f spaceUp, Vector3f spaceWind, float time, float heat, Vector3f view) {
            this.matrix = entry.getPositionMatrix();
            Matrix4f inverse = new Matrix4f(this.matrix).invert();
            this.up = inverse.transformDirection(new Vector3f(spaceUp)).normalize();
            this.wind = inverse.transformDirection(new Vector3f(spaceWind));
            this.a = new Vector3f(this.up).cross(Math.abs(this.up.x) < 0.8F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
            this.b = new Vector3f(this.up).cross(this.a).normalize();
            this.spaceUp = new Vector3f(spaceUp).normalize();
            this.spaceWind = new Vector3f(spaceWind);
            this.view = view;
            this.unit = this.matrix.transformDirection(0.0F, 1.0F, 0.0F, new Vector3f()).length();
            this.time = time;
            this.scale = 1.0F + 0.4F * heat;
            this.sway = 1.0F + 0.8F * heat;
        }
    }

    private Flames() {
    }

    static RenderLayer bodyLayer() {
        return BODY_LAYER;
    }

    static RenderLayer coreLayer() {
        return CORE_LAYER;
    }

    /** Обычный слой игры, только текстура сглажена: стандартные слои при каждой отрисовке сбрасывают её на резкие пиксели, а огонь вблизи от этого становится мозаикой. */
    private static final class SmoothLayer extends RenderLayer {
        SmoothLayer(String name, RenderLayer base, Identifier texture, boolean crumbling) {
            super(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, crumbling, true, () -> {
                base.startDrawing();
                MinecraftClient.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
            }, () -> {
                MinecraftClient.getInstance().getTextureManager().getTexture(texture).setFilter(false, false);
                base.endDrawing();
            });
        }
    }

    /** Псевдошум от 0 до 1: плавный, без текстур. */
    private static float noise(float x) {
        return 0.5F + 0.25F * MathHelper.sin(x) + 0.15F * MathHelper.sin(x * 2.3F + 1.7F) + 0.1F * MathHelper.sin(x * 4.1F + 4.1F);
    }

    static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    /**
     * Тело пламени от точки (x, y, z) (в единицах матрицы): {@code height} и {@code width} — размер полотна, {@code seed} делает каждое пламя своим, {@code power} — его непрозрачность.
     * Слой — светящийся полупрозрачный ({@link #BODY}).
     */
    static void flame(VertexConsumer out, Frame f, float x, float y, float z, float height, float width, float seed, float power) {
        sprite(out, f, x, y, z, height, width, seed, (Math.min(255, (int) (power * 255.0F)) << 24) | 0xFFFFFF, false);
    }

    /** Раскалённая серединка того же пламени: те же числа, что и у {@link #flame}, слой — свет ({@link #CORE}). {@code power} — яркость. */
    static void glow(VertexConsumer out, Frame f, float x, float y, float z, float height, float width, float seed, float power) {
        int bright = Math.min(255, (int) (power * 255.0F));
        sprite(out, f, x, y, z, height, width, seed, 0xFF000000 | (bright << 16) | (bright << 8) | bright, true);
    }

    private static void sprite(VertexConsumer out, Frame f, float x, float y, float z, float height, float width, float seed, int color, boolean bothSides) {
        Vector3f base = f.matrix.transformPosition(x, y, z, new Vector3f());
        Vector3f view = f.view != null ? f.view : new Vector3f(base).negate().normalize();
        Vector3f side = new Vector3f(f.spaceUp).cross(view);
        if (side.lengthSquared() < 1.0E-3F) {
            side.set(1.0F, 0.0F, 0.0F);
        }
        side.normalize();
        float t = f.time;
        int own = (int) (seed * 97.0F);
        // Пламя дышит: то выше, то ниже, у каждого по-своему.
        float tall = height * f.unit * f.scale * (0.82F + 0.18F * noise(t * 0.33F + seed * 11.0F));
        float half = width * f.unit * 0.5F * (0.92F + 0.08F * f.scale);
        float swing = f.sway * 0.08F * tall * MathHelper.sin(t * 0.29F + seed * 7.0F);
        int frame = MathHelper.floor(t * FRAMES_PER_TICK + hash(own, 5) * FRAMES);
        frame = Math.floorMod(frame, FRAMES);
        float u0 = (float) (frame % COLUMNS) / COLUMNS, u1 = u0 + 1.0F / COLUMNS, top = (float) (frame / COLUMNS) / ROWS, cell = 1.0F / ROWS;
        // Половина пламени отражена: одинаковые кадры рядом не заметны.
        if (hash(own, 9) > 0.5F) {
            float swap = u0;
            u0 = u1;
            u1 = swap;
        }
        Vector3f left = null, right = null;
        for (int i = 0; i <= STRIPS; i++) {
            float s = (float) i / STRIPS;
            float along = tall * s, curve = tall * s * s;
            Vector3f center = new Vector3f(base).add(f.spaceUp.x * along + f.spaceWind.x * curve + side.x * swing * s * s,
                    f.spaceUp.y * along + f.spaceWind.y * curve + side.y * swing * s * s, f.spaceUp.z * along + f.spaceWind.z * curve + side.z * swing * s * s);
            Vector3f l = new Vector3f(center).sub(side.x * half, side.y * half, side.z * half), r = new Vector3f(center).add(side.x * half, side.y * half, side.z * half);
            if (left != null) {
                float v0 = top + cell * (1.0F - (float) (i - 1) / STRIPS), v1 = top + cell * (1.0F - s);
                vertex(out, left, color, u0, v0);
                vertex(out, right, color, u1, v0);
                vertex(out, r, color, u1, v1);
                vertex(out, l, color, u0, v1);
                // В слое света обратные грани отбрасываются.
                if (bothSides) {
                    vertex(out, l, color, u0, v1);
                    vertex(out, r, color, u1, v1);
                    vertex(out, right, color, u1, v0);
                    vertex(out, left, color, u0, v0);
                }
            }
            left = l;
            right = r;
        }
    }

    /** Нормаль вверх: этот слой затеняется по ней, а огню тень не нужна. */
    private static void vertex(VertexConsumer out, Vector3f p, int color, float u, float v) {
        out.vertex(p.x, p.y, p.z, color, u, v, OverlayTexture.DEFAULT_UV, FULL_LIGHT, 0.0F, 1.0F, 0.0F);
    }

    /** Искра: крошечный раскалённый ромб. */
    static void spark(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float size, int rgb) {
        int color = 0xFF000000 | rgb;
        float[][] points = {{0.0F, size, 0.0F}, {size, 0.0F, 0.0F}, {0.0F, 0.0F, size}, {-size, 0.0F, 0.0F}, {0.0F, 0.0F, -size}, {0.0F, -size, 0.0F}};
        int[][] faces = {{0, 1, 2}, {0, 2, 3}, {0, 3, 4}, {0, 4, 1}, {5, 2, 1}, {5, 3, 2}, {5, 4, 3}, {5, 1, 4}};
        for (int[] face : faces) {
            for (int k : new int[]{face[0], face[1], face[2], face[2]}) {
                Vector3f view = matrix.transformPosition(x + points[k][0], y + points[k][1], z + points[k][2], new Vector3f());
                out.vertex(view.x, view.y, view.z, color, 0.5F, 0.5F, OverlayTexture.DEFAULT_UV, FULL_LIGHT, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** Искры, летящие вверх от точки: свои у каждого {@code seed}. */
    static void embers(VertexConsumer out, Frame f, float x, float y, float z, float spread, float rise, int count, float seed) {
        for (int i = 0; i < count; i++) {
            float life = f.time * (0.035F + 0.012F * hash(i, 3)) + i * 0.618F + seed;
            int generation = MathHelper.floor(life);
            float p = life - generation;
            float angle = MathHelper.TAU * hash(generation, i + 11);
            float out0 = spread * (0.6F + 0.8F * hash(generation, i + 23));
            float px = x + (f.a.x * MathHelper.cos(angle) + f.b.x * MathHelper.sin(angle)) * out0 + (f.up.x + f.wind.x * p) * rise * p;
            float py = y + (f.a.y * MathHelper.cos(angle) + f.b.y * MathHelper.sin(angle)) * out0 + (f.up.y + f.wind.y * p) * rise * p;
            float pz = z + (f.a.z * MathHelper.cos(angle) + f.b.z * MathHelper.sin(angle)) * out0 + (f.up.z + f.wind.z * p) * rise * p;
            int green = (int) (150.0F * (1.0F - p) * (1.0F - p));
            spark(out, f.matrix, px, py, pz, 0.018F * (1.0F - p * p), (255 << 16) | (green << 8) | (int) (30.0F * (1.0F - p)));
        }
    }
}
