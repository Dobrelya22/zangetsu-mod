package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.RotationAxis;

/** Огромная тёмная фигура в небе: проявляется из дыма, бросает меч и растворяется. */
public class SpiritRenderer extends EntityRenderer<ZangetsuSpiritEntity> {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/spirit.png");
    private static final Identifier EYES = ZangetsuMod.id("textures/entity/spirit_eyes.png");
    /** Меч в руке фигуры (в масштабе самой фигуры). */
    private static final float HELD_SWORD_SCALE = 1.5F;

    private final SpiritModel model;
    private final ItemRenderer itemRenderer;
    private final ItemStack sword = new ItemStack(ModItems.ZANGETSU);

    public SpiritRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new SpiritModel(context.getPart(SpiritModel.LAYER));
        this.itemRenderer = context.getItemRenderer();
        this.sword.set(ModComponents.EYE, Eye.OPEN);
    }

    @Override
    public void render(ZangetsuSpiritEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float opacity = entity.getOpacity(tickDelta);
        if (opacity <= 0.01F) {
            return;
        }
        float scale = entity.getSpiritScale();
        float time = entity.age + tickDelta;
        matrices.push();
        matrices.scale(scale, scale, scale);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - entity.getYaw()));
        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, -1.501F, 0.0F);
        this.model.setAngles(entity, 0.0F, 0.0F, time, 0.0F, 0.0F);

        int alpha = (int) (opacity * 255.0F);
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        this.model.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), fullBright, OverlayTexture.DEFAULT_UV,
                ColorHelper.Argb.getArgb(alpha, 255, 255, 255));
        // Глаза светятся сами по себе и проявляются вместе с фигурой.
        this.model.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), fullBright, OverlayTexture.DEFAULT_UV,
                ColorHelper.Argb.getArgb(255, alpha, alpha, alpha));

        if (time < ZangetsuSpiritEntity.RELEASE_TICK) {
            // Меч в правой руке продолжает руку: середина рукояти (центр модели) — в кулаке, клинок дальше по руке.
            matrices.push();
            this.model.body.rotate(matrices);
            this.model.rightArm.rotate(matrices);
            matrices.translate(0.0F, 17.0F / 16.0F, 0.0F);
            // Диагональ картинки (остриё вверху справа) поворачиваем вдоль руки.
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
            matrices.scale(HELD_SWORD_SCALE, HELD_SWORD_SCALE, HELD_SWORD_SCALE);
            net.minecraft.entity.Entity previous = EyeTracker.push(entity);
            this.itemRenderer.renderItem(this.sword, ModelTransformationMode.NONE, fullBright, OverlayTexture.DEFAULT_UV,
                    matrices, consumers, entity.getWorld(), entity.getId());
            EyeTracker.pop(previous);
            matrices.pop();
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public boolean shouldRender(ZangetsuSpiritEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public Identifier getTexture(ZangetsuSpiritEntity entity) {
        return TEXTURE;
    }
}
