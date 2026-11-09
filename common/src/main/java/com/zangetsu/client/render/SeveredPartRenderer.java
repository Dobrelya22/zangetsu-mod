package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.ClientState;
import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.logic.Fx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Отрубленный кусок рисуется настоящей моделью того моба, от которого отлетел, с его же текстурой, но обрезанной плоскостями разреза
 * ({@link SliceConsumer}): рука зомби — зомбиной рукой, половина эндермена — половиной эндермена с глазом и фиолетовым нутром на срезе.
 */
public class SeveredPartRenderer extends EntityRenderer<SeveredPartEntity> {
    private static final Identifier ENDER_EYES = Identifier.ofVanilla("textures/entity/enderman/enderman_eyes.png");
    private static final Identifier FALLBACK = ZangetsuMod.id("textures/entity/cut_ender.png");
    private static final Map<String, LivingEntity> DUMMIES = new HashMap<>();
    private static World dummyWorld;

    public SeveredPartRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
    }

    /** Образец моба нужного вида — только чтобы взять у его отрисовщика модель и текстуру. Его номер не совпадает ни с чьим настоящим. */
    private static LivingEntity dummy(String path, World world) {
        if (dummyWorld != world) {
            DUMMIES.clear();
            dummyWorld = world;
        }
        return DUMMIES.computeIfAbsent(path, key -> {
            EntityType<?> type = EntityType.get(Identifier.ofVanilla(key).toString()).orElse(null);
            Entity entity = type == null ? null : type.create(world);
            if (!(entity instanceof LivingEntity living)) {
                return null;
            }
            living.setId(-2 - DUMMIES.size());
            return living;
        });
    }

    @Override
    public boolean shouldRender(SeveredPartEntity entity, Frustum frustum, double x, double y, double z) {
        // Точка опоры куска может быть за краем экрана, а сам он — ещё виден.
        return frustum.isVisible(entity.getBoundingBox().expand(2.0));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(SeveredPartEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        LivingEntity source = dummy(entity.getSource(), entity.getWorld());
        if (source == null || !(this.dispatcher.getRenderer(source) instanceof LivingEntityRenderer renderer)) {
            return;
        }
        EntityModel model = renderer.getModel();
        // Модель одна на всех мобов вида: ставим позу этого моба с его отсечениями, следующий поставит свою сам.
        model.handSwingProgress = 0.0F;
        model.riding = false;
        model.child = false;
        ClientState.LIMBS.put(source.getId(), entity.getLimbs());
        model.animateModel(source, 0.0F, 0.0F, 0.0F);
        model.setAngles(source, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ClientState.LIMBS.remove(source.getId());

        Vector3f pivot = entity.getPivot();
        float scale = entity.getModelScale();
        float fall = entity.getFall() * MathHelper.RADIANS_PER_DEGREE;
        matrices.push();
        matrices.translate(0.0F, entity.getLift(), 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - entity.getYaw()));
        matrices.multiply(new Quaternionf().rotationAxis(entity.tilt(tickDelta) * MathHelper.RADIANS_PER_DEGREE,
                MathHelper.sin(fall), 0.0F, -MathHelper.cos(fall)));
        matrices.scale(-scale, -scale, scale);
        matrices.translate(-pivot.x / 16.0F, -pivot.y / 16.0F, -pivot.z / 16.0F);

        int overlay = OverlayTexture.DEFAULT_UV;
        float[][] planes = entity.planes();
        Fx.Gore gore = Fx.Gore.of(entity.getSource());
        SliceConsumer slice = new SliceConsumer(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(renderer.getTexture(source))), matrices.peek(), planes);
        model.render(matrices, slice, light, overlay, -1);
        slice.caps(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(ZangetsuMod.id(gore.cut))), overlay, light);
        if (gore == Fx.Gore.ENDER) {
            model.render(matrices, new SliceConsumer(consumers.getBuffer(RenderLayer.getEyes(ENDER_EYES)), matrices.peek(), planes), 0xF000F0, overlay, -1);
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(SeveredPartEntity entity) {
        return FALLBACK;
    }
}
