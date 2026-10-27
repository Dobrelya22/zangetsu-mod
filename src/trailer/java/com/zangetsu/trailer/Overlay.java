package com.zangetsu.trailer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

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

    static void render(DrawContext context, RenderTickCounter counter) {
        if (!Director.running()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        int w = client.getWindow().getFramebufferWidth(), h = client.getWindow().getFramebufferHeight();
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
    }
}
