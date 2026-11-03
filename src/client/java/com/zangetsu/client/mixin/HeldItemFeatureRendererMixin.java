package com.zangetsu.client.mixin;

import com.zangetsu.client.render.PlayerPoses;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В покое Зангецу лежит в кулаке лезвием вниз, а в ударе запястье подаёт клинок вперёд — по ходу взмаха. Поворот
 * идёт вокруг точки хвата (середина кулака), поэтому рукоять при этом не выезжает из руки.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    /** Где кулак держит меч — та же точка, что translation в модели предмета (пиксели → блоки). */
    @Unique
    private static final float zangetsu$FIST_Y = -2.0F / 16.0F;
    @Unique
    private static final float zangetsu$FIST_Z = 1.4F / 16.0F;

    @Inject(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void zangetsu$bladeForward(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && stack.isOf(ModItems.ZANGETSU)) {
            float flip = PlayerPoses.bladeForward(player);
            if (flip > 0.0F) {
                matrices.translate(0.0F, zangetsu$FIST_Y, zangetsu$FIST_Z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-PlayerPoses.BLADE_FLIP * flip));
                matrices.translate(0.0F, -zangetsu$FIST_Y, -zangetsu$FIST_Z);
            }
        }
    }
}
