package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Моб, забредший вплотную к объективу, не рисуется: оператор шагнул бы в сторону, а не снимал бы его спину. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void trailer$clearLens(E entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (Director.running() && entity instanceof MobEntity && entity.getBoundingBox().expand(1.0).contains(new Vec3d(x, y, z))) {
            cir.setReturnValue(false);
        }
    }
}
