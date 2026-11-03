package com.zangetsu.client.mixin;

import com.zangetsu.client.render.PlayerPoses;
import com.zangetsu.client.render.SlashTrails;
import com.zangetsu.client.render.ZangetsuItemRenderer;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Меч в руке со стороны. В приёме его положение задано целиком: кулак и направление клинка (рука сама приходит к мечу,
 * см. {@link PlayerPoses#swing}); здесь он ставится туда, минуя обычный поворот предмета вслед за рукой. В сценах запястье подаёт
 * клинок вперёд. И то и другое — вокруг точки хвата в середине кулака, поэтому рукоять не выезжает из руки.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    /** Где кулак держит меч — та же точка, что translation в модели предмета (пиксели → блоки). */
    @Unique
    private static final float zangetsu$FIST_Y = -2.0F / 16.0F;
    @Unique
    private static final float zangetsu$FIST_Z = 1.4F / 16.0F;
    /** Поворот предмета в руке из модели предмета: его нужно снять, потому что предмет применит его сам. */
    @Unique
    private static final Quaternionf zangetsu$UNDO_DISPLAY = new Quaternionf()
            .rotationXYZ(0.0F, (float) Math.toRadians(-90.0), (float) Math.toRadians(55.0)).conjugate();
    @Unique
    private final Matrix4f zangetsu$rootPosition = new Matrix4f();
    @Unique
    private final Matrix3f zangetsu$rootNormal = new Matrix3f();

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void zangetsu$rememberRoot(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        this.zangetsu$rootPosition.set(matrices.peek().getPositionMatrix());
        this.zangetsu$rootNormal.set(matrices.peek().getNormalMatrix());
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void zangetsu$placeSword(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
            VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity player) || !stack.isOf(ModItems.ZANGETSU)) {
            return;
        }
        PlayerPoses.SwordPose pose = arm == player.getMainArm() ? PlayerPoses.sword(player) : null;
        if (pose != null) {
            Vector3f fist = new Vector3f(pose.fist()).div(16.0F);
            Quaternionf sword = ZangetsuItemRenderer.swordRotation(pose.blade(), pose.edge());
            matrices.peek().getPositionMatrix().set(this.zangetsu$rootPosition).translate(fist).rotate(sword).rotate(zangetsu$UNDO_DISPLAY)
                    .translate(0.0F, -zangetsu$FIST_Y, -zangetsu$FIST_Z);
            matrices.peek().getNormalMatrix().set(this.zangetsu$rootNormal).rotate(sword).rotate(zangetsu$UNDO_DISPLAY);
            zangetsu$trail(player, pose);
            return;
        }
        float flip = PlayerPoses.bladeForward(player);
        if (flip > 0.0F) {
            matrices.translate(0.0F, zangetsu$FIST_Y, zangetsu$FIST_Z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-PlayerPoses.BLADE_FLIP * flip));
            matrices.translate(0.0F, -zangetsu$FIST_Y, -zangetsu$FIST_Z);
        }
    }

    /** След — настоящий путь клинка: запоминаем, где сейчас его острие и середина. */
    @Unique
    private void zangetsu$trail(PlayerEntity player, PlayerPoses.SwordPose pose) {
        if (pose.trail() <= 0.0F) {
            return;
        }
        Vector3f near = new Vector3f(pose.blade()).mul(ZangetsuItemRenderer.TRAIL_NEAR * ZangetsuItemRenderer.THIRD_PERSON_SCALE).add(pose.fist()).div(16.0F);
        Vector3f far = new Vector3f(pose.blade()).mul(ZangetsuItemRenderer.TRAIL_FAR * ZangetsuItemRenderer.THIRD_PERSON_SCALE).add(pose.fist()).div(16.0F);
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        Vector3f a = this.zangetsu$rootPosition.transformPosition(near), b = this.zangetsu$rootPosition.transformPosition(far);
        SlashTrails.sampleWorld(player.getId(), a.x + camera.x, a.y + camera.y, a.z + camera.z, b.x + camera.x, b.y + camera.y, b.z + camera.z, pose.color(), pose.trail());
    }
}
