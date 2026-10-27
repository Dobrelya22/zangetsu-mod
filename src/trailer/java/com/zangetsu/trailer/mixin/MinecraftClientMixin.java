package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Готовый кадр забираем прямо перед показом на экране. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V"))
    private void trailer$capture(boolean tick, CallbackInfo ci) {
        Director.endFrame();
    }
}
