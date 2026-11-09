package com.zangetsu.fabric;

import com.mojang.serialization.Codec;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.platform.Platform;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;

/** Данные мода во вложениях Fabric API. Ключи те же, что и до разделения на загрузчики: старые миры читаются как есть. */
public final class FabricPlatform implements Platform {
    /** Связь игрока с мечом: дружба, форма Пустого, сон. Переживает смерть. */
    public static final AttachmentType<ZangetsuData> PLAYER = AttachmentRegistry.<ZangetsuData>builder()
            .persistent(ZangetsuData.CODEC)
            .copyOnDeath()
            .initializer(ZangetsuData::new)
            .buildAndRegister(ZangetsuMod.id("player"));

    /** Отрубленные конечности моба. */
    public static final AttachmentType<Integer> LIMBS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .buildAndRegister(ZangetsuMod.id("limbs"));

    @Override
    public ZangetsuData data(PlayerEntity player) {
        return player.getAttachedOrCreate(PLAYER);
    }

    @Override
    public void setData(PlayerEntity player, ZangetsuData data) {
        player.setAttached(PLAYER, data);
    }

    @Override
    public int limbs(LivingEntity entity) {
        Integer value = entity.getAttached(LIMBS);
        return value == null ? 0 : value;
    }

    @Override
    public void setLimbs(LivingEntity entity, int limbs) {
        entity.setAttached(LIMBS, limbs);
    }

    @Override
    public void send(ServerPlayerEntity player, CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTrackers(Entity entity, CustomPayload payload) {
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(entity)) {
            if (viewer != entity) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    @Override
    public void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        player.networkHandler.sendPacket(packet);
    }

    @Override
    public String loader() {
        return "fabric";
    }
}
