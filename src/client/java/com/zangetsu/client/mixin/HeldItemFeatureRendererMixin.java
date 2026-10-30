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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В покое Зангецу висит в руке лезвием вниз, а в ударе кулак разворачивает его клинком вперёд — по ходу взмаха,
 * как настоящий тесак.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void zangetsu$bladeForward(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && stack.isOf(ModItems.ZANGETSU)) {
            float flip = PlayerPoses.bladeForward(player);
            if (flip > 0.0F) {
                matrices.translate(0.0F, 0.0F, -0.06F * flip);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F * flip));
            }
        }
    }
}
