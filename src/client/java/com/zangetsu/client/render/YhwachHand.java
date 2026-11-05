package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.ClientState;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.SwordBreak;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Поломка Зангецу. Меч сам взлетает к протянутой руке Яхве и ложится обухом в ладонь. Клинок здесь не цельный: он собран из объёмных кусков, которые сначала прилегают друг к другу.
 * Пальцы сжимаются, куски расходятся, между ними открываются раскалённые щели с настоящей глубиной. От резкого сжатия кулака эти же куски разлетаются. Рука рисуется в {@link SpiritRenderer}, а здесь считается её ход.
 *
 * <p>Точки клинка заданы как в {@link ZangetsuItemRenderer}: u вдоль (в пикселях модели), v поперёк (обух в минусе, кромка в плюсе), w толщина. В осях меча: x = -v, y = u, z = w.
 */
final class YhwachHand {
    private static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    private static final Identifier ATLAS = ZangetsuMod.id("textures/entity/zangetsu_model.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    /** Пикселей модели в блоке при масштабе меча: те же 1.5, что и у воткнутого. */
    private static final float SWORD_SCALE = 1.5F, PX = SWORD_SCALE / 16.0F;
    /** Где рука хватает клинок (u модели) и где на этой высоте обух. */
    private static final float GRIP_U = 14.5F, MODEL_GRIP = 3.1F;
    private static final float GRIP_Y = (GRIP_U - MODEL_GRIP) * PX, SPINE_X = -ZangetsuItemRenderer.SPINE * PX;
    /** Рука огромна, поэтому в ней меч вырастает: подлетая, он будто оказывается ближе. */
    private static final float HOLD_SIZE = 3.4F;
    /** Ось, вдоль которой вытянуты сколы (u, v). */
    private static final float GRAIN_U = 0.906F, GRAIN_V = 0.423F;

    /** Запястье правой руки, вытянутой навстречу мечу (метры модели), и кисть крупнее обычной. */
    private static final Vector3f READY_WRIST = new Vector3f(-0.215F, 1.188F, 0.984F);
    static final float HAND_SIZE = 1.9F;
    /** Куда пойманный меч толкает руку: вверх и обратно к нему. */
    private static final Vector3f PUSH = new Vector3f(0.06F, 0.62F, -0.78F).normalize();
    /** Вокруг какой точки клинка меч крутится в полёте: его середина, высота от начала меча. */
    private static final float PIVOT_Y = (12.0F - MODEL_GRIP) * PX;

    private static final ItemStack SWORD = new ItemStack(ModItems.ZANGETSU);
    private static final float[][] SPARKS = buildSparks();
    private static final float[][] OUTLINE = outline();
    private static final List<Piece> PIECES = new ArrayList<>();
    private static float farthest = 1.0F;

    static {
        SWORD.set(ModComponents.EYE, Eye.OPEN);
        buildPieces();
    }

    private YhwachHand() {
    }

    private static float span(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Плавнее обычного: без рывка и в начале, и в конце. */
    private static float glide(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    // ---------------------------------------------------------------- ход сцены

    /** Рука в этот момент: {@code track} куда дошла по своей кривой, {@code hold} пальцы сомкнулись на клинке, {@code clench} кулак сжат до конца, {@code beckon} пальцы подзывают меч, {@code kick} кисть откинута ударом, {@code offset} сдвиг запястья (метры модели). */
    record Grip(float track, float hold, float clench, float beckon, float kick, Vector3f offset) {
    }

    /** Отдача: отбрасывает разом, а возвращается долго и тяжело, чуть проскакивая обратно. */
    private static float recoil(float x) {
        if (x <= 0.0F) {
            return 0.0F;
        }
        return (1.0F - (float) Math.exp(-x / 0.9F)) * (float) Math.exp(-x / 9.0F) * MathHelper.cos(x * 0.14F);
    }

    static Grip grip(float t) {
        float track = t < SwordBreak.RELEASE ? MathHelper.clamp((t - SwordBreak.ARM_FROM) / (SwordBreak.READY - SwordBreak.ARM_FROM), 0.0F, 1.0F) : 1.0F - glide(SwordBreak.RELEASE, SwordBreak.FIGURE_OUT + 10.0F, t);
        float hold = span(SwordBreak.GRIP - 2.0F, SwordBreak.GRIP + 2.0F, t) * (1.0F - span(SwordBreak.RELEASE, SwordBreak.RELEASE + 16.0F, t));
        float clench = span(SwordBreak.SNAP - 3.0F, SwordBreak.SNAP, t) * (1.0F - span(SwordBreak.RELEASE, SwordBreak.RELEASE + 14.0F, t));
        float beckon = span(SwordBreak.READY - 4.0F, SwordBreak.READY + 3.0F, t) * (1.0F - span(SwordBreak.READY + 3.0F, SwordBreak.READY + 12.0F, t)) * 0.42F;
        float punch = recoil(t - SwordBreak.GRIP), crush = recoil(t - SwordBreak.SNAP);
        float tremble = span(SwordBreak.CRACK_FROM, SwordBreak.CRUSH, t) * (1.0F - span(SwordBreak.SNAP, SwordBreak.SNAP + 2.0F, t)) * 0.007F;
        float waiting = span(SwordBreak.READY, SwordBreak.READY + 8.0F, t) * (1.0F - hold) * 0.006F;
        Vector3f offset = new Vector3f(PUSH).mul(punch * 0.36F + crush * 0.16F).add(MathHelper.sin(t * 4.3F) * tremble, MathHelper.cos(t * 5.1F) * tremble + MathHelper.sin(t * 0.14F) * waiting, 0.0F);
        return new Grip(track, hold, clench, beckon, punch * 0.62F + crush * 0.3F, offset);
    }

    /** Всю фигуру качает назад от удара (радианы). */
    static float lean(float t) {
        return (recoil(t - SwordBreak.GRIP) * 2.0F + recoil(t - SwordBreak.SNAP) * 0.9F) * MathHelper.RADIANS_PER_DEGREE;
    }

    /** По плащу от удара бежит волна. */
    static float ripple(float t) {
        float caught = t - SwordBreak.GRIP, snapped = t - SwordBreak.SNAP;
        return (caught >= 0.0F ? 0.15F * (float) Math.exp(-caught / 11.0F) : 0.0F) + (snapped >= 0.0F ? 0.2F * (float) Math.exp(-snapped / 13.0F) : 0.0F);
    }

    /** Насколько клинок треснул: сразу под пальцами, понемногу пока он говорит, и остальное разом при сжатии. */
    private static float crack(float t) {
        return 0.45F * span(SwordBreak.CRACK_FROM, SwordBreak.SPEECH + 4.0F, t)
                + 0.27F * span(SwordBreak.SPEECH + 4.0F, SwordBreak.CRUSH, t)
                + 0.28F * span(SwordBreak.CRUSH, SwordBreak.SNAP, t);
    }

    /** Полёт: с места разгоняется всё быстрее и бьёт в ладонь на полной скорости, по прямой. */
    private static float flight(float t) {
        float k = MathHelper.clamp((t - SwordBreak.LAUNCH) / (float) (SwordBreak.GRIP - SwordBreak.LAUNCH), 0.0F, 1.0F);
        return (float) Math.pow(k, 1.8F);
    }

    /** Где начало меча (блоки, оси модели), когда его обух лежит в ладони у самых костяшек: пальцы идут вниз по одной плоскости клинка, большой палец по другой. */
    private static Vector3f held(float scale) {
        Vector3f palm = new Vector3f(READY_WRIST).add(0.0F, -0.021F * HAND_SIZE, 0.105F * HAND_SIZE).mul(scale);
        return palm.add(HOLD_SIZE * GRIP_Y, -HOLD_SIZE * SPINE_X, 0.0F);
    }

    // ---------------------------------------------------------------- кадр

    static void render(ZangetsuSpiritEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, ItemRenderer items) {
        float t = entity.age + tickDelta;
        Vector3f anchor = entity.breakAnchor();
        float yaw = entity.victimYaw();
        float rise = span(SwordBreak.SWORD_FROM, SwordBreak.SWORD_FROM + 24.0F, t);
        if (rise <= 0.01F) {
            return;
        }
        float scale = entity.getSpiritScale();
        float fly = flight(t);
        float calm = 1.0F - span(SwordBreak.SPIN_FROM, SwordBreak.SPIN_FROM + 12.0F, t);
        float size = MathHelper.lerp(fly, 1.0F, HOLD_SIZE) * rise;
        Grip grip = grip(t);
        float since = t - SwordBreak.SNAP, caught = t - SwordBreak.GRIP;
        beats(entity, caught, since);
        Quaternionf body = new Quaternionf().rotationY((180.0F - yaw) * MathHelper.RADIANS_PER_DEGREE).rotateX(-lean(t));

        // Середина меча идёт по прямой: от места, где он висел, до ладони. Поэтому он крутится вокруг неё, а не вокруг рукояти.
        Vector3f from = new Vector3f(0.0F, PIVOT_Y, 0.0F);
        Vector3f to = held(scale).add(-HOLD_SIZE * PIVOT_Y, 0.0F, 0.0F);
        Vector3f path = new Vector3f(to).sub(from).normalize();
        // Раскручиваясь, он всплывает и дрожит, а перед броском отходит назад, как натянутая тетива.
        float charge = span(SwordBreak.SPIN_FROM, SwordBreak.LAUNCH, t) * (1.0F - fly);
        float windup = span(SwordBreak.LAUNCH - 9.0F, SwordBreak.LAUNCH, t) * (1.0F - fly);
        Vector3f start = new Vector3f(from).add(0.0F, charge * 0.55F, 0.0F).sub(new Vector3f(path).mul(windup * 0.7F));
        Vector3f pivot = new Vector3f(start).lerp(to, fly).add(new Vector3f(grip.offset()).mul(scale));
        pivot.add(MathHelper.sin(t * 7.3F) * charge * 0.035F, MathHelper.sin(t * 0.09F) * 0.03F * calm - 0.5F * (1.0F - rise) + MathHelper.cos(t * 6.1F) * charge * 0.035F, 0.0F);

        if (t >= SwordBreak.LAUNCH && caught < 9.0F) {
            Vector3f tail = scene(anchor, body, new Vector3f(from).lerp(to, Math.max(0.0F, fly - 0.6F)), Math.max(0.0F, fly - 0.6F));
            trail(consumers, matrices, tail, scene(anchor, body, pivot, fly), 1.0F - span(0.0F, 9.0F, caught));
        }

        matrices.push();
        matrices.translate(anchor.x * (1.0F - fly), anchor.y * (1.0F - fly), anchor.z * (1.0F - fly));
        matrices.multiply(body);
        matrices.translate(pivot.x, pivot.y, pivot.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.06F) * 7.0F * calm));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(SwordBreak.spinAngle(t)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(grip.kick() * 0.3F));
        matrices.scale(size, size, size);
        if (caught < 0.0F) {
            spinBlur(consumers, matrices, span(SwordBreak.SPIN_FROM + 6.0F, SwordBreak.LAUNCH, t));
        }
        matrices.translate(0.0F, -PIVOT_Y, 0.0F);

        if (since < 0.0F) {
            float crack = crack(t);
            drawSword(entity, matrices, consumers, items, 1.0F, false, true);
            pieces(consumers, matrices, crack, -1.0F);
            if (crack > 0.004F) {
                core(consumers, matrices, crack, t);
            }
            if (caught >= 0.0F) {
                sparks(consumers, matrices, caught, true);
                shockwave(consumers, matrices, caught, 13.0F, 1.2F);
            }
        } else {
            pieces(consumers, matrices, 1.0F, since);
            sparks(consumers, matrices, since, false);
            shockwave(consumers, matrices, since, 16.0F, 2.6F);
            // Рукоять без клинка падает (у лежащего меча низ это -x) и исчезает.
            float gone = 1.0F - span(30.0F, 55.0F, since);
            if (gone > 0.01F) {
                matrices.push();
                matrices.translate(-0.0016F * since * since, 0.0F, 0.0F);
                drawSword(entity, matrices, consumers, items, gone, true, false);
                matrices.pop();
            }
        }
        matrices.pop();
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    /** Звук и вспышка ударов: ровно в тот кадр, когда меч бьёт в ладонь и когда кулак его ломает. */
    private static void beats(ZangetsuSpiritEntity entity, float caught, float since) {
        if (caught >= 0.0F && caught < 4.0F && !entity.caughtHeard) {
            entity.caughtHeard = true;
            play(SoundEvents.BLOCK_ANVIL_LAND, 0.6F, 1.0F);
            play(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.3F, 0.7F);
            play(SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 0.5F, 1.0F);
            play(SoundEvents.BLOCK_CHAIN_BREAK, 0.6F, 1.0F);
            ClientState.flash(ClientFxPayload.IMPACT_FLASH, 7);
        }
        if (since >= 0.0F && since < 4.0F && !entity.snapHeard) {
            entity.snapHeard = true;
            // Главный удар: звук сработавшего тотема, под ним низкий взрыв и хруст стали.
            play(SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
            play(SoundEvents.ITEM_TOTEM_USE, 0.72F, 0.8F);
            play(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.55F, 0.9F);
            play(SoundEvents.ENTITY_WITHER_BREAK_BLOCK, 0.7F, 0.6F);
            play(SoundEvents.ITEM_SHIELD_BREAK, 0.5F, 0.7F);
            play(SoundEvents.BLOCK_ANVIL_BREAK, 0.6F, 0.6F);
            play(SoundEvents.BLOCK_GLASS_BREAK, 0.6F, 0.7F);
            ClientState.flash(ClientFxPayload.IMPACT_FLASH, 10);
        }
    }

    /** Точка от начала сцены в координатах сущности. */
    private static Vector3f scene(Vector3f anchor, Quaternionf body, Vector3f local, float fly) {
        return body.transform(new Vector3f(local)).add(anchor.x * (1.0F - fly), anchor.y * (1.0F - fly), anchor.z * (1.0F - fly));
    }

    /** След летящего меча: прямая светлая полоса от места броска, сужается к хвосту. */
    private static void trail(VertexConsumerProvider consumers, MatrixStack matrices, Vector3f tail, Vector3f head, float alpha) {
        Vector3f d = new Vector3f(head).sub(tail);
        if (d.lengthSquared() < 0.01F || alpha <= 0.01F) {
            return;
        }
        d.normalize();
        Vector3f across = new Vector3f(d).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f other = new Vector3f(d).cross(across).normalize();
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        for (Vector3f n : new Vector3f[]{across, other}) {
            streak(out, m, tail, head, n, 0.42F, 150, 20, 34, (int) (120.0F * alpha));
            streak(out, m, tail, head, n, 0.14F, 255, 225, 215, (int) (235.0F * alpha));
        }
    }

    private static void streak(VertexConsumer out, Matrix4f m, Vector3f tail, Vector3f head, Vector3f n, float width, int r, int g, int b, int a) {
        put(out, m, tail.x - n.x * width * 0.1F, tail.y - n.y * width * 0.1F, tail.z - n.z * width * 0.1F, r, g, b, 0);
        put(out, m, tail.x + n.x * width * 0.1F, tail.y + n.y * width * 0.1F, tail.z + n.z * width * 0.1F, r, g, b, 0);
        put(out, m, head.x + n.x * width, head.y + n.y * width, head.z + n.z * width, r, g, b, a);
        put(out, m, head.x - n.x * width, head.y - n.y * width, head.z - n.z * width, r, g, b, a);
    }

    /** След вращения: две дуги тянутся за остриём и за рукоятью и гаснут к хвосту. Чем быстрее меч, тем длиннее и ярче. */
    private static void spinBlur(VertexConsumerProvider consumers, MatrixStack matrices, float k) {
        if (k <= 0.01F) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float sweep = MathHelper.PI * (0.25F + 0.4F * k);
        int sides = 32;
        for (int end = 0; end < 2; end++) {
            // Меч крутится против часовой: всё, что осталось позади, лежит по часовой от него.
            float start = MathHelper.HALF_PI + end * MathHelper.PI, strength = k * (end == 0 ? 1.0F : 0.5F);
            float inner = 0.3F, body = end == 0 ? 0.84F : 0.98F, rim = body + 0.1F;
            for (int i = 0; i < sides; i++) {
                float s0 = (float) i / sides, s1 = (float) (i + 1) / sides;
                float a0 = start - sweep * s0, a1 = start - sweep * s1;
                float c0 = MathHelper.cos(a0), n0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), n1 = MathHelper.sin(a1);
                float f0 = (1.0F - s0) * (1.0F - s0) * (1.0F - s0) * strength, f1 = (1.0F - s1) * (1.0F - s1) * (1.0F - s1) * strength;
                put(out, m, c0 * inner, n0 * inner, 0.0F, 120, 30, 42, 0);
                put(out, m, c1 * inner, n1 * inner, 0.0F, 120, 30, 42, 0);
                put(out, m, c1 * body, n1 * body, 0.0F, 190, 70, 80, (int) (170.0F * f1));
                put(out, m, c0 * body, n0 * body, 0.0F, 190, 70, 80, (int) (170.0F * f0));
                put(out, m, c0 * body, n0 * body, 0.0F, 255, 235, 225, (int) (40.0F * f0));
                put(out, m, c1 * body, n1 * body, 0.0F, 255, 235, 225, (int) (40.0F * f1));
                put(out, m, c1 * rim, n1 * rim, 0.0F, 255, 235, 225, (int) (230.0F * f1));
                put(out, m, c0 * rim, n0 * rim, 0.0F, 255, 235, 225, (int) (230.0F * f0));
            }
        }
    }

    private static void drawSword(ZangetsuSpiritEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, ItemRenderer items, float size, boolean hiltOnly, boolean noBlade) {
        matrices.push();
        // Картинка меча лежит по диагонали: остриё вверх.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
        float s = SWORD_SCALE * size;
        matrices.scale(s, s, s);
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        ZangetsuItemRenderer.hiltOnly = hiltOnly;
        ZangetsuItemRenderer.bladeHidden = noBlade;
        items.renderItem(SWORD, ModelTransformationMode.NONE, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.getWorld(), entity.getId());
        ZangetsuItemRenderer.hiltOnly = false;
        ZangetsuItemRenderer.bladeHidden = false;
        EyeTracker.pop(previous);
        matrices.pop();
    }

    // ---------------------------------------------------------------- клинок из кусков

    /** Кусок клинка: многоугольник в (u, v), какие его стороны лежат на краю клинка, середина, насколько он далеко от места хвата и случайные числа для отклонения и полёта. */
    private record Piece(float[][] points, boolean[] outer, float cu, float cv, float distance, float jut, float tilt, float[] flight) {
    }

    /** Где кусок сейчас: поворот, середина в осях меча, щель вокруг него (пиксели), размер, насколько он отошёл и как раскалён. */
    private record Pose(Quaternionf turn, float x, float y, float z, float gap, float size, float open, float hot) {
    }

    /** Контур клинка: обух от основания к острию, затем кромка обратно. У острия точки гуще: там кромка дугой. */
    private static float[][] outline() {
        int body = 5, tip = 9, steps = body + tip;
        float[][] out = new float[steps * 2 + 2][];
        for (int i = 0; i <= steps; i++) {
            float u = i <= body ? MathHelper.lerp((float) i / body, ZangetsuItemRenderer.BLADE_START, ZangetsuItemRenderer.TIP_START)
                    : MathHelper.lerp((float) (i - body) / tip, ZangetsuItemRenderer.TIP_START, ZangetsuItemRenderer.BLADE_END);
            float[] s = ZangetsuItemRenderer.section(u);
            out[i] = new float[]{u, s[0]};
            out[steps * 2 + 1 - i] = new float[]{u, s[2]};
        }
        return out;
    }

    /** Отсечь многоугольник прямой: остаётся часть, где точка ближе к {@code a}, чем к {@code b}. Расстояние вдоль косой оси считается меньше, поэтому сколы выходят вытянутыми и косыми, как в хрупкой стали. */
    private static List<float[]> cut(List<float[]> polygon, float au, float av, float bu, float bv) {
        float mu = (au + bu) * 0.5F, mv = (av + bv) * 0.5F, along = (bu - au) * GRAIN_U + (bv - av) * GRAIN_V;
        float nu = bu - au - 0.75F * along * GRAIN_U, nv = bv - av - 0.75F * along * GRAIN_V;
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            float[] p = polygon.get(i), q = polygon.get((i + 1) % polygon.size());
            float dp = (p[0] - mu) * nu + (p[1] - mv) * nv, dq = (q[0] - mu) * nu + (q[1] - mv) * nv;
            if (dp <= 0.0F) {
                out.add(p);
            }
            if (dp < 0.0F != dq < 0.0F) {
                float k = dp / (dp - dq);
                out.add(new float[]{MathHelper.lerp(k, p[0], q[0]), MathHelper.lerp(k, p[1], q[1])});
            }
        }
        return out;
    }

    private static boolean onRim(float u, float v) {
        if (u <= ZangetsuItemRenderer.BLADE_START + 0.01F || u >= ZangetsuItemRenderer.BLADE_END - 0.01F) {
            return true;
        }
        float[] s = ZangetsuItemRenderer.section(u);
        return Math.abs(v - s[0]) < 0.07F || Math.abs(v - s[2]) < 0.07F;
    }

    /** Куски как ячейки Вороного: случайные точки, каждая забирает всё, что к ней ближе остальных. Там, куда давят пальцы, куски мельче. */
    private static void buildPieces() {
        Random random = new Random(404L);
        List<float[]> seeds = new ArrayList<>();
        while (seeds.size() < 42) {
            boolean near = seeds.size() < 20;
            float u = near ? GRIP_U + (float) random.nextGaussian() * 2.6F : MathHelper.lerp(random.nextFloat(), ZangetsuItemRenderer.BLADE_START, ZangetsuItemRenderer.BLADE_END);
            if (u <= ZangetsuItemRenderer.BLADE_START || u >= ZangetsuItemRenderer.BLADE_END) {
                continue;
            }
            float[] s = ZangetsuItemRenderer.section(u);
            seeds.add(new float[]{u, MathHelper.lerp(random.nextFloat(), s[0], s[2])});
        }
        for (float[] seed : seeds) {
            List<float[]> polygon = new ArrayList<>(List.of(OUTLINE));
            for (float[] other : seeds) {
                if (other != seed && !polygon.isEmpty()) {
                    polygon = cut(polygon, seed[0], seed[1], other[0], other[1]);
                }
            }
            if (polygon.size() < 3) {
                continue;
            }
            float cu = 0.0F, cv = 0.0F;
            for (float[] p : polygon) {
                cu += p[0];
                cv += p[1];
            }
            cu /= polygon.size();
            cv /= polygon.size();
            boolean[] outer = new boolean[polygon.size()];
            for (int i = 0; i < outer.length; i++) {
                float[] p = polygon.get(i), q = polygon.get((i + 1) % outer.length);
                outer[i] = onRim(p[0], p[1]) && onRim(q[0], q[1]) && onRim((p[0] + q[0]) * 0.5F, (p[1] + q[1]) * 0.5F);
            }
            float distance = MathHelper.sqrt((cu - GRIP_U) * (cu - GRIP_U) + cv * cv);
            farthest = Math.max(farthest, distance);
            float[] flight = {random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()};
            PIECES.add(new Piece(polygon.toArray(new float[0][]), outer, cu, cv, distance, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, flight));
        }
    }

    /** Толщина клинка в точке: как в самой модели, от обуха к скосу и от скоса к кромке. */
    private static float thickness(float u, float v) {
        float[] s = ZangetsuItemRenderer.section(u);
        if (v <= s[1]) {
            return MathHelper.lerp(MathHelper.clamp((v - s[0]) / Math.max(0.01F, s[1] - s[0]), 0.0F, 1.0F), s[3], s[4]);
        }
        return MathHelper.lerp(MathHelper.clamp((v - s[1]) / Math.max(0.01F, s[2] - s[1]), 0.0F, 1.0F), s[4], s[5]);
    }

    /** {@code since} меньше нуля: клинок ещё держится и только трещит, трещина идёт от пальцев к концам. Иначе куски летят. */
    private static Pose pose(Piece piece, float crack, float since, float fade) {
        float start = piece.distance() / farthest * 0.75F;
        float open = since >= 0.0F ? 1.0F : span(start, start + 0.25F, crack);
        Quaternionf turn = new Quaternionf().rotateZ(piece.tilt() * open * 0.12F).rotateX(piece.jut() * open * 0.14F);
        float ox = 0.0F, oy = 0.0F, oz = piece.jut() * open * 0.7F * PX;
        if (since >= 0.0F) {
            float[] f = piece.flight();
            float dx = -piece.cv(), dy = piece.cu() - GRIP_U;
            float length = Math.max(0.1F, MathHelper.sqrt(dx * dx + dy * dy));
            float out = (1.0F - (float) Math.exp(-since / 9.0F)) * (0.35F + f[0] * 0.75F);
            ox = dx / length * out * 1.6F + (f[1] - 0.5F) * out * 0.7F - 0.00035F * since * since;
            oy = dy / length * out * 1.6F + (f[2] - 0.5F) * out * 0.7F;
            oz += (f[3] - 0.25F) * out * 1.5F;
            float spin = (1.0F - (float) Math.exp(-since / 14.0F)) * (2.0F + f[4] * 5.0F) + since * 0.012F * f[4];
            turn.rotateAxis(spin, new Vector3f(f[5] - 0.5F, f[6] - 0.5F, f[4] - 0.49F).normalize());
        }
        float hot = since < 0.0F ? open * 0.8F : Math.max(0.0F, 0.8F - since / 16.0F);
        return new Pose(turn, -piece.cv() * PX + ox, (piece.cu() - MODEL_GRIP) * PX + oy, oz, 0.24F * open, fade, open, hot);
    }

    /** Сначала сталь всех кусков, потом их стенки: у потока вершин может быть открыт только один слой. */
    private static void pieces(VertexConsumerProvider consumers, MatrixStack matrices, float crack, float since) {
        float fade = since < 0.0F ? 1.0F : 1.0F - span(62.0F, 92.0F, since);
        if (fade <= 0.01F) {
            return;
        }
        MatrixStack.Entry entry = matrices.peek();
        List<Pose> poses = new ArrayList<>(PIECES.size());
        for (Piece piece : PIECES) {
            poses.add(pose(piece, crack, since, fade));
        }
        VertexConsumer steel = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ATLAS));
        for (int i = 0; i < poses.size(); i++) {
            faces(steel, entry, PIECES.get(i), poses.get(i));
        }
        VertexConsumer walls = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        for (int i = 0; i < poses.size(); i++) {
            walls(walls, entry, PIECES.get(i), poses.get(i));
        }
    }

    private static float inset(float[] point, Piece piece, float gap) {
        float du = point[0] - piece.cu(), dv = point[1] - piece.cv();
        float length = Math.max(0.01F, MathHelper.sqrt(du * du + dv * dv));
        return Math.max(0.2F, 1.0F - gap / length);
    }

    /** Лицо и изнанка куска: веером от середины, текстура та же, что у целого клинка, поэтому рисунок стали не сбивается. */
    private static void faces(VertexConsumer out, MatrixStack.Entry entry, Piece piece, Pose pose) {
        float[][] p = piece.points();
        float wc = thickness(piece.cu(), piece.cv());
        Vector3f n = new Vector3f(), scratch = new Vector3f();
        for (int side = -1; side <= 1; side += 2) {
            n.set(0.0F, 0.0F, side);
            pose.turn().transform(n);
            for (int i = 0; i < p.length; i++) {
                float[] from = p[i], to = p[(i + 1) % p.length];
                corner(out, entry, pose, piece, piece.cu(), piece.cv(), 0.0F, wc * side, n, scratch);
                corner(out, entry, pose, piece, from[0], from[1], inset(from, piece, pose.gap()), thickness(from[0], from[1]) * side, n, scratch);
                corner(out, entry, pose, piece, to[0], to[1], inset(to, piece, pose.gap()), thickness(to[0], to[1]) * side, n, scratch);
                corner(out, entry, pose, piece, to[0], to[1], inset(to, piece, pose.gap()), thickness(to[0], to[1]) * side, n, scratch);
            }
        }
    }

    private static void corner(VertexConsumer out, MatrixStack.Entry entry, Pose pose, Piece piece, float u, float v, float k, float w, Vector3f n, Vector3f scratch) {
        float size = pose.size();
        scratch.set(-(v - piece.cv()) * k * size * PX, (u - piece.cu()) * k * size * PX, w * size * PX);
        pose.turn().transform(scratch);
        float atlasU = (u - ZangetsuItemRenderer.BLADE_START) / (ZangetsuItemRenderer.BLADE_END - ZangetsuItemRenderer.BLADE_START) * 448.0F / 512.0F;
        float atlasV = MathHelper.clamp((v - ZangetsuItemRenderer.SPINE) / (ZangetsuItemRenderer.EDGE - ZangetsuItemRenderer.SPINE), 0.0F, 1.0F) * 160.0F / 512.0F;
        out.vertex(entry.getPositionMatrix(), pose.x() + scratch.x, pose.y() + scratch.y, pose.z() + scratch.z).color(255, 255, 255, 255).texture(atlasU, atlasV).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, n.x, n.y, n.z);
    }

    /** Боковые стенки куска: они и дают щелям глубину. Снаружи клинка это сталь обуха и кромки, а внутри трещины они раскалены и остывают после удара. */
    private static void walls(VertexConsumer out, MatrixStack.Entry entry, Piece piece, Pose pose) {
        float[][] p = piece.points();
        Vector3f n = new Vector3f(), a = new Vector3f();
        float size = pose.size();
        float low = MathHelper.clamp(pose.hot() * 2.0F, 0.0F, 1.0F), high = MathHelper.clamp(pose.hot() * 2.0F - 1.0F, 0.0F, 1.0F);
        int hotR = (int) (MathHelper.lerp(low, 58.0F, 205.0F) + high * 50.0F), hotG = (int) (MathHelper.lerp(low, 58.0F, 24.0F) + high * 140.0F), hotB = (int) (MathHelper.lerp(low, 66.0F, 28.0F) + high * 96.0F);
        for (int i = 0; i < p.length; i++) {
            boolean outer = piece.outer()[i];
            if (!outer && pose.open() <= 0.001F) {
                continue;
            }
            float[] from = p[i], to = p[(i + 1) % p.length];
            float k0 = inset(from, piece, pose.gap()), k1 = inset(to, piece, pose.gap());
            float du0 = (from[0] - piece.cu()) * k0, dv0 = (from[1] - piece.cv()) * k0, du1 = (to[0] - piece.cu()) * k1, dv1 = (to[1] - piece.cv()) * k1;
            float w0 = thickness(from[0], from[1]), w1 = thickness(to[0], to[1]);
            int r = outer ? 92 : hotR, g = outer ? 92 : hotG, b = outer ? 104 : hotB;
            n.set(du1 - du0, dv1 - dv0, 0.0F);
            if (n.lengthSquared() < 1.0E-8F) {
                n.set(0.0F, 1.0F, 0.0F);
            }
            n.normalize();
            pose.turn().transform(n);
            for (int k = 0; k < 4; k++) {
                float du = k == 0 || k == 3 ? du0 : du1, dv = k == 0 || k == 3 ? dv0 : dv1;
                float w = k == 0 ? w0 : k == 1 ? w1 : k == 2 ? -w1 : -w0;
                a.set(-dv * size * PX, du * size * PX, w * size * PX);
                pose.turn().transform(a);
                out.vertex(entry.getPositionMatrix(), pose.x() + a.x, pose.y() + a.y, pose.z() + a.z).color(r, g, b, 255).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, n.x, n.y, n.z);
            }
        }
    }

    /** Жар внутри клинка: лист посередине толщины, его видно только сквозь щели между кусками. */
    private static void core(VertexConsumerProvider consumers, MatrixStack matrices, float crack, float t) {
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float pulse = 0.82F + 0.18F * MathHelper.sin(t * 0.9F);
        int g = (int) (90.0F + 150.0F * crack * pulse), b = (int) (50.0F + 130.0F * crack * crack), a = (int) (MathHelper.clamp(crack * 6.0F, 0.0F, 1.0F) * 255.0F);
        int steps = OUTLINE.length / 2 - 1;
        for (int i = 0; i < steps; i++) {
            float[] s0 = OUTLINE[i], s1 = OUTLINE[i + 1], e1 = OUTLINE[steps * 2 + 1 - (i + 1)], e0 = OUTLINE[steps * 2 + 1 - i];
            put(out, m, -(s0[1] + 0.12F) * PX, (s0[0] - MODEL_GRIP) * PX, 0.0F, 255, g, b, a);
            put(out, m, -(s1[1] + 0.12F) * PX, (s1[0] - MODEL_GRIP) * PX, 0.0F, 255, g, b, a);
            put(out, m, -(e1[1] - 0.12F) * PX, (e1[0] - MODEL_GRIP) * PX, 0.0F, 255, g, b, a);
            put(out, m, -(e0[1] - 0.12F) * PX, (e0[0] - MODEL_GRIP) * PX, 0.0F, 255, g, b, a);
        }
    }

    private static void put(VertexConsumer out, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        out.vertex(m, x, y, z).color(r, g, b, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(0.0F, 1.0F, 0.0F);
    }

    // ---------------------------------------------------------------- искры и удар

    /** Мелкая стальная пыль: u, v, размер, направление (x, y, z), две фазы вращения, скорость вращения. */
    private static float[][] buildSparks() {
        Random random = new Random(909L);
        float[][] all = new float[60][];
        for (int i = 0; i < all.length; i++) {
            float u = 6.5F + random.nextFloat() * 14.5F, v = -2.2F + random.nextFloat() * 4.8F;
            float x = -v * PX, y = (u - GRIP_U) * PX;
            float l = Math.max(0.05F, MathHelper.sqrt(x * x + y * y));
            float out = 0.6F + random.nextFloat() * 1.6F;
            all[i] = new float[]{u, v, 0.012F + random.nextFloat() * 0.03F, x / l * out, y / l * out, (random.nextFloat() - 0.3F) * 1.4F, random.nextFloat() * 6.28F, random.nextFloat() * 6.28F, 2.0F + random.nextFloat() * 5.0F};
        }
        return all;
    }

    /** {@code fromPalm}: искры от удара о ладонь. Иначе стальная пыль со всего разлетевшегося клинка. */
    private static void sparks(VertexConsumerProvider consumers, MatrixStack matrices, float since, boolean fromPalm) {
        float fade = fromPalm ? 1.0F - span(8.0F, 22.0F, since) : 1.0F - span(40.0F, 70.0F, since);
        if (fade <= 0.01F) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float e = (1.0F - (float) Math.exp(-since / (fromPalm ? 5.0F : 10.0F))) * (fromPalm ? 0.62F : 1.0F);
        for (float[] s : SPARKS) {
            float x = (fromPalm ? SPINE_X : -s[1] * PX) + s[3] * e - 0.0005F * since * since, y = (fromPalm ? GRIP_Y : (s[0] - MODEL_GRIP) * PX) + s[4] * e, z = s[5] * e;
            float size = s[2] * fade;
            float angle = s[6] + s[8] * e + since * 0.012F * s[8];
            float c = MathHelper.cos(angle), n = MathHelper.sin(angle), tilt = s[7] + since * 0.008F * s[8], ct = MathHelper.cos(tilt), st = MathHelper.sin(tilt);
            float ax = c * size, ay = n * size * ct, az = n * size * st;
            float bx = -n * size * 0.7F, by = c * size * 0.7F * ct, bz = c * size * 0.7F * st;
            float gleam = 0.4F + 0.6F * Math.abs(ct * MathHelper.cos(angle * 0.5F + since * 0.05F));
            int hot = (int) (MathHelper.clamp(1.0F - since / 22.0F, 0.0F, 1.0F) * 255.0F), tone = (int) (120.0F + 120.0F * gleam);
            int r = Math.max(tone, hot), g = Math.max(tone - 10, hot / 2), b = Math.max(tone, hot / 5);
            put(out, m, x + ax, y + ay, z + az, r, g, b, 255);
            put(out, m, x + bx, y + by, z + bz, r / 2, g / 2, b / 2, 255);
            put(out, m, x - ax, y - ay, z - az, r, g, b, 255);
            put(out, m, x - bx, y - by, z - bz, r / 2, g / 2, b / 2, 255);
        }
    }

    /** Кольцо удара расходится от кулака. */
    private static void shockwave(VertexConsumerProvider consumers, MatrixStack matrices, float since, float duration, float reach) {
        if (since > duration) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float k = since / duration, radius = 0.2F + span(0.0F, 1.0F, k) * reach, width = 0.1F * reach * (1.0F - k) + 0.03F;
        int alpha = (int) (210.0F * (1.0F - k));
        int sides = 48;
        for (int i = 0; i < sides; i++) {
            float a0 = MathHelper.TAU * i / sides, a1 = MathHelper.TAU * (i + 1) / sides;
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            put(out, m, c0 * radius, GRIP_Y + s0 * radius, 0.0F, 255, 230, 225, alpha);
            put(out, m, c1 * radius, GRIP_Y + s1 * radius, 0.0F, 255, 230, 225, alpha);
            put(out, m, c1 * (radius + width), GRIP_Y + s1 * (radius + width), 0.0F, 255, 60, 50, 0);
            put(out, m, c0 * (radius + width), GRIP_Y + s0 * (radius + width), 0.0F, 255, 60, 50, 0);
        }
    }
}
