package com.zangetsu.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.client.CallingScene;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Пустота в сцене, где ломается меч: время встало, и мир тает в бледном небе. Рисуется поверх уже нарисованного мира и перед сущностями; когда небо проступило целиком, глубина стирается, поэтому фигуре
 * и мечу не мешают ни стены пещеры, ни холмы. Под игроком остаётся только мягкая тень.
 */
public final class DreamVoid {
    private static final float[] PALE = {0.84F, 0.855F, 0.87F, 1.0F};
    private static boolean shadowsOff;

    private DreamVoid() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        float amount = CallingScene.voidAmount();
        if (amount <= 0.0F) {
            if (shadowsOff) {
                client.getEntityRenderDispatcher().setRenderShadows(client.options.getEntityShadows().getValue());
                shadowsOff = false;
            }
            return;
        }
        if (amount > 0.5F && !shadowsOff) {
            client.getEntityRenderDispatcher().setRenderShadows(false);
            shadowsOff = true;
        }
        float tickDelta = context.tickCounter().getTickDelta(false);
        float time = context.world().getTime() + tickDelta;
        Matrix4f view = context.positionMatrix();
        RenderSystem.disableDepthTest();
        DreamSky.paint(view, PALE, time, amount);
        pool(view, context, tickDelta, amount);
        RenderSystem.enableDepthTest();
        if (amount >= 0.999F) {
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }
    }

    /** Мягкая тень под игроком: он стоит хоть на чём-то. */
    private static void pool(Matrix4f view, WorldRenderContext context, float tickDelta, float amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        Vec3d feet = client.player.getLerpedPos(tickDelta).subtract(context.camera().getPos());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int sides = 48;
        float[][] rings = {{0.0F, 0.62F}, {1.6F, 0.42F}, {4.5F, 0.12F}, {9.0F, 0.0F}};
        for (int r = 0; r < rings.length - 1; r++) {
            for (int i = 0; i < sides; i++) {
                float a0 = MathHelper.TAU * i / sides, a1 = MathHelper.TAU * (i + 1) / sides;
                ring(buffer, view, feet, a0, rings[r], amount);
                ring(buffer, view, feet, a1, rings[r], amount);
                ring(buffer, view, feet, a1, rings[r + 1], amount);
                ring(buffer, view, feet, a0, rings[r + 1], amount);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private static void ring(BufferBuilder buffer, Matrix4f view, Vec3d feet, float angle, float[] ring, float amount) {
        buffer.vertex(view, (float) feet.x + MathHelper.cos(angle) * ring[0], (float) feet.y, (float) feet.z + MathHelper.sin(angle) * ring[0]).color(0.08F, 0.07F, 0.11F, ring[1] * amount);
    }
}
