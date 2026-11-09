package com.zangetsu.neoforge;

import com.mojang.serialization.Codec;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Данные мода в data attachments NeoForge под теми же ключами zangetsu:player и zangetsu:limbs. */
public final class NeoForgePlatform implements Platform {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZangetsuMod.MOD_ID);

    /** Связь игрока с мечом: дружба, форма Пустого, сон. Переживает смерть. */
    public static final Supplier<AttachmentType<ZangetsuData>> PLAYER = ATTACHMENTS.register("player",
            () -> AttachmentType.builder(ZangetsuData::new).serialize(ZangetsuData.CODEC).copyOnDeath().build());

    /** Отрубленные конечности моба. */
    public static final Supplier<AttachmentType<Integer>> LIMBS = ATTACHMENTS.register("limbs",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    @Override
    public ZangetsuData data(PlayerEntity player) {
        return player.getData(PLAYER);
    }

    @Override
    public void setData(PlayerEntity player, ZangetsuData data) {
        player.setData(PLAYER, data);
    }

    @Override
    public int limbs(LivingEntity entity) {
        return entity.hasData(LIMBS) ? entity.getData(LIMBS) : 0;
    }

    @Override
    public void setLimbs(LivingEntity entity, int limbs) {
        if (limbs == 0) {
            entity.removeData(LIMBS);
        } else {
            entity.setData(LIMBS, limbs);
        }
    }

    @Override
    public void send(ServerPlayerEntity player, CustomPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToTrackers(Entity entity, CustomPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    @Override
    public void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        player.networkHandler.send(packet);
    }

    @Override
    public String loader() {
        return "neoforge";
    }
}
