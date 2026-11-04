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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Финальная форма на игроке: чёрно-алый огонь на руках и ногах, кандалы с обрывками цепей, цепи на груди и поясе и рваный плащ.
 * Ткань и цепи считает {@link FormPhysics}, огонь отстаёт от движущихся рук и разгорается от бега и ударов. Всё появляется при превращении.
 */
public class FinalFormFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Запястье и щиколотка от шарнира конечности (пиксели), половина её толщины. */
    private static final float WRIST = 9.0F, ANKLE = 10.6F, HALF = 2.0F;
    /** Радиус конечностей и туловища для столкновений с тканью (блоки): с запасом, чтобы складки не входили в тело. */
    private static final float LIMB_RADIUS = 0.17F, TORSO_RADIUS = 0.19F;
    private static final Vector3f WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);

    public FinalFormFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    private static float speed(AbstractClientPlayerEntity player) {
        return MathHelper.sqrt((float) ((player.getX() - player.prevX) * (player.getX() - player.prevX) + (player.getZ() - player.prevZ) * (player.getZ() - player.prevZ)));
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
        float[][] capsules = stepped ? colliders(matrices, model, physics) : new float[0][];
        float ground = physics.ground(player);

        matrices.push();
        model.body.rotate(matrices);
        Matrix4f simFromBody = physics.simFromLocal(matrices.peek().getPositionMatrix());
        Matrix4f bodyFromSim = physics.bodyFromSim(stepped, simFromBody);
        Vector3f bodyWind = physics.spaceDirection(physics.flameWind("body", simFromBody.transformPosition(0.0F, 0.5F, 0.0F, new Vector3f()), animationProgress));
        Flames.Frame frame = new Flames.Frame(matrices.peek(), WORLD_UP, bodyWind, flameTime, heat);
        TatteredCloak.render(matrices, consumers, light, physics, animationProgress, grow, speed, heat, stepped, simFromBody, bodyFromSim, capsules, ground, frame);
        bodyChains(matrices, consumers, light, physics, player, simFromBody, bodyFromSim, frame, capsules, ground, animationProgress, grow, stepped);
        matrices.pop();

        limb(matrices, consumers, light, physics, player, model.rightArm, -1.0F, WRIST, true, WORLD_UP, animationProgress, flameTime, heat, grow, 1.0F, "wrist_r", capsules, ground, stepped);
        limb(matrices, consumers, light, physics, player, model.leftArm, 1.0F, WRIST, true, WORLD_UP, animationProgress, flameTime, heat, grow, 2.0F, "wrist_l", capsules, ground, stepped);
        limb(matrices, consumers, light, physics, player, model.rightLeg, 0.0F, ANKLE, false, WORLD_UP, animationProgress, flameTime, heat, grow, 3.0F, "ankle_r", capsules, ground, stepped);
        limb(matrices, consumers, light, physics, player, model.leftLeg, 0.0F, ANKLE, false, WORLD_UP, animationProgress, flameTime, heat, grow, 4.0F, "ankle_l", capsules, ground, stepped);
    }

    /** Туловище и конечности в координатах физики: вокруг них обтекает ткань и цепи. */
    private static float[][] colliders(MatrixStack matrices, PlayerEntityModel<?> model, FormPhysics physics) {
        float[][] out = new float[6][];
        matrices.push();
        model.body.rotate(matrices);
        Matrix4f body = physics.simFromLocal(matrices.peek().getPositionMatrix());
        out[0] = FormPhysics.capsule(body, 0.125F, 0.05F, 0.0F, 0.125F, 0.72F, 0.0F, TORSO_RADIUS);
        out[1] = FormPhysics.capsule(body, -0.125F, 0.05F, 0.0F, -0.125F, 0.72F, 0.0F, TORSO_RADIUS);
        matrices.pop();
        ModelPart[] limbs = {model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < limbs.length; i++) {
            matrices.push();
            limbs[i].rotate(matrices);
            Matrix4f local = physics.simFromLocal(matrices.peek().getPositionMatrix());
            float cx = i == 0 ? -0.0625F : (i == 1 ? 0.0625F : 0.0F);
            out[2 + i] = FormPhysics.capsule(local, cx, 0.0F, 0.0F, cx, i < 2 ? 0.625F : 0.75F, 0.0F, LIMB_RADIUS);
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
        limb(matrices, consumers, light, physics, player, arm, right ? -1.0F : 1.0F, WRIST, true, up, time, physics.flameTime(time), heat, 1.0F, right ? 1.0F : 2.0F,
                right ? "fp_wrist_r" : "fp_wrist_l", new float[0][], Float.NEGATIVE_INFINITY, true);
    }

    /**
     * Конечность: обруч на запястье или щиколотке, с него свисает обрывок цепи, а вокруг горит огонь: большое кольцо языков у обруча и кольцо
     * поменьше посередине. Сначала железо, потом чёрные сердца языков, потом алый слой, потом свет: каждый слой можно запрашивать, только закончив предыдущий.
     */
    private static void limb(MatrixStack matrices, VertexConsumerProvider consumers, int light, FormPhysics physics, AbstractClientPlayerEntity player, ModelPart part,
            float centerX, float y, boolean arm, Vector3f spaceUp, float now, float flameTime, float heat, float grow, float seed, String key, float[][] capsules, float ground, boolean stepped) {
        matrices.push();
        part.rotate(matrices);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f simFromLimb = physics.simFromLocal(entry.getPositionMatrix());
        Vector3f wrist = simFromLimb.transformPosition(centerX / 16.0F, y / 16.0F, 0.0F, new Vector3f());
        // Огонь отстаёт от самой руки: махнул — языки вытянулись назад.
        Vector3f wind = physics.spaceDirection(physics.flameWind(key, wrist, now));
        Flames.Frame frame = new Flames.Frame(entry, spaceUp, wind, flameTime, heat);

        VertexConsumer iron = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        Chains.cuff(iron, entry, centerX, y, HALF, 0.95F, light);
        if (grow > 0.25F && stepped) {
            FormPhysics.Rope rope = physics.rope(key, arm ? 7 : 4);
            Vector3f ring = simFromLimb.transformPosition((centerX - HALF - 0.8F) / 16.0F, (y + 0.4F) / 16.0F, 0.0F, new Vector3f());
            rope.step(ring, new Vector3f(0.0F, -1.0F, 0.0F), Chains.LINK_STEP / 16.0F, now, capsules, ground);
            physics.jingle(player, now, rope.shock);
            Chains.rope(iron, entry, rope.local(new Matrix4f(simFromLimb).invert()), light);
        }

        int big = 8, small = 4;
        float length = (arm ? 6.6F : 5.2F) * grow / 16.0F, width = (arm ? 1.15F : 1.0F) * grow / 16.0F;
        float midY = y - (arm ? 5.0F : 5.5F);
        VertexConsumer dark = consumers.getBuffer(RenderLayer.getEntityTranslucent(Flames.WHITE));
        for (int i = 0; i < big; i += 2) {
            float[] base = ringPoint(centerX, y - 1.0F, i, big, seed);
            Flames.heart(dark, frame, base[0], base[1], base[2], length * 0.55F, width * 0.62F, seed * 10.0F + i * 1.7F);
        }
        VertexConsumer blend = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(Flames.WHITE));
        for (int i = 0; i < big; i++) {
            float[] base = ringPoint(centerX, y - 1.0F, i, big, seed);
            float own = 0.75F + 0.5F * Flames.hash((int) seed, i);
            Flames.flame(blend, frame, base[0], base[1], base[2], length * own, width, seed * 10.0F + i * 1.7F, 1.0F);
        }
        for (int i = 0; i < small; i++) {
            float[] base = ringPoint(centerX, midY, i, small, seed + 5.0F);
            Flames.flame(blend, frame, base[0], base[1], base[2], length * 0.5F, width * 0.6F, seed * 13.0F + i * 2.3F, 0.85F);
        }
        VertexConsumer glow = consumers.getBuffer(RenderLayer.getEyes(Flames.WHITE));
        for (int i = 0; i < big; i++) {
            float[] base = ringPoint(centerX, y - 1.0F, i, big, seed);
            float own = 0.75F + 0.5F * Flames.hash((int) seed, i);
            Flames.tongue(glow, frame, base[0], base[1], base[2], length * own * 0.62F, width * 0.5F, seed * 10.0F + i * 1.7F + 0.4F, Flames.INNER, 0.9F);
        }
        // От жара искр больше, они летят выше.
        Flames.embers(glow, frame, centerX / 16.0F, (y - 2.0F) / 16.0F, 0.0F, 2.6F / 16.0F, (16.0F + heat * 12.0F) / 16.0F, 9 + (int) (heat * 18.0F), seed);
        matrices.pop();
    }

    /** Точка основания языка на кольце вокруг конечности (в блоках). */
    private static float[] ringPoint(float centerX, float y, int i, int count, float seed) {
        float angle = MathHelper.TAU * i / count + seed;
        // Сечение конечности квадратное: прижимаем кольцо к граням.
        float c = MathHelper.cos(angle), s = MathHelper.sin(angle);
        float scale = (HALF + 0.35F) / Math.max(Math.abs(c), Math.abs(s));
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
            physics.jingle(player, now, rope.shock);
        }
        Chains.rope(iron, entry, rope.local(bodyFromSim), light);
    }
}
