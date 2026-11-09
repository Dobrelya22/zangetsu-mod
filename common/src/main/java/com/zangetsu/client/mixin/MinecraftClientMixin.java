package com.zangetsu.client.mixin;

import com.zangetsu.client.ZangetsuCombat;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** С Зангецу нажатие атаки начинает замах, а урон приходит, когда клинок дойдёт до цели. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void zangetsu$windUp(CallbackInfoReturnable<Boolean> cir) {
        if (ZangetsuCombat.onAttackKey((MinecraftClient) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
