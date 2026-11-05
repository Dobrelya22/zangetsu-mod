package com.zangetsu.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zangetsu.client.render.CrescentMoon;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Небо в форме Пустого: солнце, заря и обычная луна гаснут, а высоко проступает кровавая серповидная
 * (рисуется после солнца и луны, перед звёздами).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getStarBrightness(F)F"))
    private void zangetsu$crescentMoon(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog,
            Runnable fogCallback, CallbackInfo ci) {
        CrescentMoon.render(positionMatrix, tickDelta);
    }

/** В бледной пустоте нет ни облаков, ни дождя, ни воды. */
@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
private void zangetsu$noClouds(CallbackInfo ci) {
    if (com.zangetsu.client.CallingScene.voidFull()) {
        ci.cancel();
    }
}

@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
private void zangetsu$noWeather(CallbackInfo ci) {
    if (com.zangetsu.client.CallingScene.voidFull()) {
        ci.cancel();
    }
}

@Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
private void zangetsu$noWater(net.minecraft.client.render.RenderLayer layer, double x, double y, double z, Matrix4f a, Matrix4f b, CallbackInfo ci) {
    if ((layer == net.minecraft.client.render.RenderLayer.getTranslucent() || layer == net.minecraft.client.render.RenderLayer.getTripwire()) && com.zangetsu.client.CallingScene.voidFull()) {
        ci.cancel();
    }
}

/** Первые три вызова отрисовки в renderSky: закатное сияние, солнце и луна. */
    @WrapOperation(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V", ordinal = 0),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V", ordinal = 1),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V", ordinal = 2)
            })
    private void zangetsu$hideSunAndMoon(BuiltBuffer buffer, Operation<Void> original) {
        float shown = CrescentMoon.shown();
        if (shown <= 0.0F) {
            original.call(buffer);
            return;
        }
        if (shown >= 0.97F) {
            buffer.close();
            return;
        }
        float[] color = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        float r = color[0], g = color[1], b = color[2], a = color[3];
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a * (1.0F - shown));
        original.call(buffer);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, g, b, a);
    }
}
