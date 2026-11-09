package com.zangetsu.fabric;

import com.zangetsu.client.KeyBindings;
import com.zangetsu.client.WorldRenderInfo;
import com.zangetsu.client.ZangetsuClient;
import com.zangetsu.client.ZangetsuHud;
import com.zangetsu.network.ZangetsuNetworking;
import com.zangetsu.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.network.packet.CustomPayload;

/** Fabric, клиент: подключить общий клиент мода к событиям Fabric API. */
public class ZangetsuFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.zangetsu.client.ClientNet.set(ClientPlayNetworking::send);
        ZangetsuClient.registerEntityRenderers(EntityRendererRegistry::register);
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.ZANGETSU, ZangetsuClient.ITEM_RENDERER::render);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                ZangetsuClient.addPlayerFeatures(playerRenderer, helper::register);
            }
        });
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(ZangetsuClient.YHWACH_SHADER, ZangetsuClient.YHWACH_FORMAT, ZangetsuClient::shaderLoaded));
        KeyBindingHelper.registerKeyBinding(KeyBindings.hollow);
        HudRenderCallback.EVENT.register(ZangetsuHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(ZangetsuClient::clientTick);
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> ZangetsuClient.beforeEntities(info(context)));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> ZangetsuClient.afterEntities(info(context)));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> ZangetsuClient.entityUnloaded(entity));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ZangetsuClient.disconnected());

        for (ZangetsuNetworking.Type<?> type : ZangetsuNetworking.TO_CLIENT) {
            receive(type);
        }
    }

    private static <T extends CustomPayload> void receive(ZangetsuNetworking.Type<T> type) {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (payload, context) -> ZangetsuClient.receive(payload));
    }

    public static WorldRenderInfo info(WorldRenderContext context) {
        return new WorldRenderInfo(context.world(), context.camera(), context.positionMatrix(), context.matrixStack(), context.consumers(),
                context.tickCounter().getTickDelta(false));
    }
}
