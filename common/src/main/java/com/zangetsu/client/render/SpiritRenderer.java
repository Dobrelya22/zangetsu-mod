package com.zangetsu.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.List;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Яхве: человек в огромном плаще с капюшоном. Плащ, лицо, рубашка и волосы неподвижны и лежат в видеопамяти ({@link YhwachModel}), шейдер колышет ткань на ветру.
 * Здесь: живые глаза, руки с пальцами, меч, выходящий из груди, и рука на клинке в сцене поломки. Масштаб сущности — блоков на метр модели. Числа кисти те же, что в tools/blender/yhwach.py.
 */
public class SpiritRenderer extends EntityRenderer<ZangetsuSpiritEntity> {
    private static final Identifier NONE = ZangetsuMod.id("textures/misc/white.png");
    /** Свет в осях модели, как солнце в рендерах Blender: спереди, слева, сверху. */
    private static final Vector3f LIGHT = new Vector3f(-0.414F, 0.469F, 0.78F).normalize();
    private static final float SHOULDER_X = 0.34F, SHOULDER_Y = 1.5F, HAND_SIZE = 1.55F;
    private static final float[][] KNUCKLE = {{0.034F, -0.118F, 0.002F}, {0.012F, -0.126F, 0.004F}, {-0.011F, -0.121F, 0.003F}, {-0.032F, -0.107F, 0.0F}};
    private static final float[][] PHALANX = {{0.06F, 0.038F, 0.028F}, {0.066F, 0.042F, 0.03F}, {0.06F, 0.038F, 0.028F}, {0.046F, 0.028F, 0.023F}, {0.052F, 0.042F, 0.033F}};
    private static final float[] SPREAD = {15.0F, 4.0F, -7.0F, -22.0F};
    /** Пальцы сгибаются не одинаково: указательный первым раскрывается, мизинец сильнее подгибается. */
    private static final float[] FAN = {0.82F, 0.94F, 1.06F, 1.18F};
    private static final float[][] OPEN = {{10.0F, 16.0F, 12.0F}, {8.0F, 14.0F, 10.0F}, {10.0F, 16.0F, 12.0F}, {12.0F, 18.0F, 14.0F}};

    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private final ItemRenderer itemRenderer;
    private float reveal = 1.0F;
    private final ItemStack sword = new ItemStack(ModItems.ZANGETSU);

    public SpiritRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.sword.set(ModComponents.EYE, Eye.OPEN);
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0F;
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void render(ZangetsuSpiritEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float presence = entity.presence(tickDelta);
        if (presence <= 0.002F) {
            return;
        }
        float time = entity.age + tickDelta;
        float scale = entity.getSpiritScale();
        Vec3d toCamera = this.dispatcher.camera.getPos().subtract(entity.getLerpedPos(tickDelta));
        float bodyYaw = entity.isBreakScene() ? entity.getYaw() : this.facing(entity, toCamera);
        boolean breaking = entity.isBreakScene();
        Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix()).rotateY(-bodyYaw * MathHelper.RADIANS_PER_DEGREE).rotateX(breaking ? -YhwachHand.lean(time) : 0.0F).scale(scale);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(model);
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        if (!YhwachModel.begin(view, LIGHT, time / 20.0F)) {
            return;
        }
        // Камера в осях модели: туда смотрят глаза.
        Vec3d rel = toCamera;
        float rad = bodyYaw * MathHelper.RADIANS_PER_DEGREE, cos = MathHelper.cos(rad), sin = MathHelper.sin(rad);
        float camX = (float) (rel.x * cos + rel.z * sin) / scale, camY = (float) rel.y / scale, camZ = (float) (-rel.x * sin + rel.z * cos) / scale;

        this.reveal = presence >= 1.0F ? 1.0F : presence * 1.05F;
        YhwachModel.set("Reveal", this.reveal);
        this.cloth(view, proj, breaking ? YhwachHand.ripple(time) : 0.0F);
        this.person(view, proj);
        this.eyes(view, proj, entity, time, tickDelta, camX, camY, camZ);
        if (entity.isBreakScene()) {
            this.gripArm(view, proj, time);
        } else {
            float arms = entity.armsShown(tickDelta);
            if (arms > 0.01F) {
                this.arms(view, proj, time, arms);
            }
        }
        YhwachModel.end();
        if (entity.isBreakScene()) {
            YhwachHand.render(entity, tickDelta, matrices, consumers, this.itemRenderer);
        }
        this.holdingSword(entity, tickDelta, bodyYaw, scale, matrices, consumers);
    }

    /** Он поворачивается к зрителю медленно, с запаздыванием, как и подобает такой громаде. Отдельно для каждого кадра, потому что камера движется плавно. */
    private float facing(ZangetsuSpiritEntity entity, Vec3d toCamera) {
        float wanted = (float) Math.toDegrees(Math.atan2(-toCamera.x, toCamera.z));
        long now = System.nanoTime();
        if (entity.faceNanos == 0L) {
            entity.faceYaw = wanted;
        } else {
            float dt = MathHelper.clamp((now - entity.faceNanos) / 1.0E9F, 0.0F, 0.1F);
            entity.faceYaw += MathHelper.wrapDegrees(wanted - entity.faceYaw) * (1.0F - (float) Math.exp(-dt * 1.4F));
        }
        entity.faceNanos = now;
        return entity.faceYaw;
    }

    /** Меч выходит из груди, из выреза рубашки, и растёт, пока не вылетит. */
    private void holdingSword(ZangetsuSpiritEntity entity, float tickDelta, float bodyYaw, float scale, MatrixStack matrices, VertexConsumerProvider consumers) {
        int hold = entity.getHold();
        if (hold <= 0) {
            return;
        }
        float out = ease((entity.age + tickDelta - entity.holdAt) / hold);
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.translate(0.0F, (1.38F - out * 0.1F) * scale, (0.16F + out * 0.3F) * scale);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-16.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 3.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
        float size = scale * (0.1F + 0.3F * out);
        matrices.scale(size, size, size);
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        this.itemRenderer.renderItem(this.sword, ModelTransformationMode.NONE, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.getWorld(), entity.getId());
        EyeTracker.pop(previous);
        matrices.pop();
    }

    /** Правая рука достаётся из внутреннего кармана, вытягивается ладонью к мечу, а поймав его, смыкается: обух в ладони, пальцы по одной плоскости клинка, большой палец по другой. */
    private void gripArm(Matrix4f view, Matrix4f proj, float time) {
        YhwachHand.Grip grip = YhwachHand.grip(time);
        if (grip.track() <= 0.001F) {
            return;
        }
        YhwachArms.Pose pose = YhwachArms.reach(grip.track());
        float hold = grip.hold(), clench = grip.clench();
        Vector3f wrist = new Vector3f(pose.wrist()).add(grip.offset());
        Vector3f fingers = new Vector3f(pose.fingers()).lerp(new Vector3f(0.0F, 0.0F, 1.0F), hold);
        Vector3f back = new Vector3f(pose.back()).lerp(new Vector3f(0.0F, 1.0F, 0.0F), hold);
        float curl = pose.curl() * (1.0F - hold) + grip.beckon();
        float[] bend = {curl * 58.0F + 62.0F * hold + 24.0F * clench, curl * 62.0F + 28.0F * hold + 54.0F * clench, curl * 40.0F + 22.0F * hold + 48.0F * clench};
        float thumb = pose.thumb() * 30.0F * (1.0F - hold) + 22.0F * hold + 34.0F * clench;
        this.posedArm(view, proj, -1.0F, wrist, pose.pole(), fingers, back, YhwachHand.HAND_SIZE, bend, thumb, MathHelper.lerp(hold, pose.spread(), 0.55F), grip.kick());
    }

    /** Рука целиком: плечо стоит на месте, локоть считается, рукав гнётся. */
    private void posedArm(Matrix4f view, Matrix4f proj, float side, Vector3f wrist, Vector3f pole, Vector3f fingers, Vector3f back, float handSize, float[] bend, float thumb, float spread, float kick) {
        Vector3f shoulder = new Vector3f(side * SHOULDER_X, SHOULDER_Y, 0.0F);
        Vector3f elbow = YhwachArms.elbow(shoulder, wrist, pole);
        Matrix4f upper = YhwachArms.upper(shoulder, elbow);
        YhwachModel.material(0.3F, 0.25F, 0.5F, 0.42F, 0.48F);
        YhwachModel.set("Sway", 0.04F);
        YhwachModel.bend(new Matrix4f(upper).invert().mul(YhwachArms.fore(shoulder, elbow, wrist)));
        YhwachModel.drawRimmed("arm", new Matrix4f(view).mul(upper), proj, 0.006F, 0.0085F, 0.46F, 0.05F, 0.14F, true);
        YhwachModel.straight();
        YhwachModel.set("Sway", 0.0F);
        Matrix4f basis = YhwachArms.hand(wrist, fingers, back).rotateX(-kick);
        this.hand(new Matrix4f(view).mul(basis).scale(handSize), proj, bend, thumb, spread, side > 0.0F);
    }

    private void cloth(Matrix4f view, Matrix4f proj, float ripple) {
        YhwachModel.material(0.3F, 0.25F, 0.5F, 0.42F, 0.48F);
        YhwachModel.set("Sway", 0.07F + ripple);
        YhwachModel.drawCloth("cloak", view, proj, 0.03F, 0.037F, 0.46F, 0.05F, 0.14F);
        YhwachModel.material(0.2F, 0.015F, 0.66F, 0.52F, 0.6F);
        YhwachModel.set("Sway", 0.03F);
        YhwachModel.draw("hair", view, proj, 0.004F, false);
        YhwachModel.set("Sway", 0.0F);
    }

    /** Порог проявления для однослойной части: {@code extra} подобран по замеру шума, чтобы она исчезала вместе с двухслойным плащом. */
    private void layer(float extra) {
        YhwachModel.set("Reveal", this.reveal >= 1.0F ? 1.0F : Math.min(1.0F, this.reveal + extra * Math.min(1.0F, this.reveal / 0.15F)));
    }

    private void person(Matrix4f view, Matrix4f proj) {
        YhwachModel.material(0.03F, 0.015F, 0.9F, 0.82F, 0.86F);
        this.layer(0.05F);
        YhwachModel.draw("neck", view, proj, 0.0F, false);
        this.layer(0.14F);
        YhwachModel.draw("head", view, proj, 0.0008F, false);
        YhwachModel.material(0.05F, 0.015F, 0.82F, 0.84F, 0.94F);
        this.layer(0.05F);
        YhwachModel.draw("shirt", view, proj, 0.0F, true);
        YhwachModel.material(0.02F, 0.015F, 0.9F, 0.9F, 0.95F);
        YhwachModel.draw("collar", view, proj, 0.0F, true);
        YhwachModel.material(0.2F, 0.015F, 0.7F, 0.6F, 0.7F);
        this.layer(0.2F);
        YhwachModel.draw("moustache", view, proj, 0.0F, false);
        YhwachModel.set("Reveal", this.reveal);
    }

    private void eyes(Matrix4f view, Matrix4f proj, ZangetsuSpiritEntity entity, float time, float tickDelta, float camX, float camY, float camZ) {
        YhwachModel.material(0.04F, 0.015F, 0.88F, 0.8F, 0.82F);
        List<float[]> eyes = YhwachModel.eyes();
        for (int index = 0; index < eyes.size(); index++) {
            float open = this.openness(entity, index, eyes.size(), time);
            if (open <= 0.02F) {
                continue;
            }
            float[] e = eyes.get(index);
            // Веко: глаз сжимается к своей средней линии, а не проваливается.
            Matrix4f basis = new Matrix4f(e[3], e[4], e[5], 0.0F, e[6], e[7], e[8], 0.0F, e[9], e[10], e[11], 0.0F, e[0], e[1], e[2], 1.0F);
            Matrix4f lid = new Matrix4f(view).mul(basis).scale(1.0F, open, 1.0F).mul(new Matrix4f(basis).invert());
            YhwachModel.draw("eye_" + index, lid, proj, 0.0F, false);
            float[] gaze = this.gaze(entity, index, time, tickDelta, e, camX, camY, camZ);
            float x = gaze[0] * e[12] * 0.5F, y = gaze[1] * e[13] * 0.5F;
            Matrix4f shifted = new Matrix4f(lid).translate(e[3] * x + e[6] * y, e[4] * x + e[7] * y, e[5] * x + e[8] * y);
            YhwachModel.draw("pupil_" + index, shifted, proj, 0.0F, false);
        }
    }

    /** Насколько открыт глаз: открывается по очереди, живёт своими морганиями, на прощание закрывается навсегда. */
    private float openness(ZangetsuSpiritEntity entity, int index, int count, float time) {
        float open = ease((time - entity.eyesAt() - index * ZangetsuSpiritEntity.EYE_OPEN_STEP) / 8.0F);
        int phase = entity.getPhase();
        if (phase == ZangetsuSpiritEntity.DISSOLVING) {
            return 0.0F;
        }
        if (phase == ZangetsuSpiritEntity.CLOSING) {
            open *= 1.0F - ease((time - entity.phaseAt - (count - 1 - index) * ZangetsuSpiritEntity.EYE_CLOSE_STEP) / 7.0F);
        }
        float period = 56.0F + hash(index, 1) * 74.0F;
        float t = time + hash(index, 2) * 200.0F;
        int cycle = MathHelper.floor(t / period);
        float local = t - cycle * period - period * 0.6F;
        float closed = blink(local);
        if (hash(index, cycle) < 0.3F) {
            closed = Math.max(closed, blink(local - 9.0F));
        }
        return open * (1.0F - closed);
    }

    private static float blink(float t) {
        if (t < 0.0F || t > 7.0F) {
            return 0.0F;
        }
        return t < 2.5F ? ease(t / 2.5F) : 1.0F - ease((t - 2.5F) / 4.5F);
    }

    /** Куда смотрит зрачок, в долях белка: либо бегает сам резкими перебежками, либо следит за зрителем. */
    private float[] gaze(ZangetsuSpiritEntity entity, int index, float time, float tickDelta, float[] e, float camX, float camY, float camZ) {
        float tx = camX - e[0], ty = camY - e[1], tz = camZ - e[2];
        float length = Math.max(1.0E-3F, MathHelper.sqrt(tx * tx + ty * ty + tz * tz));
        float toX = (tx * e[3] + ty * e[4] + tz * e[5]) / length, toY = (tx * e[6] + ty * e[7] + tz * e[8]) / length;

        float period = 26.0F + hash(index, 3) * 46.0F;
        float t = time + hash(index, 4) * 200.0F;
        int segment = MathHelper.floor(t / period);
        float move = ease((t - segment * period) / 3.0F);
        float roamX = MathHelper.lerp(move, hash(index, segment + 90) - 0.5F, hash(index, segment + 91) - 0.5F) * 1.2F;
        float roamY = MathHelper.lerp(move, hash(index + 40, segment + 90) - 0.5F, hash(index + 40, segment + 91) - 0.5F) * 1.0F;

        float k = ease((entity.age + tickDelta - entity.phaseAt - index) / 3.0F);
        float stare = MathHelper.lerp(k, stareOf(entity.previousPhase, index), stareOf(entity.getPhase(), index));
        return new float[]{MathHelper.clamp(MathHelper.lerp(stare, roamX, toX), -0.7F, 0.7F), MathHelper.clamp(MathHelper.lerp(stare, roamY, toY), -0.7F, 0.7F)};
    }

    /** На игрока или нет: пока он просто наблюдает, часть глаз всё равно бегает по сторонам. */
    private static float stareOf(int phase, int index) {
        return switch (phase) {
            case ZangetsuSpiritEntity.ROAM -> 0.0F;
            case ZangetsuSpiritEntity.WATCH -> index % 3 == 2 ? 0.0F : 1.0F;
            default -> 1.0F;
        };
    }

    /** Руки разведены и подняты, ладони к зрителю, как на кадре снизу. {@code raised} от 0 (под плащом) до 1. */
    /** Обе руки достаются из внутренних карманов и вытягиваются перед ним. Левая чуть опережает правую: одинаковые руки выглядят как механизм. */
    private void arms(Matrix4f view, Matrix4f proj, float time, float raised) {
        float idle = MathHelper.sin(time * 0.037F) * 0.02F * raised;
        this.raisedArm(view, proj, -1.0F, MathHelper.clamp(raised * 1.07F - 0.07F, 0.0F, 1.0F), idle);
        this.raisedArm(view, proj, 1.0F, MathHelper.clamp(raised * 1.07F, 0.0F, 1.0F), -idle);
    }

    private void raisedArm(Matrix4f view, Matrix4f proj, float side, float u, float idle) {
        if (u <= 0.001F) {
            return;
        }
        YhwachArms.Pose pose = YhwachArms.raise(u, side);
        Vector3f wrist = new Vector3f(pose.wrist()).add(side * idle, idle * 0.4F, 0.0F);
        float curl = pose.curl();
        this.posedArm(view, proj, side, wrist, pose.pole(), pose.fingers(), pose.back(), HAND_SIZE, new float[]{curl * 58.0F, curl * 62.0F, curl * 40.0F}, pose.thumb() * 30.0F, pose.spread(), 0.0F);
    }

    private static void glove(String name, Matrix4f matrix, Matrix4f proj) {
        YhwachModel.drawRimmed(name, matrix, proj, 0.0035F, 0.005F, 0.46F, 0.05F, 0.14F, false);
    }

    /** Кисть: ладонь и пятнадцать фаланг, каждая со своим суставом. {@code bend}: на сколько градусов сверх покоя согнут каждый сустав пальцев. */
    private void hand(Matrix4f wrist, Matrix4f proj, float[] bend, float thumbBend, float spread, boolean left) {
        float mirror = left ? -1.0F : 1.0F;
        // Перчатка под цвет плаща: на свету винная, в тени почти чёрная.
        YhwachModel.material(0.32F, 0.03F, 0.42F, 0.36F, 0.4F);
        glove(left ? "palm_l" : "palm_r", wrist, proj);
        for (int f = 0; f < 4; f++) {
            Matrix4f joint = new Matrix4f(wrist).translate(KNUCKLE[f][0] * mirror, KNUCKLE[f][1], KNUCKLE[f][2]).rotateZ(mirror * SPREAD[f] * spread * MathHelper.RADIANS_PER_DEGREE);
            for (int j = 0; j < 3; j++) {
                joint.rotateX((OPEN[f][j] + bend[j] * FAN[f]) * MathHelper.RADIANS_PER_DEGREE);
                glove("f" + f + "_" + j, joint, proj);
                joint.translate(0.0F, -PHALANX[f][j], 0.0F);
            }
        }
        // Большой палец отставлен и смотрит к ладони.
        Matrix4f thumb = new Matrix4f(wrist).translate(0.034F * mirror, -0.026F, -0.006F).rotateY(mirror * 26.0F * MathHelper.RADIANS_PER_DEGREE).rotateZ(mirror * 54.0F * MathHelper.RADIANS_PER_DEGREE);
        float[] thumbRest = {6.0F, 14.0F, 10.0F};
        for (int j = 0; j < 3; j++) {
            thumb.rotateX((thumbRest[j] + thumbBend) * MathHelper.RADIANS_PER_DEGREE);
            glove("f4_" + j, thumb, proj);
            thumb.translate(0.0F, -PHALANX[4][j], 0.0F);
        }
    }

    @Override
    public boolean shouldRender(ZangetsuSpiritEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public Identifier getTexture(ZangetsuSpiritEntity entity) {
        return NONE;
    }
}
