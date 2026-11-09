package com.zangetsu.trailer;

import com.zangetsu.client.render.EyeTracker;
import com.zangetsu.client.render.ZangetsuItemRenderer;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.function.DoubleUnaryOperator;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Крупный план меча: Зангецу висит в пустоте (высоко над площадкой), камера подходит к самому глазу.
 * Клинок лежит вдоль оси X (рукоять в {@link Spec#pos}, острие — в +X), плоскость с глазом смотрит в +Z;
 * глаз — примерно в 0,47 блока от рукояти.
 */
public final class Insert {
    /** Где глаз относительно рукояти (в блоках, до поворота). */
    public static final Vec3d EYE = new Vec3d(0.469, -0.0375, 0.0375);

    /**
     * Что показать: положение рукояти, поворот, состояние глаза, насколько он открыт (от времени ролика)
     * и смотрит ли он строго в камеру.
     */
    public record Spec(Vec3d pos, Quaternionf rotation, int eye, DoubleUnaryOperator openness, boolean steady, int light) {
        public Vec3d eyeWorld() {
            Vector3f e = new Vector3f((float) EYE.x, (float) EYE.y, (float) EYE.z).rotate(this.rotation);
            return this.pos.add(e.x, e.y, e.z);
        }
    }

    private static final ZangetsuItemRenderer RENDERER = new ZangetsuItemRenderer();
    private static volatile Spec spec;

    private Insert() {
    }

    public static void show(Spec value) {
        spec = value;
    }

    public static Spec current() {
        return spec;
    }

    static void render(WorldRenderContext context) {
        Spec s = spec;
        if (s == null || context.consumers() == null) {
            return;
        }
        ItemStack stack = new ItemStack(ModItems.ZANGETSU);
        stack.set(ModComponents.EYE, s.eye());
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(s.pos().x - camera.x, s.pos().y - camera.y, s.pos().z - camera.z);
        matrices.multiply(s.rotation());
        // Ось клинка в модели — диагональ (1, 1, 0); поворот на -45° кладёт её вдоль X.
        matrices.multiply(new Quaternionf().rotateZ((float) Math.toRadians(-45.0)));
        matrices.translate(-0.5F, -0.5F, -0.5F);
        EyeTracker.forcedOpenness = (float) Math.max(0.0, Math.min(1.0, s.openness().applyAsDouble(Director.video())));
        EyeTracker.steady = s.steady();
        try {
            RENDERER.render(stack, ModelTransformationMode.FIXED, matrices, context.consumers(), s.light(), OverlayTexture.DEFAULT_UV);
        } finally {
            EyeTracker.forcedOpenness = -1.0F;
            EyeTracker.steady = false;
        }
        matrices.pop();
    }

    public static int light(int block, int sky) {
        return LightmapTextureManager.pack(block, sky);
    }
}
