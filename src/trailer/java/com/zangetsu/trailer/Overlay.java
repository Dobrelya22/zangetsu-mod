package com.zangetsu.trailer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * Монтажные эффекты поверх кадра: вспышки на ударах, затемнения и кашетирование 2.39:1.
 * Рисуются прямо в игре — так они попадают ровно в нужный кадр.
 */
public final class Overlay {
    public static final int FLASH = 0;
    public static final int FADE_IN = 1;
    public static final int FADE_OUT = 2;
    public static final int HOLD = 3;
    /** Высота чёрной полосы сверху и снизу при 1080 строках (2.39:1). */
    private static final float BAR = 138.0F / 1080.0F;

    /** Эффект: начало, длительность (секунды ролика), цвет RGB, вид и сила. */
    public record Fx(double start, double duration, int rgb, int kind, float strength) {
        float alpha(double v) {
            double t = (v - this.start) / this.duration;
            if (t < 0.0 || t > 1.0) {
                return 0.0F;
            }
            double a = switch (this.kind) {
                case FLASH -> Math.pow(1.0 - t, 2.2);
                case FADE_IN -> 1.0 - t;
                case FADE_OUT -> t;
                default -> 1.0;
            };
            return (float) (a * this.strength);
        }
    }

    private static final List<Fx> EFFECTS = new ArrayList<>();

    private Overlay() {
    }

    public static void add(Fx fx) {
        EFFECTS.add(fx);
    }

    /** Поверх всего кадра, уже после интерфейса (и вспышек самого мода). */
    public static void render() {
        if (!Director.running()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        int w = client.getWindow().getFramebufferWidth(), h = client.getWindow().getFramebufferHeight();
        // Та же проекция, что у интерфейса: интерфейс уже нарисован, слой ложится поверх.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, (float) (w / scale), (float) (h / scale), 0.0F, 1000.0F, 21000.0F),
                VertexSorter.BY_Z);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();
        DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        context.getMatrices().push();
        context.getMatrices().scale((float) (1.0 / scale), (float) (1.0 / scale), 1.0F);
        double v = Director.video();
        for (Fx fx : EFFECTS) {
            float a = MathHelper.clamp(fx.alpha(v), 0.0F, 1.0F);
            if (a > 0.003F) {
                context.fill(0, 0, w, h, ((int) (a * 255.0F) << 24) | (fx.rgb() & 0xFFFFFF));
            }
        }
        int bar = Math.round(h * BAR);
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        context.getMatrices().pop();
        context.draw();
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }
}
