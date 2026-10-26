package com.zangetsu.client.mixin;

import com.zangetsu.client.render.EyeTracker;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Глазу меча нужно знать, кто его держит: от него он ищет мобов, чтобы на них смотреть. */
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
}
