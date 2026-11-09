package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import com.zangetsu.trailer.Overlay;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Монтажные эффекты — поверх всего кадра; готовый кадр забираем прямо перед показом на экране. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;endWrite()V"))
    private void trailer$overlay(boolean tick, CallbackInfo ci) {
        Overlay.render();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V"))
    private void trailer$capture(boolean tick, CallbackInfo ci) {
        Director.endFrame();
    }
}
