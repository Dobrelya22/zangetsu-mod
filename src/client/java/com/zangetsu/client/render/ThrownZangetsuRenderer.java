package com.zangetsu.client.render;

import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Летящий и воткнутый Зангецу. В полёте меч кувыркается и за последние тики докручивается так,
 * чтобы вонзиться точно лезвием вниз; воткнутый стоит рукоятью вверх, во сне — с небольшим наклоном.
 */
public class ThrownZangetsuRenderer extends EntityRenderer<ThrownZangetsuEntity> {
    /** Во сколько раз меч больше обычного предмета. */
    private static final float SCALE = 1.5F;
    /** Расстояние от середины рукояти (центра модели) до острия, в блоках при масштабе 1. */
    private static final float TIP = 1.181F;
    /** На сколько блоков остриё уходит в землю. */
    private static final float SINK = 0.45F;
    private static final int SPINS = 3;

    private final ItemRenderer itemRenderer;
    private final ItemStack awake = new ItemStack(ModItems.ZANGETSU);
    private final ItemStack sleeping = new ItemStack(ModItems.ZANGETSU);

    public ThrownZangetsuRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.awake.set(ModComponents.EYE, Eye.OPEN);
        this.sleeping.set(ModComponents.EYE, Eye.CLOSED);
    }

    /**
     * Где кулак должен держать этот меч, куда смотрят клинок и лезвие: в координатах модели игрока {кулак (пиксели), клинок, лезвие}, в том же виде,
     * что позы меча в руке. Рука в сцене подбора идёт ровно сюда, поэтому и хватается за настоящую рукоять, и меч из земли в руку переходит без рывка.
     */
    static float[] gripInModel(ThrownZangetsuEntity sword, LivingEntity player, float tickDelta) {
        float rise = 0.0F;
        if (sword.getState() == ThrownZangetsuEntity.RISING) {
            float t = MathHelper.clamp((sword.age - sword.stateAge + tickDelta) / 22.0F, 0.0F, 1.0F);
            rise = t * t * (3.0F - 2.0F * t) * 1.6F;
        }
        Matrix4f matrix = new Matrix4f().translate(0.0F, TIP * SCALE - SINK + rise, 0.0F)
                .rotateY(-sword.getYaw() * MathHelper.RADIANS_PER_DEGREE)
                .rotateX(sword.getTiltX() * MathHelper.RADIANS_PER_DEGREE)
                .rotateZ(sword.getTiltZ() * MathHelper.RADIANS_PER_DEGREE)
                .rotateZ(-135.0F * MathHelper.RADIANS_PER_DEGREE)
                .scale(SCALE).translate(-0.5F, -0.5F, -0.5F);
        Vector3f hold = matrix.transformPosition(ZangetsuItemRenderer.holdPoint(), new Vector3f());
        Vector3f blade = matrix.transformDirection(ZangetsuItemRenderer.bladeAxis(), new Vector3f()).normalize();
        Vector3f edge = matrix.transformDirection(ZangetsuItemRenderer.edgeAxis(), new Vector3f()).normalize();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        Quaternionf toBody = new Quaternionf().rotationY((180.0F - bodyYaw) * MathHelper.RADIANS_PER_DEGREE).conjugate();
        Vector3f local = toBody.transform(
                (float) (MathHelper.lerp(tickDelta, sword.prevX, sword.getX()) - MathHelper.lerp(tickDelta, player.prevX, player.getX())) + hold.x,
                (float) (MathHelper.lerp(tickDelta, sword.prevY, sword.getY()) - MathHelper.lerp(tickDelta, player.prevY, player.getY())) + hold.y,
                (float) (MathHelper.lerp(tickDelta, sword.prevZ, sword.getZ()) - MathHelper.lerp(tickDelta, player.prevZ, player.getZ())) + hold.z, new Vector3f());
        toBody.transform(blade);
        toBody.transform(edge);
        return new float[]{-local.x * 16.0F, (1.501F - local.y) * 16.0F, local.z * 16.0F, -blade.x, -blade.y, blade.z, -edge.x, -edge.y, edge.z};
    }

    @Override
    public void render(ThrownZangetsuEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        int state = entity.getState();
        float lift = TIP * SCALE - SINK;
        ItemStack stack = this.awake;
        matrices.push();
        if (state == ThrownZangetsuEntity.FLYING) {
            float p = entity.flightProgress(tickDelta);
            Vec3d v = entity.getVelocity();
            float heading = (float) MathHelper.atan2(v.x, v.z);
            // Сначала меч вращается быстро, к концу полёта замедляется и встаёт остриём вниз.
            float spin = 360.0F * SPINS * (1.0F - (1.0F - p) * (1.0F - p));
            matrices.translate(0.0F, lift * p, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(heading));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
            light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        } else {
            float rise = 0.0F;
            if (state == ThrownZangetsuEntity.RISING) {
                float t = MathHelper.clamp((entity.age - entity.stateAge + tickDelta) / 22.0F, 0.0F, 1.0F);
                rise = t * t * (3.0F - 2.0F * t) * 1.6F;
            } else if (entity.isDream()) {
                stack = this.sleeping;
            }
            matrices.translate(0.0F, lift + rise, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getTiltX()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(entity.getTiltZ()));
        }
        // Картинка меча лежит по диагонали (остриё вверху справа) — поворот на −135° ставит его остриём вниз.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
        matrices.scale(SCALE, SCALE, SCALE);
        // У подделок глаз снаружи клинка. Настоящий, если его долго не находят, подмигивает.
        int kind = entity.getKind();
        boolean winks = kind == ThrownZangetsuEntity.REAL && state == ThrownZangetsuEntity.STUCK && entity.isWinking();
        if (winks) {
            stack = this.awake;
            EyeTracker.forcedOpenness = ThrownZangetsuEntity.winkOpenness(entity.age + tickDelta);
        }
        ZangetsuItemRenderer.eyeSideOverride = kind == ThrownZangetsuEntity.FAKE ? -1 : 0;
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        this.itemRenderer.renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers,
                entity.getWorld(), entity.getId());
        EyeTracker.pop(previous);
        ZangetsuItemRenderer.eyeSideOverride = 0;
        if (winks) {
            EyeTracker.forcedOpenness = -1.0F;
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTexture(ThrownZangetsuEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
