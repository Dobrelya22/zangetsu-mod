package com.zangetsu.client.mixin;

import com.zangetsu.client.render.EyeTracker;
import com.zangetsu.client.render.FirstPersonMotion;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Глазу меча нужно знать, кто его держит: от него он ищет мобов, чтобы на них смотреть. От первого лица
 * Зангецу двигается по своим анимациям — взмахи через весь экран вместо ванильного тычка.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private Entity zangetsu$previousHolder;

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void zangetsu$rememberHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
            MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        this.zangetsu$previousHolder = EyeTracker.push(entity);
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void zangetsu$forgetHolder(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, boolean leftHanded,
            MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        EyeTracker.pop(this.zangetsu$previousHolder);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void zangetsu$swingFirstPerson(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
            ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!item.isOf(ModItems.ZANGETSU) || player.isUsingSpyglass() || player.isUsingItem()) {
            return;
        }
        boolean right = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        int side = right ? 1 : -1;
        matrices.push();
        // Где рука держит предмет (как у ванильной руки с предметом), дальше — свои взмахи.
        matrices.translate(side * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
        FirstPersonMotion.apply(matrices, player, side, hand == Hand.MAIN_HAND ? swingProgress : 0.0F, tickDelta);
        ((HeldItemRenderer) (Object) this).renderItem(player, item,
                right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND, !right, matrices,
                consumers, light);
        matrices.pop();
        ci.cancel();
    }
}
