package com.zangetsu.mixin;

import com.zangetsu.logic.Limbs;
import com.zangetsu.registry.ModAttachments;
import net.minecraft.entity.mob.EndermanEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Эндермен с подрубленным коленом больше не может телепортироваться — стоит на месте. */
@Mixin(EndermanEntity.class)
public abstract class EndermanEntityMixin {
    @Inject(method = "teleportTo(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void zangetsu$noKneesNoTeleport(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        EndermanEntity self = (EndermanEntity) (Object) this;
        if ((ModAttachments.limbs(self) & (Limbs.LEFT_KNEE | Limbs.RIGHT_KNEE)) != 0) {
            cir.setReturnValue(false);
        }
    }
}
