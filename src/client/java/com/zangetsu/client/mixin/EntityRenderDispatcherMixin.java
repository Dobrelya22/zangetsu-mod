package com.zangetsu.client.mixin;

import com.zangetsu.client.CallingScene;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** В бледной пустоте остались только игрок и Яхве, и свет на них одинаковый, где бы игрока ни настиг удар. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void zangetsu$onlyInVoid(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (CallingScene.voidFull() && entity != MinecraftClient.getInstance().player && !(entity instanceof ZangetsuSpiritEntity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getLight", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void zangetsu$voidLight(E entity, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        if (CallingScene.voidAmount() > 0.5F) {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }
}
