package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Белый дымок после смерти моба у самой камеры превращался в белые пятна на полкадра — в трейлере его нет. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "addDeathParticles", at = @At("HEAD"), cancellable = true)
    private void trailer$noDeathPuff(CallbackInfo ci) {
        if (Director.running()) {
            ci.cancel();
        }
    }
}
