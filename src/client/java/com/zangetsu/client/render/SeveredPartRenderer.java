package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.SeveredPartEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * Отрубленная часть рисуется кусочком настоящей модели того моба, от которого отлетела, с его же
 * текстурой: рука зомби — зомбиной рукой, голова пиглина — с ушами и пятачком.
 */
public class SeveredPartRenderer extends EntityRenderer<SeveredPartEntity> {
    private static final Identifier ENDER_EYES = Identifier.ofVanilla("textures/entity/enderman/enderman_eyes.png");
    private static final Identifier ENDER_CUT = ZangetsuMod.id("textures/entity/ender_cut.png");
    private static final Map<String, LivingEntity> DUMMIES = new HashMap<>();
    private static World dummyWorld;

    public SeveredPartRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
    }

    /** Образец моба нужного вида — только чтобы взять у его отрисовщика модель и текстуру. */
    private static LivingEntity dummy(String id, World world) {
        if (dummyWorld != world) {
            DUMMIES.clear();
            dummyWorld = world;
        }
        return DUMMIES.computeIfAbsent(id, key -> {
            Identifier typeId = Identifier.tryParse(key);
            EntityType<?> type = typeId == null ? null : EntityType.get(typeId.toString()).orElse(null);
            Entity entity = type == null ? null : type.create(world);
            return entity instanceof LivingEntity living ? living : null;
        });
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(SeveredPartEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        LivingEntity source = dummy(entity.getSource(), entity.getWorld());
        if (source == null || !(this.dispatcher.getRenderer(source) instanceof LivingEntityRenderer renderer)) {
            return;
        }
        EntityModel<?> model = renderer.getModel();
        if (!(model instanceof BipedEntityModel<?> biped)) {
            return;
        }
        Identifier texture = renderer.getTexture(source);
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        int part = entity.getPart();
        float spin = MathHelper.lerp(tickDelta, entity.prevSpin, entity.spin);

        matrices.push();
        matrices.translate(0.0F, 0.15F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin * 0.37F));
        matrices.scale(-1.0F, -1.0F, 1.0F);
        if (entity.isBabySource()) {
            matrices.scale(0.6F, 0.6F, 0.6F);
        }
        int overlay = OverlayTexture.DEFAULT_UV;
        switch (part) {
            case SeveredPartEntity.LEFT_ARM -> draw(biped.leftArm, matrices, vertices, light, overlay, -1.0F, -4.0F, 0.0F, 1.0F);
            case SeveredPartEntity.RIGHT_ARM -> draw(biped.rightArm, matrices, vertices, light, overlay, 1.0F, -4.0F, 0.0F, 1.0F);
            case SeveredPartEntity.HEAD -> {
                draw(biped.head, matrices, vertices, light, overlay, 0.0F, 4.0F, 0.0F, 1.0F);
                draw(biped.hat, matrices, vertices, light, overlay, 0.0F, 4.0F, 0.0F, 1.0F);
            }
            case SeveredPartEntity.LEFT_SHIN -> draw(biped.leftLeg, matrices, vertices, light, overlay, 0.0F, -8.0F, 0.0F, 0.55F);
            case SeveredPartEntity.RIGHT_SHIN -> draw(biped.rightLeg, matrices, vertices, light, overlay, 0.0F, -8.0F, 0.0F, 0.55F);
            case SeveredPartEntity.ENDER_TOP -> renderEnderTop(biped, matrices, consumers, vertices, light, overlay);
            case SeveredPartEntity.ENDER_BOTTOM -> renderEnderBottom(biped, matrices, consumers, vertices, light, overlay);
            default -> {
            }
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    /**
     * Нарисовать одну часть модели отдельно: на время сбрасываем её позу (модель общая для всех
     * мобов этого вида), сдвигаем так, чтобы центр части был в начале координат, и возвращаем как было.
     */
    private static void draw(ModelPart part, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
            float dx, float dy, float dz, float yScale) {
        ModelTransform saved = part.getTransform();
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        boolean visible = part.visible;
        part.setTransform(ModelTransform.NONE);
        part.yScale = yScale;
        part.visible = true;
        matrices.push();
        matrices.translate(dx / 16.0F, dy / 16.0F, dz / 16.0F);
        part.render(matrices, vertices, light, overlay);
        matrices.pop();
        part.setTransform(saved);
        part.xScale = sx;
        part.yScale = sy;
        part.zScale = sz;
        part.visible = visible;
    }

    /** Верхняя половина эндермена: голова, руки и верх туловища, со светящимися глазами. */
    private static void renderEnderTop(BipedEntityModel<?> model, MatrixStack matrices, VertexConsumerProvider consumers,
            VertexConsumer vertices, int light, int overlay) {
        drawAt(model.body, matrices, vertices, light, overlay, 0.0F, -2.0F, 0.0F, 0.5F);
        drawAt(model.head, matrices, vertices, light, overlay, 0.0F, -2.0F, 0.0F, 1.0F);
        drawAt(model.hat, matrices, vertices, light, overlay, 0.0F, -2.0F, 0.0F, 1.0F);
        drawAt(model.rightArm, matrices, vertices, light, overlay, -5.0F, 0.0F, 0.0F, 0.8F);
        drawAt(model.leftArm, matrices, vertices, light, overlay, 5.0F, 0.0F, 0.0F, 0.8F);
        drawAt(model.head, matrices, consumers.getBuffer(RenderLayer.getEyes(ENDER_EYES)), 0xF000F0, overlay, 0.0F, -2.0F, 0.0F, 1.0F);
        cutFace(matrices, consumers, light, 4.0F);
    }

    /** Нижняя половина: низ туловища и обрубки ног. */
    private static void renderEnderBottom(BipedEntityModel<?> model, MatrixStack matrices, VertexConsumerProvider consumers,
            VertexConsumer vertices, int light, int overlay) {
        drawAt(model.body, matrices, vertices, light, overlay, 0.0F, -4.0F, 0.0F, 0.5F);
        drawAt(model.rightLeg, matrices, vertices, light, overlay, -2.0F, 1.0F, 0.0F, 0.45F);
        drawAt(model.leftLeg, matrices, vertices, light, overlay, 2.0F, 1.0F, 0.0F, 0.45F);
        cutFace(matrices, consumers, light, -4.0F);
    }

    /** Часть с поворотом в ноль, но сдвинутая в точку (x, y, z) модели. */
    private static void drawAt(ModelPart part, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
            float x, float y, float z, float yScale) {
        draw(part, matrices, vertices, light, overlay, x, y, z, yScale);
    }

    /** Срез — фиолетовое нутро эндермена на плоскости разреза. */
    private static void cutFace(MatrixStack matrices, VertexConsumerProvider consumers, int light, float y) {
        VertexConsumer cut = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ENDER_CUT));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        float yy = y / 16.0F, x0 = -4.0F / 16.0F, x1 = 4.0F / 16.0F, z0 = -2.0F / 16.0F, z1 = 2.0F / 16.0F;
        float ny = y > 0 ? 1.0F : -1.0F;
        cut.vertex(m, x0, yy, z0).color(-1).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, ny, 0.0F);
        cut.vertex(m, x1, yy, z0).color(-1).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, ny, 0.0F);
        cut.vertex(m, x1, yy, z1).color(-1).texture(1.0F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, ny, 0.0F);
        cut.vertex(m, x0, yy, z1).color(-1).texture(0.0F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, ny, 0.0F);
    }

    @Override
    public Identifier getTexture(SeveredPartEntity entity) {
        return ENDER_CUT;
    }
}
