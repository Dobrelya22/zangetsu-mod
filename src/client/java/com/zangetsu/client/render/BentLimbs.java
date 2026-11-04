package com.zangetsu.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

/**
 * Настоящие локти и колени. В модели игрока конечность — один брусок, поэтому в сценах, где она должна согнуться (встать на колено, поднести руку
 * к лицу), брусок разрезается ({@link SliceConsumer}) на две половины, и нижняя поворачивается вокруг сустава. Половины заходят друг в друга на
 * толщину конечности: на сгибе нет дыры. Сама конечность в этот кадр спрятана ({@link PlayerPoses}).
 */
final class BentLimbs {
    /** Сустав от шарнира конечности (пиксели): брусок руки идёт от −2 до 10, ноги — от 0 до 12. */
    private static final float ELBOW = 4.0F, KNEE = 6.0F;
    /** Насколько половины заходят за сустав. */
    private static final float OVERLAP = 2.0F;

    private BentLimbs() {
    }

    /** {@code bends} — угол сгиба и виден ли верхний слой скина для правой руки, левой руки, правой ноги и левой ноги (см. {@link PlayerPoses#bends}). */
    static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, PlayerEntityModel<?> model, Identifier skin, float[] bends) {
        limb(matrices, consumers, light, model.rightArm, bends[4] > 0.0F ? model.rightSleeve : null, ELBOW, bends[0], skin);
        limb(matrices, consumers, light, model.leftArm, bends[5] > 0.0F ? model.leftSleeve : null, ELBOW, bends[1], skin);
        limb(matrices, consumers, light, model.rightLeg, bends[6] > 0.0F ? model.rightPants : null, KNEE, bends[2], skin);
        limb(matrices, consumers, light, model.leftLeg, bends[7] > 0.0F ? model.leftPants : null, KNEE, bends[3], skin);
    }

    private static void limb(MatrixStack matrices, VertexConsumerProvider consumers, int light, ModelPart part, ModelPart overlayPart, float joint, float angle, Identifier skin) {
        if (Math.abs(angle) < PlayerPoses.MIN_BEND) {
            return;
        }
        int overlay = OverlayTexture.DEFAULT_UV;
        RenderLayer layer = RenderLayer.getEntityTranslucent(skin);
        matrices.push();
        part.rotate(matrices);
        ModelTransform savedPart = part.getTransform();
        ModelTransform savedOverlay = overlayPart == null ? null : overlayPart.getTransform();
        // Поворот уже в матрице: сами части рисуем в нуле.
        part.setTransform(ModelTransform.NONE);
        part.visible = true;
        if (overlayPart != null) {
            overlayPart.setTransform(ModelTransform.NONE);
            overlayPart.visible = true;
        }
        SliceConsumer upper = new SliceConsumer(consumers.getBuffer(layer), matrices.peek(), new float[][]{{0.0F, -1.0F, 0.0F, -(joint + OVERLAP)}});
        part.render(matrices, upper, light, overlay);
        if (overlayPart != null) {
            overlayPart.render(matrices, upper, light, overlay);
        }
        matrices.translate(0.0F, joint / 16.0F, 0.0F);
        matrices.multiply(new Quaternionf().rotationX(angle));
        matrices.translate(0.0F, -joint / 16.0F, 0.0F);
        SliceConsumer lower = new SliceConsumer(consumers.getBuffer(layer), matrices.peek(), new float[][]{{0.0F, 1.0F, 0.0F, joint - OVERLAP}});
        part.render(matrices, lower, light, overlay);
        if (overlayPart != null) {
            overlayPart.render(matrices, lower, light, overlay);
        }
        part.setTransform(savedPart);
        part.visible = false;
        if (overlayPart != null) {
            overlayPart.setTransform(savedOverlay);
            overlayPart.visible = false;
        }
        matrices.pop();
    }
}
