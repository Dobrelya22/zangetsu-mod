package com.zangetsu.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

/**
 * Всё, что нужно отрисовке в мире, в одном виде для обоих загрузчиков.
 * positionMatrix — поворот взгляда, matrixStack — стопка без сдвига камеры, consumers — буферы, которые игра дорисует сама.
 */
public record WorldRenderInfo(ClientWorld world, Camera camera, Matrix4f positionMatrix, MatrixStack matrixStack,
        VertexConsumerProvider consumers, float tickDelta) {
}
