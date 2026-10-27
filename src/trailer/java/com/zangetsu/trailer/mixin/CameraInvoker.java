package com.zangetsu.trailer.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Доступ к положению и повороту камеры — кино-камера трейлера ставит их сама. */
@Mixin(Camera.class)
public interface CameraInvoker {
    @Invoker("setPos")
    void trailer$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void trailer$setRotation(float yaw, float pitch);
}
