package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Финальная форма на игроке: настоящий алый огонь на руках и ногах, кандалы с обрывками цепей, цепи на груди и поясе и рваный плащ.
 * Ткань и цепи считает {@link FormPhysics}, огонь отстаёт от движущихся рук и разгорается от бега и ударов. Всё появляется при превращении.
 * Кандалы и огонь сидят на самой конечности: сгибаются локоть или колено — они уходят вместе с предплечьем или голенью.
 */
public class FinalFormFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Запястье и щиколотка от шарнира конечности (пиксели), половина её толщины и половина ширины тонкой руки скина. */
    private static final float WRIST = 9.0F, ANKLE = 10.6F, HALF = 2.0F, SLIM_HALF = 1.5F;
    /** Длина руки и ноги от шарнира (пиксели). */
    private static final float ARM_END = 10.0F, LEG_END = 12.0F;
    /** Радиусы для цепей (блоки): цепь висит у самой конечности. */
    private static final float LIMB_RADIUS = 0.17F, TORSO_RADIUS = 0.19F;
    /**
     * Радиус конечности для ткани (блоки). Складки уходят внутрь, подкладка ещё ближе к телу, а углы бруска дальше его граней: только с таким запасом ни рука, ни нога
     * не вылезают сквозь плащ.
     */
    private static final float CLOTH_LIMB_RADIUS = 0.235F;
    /** Кандалы толще самой конечности, а углы и кольцо цепи торчат дальше всего: вокруг них ткани нужен свой запас (блоки). */
    private static final float CLOTH_CUFF_RADIUS = 0.27F;
    /** С какого расстояния за спиной конечность начинает уходить под плащ и где скрыта целиком (пиксели). */
    private static final float COVER_FROM = 2.5F, COVER_TO = 6.5F;
    /** Сколько полотен огня на конечности и их размер (пиксели). */
    private static final int BIG_FLAMES = 5, SMALL_FLAMES = 2;
    private static final float ARM_FLAME_HEIGHT = 9.5F, LEG_FLAME_HEIGHT = 8.0F, FLAME_WIDTH = 6.5F;
    /** От первого лица рука у самой камеры: такой же огонь закрыл бы весь обзор. */
    private static final float FIRST_PERSON_FLAME = 0.5F;
    private static final Vector3f WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);

    /** Конечность с кандалами. {@code side} — её внешняя сторона, {@code bend} — насколько согнута в этот кадр. */
    private record Limb(ModelPart part, float centerX, float halfX, float side, boolean arm, float bend, float seed, String key) {
        float y() {
            return this.arm ? WRIST : ANKLE;
        }

        float joint() {
            return this.arm ? BentLimbs.ELBOW : BentLimbs.KNEE;
        }
    }

    public FinalFormFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    private static float speed(AbstractClientPlayerEntity player) {
        return MathHelper.sqrt((float) ((player.getX() - player.prevX) * (player.getX() - player.prevX) + (player.getZ() - player.prevZ) * (player.getZ() - player.prevZ)));
    }

    /** Середина руки от её шарнира: у тонких рук скина она ближе. */
    private static float armCenter(AbstractClientPlayerEntity player) {
        return slim(player) ? 0.5F : 1.0F;
    }

    private static float armHalf(AbstractClientPlayerEntity player) {
        return slim(player) ? SLIM_HALF : HALF;
    }

    private static boolean slim(AbstractClientPlayerEntity player) {
        return player.getSkinTextures().model() == SkinTextures.Model.SLIM;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!ClientState.isFinal(player) || player.isInvisible()) {
            return;
        }
        float grow = HollowRite.bodyGrow(player, tickDelta);
        if (grow <= 0.0F) {
            return;
        }
        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
        FormPhysics physics = FormPhysics.of(player);
        boolean stepped = physics.enterWorld(matrices.peek().getPositionMatrix(), player, tickDelta);
        float speed = speed(player);
        float heat = physics.heat(animationProgress, speed);
        float flameTime = physics.flameTime(animationProgress);
        float[] bends = PlayerPoses.bends(player);
        float armX = armCenter(player), armHalf = armHalf(player);
        float[][] capsules = stepped ? colliders(matrices, model, physics, bends, armX) : new float[0][];
        float ground = physics.ground(player);

        matrices.push();
        model.body.rotate(matrices);
        Matrix4f simFromBody = physics.simFromLocal(matrices.peek().getPositionMatrix());
        Matrix4f bodyFromSim = physics.bodyFromSim(stepped, simFromBody);
        Vector3f bodyWind = physics.spaceDirection(physics.flameWind("body", simFromBody.transformPosition(0.0F, 0.5F, 0.0F, new Vector3f()), animationProgress));
        Flames.Frame frame = stepped ? new Flames.Frame(matrices.peek(), WORLD_UP, bodyWind, flameTime, heat)
                : new Flames.Frame(matrices.peek(), Flames.SCREEN_UP, bodyWind, flameTime, heat, Flames.SCREEN_VIEW);
        TatteredCloak.render(matrices, consumers, light, physics, animationProgress, grow, speed, heat, stepped, simFromBody, bodyFromSim, capsules, ground, frame);
        bodyChains(matrices, consumers, light, physics, player, simFromBody, bodyFromSim, frame, capsules, ground, animationProgress, grow, stepped);
        matrices.pop();

        limb(matrices, consumers, light, physics, player, new Limb(model.rightArm, -armX, armHalf, -1.0F, true, bend(bends, 0), 1.0F, "wrist_r"), WORLD_UP, animationProgress, flameTime, heat, grow, capsules, ground, stepped, bodyFromSim);
        limb(matrices, consumers, light, physics, player, new Limb(model.leftArm, armX, armHalf, 1.0F, true, bend(bends, 1), 2.0F, "wrist_l"), WORLD_UP, animationProgress, flameTime, heat, grow, capsules, ground, stepped, bodyFromSim);
        limb(matrices, consumers, light, physics, player, new Limb(model.rightLeg, 0.0F, HALF, -1.0F, false, bend(bends, 2), 3.0F, "ankle_r"), WORLD_UP, animationProgress, flameTime, heat, grow, capsules, ground, stepped, bodyFromSim);
        limb(matrices, consumers, light, physics, player, new Limb(model.leftLeg, 0.0F, HALF, 1.0F, false, bend(bends, 3), 4.0F, "ankle_l"), WORLD_UP, animationProgress, flameTime, heat, grow, capsules, ground, stepped, bodyFromSim);
    }

    private static float bend(float[] bends, int limb) {
        return bends == null ? 0.0F : bends[limb];
    }

    /** Туловище и конечности в координатах физики: вокруг них обтекает ткань и цепи. Согнутая конечность — две капсулы, по бедру и по голени. */
    private static float[][] colliders(MatrixStack matrices, PlayerEntityModel<?> model, FormPhysics physics, float[] bends, float armX) {
        float[][] out = new float[14][];
        matrices.push();
        model.body.rotate(matrices);
        Matrix4f body = physics.simFromLocal(matrices.peek().getPositionMatrix());
        out[0] = FormPhysics.capsule(body, 0.125F, 0.05F, 0.0F, 0.125F, 0.72F, 0.0F, TORSO_RADIUS, TORSO_RADIUS, false);
        out[1] = FormPhysics.capsule(body, -0.125F, 0.05F, 0.0F, -0.125F, 0.72F, 0.0F, TORSO_RADIUS, TORSO_RADIUS, false);
        matrices.pop();
        ModelPart[] limbs = {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < limbs.length; i++) {
            boolean arm = i < 2;
            float cx = i == 0 ? -armX : (i == 1 ? armX : 0.0F);
            float joint = arm ? BentLimbs.ELBOW : BentLimbs.KNEE, end = arm ? ARM_END : LEG_END;
            matrices.push();
            limbs[i].rotate(matrices);
            Matrix4f local = physics.simFromLocal(matrices.peek().getPositionMatrix());
            Vector3f knee = BentLimbs.axisPoint(cx, joint, bend(bends, i), joint).div(16.0F), foot = BentLimbs.axisPoint(cx, joint, bend(bends, i), end).div(16.0F);
            out[2 + i * 2] = FormPhysics.capsule(local, cx / 16.0F, 0.0F, 0.0F, knee.x, knee.y, knee.z, CLOTH_LIMB_RADIUS, LIMB_RADIUS, true);
            out[3 + i * 2] = FormPhysics.capsule(local, knee.x, knee.y, knee.z, foot.x, foot.y, foot.z, CLOTH_LIMB_RADIUS, LIMB_RADIUS, true);
            float cuff = arm ? WRIST : ANKLE;
            Vector3f above = BentLimbs.axisPoint(cx, joint, bend(bends, i), cuff - 0.5F).div(16.0F), below = BentLimbs.axisPoint(cx, joint, bend(bends, i), cuff + 0.5F).div(16.0F);
            out[10 + i] = FormPhysics.capsule(local, above.x, above.y, above.z, below.x, below.y, below.z, CLOTH_CUFF_RADIUS, 0.0F, true);
            matrices.pop();
        }
        return out;
    }

    /** Рука от первого лица: те же кандалы, цепь и огонь, только физика считается из вида камеры. */
    public static void firstPersonArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, ModelPart arm, boolean right) {
        if (!ClientState.isFinal(player)) {
            return;
        }
        float time = player.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        Quaternionf toView = new Quaternionf(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()).conjugate();
        Vector3f up = toView.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        FormPhysics physics = FormPhysics.of(player);
        physics.enterFirstPerson(player);
        float heat = physics.heat(time, speed(player));
        float side = right ? -1.0F : 1.0F;
        Limb limb = new Limb(arm, side * armCenter(player), armHalf(player), side, true, 0.0F, right ? 1.0F : 2.0F, right ? "fp_wrist_r" : "fp_wrist_l");
        limb(matrices, consumers, light, physics, player, limb, up, time, physics.flameTime(time), heat, 1.0F, new float[0][], Float.NEGATIVE_INFINITY, true, null);
    }

    /**
     * Конечность: обруч на запястье или щиколотке, с него свисает обрывок цепи, а вокруг горит огонь: большие полотна у обруча и поменьше у сгиба.
     * Сначала железо, потом тело пламени, потом свет: каждый слой можно запрашивать, только закончив предыдущий.
     */
    private static void limb(MatrixStack matrices, VertexConsumerProvider consumers, int light, FormPhysics physics, AbstractClientPlayerEntity player, Limb limb,
            Vector3f spaceUp, float now, float flameTime, float heat, float grow, float[][] capsules, float ground, boolean stepped, Matrix4f bodyFromSim) {
        boolean arm = limb.arm();
        float centerX = limb.centerX(), halfX = limb.halfX(), y = limb.y(), seed = limb.seed();
        float midY = y - (arm ? 5.0F : 5.5F);

        matrices.push();
        limb.part().rotate(matrices);

        // Всё, что ниже сгиба: обруч, цепь и большое кольцо огня идут за предплечьем или голенью.
        matrices.push();
        BentLimbs.toSection(matrices, limb.joint(), limb.bend(), y);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f simFromLimb = physics.simFromLocal(entry.getPositionMatrix());
        Vector3f wrist = simFromLimb.transformPosition(centerX / 16.0F, y / 16.0F, 0.0F, new Vector3f());
        // В инвентаре модель нарисована на экране, а не в мире: и верх, и взгляд там свои.
        Vector3f up = stepped ? spaceUp : Flames.SCREEN_UP, view = stepped ? null : Flames.SCREEN_VIEW;
        // Огонь отстаёт от самой руки: махнул — языки вытянулись назад.
        Vector3f wind = physics.spaceDirection(physics.flameWind(limb.key(), wrist, now));
        Flames.Frame frame = new Flames.Frame(entry, up, wind, flameTime, heat, view);
        // Рука или нога ушла назад под плащ: огонь там приседает, иначе его языки торчали бы сквозь ткань. Вышла вперёд — разгорается снова.
        float covered = stepped && bodyFromSim != null ? coverage(bodyFromSim.transformPosition(wrist, new Vector3f()).z * 16.0F) : 0.0F;
        float near = stepped && capsules.length == 0 ? FIRST_PERSON_FLAME : 1.0F;
        float length = (arm ? ARM_FLAME_HEIGHT : LEG_FLAME_HEIGHT) * near * grow * (1.0F - 0.8F * covered) / 16.0F, width = FLAME_WIDTH * near * grow * (1.0F - 0.4F * covered) / 16.0F;

        VertexConsumer iron = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        Chains.cuff(iron, entry, centerX, y, halfX, HALF, 0.95F, limb.side(), light);
        if (grow > 0.25F && stepped) {
            FormPhysics.Rope rope = physics.rope(limb.key(), arm ? 7 : 4);
            Vector3f ring = simFromLimb.transformPosition((centerX + limb.side() * (halfX + 0.8F)) / 16.0F, (y + 0.4F) / 16.0F, 0.0F, new Vector3f());
            rope.step(ring, new Vector3f(0.0F, -1.0F, 0.0F), Chains.LINK_STEP / 16.0F, now, capsules, ground);
            if (capsules.length > 0) {
                rope.under(physics.existingCloth(TatteredCloak.OUTER), Chains.LINK_STEP / 16.0F);
            }
            physics.jingle(player, now, rope.shock);
            // Конечность глубоко под плащом: цепи всё равно не видно, а прижатая к ткани изнутри она проступала бы сквозь неё.
            if (covered < 0.85F) {
                Chains.rope(iron, entry, rope.local(new Matrix4f(simFromLimb).invert()), light);
            }
        }

        // Огонь обнимает конечность: большие полотна от обруча и пара поменьше у сгиба. Сначала тело пламени, потом свет: слои нельзя смешивать.
        VertexConsumer blend = consumers.getBuffer(Flames.bodyLayer());
        for (int i = 0; i < BIG_FLAMES; i++) {
            float[] base = ringPoint(centerX, halfX, y + 0.5F, i, BIG_FLAMES, seed);
            Flames.flame(blend, frame, base[0], base[1], base[2], length * bigHeight(seed, i), width, seed * 10.0F + i * 1.7F, 1.0F);
        }
        matrices.pop();

        // Огонь поменьше стоит у самого сгиба: у него своё положение.
        matrices.push();
        BentLimbs.toSection(matrices, limb.joint(), limb.bend(), midY);
        Flames.Frame middle = new Flames.Frame(matrices.peek(), up, wind, flameTime, heat, view);
        for (int i = 0; i < SMALL_FLAMES; i++) {
            float[] base = ringPoint(centerX, halfX, midY, i, SMALL_FLAMES, seed + 5.0F);
            Flames.flame(blend, middle, base[0], base[1], base[2], length * 0.58F, width * 0.7F, seed * 13.0F + i * 2.3F, 0.95F);
        }
        VertexConsumer core = consumers.getBuffer(Flames.coreLayer());
        for (int i = 0; i < SMALL_FLAMES; i++) {
            float[] base = ringPoint(centerX, halfX, midY, i, SMALL_FLAMES, seed + 5.0F);
            Flames.glow(core, middle, base[0], base[1], base[2], length * 0.58F, width * 0.7F, seed * 13.0F + i * 2.3F, 0.8F);
        }
        matrices.pop();

        matrices.push();
        BentLimbs.toSection(matrices, limb.joint(), limb.bend(), y);
        Flames.Frame lit = new Flames.Frame(matrices.peek(), up, wind, flameTime, heat, view);
        for (int i = 0; i < BIG_FLAMES; i++) {
            float[] base = ringPoint(centerX, halfX, y + 0.5F, i, BIG_FLAMES, seed);
            Flames.glow(core, lit, base[0], base[1], base[2], length * bigHeight(seed, i), width, seed * 10.0F + i * 1.7F, 0.9F);
        }
        // От жара искр больше, они летят выше.
        VertexConsumer sparks = consumers.getBuffer(RenderLayer.getEyes(Flames.WHITE));
        Flames.embers(sparks, lit, centerX / 16.0F, (y - 2.0F) / 16.0F, 0.0F, 2.6F / 16.0F, (16.0F + heat * 12.0F) / 16.0F, (int) ((9.0F + heat * 18.0F) * (1.0F - 0.7F * covered)), seed);
        matrices.pop();

        matrices.pop();
    }

    /** Высота большого пламени: у каждого своя, чтобы верхушки не стояли ровным рядом. */
    private static float bigHeight(float seed, int i) {
        return 0.78F + 0.42F * Flames.hash((int) seed, i);
    }

    /** Насколько конечность закрыта плащом: от того, как далеко за спиной её конец (пиксели от оси туловища). */
    private static float coverage(float behind) {
        float k = MathHelper.clamp((behind - COVER_FROM) / (COVER_TO - COVER_FROM), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Точка основания языка на кольце вокруг конечности (в блоках). */
    private static float[] ringPoint(float centerX, float halfX, float y, int i, int count, float seed) {
        float angle = MathHelper.TAU * i / count + seed;
        // Сечение конечности прямоугольное: прижимаем кольцо к граням.
        float c = MathHelper.cos(angle), s = MathHelper.sin(angle);
        float scale = 1.0F / Math.max(Math.abs(c) / (halfX + 0.35F), Math.abs(s) / (HALF + 0.35F));
        return new float[]{(centerX + c * scale) / 16.0F, y / 16.0F, s * scale / 16.0F};
    }

    /** Цепи на туловище: перевязь через грудь и пояс сидят плотно, а с бедра свисают два обрывка, которые болтаются сами. */
    private static void bodyChains(MatrixStack matrices, VertexConsumerProvider consumers, int light, FormPhysics physics, AbstractClientPlayerEntity player,
            Matrix4f simFromBody, Matrix4f bodyFromSim, Flames.Frame frame, float[][] capsules, float ground, float now, float grow, boolean stepped) {
        if (grow < 0.25F) {
            return;
        }
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer iron = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        float zFront = -2.42F, zBack = 2.42F, sideX = 4.42F, belt = 10.6F;
        Chains.along(iron, entry, new Vector3f[]{new Vector3f(-4.1F, 0.1F, zFront), new Vector3f(4.1F, belt - 0.4F, zFront)}, new Vector3f(1.0F, 1.0F, 0.0F), light);
        Chains.along(iron, entry, new Vector3f[]{new Vector3f(-sideX, belt, zFront), new Vector3f(sideX, belt, zFront), new Vector3f(sideX, belt, zBack),
                new Vector3f(-sideX, belt, zBack), new Vector3f(-sideX, belt, zFront)}, new Vector3f(0.0F, 1.0F, 0.0F), light);
        hipChain(iron, entry, physics, player, "hip_a", 9, new Vector3f(sideX, belt + 0.3F, -1.2F), simFromBody, bodyFromSim, capsules, ground, now, light, stepped);
        hipChain(iron, entry, physics, player, "hip_b", 6, new Vector3f(-sideX + 1.2F, belt + 0.3F, zFront), simFromBody, bodyFromSim, capsules, ground, now, light, stepped);
    }

    private static void hipChain(VertexConsumer iron, MatrixStack.Entry entry, FormPhysics physics, AbstractClientPlayerEntity player, String key, int links, Vector3f at,
            Matrix4f simFromBody, Matrix4f bodyFromSim, float[][] capsules, float ground, float now, int light, boolean stepped) {
        FormPhysics.Rope rope = physics.rope(key, links);
        if (stepped) {
            rope.step(simFromBody.transformPosition(at.x / 16.0F, at.y / 16.0F, at.z / 16.0F, new Vector3f()), new Vector3f(0.0F, -1.0F, 0.0F), Chains.LINK_STEP / 16.0F, now, capsules, ground);
            rope.under(physics.existingCloth(TatteredCloak.OUTER), Chains.LINK_STEP / 16.0F);
            physics.jingle(player, now, rope.shock);
        }
        Chains.rope(iron, entry, rope.local(bodyFromSim), light);
    }
}
