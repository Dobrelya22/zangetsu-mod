package com.zangetsu.client.mixin;

import com.zangetsu.client.CameraDirector;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В сценах камера облетает игрока сбоку (ракурс задаёт {@link CameraDirector}). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float lastCameraY;

    @Shadow
    private float cameraY;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void moveBy(float f, float g, float h);

    @Shadow
    private float clipToSpace(float f) {
        throw new AssertionError();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void zangetsu$cinematic(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta,
            CallbackInfo ci) {
        if (!thirdPerson) {
            return;
        }
        float[] frame = CameraDirector.frame(focused, tickDelta);
        if (frame == null) {
            return;
        }
        float yaw = focused.getYaw(tickDelta);
        // Центр кадра — между игроком и тем, к чему он обращён (меч, моб).
        double yawRad = Math.toRadians(yaw);
        double ahead = frame[3];
        this.setRotation(yaw + frame[0], frame[1]);
        this.setPos(MathHelper.lerp(tickDelta, focused.prevX, focused.getX()) - Math.sin(yawRad) * ahead,
                MathHelper.lerp(tickDelta, focused.prevY, focused.getY()) + MathHelper.lerp(tickDelta, this.lastCameraY, this.cameraY) - 0.35,
                MathHelper.lerp(tickDelta, focused.prevZ, focused.getZ()) + Math.cos(yawRad) * ahead);
        this.moveBy(-this.clipToSpace(frame[2]), 0.0F, 0.0F);
    }
}
