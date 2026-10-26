package com.zangetsu.client;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.render.ArrowHalfRenderer;
import com.zangetsu.client.render.GetsugaRenderer;
import com.zangetsu.client.render.HollowFeatureRenderer;
import com.zangetsu.client.render.HollowMaskModel;
import com.zangetsu.client.render.SeveredPartRenderer;
import com.zangetsu.client.render.SpiritModel;
import com.zangetsu.client.render.SpiritRenderer;
import com.zangetsu.client.render.ThrownZangetsuRenderer;
import com.zangetsu.logic.Eye;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.network.PlayerStatePayload;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModEntities;
import com.zangetsu.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

public class ZangetsuClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(SpiritModel.LAYER, SpiritModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(HollowMaskModel.LAYER, HollowMaskModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.SPIRIT, SpiritRenderer::new);
        EntityRendererRegistry.register(ModEntities.THROWN_ZANGETSU, ThrownZangetsuRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEVERED_PART, SeveredPartRenderer::new);
        EntityRendererRegistry.register(ModEntities.ARROW_HALF, ArrowHalfRenderer::new);
        EntityRendererRegistry.register(ModEntities.GETSUGA, GetsugaRenderer::new);

        // Текстура меча зависит от глаза: закрыт / открыт / горит / сердится.
        ModelPredicateProviderRegistry.register(ModItems.ZANGETSU, ZangetsuMod.id("eye"), (stack, world, entity, seed) -> {
            Integer eye = stack.get(ModComponents.EYE);
            return eye == null ? 0.0F : eye / 3.0F;
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new HollowFeatureRenderer(playerRenderer, context.getModelLoader()));
            }
        });

        KeyBindings.register();
        HudRenderCallback.EVENT.register(ZangetsuHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientState.ticks++;
            KeyBindings.tick(client);
            CameraDirector.tick(client);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ClientState.forget(entity.getId()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            CameraDirector.reset();
        });

        registerReceivers();
    }

    private static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerStatePayload.ID, (payload, context) -> ClientState.state = payload);
        ClientPlayNetworking.registerGlobalReceiver(LimbPayload.ID, (payload, context) -> {
            if (payload.limbs() == 0) {
                ClientState.LIMBS.remove(payload.entityId());
            } else {
                ClientState.LIMBS.put(payload.entityId(), payload.limbs());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(HollowPayload.ID, (payload, context) -> {
            if (payload.active()) {
                ClientState.HOLLOW.add(payload.entityId());
            } else {
                ClientState.HOLLOW.remove(payload.entityId());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(AnimationPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            Entity entity = client.world == null ? null : client.world.getEntityById(payload.entityId());
            if (entity == null) {
                return;
            }
            ClientState.ANIMATIONS.put(payload.entityId(), new ClientState.Animation(payload.animation(), entity.age, payload.duration()));
            if (entity == client.player) {
                // У себя — камера облетает сбоку; превращение — спереди, чтобы видеть маску.
                CameraDirector.begin(payload.duration() + 8, switch (payload.animation()) {
                    case AnimationPayload.TOUCH_HANDLE -> new CameraDirector.Shot(60.0F, 90.0F, 14.0F, 4.0F, 1.2F);
                    case AnimationPayload.EXECUTE -> new CameraDirector.Shot(65.0F, 95.0F, 10.0F, 4.6F, 1.3F);
                    case AnimationPayload.SLASH -> new CameraDirector.Shot(-65.0F, -100.0F, 8.0F, 5.6F, 1.7F);
                    case AnimationPayload.DREAM_PULL -> new CameraDirector.Shot(55.0F, 105.0F, 14.0F, 4.4F, 1.2F);
                    case AnimationPayload.HOLLOW -> new CameraDirector.Shot(150.0F, 205.0F, 4.0F, 3.6F, 0.0F);
                    default -> new CameraDirector.Shot(90.0F, 90.0F, 10.0F, 4.0F, 0.0F);
                });
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ClientFxPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            switch (payload.effect()) {
                case ClientFxPayload.FLOATING_SWORD -> {
                    ItemStack sword = new ItemStack(ModItems.ZANGETSU);
                    sword.set(ModComponents.EYE, Eye.OPEN);
                    client.gameRenderer.showFloatingItem(sword);
                }
                case ClientFxPayload.WHITE_FLASH, ClientFxPayload.RED_FLASH -> ClientState.flash(payload.effect(), payload.duration());
                case ClientFxPayload.SHAKE -> {
                    if (client.player != null) {
                        client.player.maxHurtTime = 10;
                        client.player.hurtTime = 10;
                    }
                }
                default -> {
                }
            }
        });
    }
}
