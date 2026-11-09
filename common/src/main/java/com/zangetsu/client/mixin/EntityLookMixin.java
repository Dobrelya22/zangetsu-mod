package com.zangetsu.client.mixin;

import com.zangetsu.client.ZangetsuCombat;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пока игрок идёт к мечу и берёт его, мышь не крутит взгляд: куда смотреть, решает сцена. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void zangetsu$sceneLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (ZangetsuCombat.holdsLook((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
