package com.zangetsu.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Настоящие локти и колени. В модели игрока конечность — один брусок, поэтому в сценах она рисуется особо: участок вокруг сустава нарезается на тонкие
 * ломтики и плавно изгибается дугой, а всё, что ниже, идёт дальше прямо. Брусок остаётся цельным: ни щелей на сгибе, ни торчащих углов, просто скруглённое
 * колено. Сама конечность в этот кадр спрятана ({@link PlayerPoses}).
 */
final class BentLimbs {
    /** Сустав от шарнира конечности (пиксели): брусок руки идёт от −2 до 10, ноги — от 0 до 12. */
    static final float ELBOW = 4.0F, KNEE = 6.0F;
    /** Длина дуги на единицу угла. Радиус дуги должен быть больше половины толщины конечности (с верхним слоем скина — 2,25 пикселя), иначе сгиб вывернется. */
    private static final float ARC_PER_ANGLE = 2.4F;
    private static final int SLICES = 6;

    private BentLimbs() {
    }

    /** Куда попало поперечное сечение прямого бруска на высоте {@code y} после сгиба: {поворот, новая высота, новая глубина} (пиксели). */
    private static float[] section(float joint, float angle, float y) {
        float size = Math.abs(angle), band = ARC_PER_ANGLE * size, from = joint - band * 0.5F;
        if (size < PlayerPoses.MIN_BEND || y <= from) {
            return new float[]{0.0F, y, 0.0F};
        }
        float phi = angle * Math.min(1.0F, (y - from) / band);
        float sin = MathHelper.sin(phi), cos = MathHelper.cos(phi), past = Math.max(0.0F, y - from - band);
        return new float[]{phi, from + ARC_PER_ANGLE * Math.abs(sin) + past * cos, Math.signum(angle) * ARC_PER_ANGLE * (1.0F - cos) + past * sin};
    }

    /** Перейти от прямой конечности к её согнутому виду на высоте {@code y}: то, что надето на неё здесь (кандалы, огонь), идёт вместе с ней. */
    static void toSection(MatrixStack matrices, float joint, float angle, float y) {
        float[] s = section(joint, angle, y);
        if (s[0] != 0.0F) {
            matrices.translate(0.0F, s[1] / 16.0F, s[2] / 16.0F);
            matrices.multiply(new Quaternionf().rotationX(s[0]));
            matrices.translate(0.0F, -y / 16.0F, 0.0F);
        }
    }

    /** Точка на оси согнутой конечности (пиксели). */
    static Vector3f axisPoint(float x, float joint, float angle, float y) {
        float[] s = section(joint, angle, y);
        return new Vector3f(x, s[1], s[2]);
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
        BendConsumer bent = new BendConsumer(consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), matrices.peek(), joint, angle);
        part.render(matrices, bent, light, overlay);
        if (overlayPart != null) {
            overlayPart.render(matrices, bent, light, overlay);
        }
        part.setTransform(savedPart);
        part.visible = false;
        if (overlayPart != null) {
            overlayPart.setTransform(savedOverlay);
            overlayPart.visible = false;
        }
        matrices.pop();
    }

    /** Принимает обычные грани бруска, режет их поперёк оси на ломтики и отдаёт дальше уже изогнутыми. */
    private static final class BendConsumer implements VertexConsumer {
        private record Point(float x, float y, float z, float u, float v) {
        }

        private final VertexConsumer out;
        private final Matrix4f toView, toLocal;
        private final Matrix3f normalToView, normalToLocal;
        private final float angle, sign, radius, from, band;
        private final Point[] quad = new Point[4];
        private int count;

        BendConsumer(VertexConsumer out, MatrixStack.Entry entry, float joint, float angle) {
            this.out = out;
            this.toView = new Matrix4f(entry.getPositionMatrix());
            this.toLocal = new Matrix4f(this.toView).invert();
            this.normalToView = new Matrix3f(entry.getNormalMatrix());
            this.normalToLocal = new Matrix3f(this.normalToView).invert();
            this.angle = angle;
            this.sign = Math.signum(angle);
            this.band = ARC_PER_ANGLE * Math.abs(angle);
            this.radius = this.band / Math.abs(angle);
            this.from = joint - this.band * 0.5F;
        }

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            Vector3f p = this.toLocal.transformPosition(x, y, z, new Vector3f()).mul(16.0F);
            this.quad[this.count++] = new Point(p.x, p.y, p.z, u, v);
            if (this.count == 4) {
                this.count = 0;
                Vector3f normal = this.normalToLocal.transform(nx, ny, nz, new Vector3f());
                float step = this.band / SLICES;
                for (int i = -1; i <= SLICES; i++) {
                    float low = i < 0 ? Float.NEGATIVE_INFINITY : this.from + i * step;
                    float high = i == SLICES ? Float.POSITIVE_INFINITY : this.from + (i + 1) * step;
                    List<Point> piece = clip(clip(List.of(this.quad), low, true), high, false);
                    for (int k = 1; k < piece.size() - 1; k += 2) {
                        this.emit(piece.get(0), color, overlay, light, normal);
                        this.emit(piece.get(k), color, overlay, light, normal);
                        this.emit(piece.get(k + 1), color, overlay, light, normal);
                        this.emit(piece.get(Math.min(k + 2, piece.size() - 1)), color, overlay, light, normal);
                    }
                }
            }
        }

        /** Оставить часть многоугольника выше ({@code above}) или ниже плоскости поперёк оси. */
        private static List<Point> clip(List<Point> polygon, float y, boolean above) {
            if (Float.isInfinite(y)) {
                return polygon;
            }
            List<Point> result = new ArrayList<>(polygon.size() + 1);
            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
                float da = above ? a.y() - y : y - a.y(), db = above ? b.y() - y : y - b.y();
                if (da >= 0.0F) {
                    result.add(a);
                }
                if ((da > 0.0F && db < 0.0F) || (da < 0.0F && db > 0.0F)) {
                    float t = da / (da - db);
                    result.add(new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t,
                            a.u() + (b.u() - a.u()) * t, a.v() + (b.v() - a.v()) * t));
                }
            }
            return result;
        }

        private void emit(Point p, int color, int overlay, int light, Vector3f normal) {
            float s = MathHelper.clamp((p.y() - this.from) / this.band, 0.0F, 1.0F);
            float phi = this.angle * s;
            float cos = MathHelper.cos(phi), sin = MathHelper.sin(phi);
            float y = p.y(), z = p.z();
            if (p.y() > this.from) {
                // По дуге вокруг внутренней стороны сгиба; за концом дуги брусок идёт прямо.
                float rho = this.radius - this.sign * p.z();
                float arcY = this.from + rho * Math.abs(sin);
                float arcZ = this.sign * (this.radius - rho * cos);
                float past = Math.max(0.0F, p.y() - (this.from + this.band));
                y = arcY + past * cos;
                z = arcZ + past * sin;
            }
            Vector3f view = this.toView.transformPosition(p.x() / 16.0F, y / 16.0F, z / 16.0F, new Vector3f());
            Vector3f n = this.normalToView.transform(normal.x, normal.y * cos - normal.z * sin, normal.y * sin + normal.z * cos, new Vector3f());
            this.out.vertex(view.x, view.y, view.z, color, p.u(), p.v(), overlay, light, n.x, n.y, n.z);
        }

        // Модели рисуются только через полный vertex(...) выше.

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }
}
