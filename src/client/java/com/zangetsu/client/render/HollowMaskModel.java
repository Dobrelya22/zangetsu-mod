package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/**
 * Маска Пустого: костяной шлем с алыми полосами и прорезями глаз и длинные рога вперёд.
 * Раскладка текстуры — в tools/textures.cs (HollowMask).
 */
public class HollowMaskModel {
    public static final EntityModelLayer LAYER = new EntityModelLayer(ZangetsuMod.id("hollow_mask"), "main");

    /** Всё, что крепится к голове (шлем и рога). */
    public final ModelPart head;
    public final ModelPart leftHorn;
    public final ModelPart rightHorn;

    public HollowMaskModel(ModelPart root) {
        this.head = root.getChild("head");
        this.leftHorn = this.head.getChild("left_horn");
        this.rightHorn = this.head.getChild("right_horn");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData head = root.addChild("head", ModelPartBuilder.create(), ModelTransform.NONE);
        head.addChild("helmet", ModelPartBuilder.create().uv(0, 10).cuboid(-4.0F, -8.0F, -4.0F, 8, 8, 8, new Dilation(0.6F)),
                ModelTransform.NONE);
        addHorn(head, 1.0F);
        addHorn(head, -1.0F);
        return TexturedModelData.of(data, 64, 64);
    }

    /** Рог: от виска вперёд и чуть вверх, тремя сужающимися звеньями. */
    private static void addHorn(ModelPartData head, float side) {
        String name = side > 0 ? "left_horn" : "right_horn";
        ModelPartBuilder base = ModelPartBuilder.create().uv(32, 10);
        if (side < 0) {
            base.mirrored();
        }
        ModelPartData h1 = head.addChild(name, base.cuboid(-1.0F, -1.0F, -6.0F, 2, 2, 6),
                ModelTransform.of(3.4F * side, -7.0F, -2.5F, -0.2F, -0.25F * side, 0.0F));
        ModelPartData h2 = h1.addChild("mid", ModelPartBuilder.create().uv(32, 18).cuboid(-0.75F, -0.75F, -6.0F, 1.5F, 1.5F, 6.0F),
                ModelTransform.of(0.0F, 0.0F, -5.6F, 0.05F, 0.15F * side, 0.0F));
        h2.addChild("tip", ModelPartBuilder.create().uv(48, 10).cuboid(-0.5F, -0.5F, -4.0F, 1, 1, 4),
                ModelTransform.of(0.0F, 0.0F, -5.6F, 0.15F, 0.1F * side, 0.0F));
    }
}
