package com.zangetsu.mixin;

import com.zangetsu.logic.Limbs;
import com.zangetsu.registry.ModAttachments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Без обеих рук зомби, пиглин или скелет больше не может ударить. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "tryAttack", at = @At("HEAD"), cancellable = true)
    private void zangetsu$noArmsNoAttack(Entity target, CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity) (Object) this;
        if (Limbs.isHumanoid(self) && Limbs.disarmed(ModAttachments.limbs(self))) {
            cir.setReturnValue(false);
        }
    }
}
