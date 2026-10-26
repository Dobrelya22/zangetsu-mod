package com.zangetsu.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.client.render.CrescentMoon;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Туман и горизонт тоже краснеют, пока в небе кровавая луна. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("RETURN"))
    private static void zangetsu$bloodFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        float k = CrescentMoon.intensity() * 0.8F;
        if (k > 0.0F) {
            red = MathHelper.lerp(k, red, 0.26F);
            green = MathHelper.lerp(k, green, 0.02F);
            blue = MathHelper.lerp(k, blue, 0.03F);
            RenderSystem.clearColor(red, green, blue, 0.0F);
        }
    }
}
