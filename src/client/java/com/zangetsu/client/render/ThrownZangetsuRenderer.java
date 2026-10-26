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
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Летящий и воткнутый Зангецу. В полёте меч кувыркается и за последние тики докручивается так,
 * чтобы вонзиться точно лезвием вниз; воткнутый стоит рукоятью вверх, во сне — с небольшим наклоном.
 */
public class ThrownZangetsuRenderer extends EntityRenderer<ThrownZangetsuEntity> {
    /** Во сколько раз меч больше обычного предмета. */
    private static final float SCALE = 1.6F;
    /** Расстояние от центра картинки до острия (в долях предмета, вдоль диагонали). */
    private static final float TIP = 0.684F;
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
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        this.itemRenderer.renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers,
                entity.getWorld(), entity.getId());
        EyeTracker.pop(previous);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTexture(ThrownZangetsuEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
