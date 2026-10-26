package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Таинственная фигура: тёмное тело в рваном плаще, голова с загнутыми рогами, длинные руки с когтями,
 * перепончатые крылья и хвост. Раскладка текстуры — в tools/textures.cs (Spirit).
 */
public class SpiritModel extends EntityModel<ZangetsuSpiritEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(ZangetsuMod.id("spirit"), "main");
    private static final int TAIL_SEGMENTS = 5;

    public final ModelPart body;
    public final ModelPart head;
    public final ModelPart rightArm;
    public final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart coatBack;
    private final ModelPart coatRight;
    private final ModelPart coatLeft;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart[] tail = new ModelPart[TAIL_SEGMENTS];

    public SpiritModel(ModelPart root) {
        super(RenderLayer::getEntityTranslucent);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightLeg = this.body.getChild("right_leg");
        this.leftLeg = this.body.getChild("left_leg");
        this.coatBack = this.body.getChild("coat_back");
        this.coatRight = this.body.getChild("coat_right");
        this.coatLeft = this.body.getChild("coat_left");
        this.rightWing = this.body.getChild("right_wing");
        this.rightWingTip = this.rightWing.getChild("tip");
        this.leftWing = this.body.getChild("left_wing");
        this.leftWingTip = this.leftWing.getChild("tip");
        ModelPart segment = this.body;
        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            segment = segment.getChild("tail" + i);
            this.tail[i] = segment;
        }
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-6.0F, -18.0F, -3.0F, 12, 18, 6),
                ModelTransform.pivot(0.0F, 6.0F, 0.0F));

        ModelPartData head = body.addChild("head", ModelPartBuilder.create().uv(0, 24).cuboid(-4.5F, -9.0F, -4.5F, 9, 9, 9),
                ModelTransform.pivot(0.0F, -18.0F, 0.0F));
        addHorn(head, 1.0F);
        addHorn(head, -1.0F);

        ModelPartData rightArm = body.addChild("right_arm", ModelPartBuilder.create().uv(64, 0).cuboid(-2.0F, -2.0F, -2.0F, 4, 20, 4),
                ModelTransform.pivot(-8.0F, -16.0F, 0.0F));
        ModelPartData leftArm = body.addChild("left_arm", ModelPartBuilder.create().uv(64, 0).mirrored().cuboid(-2.0F, -2.0F, -2.0F, 4, 20, 4),
                ModelTransform.pivot(8.0F, -16.0F, 0.0F));
        for (ModelPartData arm : new ModelPartData[]{rightArm, leftArm}) {
            arm.addChild("claws", ModelPartBuilder.create().uv(82, 0)
                            .cuboid(-1.8F, 0.0F, -1.5F, 1, 4, 1)
                            .cuboid(-0.5F, 0.0F, -1.8F, 1, 5, 1)
                            .cuboid(0.8F, 0.0F, -1.5F, 1, 4, 1),
                    ModelTransform.pivot(0.0F, 18.0F, 0.0F));
        }

        body.addChild("right_leg", ModelPartBuilder.create().uv(88, 0).cuboid(-2.5F, 0.0F, -2.5F, 5, 18, 5),
                ModelTransform.pivot(-3.0F, 0.0F, 0.0F));
        body.addChild("left_leg", ModelPartBuilder.create().uv(88, 0).mirrored().cuboid(-2.5F, 0.0F, -2.5F, 5, 18, 5),
                ModelTransform.pivot(3.0F, 0.0F, 0.0F));

        body.addChild("coat_back", ModelPartBuilder.create().uv(0, 42).cuboid(-6.5F, 0.0F, 0.0F, 13, 18, 1),
                ModelTransform.pivot(0.0F, -2.0F, 3.2F));
        body.addChild("coat_right", ModelPartBuilder.create().uv(28, 42).cuboid(-3.0F, 0.0F, -0.5F, 6, 18, 1),
                ModelTransform.pivot(-3.3F, -2.0F, -3.7F));
        body.addChild("coat_left", ModelPartBuilder.create().uv(28, 42).mirrored().cuboid(-3.0F, 0.0F, -0.5F, 6, 18, 1),
                ModelTransform.pivot(3.3F, -2.0F, -3.7F));

        addWing(body, "right_wing", -1.0F);
        addWing(body, "left_wing", 1.0F);

        ModelPartData segment = body.addChild("tail0", ModelPartBuilder.create().uv(64, 32).cuboid(-1.5F, -1.5F, 0.0F, 3, 3, 6),
                ModelTransform.of(0.0F, 4.0F, 2.5F, 0.6F, 0.0F, 0.0F));
        for (int i = 1; i < TAIL_SEGMENTS; i++) {
            ModelPartBuilder builder = ModelPartBuilder.create().uv(64, 41).cuboid(-1.25F, -1.25F, 0.0F, 2.5F, 2.5F, 6.0F);
            if (i == TAIL_SEGMENTS - 1) {
                builder.uv(84, 32).cuboid(-2.5F, -0.5F, 5.0F, 5, 1, 4);
            }
            segment = segment.addChild("tail" + i, builder, ModelTransform.pivot(0.0F, 0.0F, 5.5F));
        }
        return TexturedModelData.of(data, 128, 128);
    }

    /** Рог из трёх звеньев: в сторону, потом вверх, потом остриём вперёд. side = +1 — левый, −1 — правый. */
    private static void addHorn(ModelPartData head, float side) {
        String name = side > 0 ? "left_horn" : "right_horn";
        ModelPartBuilder base = ModelPartBuilder.create().uv(40, 0);
        if (side < 0) {
            base.mirrored();
        }
        ModelPartData h1 = head.addChild(name, base.cuboid(-1.5F, -6.0F, -1.5F, 3, 6, 3),
                ModelTransform.of(3.5F * side, -7.5F, -0.5F, -0.15F, 0.0F, 1.0F * side));
        ModelPartData h2 = h1.addChild("mid", ModelPartBuilder.create().uv(52, 0).cuboid(-1.0F, -6.0F, -1.0F, 2, 6, 2),
                ModelTransform.of(0.0F, -5.5F, 0.0F, -0.1F, 0.0F, -0.8F * side));
        h2.addChild("tip", ModelPartBuilder.create().uv(60, 0).cuboid(-0.5F, -5.0F, -0.5F, 1, 5, 1),
                ModelTransform.of(0.0F, -5.5F, 0.0F, -0.55F, 0.0F, -0.5F * side));
    }

    /** Крыло: кость с перепонкой и складывающийся кончик. side = −1 — правое (растёт к −x), +1 — левое. */
    private static void addWing(ModelPartData body, String name, float side) {
        ModelPartBuilder inner = ModelPartBuilder.create();
        ModelPartBuilder outer = ModelPartBuilder.create();
        if (side < 0) {
            inner.mirrored();
            outer.mirrored();
        }
        float x = side < 0 ? -16.0F : 0.0F;
        inner.uv(0, 64).cuboid(x, -1.0F, -1.0F, 16, 2, 2).uv(0, 68).cuboid(x, 0.0F, 0.0F, 16, 20, 0, Dilation.NONE);
        ModelPartData wing = body.addChild(name, inner, ModelTransform.pivot(3.0F * side, -15.0F, 3.0F));
        float tx = side < 0 ? -14.0F : 0.0F;
        outer.uv(36, 64).cuboid(tx, -0.75F, -0.75F, 14.0F, 1.5F, 1.5F).uv(32, 68).cuboid(tx, 0.0F, 0.0F, 14, 24, 0, Dilation.NONE);
        wing.addChild("tip", outer, ModelTransform.pivot(16.0F * side, 0.0F, 0.0F));
    }

    @Override
    public void setAngles(ZangetsuSpiritEntity entity, float limbAngle, float limbDistance, float time, float headYaw, float headPitch) {
        this.body.pivotY = 6.0F + MathHelper.sin(time * 0.1F) * 0.6F;
        this.body.pitch = 0.0F;
        this.head.pitch = 0.35F;
        this.head.yaw = 0.0F;

        float flap = MathHelper.sin(time * 0.22F);
        this.rightWing.yaw = 0.55F + 0.12F * flap;
        this.rightWing.roll = -0.25F - 0.35F * flap;
        this.rightWingTip.roll = 0.45F + 0.25F * MathHelper.sin(time * 0.22F - 0.7F);
        this.leftWing.yaw = -this.rightWing.yaw;
        this.leftWing.roll = -this.rightWing.roll;
        this.leftWingTip.roll = -this.rightWingTip.roll;

        for (int i = 0; i < TAIL_SEGMENTS; i++) {
            this.tail[i].yaw = 0.28F * MathHelper.sin(time * 0.18F - i * 0.7F);
            this.tail[i].pitch = (i == 0 ? 0.6F : -0.12F) + 0.05F * MathHelper.cos(time * 0.15F - i);
        }
        this.coatBack.pitch = 0.18F + 0.1F * MathHelper.sin(time * 0.3F);
        this.coatRight.pitch = -0.08F - 0.05F * MathHelper.sin(time * 0.33F + 1.0F);
        this.coatLeft.pitch = -0.08F - 0.05F * MathHelper.sin(time * 0.31F + 2.0F);
        this.rightLeg.roll = 0.08F;
        this.leftLeg.roll = -0.08F;
        this.leftArm.pitch = -0.25F + 0.05F * MathHelper.sin(time * 0.12F);
        this.leftArm.roll = -0.35F;

        // Бросок: меч над головой → замах за плечо → резкий бросок вперёд → рука опускается.
        float a = time;
        float raised = -2.6F, back = -3.35F, thrown = -0.55F;
        float armPitch;
        float lean;
        int release = ZangetsuSpiritEntity.RELEASE_TICK;
        if (a < release - 30) {
            armPitch = raised;
            lean = 0.0F;
        } else if (a < release - 6) {
            float t = (a - (release - 30)) / 24.0F;
            armPitch = MathHelper.lerp(easeInOut(t), raised, back);
            lean = -0.18F * easeInOut(t);
        } else if (a < release + 3) {
            float t = (a - (release - 6)) / 9.0F;
            armPitch = MathHelper.lerp(t * t, back, thrown);
            lean = MathHelper.lerp(t, -0.18F, 0.3F);
        } else {
            float t = Math.min(1.0F, (a - (release + 3)) / 25.0F);
            armPitch = MathHelper.lerp(easeInOut(t), thrown, -0.2F);
            lean = MathHelper.lerp(t, 0.3F, 0.05F);
        }
        this.rightArm.pitch = armPitch;
        this.rightArm.roll = 0.18F;
        this.rightArm.yaw = 0.0F;
        this.body.pitch = lean;
    }

    private static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.body.render(matrices, vertices, light, overlay, color);
    }
}
