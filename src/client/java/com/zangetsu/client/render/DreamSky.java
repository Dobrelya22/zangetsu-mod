package com.zangetsu.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/** Небо первого сна: бледное, сплошь в медленных облаках. Горизонт того же цвета, что и туман, поэтому холмы тают в нём. */
public final class DreamSky {
    private static final Identifier CLOUDS = ZangetsuMod.id("textures/environment/dream_clouds.png");
    private static final float RADIUS = 100.0F;
    private static final int RINGS = 22, SEGMENTS = 44;
    private static final float BELOW = -12.0F * MathHelper.RADIANS_PER_DEGREE;

    private static float tintR = 1.0F, tintG = 1.0F, tintB = 1.0F;

    private DreamSky() {
    }

    public static void render(WorldRenderContext context) {
        float time = context.world().getTime() + context.tickCounter().getTickDelta(false);
        boolean crimson = context.world().getRegistryKey() == com.zangetsu.logic.Calling.WORLD;
        paint(context.positionMatrix(), RenderSystem.getShaderFogColor(), time, 1.0F, crimson ? 0.5F : 1.0F);
    }

    /** Нарисовать небо вокруг камеры. {@code strength} меньше единицы: сквозь него ещё виден мир. */
    public static void paint(Matrix4f view, float[] fog, float time, float strength) {
        paint(view, fog, time, strength, 1.0F);
    }

    /** {@code clouds} — насколько светлы облака: в тёмном небе они лишь чуть проступают. */
    public static void paint(Matrix4f view, float[] fog, float time, float strength, float clouds) {
        tintR = 1.0F;
        tintG = clouds < 1.0F ? 0.26F : 1.0F;
        tintB = clouds < 1.0F ? 0.46F : 1.0F;
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, strength);
        gradient(view, fog);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, CLOUDS);
        // Верхний слой большой и почти неподвижен, под ним плывут клочья поменьше и потемнее.
        clouds(view, 0.22F, time * 0.00007F, time * 0.00002F, clouds, clouds < 1.0F ? 0.5F : 1.0F);
        clouds(view, 0.5F, time * 0.00017F, -time * 0.00006F, 0.86F * clouds, clouds < 1.0F ? 0.4F : 0.72F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private static void gradient(Matrix4f view, float[] fog) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < RINGS; i++) {
            // Вся сфера: от надира до зенита.
            float e0 = -MathHelper.HALF_PI + MathHelper.PI * i / RINGS, e1 = -MathHelper.HALF_PI + MathHelper.PI * (i + 1) / RINGS;
            for (int j = 0; j < SEGMENTS; j++) {
                float a0 = MathHelper.TAU * j / SEGMENTS, a1 = MathHelper.TAU * (j + 1) / SEGMENTS;
                skyVertex(buffer, view, fog, e0, a0);
                skyVertex(buffer, view, fog, e0, a1);
                skyVertex(buffer, view, fog, e1, a1);
                skyVertex(buffer, view, fog, e1, a0);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static float elevation(int ring) {
        float t = (float) ring / RINGS;
        return BELOW + (MathHelper.HALF_PI - BELOW) * t * t;
    }

    private static void skyVertex(BufferBuilder buffer, Matrix4f view, float[] fog, float elevation, float angle) {
        float dy = MathHelper.sin(elevation), flat = MathHelper.cos(elevation);
        // Зенит чуть темнее и холоднее горизонта.
        float k = MathHelper.clamp(Math.abs(dy) * 1.4F, 0.0F, 1.0F);
        buffer.vertex(view, MathHelper.cos(angle) * flat * RADIUS, dy * RADIUS, MathHelper.sin(angle) * flat * RADIUS)
                .color(fog[0] * (1.0F - k * 0.16F), fog[1] * (1.0F - k * 0.13F), fog[2] * (1.0F - k * 0.08F), 1.0F);
    }

    private static void clouds(Matrix4f view, float scale, float shiftU, float shiftV, float brightness, float alpha) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int i = 0; i < RINGS; i++) {
            float e0 = elevation(i), e1 = elevation(i + 1);
            if (e1 <= 0.0F) {
                continue;
            }
            for (int j = 0; j < SEGMENTS; j++) {
                float a0 = MathHelper.TAU * j / SEGMENTS, a1 = MathHelper.TAU * (j + 1) / SEGMENTS;
                cloudVertex(buffer, view, e0, a0, scale, shiftU, shiftV, brightness, alpha);
                cloudVertex(buffer, view, e0, a1, scale, shiftU, shiftV, brightness, alpha);
                cloudVertex(buffer, view, e1, a1, scale, shiftU, shiftV, brightness, alpha);
                cloudVertex(buffer, view, e1, a0, scale, shiftU, shiftV, brightness, alpha);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void cloudVertex(BufferBuilder buffer, Matrix4f view, float elevation, float angle, float scale, float shiftU, float shiftV, float brightness, float alpha) {
        float dy = MathHelper.sin(elevation), flat = MathHelper.cos(elevation);
        float dx = MathHelper.cos(angle) * flat, dz = MathHelper.sin(angle) * flat;
        // Облака лежат на плоскости над головой: к горизонту сходятся и тают.
        float height = Math.max(0.0F, dy) + 0.22F;
        float fade = MathHelper.clamp((dy - 0.02F) / 0.26F, 0.0F, 1.0F);
        buffer.vertex(view, dx * RADIUS, dy * RADIUS, dz * RADIUS)
                .texture(dx / height * scale + shiftU, dz / height * scale + shiftV)
                .color(brightness * tintR, brightness * tintG, brightness * tintB, alpha * fade * fade * (3.0F - 2.0F * fade));
    }
}
