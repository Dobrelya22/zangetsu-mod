package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import com.zangetsu.trailer.HeroSkin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Герой трейлера — в чёрном косодэ синигами. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void trailer$heroSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (Director.running()) {
            cir.setReturnValue(HeroSkin.textures());
        }
    }
}
