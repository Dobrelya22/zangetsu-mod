package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Меч глазами игрока. В приёмах ({@link SwordMoves}) он не просто ходит по экрану одной стороной к камере: кулак идёт
 * по дуге, клинок разворачивается, лезвие ведёт взмах, видны то плоскость, то обух. В двуручных приёмах вторая рука выходит из-за
 * края экрана и ложится на рукоять. В покое меч чуть дышит. Гецуга — свой замах: клинок уходит высоко за правое плечо,
 * замирает — и проносится по горизонтали.
 */
public final class FirstPersonMotion {
    /** Поза меча: кулак (в блоках от глаз), куда смотрят клинок и лезвие, насколько вторая рука на рукояти. */
    public record Pose(Vector3f fist, Vector3f blade, Vector3f edge, float two, int color, float trail) {
    }

    /** Ключевой кадр Гецуги: доля анимации, сдвиг (блоки) и повороты (градусы) вокруг X, Y, Z относительно покоя. */
    private record Key(float t, float tx, float ty, float tz, float rx, float ry, float rz) {
    }

    private static final Key REST_KEY = new Key(0, 0, 0, 0, 0, 0, 0);
    private static final Key[] GETSUGA = {REST_KEY, new Key(0.33F, 0.12F, 0.25F, 0.1F, -20, 0, -25), new Key(0.37F, 0.13F, 0.26F, 0.11F, -21, 0, -26),
            new Key(0.42F, -0.15F, 0.12F, -0.15F, -60, 0, -34), new Key(0.5F, -0.48F, 0.1F, -0.12F, 0, -30, 62),
            new Key(0.62F, -0.5F, 0.08F, -0.08F, 0, -30, 64), new Key(1.0F, 0, 0, 0, 0, 0, 0)};
    /** Где кулак в покое, относительно глаз (правая рука). */
    private static final Vector3f BASE = new Vector3f(0.56F, -0.52F, -0.72F);
    /** Рука от первого лица тоньше обычной, иначе широкий кулак целиком скрывал бы рукоять. */
    private static final float ARM_SCALE = 0.7F;
    /** Вторая рука за рукоятью и откуда она выходит на неё (из-за левого нижнего края). */
    private static final float SECOND_HAND = 0.125F;
    private static final Vector3f OFFSCREEN = new Vector3f(-0.75F, -1.15F, -0.45F);
    /** Пока рисуется рука от первого лица, позы от третьего лица к модели не применяются. */
    public static boolean drawingArm;

    private FirstPersonMotion() {
    }

    /** Где сейчас меч; {@code side} — 1 правая рука, −1 левая (зеркально), {@code main} — это рука, которая бьёт. */
    public static Pose pose(AbstractClientPlayerEntity player, int side, boolean main, float equip, float tickDelta) {
        Vector3f fist, blade, edge;
        float two = 0.0F, trail = 0.0F;
        int color = SlashTrails.STEEL;
        float time = player.age + tickDelta;
        Vector3f restFist = new Vector3f(BASE).add(ZangetsuItemRenderer.firstPersonHold(true));
        Vector3f restBlade = ZangetsuItemRenderer.restBlade(), restEdge = ZangetsuItemRenderer.restEdge();
        ClientState.Animation animation = main ? ClientState.animation(player) : null;
        ClientState.Swing swing = main && animation == null ? ClientState.swing(player, time) : null;
        if (animation != null && animation.type() == AnimationPayload.GETSUGA) {
            Key key = sampleKey(GETSUGA, animation.progress(player, tickDelta));
            Quaternionf turn = new Quaternionf().rotationY(key.ry() * MathHelper.RADIANS_PER_DEGREE)
                    .rotateX(key.rx() * MathHelper.RADIANS_PER_DEGREE).rotateZ(key.rz() * MathHelper.RADIANS_PER_DEGREE);
            fist = turn.transform(ZangetsuItemRenderer.firstPersonHold(true)).add(BASE).add(key.tx(), key.ty(), key.tz());
            blade = turn.transform(new Vector3f(restBlade));
            edge = turn.transform(new Vector3f(restEdge));
        } else if (swing != null) {
            SwordMoves.Move move = SwordMoves.ALL[swing.move];
            float t = MathHelper.clamp(swing.progress(time), 0.0F, 1.0F);
            float[] rest = new float[SwordMoves.SIZE];
            put(rest, SwordMoves.FX, restFist);
            put(rest, SwordMoves.BX, restBlade);
            put(rest, SwordMoves.EX, restEdge);
            float[] v = SwordMoves.sample(move.view(), t, rest);
            fist = turned(v[SwordMoves.FX], v[SwordMoves.FX + 1], v[SwordMoves.FX + 2], swing.aim);
            blade = turned(v[SwordMoves.BX], v[SwordMoves.BX + 1], v[SwordMoves.BX + 2], swing.aim).normalize();
            edge = turned(v[SwordMoves.EX], v[SwordMoves.EX + 1], v[SwordMoves.EX + 2], swing.aim);
            two = MathHelper.clamp(v[SwordMoves.TWO], 0.0F, 1.0F);
            color = swing.color;
            trail = SwordMoves.trail(move, t);
        } else {
            // Покой: меч чуть дышит в руке.
            float breath = MathHelper.sin(time * 0.055F);
            fist = new Vector3f(restFist).add(0.0F, breath * 0.006F, 0.0F);
            blade = new Vector3f(restBlade).add(breath * 0.008F, 0.0F, breath * 0.006F).normalize();
            edge = new Vector3f(restEdge);
        }
        edge.sub(new Vector3f(blade).mul(edge.dot(blade))).normalize();
        fist.y -= equip * 0.6F;
        if (side < 0) {
            // Зеркало: внутренняя сторона клинка должна остаться внутренней.
            Vector3f inner = new Vector3f(blade).cross(edge);
            fist.x = -fist.x;
            blade.x = -blade.x;
            inner.x = -inner.x;
            edge = inner.cross(blade, new Vector3f()).normalize();
        }
        return new Pose(fist, blade, edge, two, color, trail);
    }

    private static void put(float[] values, int at, Vector3f v) {
        values[at] = v.x;
        values[at + 1] = v.y;
        values[at + 2] = v.z;
    }

    /** Кулак второй руки: на рукояти за первым; пока приём не двуручный — за краем экрана. */
    public static Vector3f secondHand(Pose pose, int side) {
        Vector3f hold = new Vector3f(pose.fist()).sub(new Vector3f(pose.blade()).mul(SECOND_HAND));
        Vector3f away = new Vector3f(side * OFFSCREEN.x, OFFSCREEN.y, OFFSCREEN.z);
        return away.lerp(hold, pose.two());
    }

    /** Повернуть вокруг вертикали на {@code aim} радиан вправо: рывок к цели. */
    private static Vector3f turned(float x, float y, float z, float aim) {
        if (aim == 0.0F) {
            return new Vector3f(x, y, z);
        }
        float limited = MathHelper.clamp(aim, -0.7F, 0.7F), c = MathHelper.cos(limited), s = MathHelper.sin(limited);
        return new Vector3f(x * c - z * s, y, x * s + z * c);
    }

    /**
     * Рука на рукояти: кулак — в точке хвата {@code fist}, предплечье тянется к нему от плеча за нижним углом экрана,
     * поэтому во всех взмахах меч остаётся в кулаке, а рука следует за ним.
     */
    public static void renderArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, boolean right, Vector3f fist) {
        if (player.isInvisible()) {
            return;
        }
        float side = right ? 1.0F : -1.0F;
        Vector3f toHand = new Vector3f(fist).sub(side * 0.62F, -0.95F, 0.25F).normalize();
        // Ось руки в модели — +y к кисти, перед руки (−z) смотрит вверх.
        Vector3f back = new Vector3f(0.0F, -1.0F, 0.0F);
        back.sub(new Vector3f(toHand).mul(back.dot(toHand))).normalize();
        Vector3f across = new Vector3f(toHand).cross(back).normalize();
        Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(across, toHand, back));
        matrices.push();
        matrices.translate(fist.x, fist.y, fist.z);
        matrices.multiply(rotation);
        matrices.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        // Кулак в координатах модели игрока: плечо (∓5, 2) и рука до середины кисти.
        matrices.translate(side * 6.0F / 16.0F, -10.6F / 16.0F, 0.0F);
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player);
        drawingArm = true;
        if (right) {
            renderer.renderRightArm(matrices, consumers, light, player);
        } else {
            renderer.renderLeftArm(matrices, consumers, light, player);
        }
        drawingArm = false;
        // Цепи и огонь рисуются уже вне setAngles, в тех же координатах, где только что нарисована рука.
        FinalFormFeatureRenderer.firstPersonArm(matrices, consumers, light, player, right ? renderer.getModel().rightArm : renderer.getModel().leftArm, right);
        matrices.pop();
    }

    private static Key sampleKey(Key[] keys, float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        for (int i = 0; i + 1 < keys.length; i++) {
            Key a = keys[i], b = keys[i + 1];
            if (t <= b.t()) {
                float k = (t - a.t()) / Math.max(1.0E-4F, b.t() - a.t());
                k = k * k * (3.0F - 2.0F * k);
                return new Key(t, MathHelper.lerp(k, a.tx(), b.tx()), MathHelper.lerp(k, a.ty(), b.ty()), MathHelper.lerp(k, a.tz(), b.tz()),
                        MathHelper.lerp(k, a.rx(), b.rx()), MathHelper.lerp(k, a.ry(), b.ry()), MathHelper.lerp(k, a.rz(), b.rz()));
            }
        }
        return REST_KEY;
    }
}
