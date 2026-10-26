package com.zangetsu.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.ClientState;
import com.zangetsu.logic.Dream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Серповидная луна: когда кто-то рядом принял форму Пустого (и во сне с мечами), высоко в небе
 * проступает огромный светлый серп с алым ореолом.
 */
public final class CrescentMoon {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/environment/crescent_moon.png");
    private static float shown;
    /** Краснота неба — только в форме Пустого (во сне луна есть, а небо обычное). */
    private static float blood;

    private CrescentMoon() {
    }

    /** Насколько проступила кровавая луна (0..1): небо и туман краснеют вместе с ней. */
    public static float intensity() {
        return blood;
    }

    /** Обычную луну прячем, пока видна кровавая. */
    public static boolean hidesVanillaMoon() {
        return shown > 0.05F;
    }

    private static boolean wanted() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world != null && (!ClientState.HOLLOW.isEmpty() || client.world.getRegistryKey() == Dream.WORLD);
    }

    /** Вызывается из WorldRenderer.renderSky, после солнца и луны, перед звёздами. */
    public static void render(Matrix4f positionMatrix, float tickDelta) {
        // Плавно проступает и гаснет (≈ за секунду).
        float target = wanted() ? 1.0F : 0.0F;
        shown = MathHelper.clamp(shown + (target - shown) * 0.04F, 0.0F, 1.0F);
        blood = MathHelper.clamp(blood + ((ClientState.HOLLOW.isEmpty() ? 0.0F : 1.0F) - blood) * 0.04F, 0.0F, 1.0F);
        if (shown < 0.01F) {
            return;
        }
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-30.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-40.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, shown);
        float k = 38.0F;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(m, -k, 100.0F, -k).texture(0.0F, 0.0F);
        buffer.vertex(m, k, 100.0F, -k).texture(1.0F, 0.0F);
        buffer.vertex(m, k, 100.0F, k).texture(1.0F, 1.0F);
        buffer.vertex(m, -k, 100.0F, k).texture(0.0F, 1.0F);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
