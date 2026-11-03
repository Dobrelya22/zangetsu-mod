package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
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
 * Всё появляется вместе с маской во время превращения.
 */
public class FinalFormFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Запястье и щиколотка от шарнира конечности (пиксели), половина её толщины. */
    private static final float WRIST = 9.0F, ANKLE = 10.6F, HALF = 2.0F;
    private static final Vector3f WORLD_UP = new Vector3f(0.0F, 1.0F, 0.0F);

    public FinalFormFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!ClientState.isFinal(player) || player.isInvisible()) {
            return;
        }
        float grow = 1.0F;
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && animation.type() == AnimationPayload.HOLLOW) {
            grow = MathHelper.clamp(animation.progress(player, tickDelta) * 1.8F - 0.3F, 0.0F, 1.0F);
        }
        if (grow <= 0.0F) {
            return;
        }
        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
        Vector3f wind = wind(player);

        matrices.push();
        model.body.rotate(matrices);
        Flames.Frame frame = new Flames.Frame(matrices.peek(), WORLD_UP, wind, animationProgress);
        TatteredCloak.render(matrices, consumers, light, player, tickDelta, animationProgress, grow, frame);
        bodyChains(matrices, consumers, light, frame, grow);
        matrices.pop();

        limb(matrices, consumers, light, model.rightArm, -1.0F, WRIST, true, WORLD_UP, wind, animationProgress, grow, 1.0F);
        limb(matrices, consumers, light, model.leftArm, 1.0F, WRIST, true, WORLD_UP, wind, animationProgress, grow, 2.0F);
        limb(matrices, consumers, light, model.rightLeg, 0.0F, ANKLE, false, WORLD_UP, wind, animationProgress, grow, 3.0F);
        limb(matrices, consumers, light, model.leftLeg, 0.0F, ANKLE, false, WORLD_UP, wind, animationProgress, grow, 4.0F);
    }

    /** Встречный ветер: куда сносит пламя и цепи (в мире). */
    private static Vector3f wind(AbstractClientPlayerEntity player) {
        return new Vector3f((float) (player.prevX - player.getX()), (float) (player.prevY - player.getY()) * 0.4F, (float) (player.prevZ - player.getZ())).mul(1.6F);
    }

    /** Рука от первого лица: те же кандалы, цепь и огонь, только «вверх» и ветер считаются из вида камеры. */
    public static void firstPersonArm(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player, ModelPart arm, boolean right) {
        if (!ClientState.isFinal(player)) {
            return;
        }
        float time = player.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        Quaternionf toView = new Quaternionf(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()).conjugate();
        Vector3f up = toView.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f wind = toView.transform(wind(player));
        limb(matrices, consumers, light, arm, right ? -1.0F : 1.0F, WRIST, true, up, wind, time, 1.0F, right ? 1.0F : 2.0F);
    }

    /**
     * Конечность: обруч на запястье или щиколотке, с него свисает обрывок цепи, а вокруг горит огонь: большое кольцо языков у обруча и кольцо
     * поменьше посередине. Сначала железо, потом чёрные сердца языков, потом свет: каждый слой можно запрашивать, только закончив предыдущий.
     */
    private static void limb(MatrixStack matrices, VertexConsumerProvider consumers, int light, ModelPart part, float centerX, float y, boolean arm,
            Vector3f spaceUp, Vector3f spaceWind, float time, float grow, float seed) {
        matrices.push();
        part.rotate(matrices);
        MatrixStack.Entry entry = matrices.peek();
        Flames.Frame frame = new Flames.Frame(entry, spaceUp, spaceWind, time);

        VertexConsumer iron = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        Chains.cuff(iron, entry, centerX, y, HALF, 0.95F, light);
        Vector3f down = new Vector3f(frame.up).negate();
        // Матрица в блоках, а цепь считает в пикселях и сама переводит их при выводе.
        Chains.hanging(iron, entry, new Vector3f(centerX - HALF - 0.8F, y + 0.4F, 0.0F), down, frame.wind, arm ? 7 : 4, time, seed, light);

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
        Flames.embers(glow, frame, centerX / 16.0F, (y - 2.0F) / 16.0F, 0.0F, 2.6F / 16.0F, 16.0F / 16.0F, 9, seed);
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

    /** Цепи на туловище: перевязь через грудь, пояс и обрывок цепи с бедра. */
    private static void bodyChains(MatrixStack matrices, VertexConsumerProvider consumers, int light, Flames.Frame frame, float grow) {
        if (grow < 0.6F) {
            return;
        }
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer iron = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        float zFront = -2.42F, zBack = 2.42F, sideX = 4.42F, belt = 10.6F;
        Chains.along(iron, entry, new Vector3f[]{new Vector3f(-4.1F, 0.1F, zFront), new Vector3f(4.1F, belt - 0.4F, zFront)}, new Vector3f(1.0F, 1.0F, 0.0F), light);
        Chains.along(iron, entry, new Vector3f[]{new Vector3f(-sideX, belt, zFront), new Vector3f(sideX, belt, zFront), new Vector3f(sideX, belt, zBack),
                new Vector3f(-sideX, belt, zBack), new Vector3f(-sideX, belt, zFront)}, new Vector3f(0.0F, 1.0F, 0.0F), light);
        Vector3f down = new Vector3f(frame.up).negate();
        Chains.hanging(iron, entry, new Vector3f(sideX, belt + 0.3F, -1.2F), down, frame.wind, 8, frame.time, 7.0F, light);
        Chains.hanging(iron, entry, new Vector3f(-sideX + 1.2F, belt + 0.3F, zFront), down, frame.wind, 5, frame.time, 9.0F, light);
    }
}
