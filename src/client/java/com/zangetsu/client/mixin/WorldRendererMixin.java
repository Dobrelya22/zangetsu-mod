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
 * Небо в форме Пустого: обычная луна исчезает, а высоко проступает кровавая серповидная
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

    /** Третий вызов отрисовки в renderSky — луна (до неё: закатное сияние и солнце). */
    @WrapOperation(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BufferRenderer;drawWithGlobalProgram(Lnet/minecraft/client/render/BuiltBuffer;)V",
                    ordinal = 2))
    private void zangetsu$hideMoon(BuiltBuffer buffer, Operation<Void> original) {
        if (CrescentMoon.hidesVanillaMoon()) {
            buffer.close();
            return;
        }
        original.call(buffer);
    }
}
