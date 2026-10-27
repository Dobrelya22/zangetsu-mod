package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Гроза остаётся (тёмное небо, молнии), а снежные хлопья у самой камеры забивали кадр шумом. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void trailer$noFlakes(LightmapTextureManager manager, float tickDelta, double x, double y, double z, CallbackInfo ci) {
        if (Director.running()) {
            ci.cancel();
        }
    }
}
